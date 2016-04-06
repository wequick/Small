/*
 * Copyright 2015-present wequick.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package net.wequick.small;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;

import net.wequick.small.util.BundleParser;
import net.wequick.small.util.FileUtils;
import net.wequick.small.webkit.WebViewPool;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * This class consists exclusively of methods that operate on apk plugin.
 *
 * <p>All the <tt>bundles</tt> are loaded by <tt>bundle.json</tt>.
 * The <tt>bundle.json</tt> format and usage are in
 * <a href="https://github.com/wequick/Small/wiki/UI-route">UI Route</a>.
 *
 * <p>Each bundle is resolved by <tt>BundleLauncher</tt>.
 *
 * <p>If the <tt>pkg</tt> is specified in <tt>bundle.json</tt>,
 * the <tt>bundle</tt> is refer to a plugin file with file name in converter
 * {@code "lib" + pkg.replaceAll("\\.", "_") + ".so"}
 * and resolved by a <tt>SoBundleLauncher</tt>.
 *
 * @see BundleLauncher
 */
public class Bundle {
    //______________________________________________________________________________
    // Fields
    public static final String BUNDLE_MANIFEST_NAME = "bundle.json";
    public static final String BUNDLES_KEY = "bundles";
    public static final String HOST_PACKAGE = "main";

    private static List<BundleLauncher> sBundleLaunchers = null;
    private static List<Bundle> sPreloadBundles = null;

    // Thread & Handler
    private static final int MSG_COMPLETE = 1;
    private static final int MSG_INIT_WEBVIEW = 100;
    private static LoadBundleHandler sHandler;
    private static LoadBundleThread sThread;

    private String mPackageName;
    private String uriString;
    private Uri uri;
    private URL url; // for WebBundleLauncher
    private Intent mIntent;
    private String type; // for ApkBundleLauncher
    private String path;
    private String query;
    private HashMap<String, String> rules;
    private int versionCode;

    private BundleLauncher mApplicableLauncher = null;

    private File mBuiltinFile = null;
    private File mPatchFile = null;

    private boolean launchable = true;
    private boolean enabled = true;
    private boolean patching = false;

    private String entrance = null; // Main activity for `apk bundle', index page for `web bundle'

    private BundleParser parser;

    //______________________________________________________________________________
    // Class methods

    public static Bundle findByName(String name) {
        if (name == null) return null;
        if (sPreloadBundles == null) return null;
        for (Bundle bundle : sPreloadBundles) {
            if (bundle.mPackageName == null) continue;
            if (bundle.mPackageName.equals(name)) return bundle;
        }
        return null;
    }

    public static String getUserBundlesPath() {
        return Small.getContext().getApplicationInfo().dataDir + "/lib/";
    }

    /**
     * Load bundles from manifest
     */
    public static void loadLaunchableBundles(Small.OnCompleteListener listener) {
        Context context = Small.getContext();
        // Read manifest file
        File manifestFile = new File(context.getFilesDir(), BUNDLE_MANIFEST_NAME);
        manifestFile.delete();
        String manifestJson;
        if (!manifestFile.exists()) {
            // Copy asset to files
            try {
                InputStream is = context.getAssets().open(BUNDLE_MANIFEST_NAME);
                int size = is.available();
                byte[] buffer = new byte[size];
                is.read(buffer);
                is.close();

                manifestFile.createNewFile();
                FileOutputStream os = new FileOutputStream(manifestFile);
                os.write(buffer);
                os.close();

                manifestJson = new String(buffer, 0, size);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        } else {
            try {
                BufferedReader br = new BufferedReader(new FileReader(manifestFile));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                manifestJson = sb.toString();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return;
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        // Parse manifest file
        try {
            JSONObject jsonObject = new JSONObject(manifestJson);
            String version = jsonObject.getString("version");
            loadManifest(version, jsonObject, listener);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
    }

    public static Boolean isLoadingAsync() {
        return (sThread != null);
    }

    private static boolean loadManifest(String version, JSONObject jsonObject,
                                        Small.OnCompleteListener listener) {
        if (version.equals("1.0.0")) {
            try {
                JSONArray bundles = jsonObject.getJSONArray(BUNDLES_KEY);
                loadBundles(bundles, listener);
                return true;
            } catch (JSONException e) {
                return false;
            }
        }

        throw new UnsupportedOperationException("Unknown version " + version);
    }

    private static void loadBundles(JSONArray bundles, Small.OnCompleteListener listener) {
        if (listener == null) {
            loadBundles(bundles);
            return;
        }

        // Asynchronous
        if (sThread == null) {
            sThread = new LoadBundleThread(bundles);
            sHandler = new LoadBundleHandler(listener);
            sThread.start();
        }
    }

    public static List<Bundle> getLaunchableBundles() {
        return sPreloadBundles;
    }

    public static void registerLauncher(BundleLauncher launcher) {
        if (sBundleLaunchers == null) {
            sBundleLaunchers = new ArrayList<BundleLauncher>();
        }
        sBundleLaunchers.add(launcher);
    }

    public static void setupLaunchers(Context context) {
        if (sBundleLaunchers == null) return;
        for (BundleLauncher launcher : sBundleLaunchers) {
            launcher.setUp(context);
        }
    }

    public static Bundle getLaunchableBundle(Uri uri) {
        if (sPreloadBundles != null) {
            for (Bundle bundle : sPreloadBundles) {
                if (bundle.matchesRule(uri)) {
                    if (!bundle.enabled) return null; // Illegal bundle (invalid signature, etc.)
                    return bundle;
                }
            }
        }

        // Downgrade to show webView
        if (uri.getScheme() != null) {
            Bundle bundle = new Bundle();
            try {
                bundle.url = new URL(uri.toString());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            bundle.prepareForLaunch();
            bundle.setQuery(uri.getEncodedQuery()); // Fix issue #6 from Spring-Xu.
            bundle.mApplicableLauncher = new WebBundleLauncher();
            bundle.mApplicableLauncher.prelaunchBundle(bundle);
            return bundle;
        }
        return null;
    }

    private Boolean matchesRule(Uri uri) {
        /* e.g.
         *  input
         *      - uri: http://base/abc.html
         *      - self.uri: http://base
         *      - self.rules: abc.html -> AbcController
         *  output
         *      - target => AbcController
         */
        String uriString = uri.toString();
        if (this.uriString == null || !uriString.startsWith(this.uriString)) return false;

        String srcPath = uriString.substring(this.uriString.length());
        String srcQuery = uri.getQuery();
        if (srcQuery != null) {
            srcPath = srcPath.substring(0, srcPath.length() - srcQuery.length() - 1);
        }

        String dstPath = null;
        String dstQuery = srcQuery;
        if (srcPath.equals("")) {
            dstPath = srcPath;
        } else {
            for (String key : this.rules.keySet()) {
                // TODO: regex match and replace
                if (key.equals(srcPath)) dstPath = this.rules.get(key);
                if (dstPath != null) break;
            }
            if (dstPath == null) return false;

            int index = dstPath.indexOf("?");
            if (index > 0) {
                if (dstQuery != null) {
                    dstQuery = dstQuery + "&" + dstPath.substring(index + 1);
                } else {
                    dstQuery = dstPath.substring(index + 1);
                }
                dstPath = dstPath.substring(0, index);
            }
        }

        this.path = dstPath;
        this.query = dstQuery;
        return true;
    }

    //______________________________________________________________________________
    // Instance methods
    public Bundle() {

    }

    public Bundle(JSONObject map) {
        try {
            this.initWithMap(map);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void upgrade() {
        if (mApplicableLauncher == null) return;
        mApplicableLauncher.upgradeBundle(this);
    }

    private void initWithMap(JSONObject map) throws JSONException {
        String pkg = map.getString("pkg");
        if (pkg != null && !pkg.equals(HOST_PACKAGE)) {
            String soName = "lib" + pkg.replaceAll("\\.", "_") + ".so";
            mBuiltinFile = new File(Bundle.getUserBundlesPath(), soName);
            mPatchFile = new File(FileUtils.getDownloadBundlePath(), soName);
            mPackageName = pkg;
        }

        if (map.has("uri")) {
            String uri = map.getString("uri");
            if (!uri.startsWith("http") && Small.getBaseUri() != null) {
                uri = Small.getBaseUri() + uri;
            }
            this.uriString = uri;
            this.uri = Uri.parse(uriString);
        }

        this.rules = new HashMap<String, String>();
        // Default rules to visit entrance page of bundle
        this.rules.put("", "");
        this.rules.put(".html", "");
        this.rules.put("/index", "");
        this.rules.put("/index.html", "");
        if (map.has("rules")) {
            // User rules to visit other page of bundle
            JSONObject rulesObj = map.getJSONObject("rules");
            Iterator<String> it = rulesObj.keys();
            while (it.hasNext()) {
                String key = it.next();
                this.rules.put("/" + key, rulesObj.getString(key));
            }
        }
    }

    public void prepareForLaunch() {
        if (mIntent != null) return;

        if (mApplicableLauncher == null && sBundleLaunchers != null) {
            for (BundleLauncher launcher : sBundleLaunchers) {
                if (launcher.resolveBundle(this)) {
                    mApplicableLauncher = launcher;
                    break;
                }
            }
        }
    }

    public void launchFrom(Context context) {
        if (mApplicableLauncher != null) {
            mApplicableLauncher.launchBundle(this, context);
        }
    }

    public Intent createIntent(Context context) {
        if (mApplicableLauncher == null) {
            prepareForLaunch();
        }
        if (mApplicableLauncher != null) {
            mApplicableLauncher.prelaunchBundle(this);
        }

        return mIntent;
    }

    public Intent getIntent() { return mIntent; }
    public void setIntent(Intent intent) { mIntent = intent; }

    public String getPackageName() {
        return mPackageName;
    }

    public Uri getUri() {
        return uri;
    }

    public void setURL(URL url) {
        this.url = url;
    }

    public URL getURL() {
        return url;
    }

    public File getBuiltinFile() {
        return mBuiltinFile;
    }

    public File getPatchFile() {
        return mPatchFile;
    }

    public void setPatchFile(File file) {
        mPatchFile = file;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setVersionCode(int versionCode) {
        this.versionCode = versionCode;
        Small.setBundleVersionCode(this.mPackageName, versionCode);
    }

    public boolean isLaunchable() {
        return launchable && enabled;
    }

    public void setLaunchable(boolean flag) {
        this.launchable = flag;
    }

    public String getEntrance() {
        return entrance;
    }

    public void setEntrance(String entrance) {
        this.entrance = entrance;
    }

    public <T> T createObject(Context context, String type) {
        if (mApplicableLauncher == null) {
            prepareForLaunch();
        }
        if (mApplicableLauncher == null) return null;
        return mApplicableLauncher.createObject(this, context, type);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPatching() {
        return patching;
    }

    public void setPatching(boolean patching) {
        this.patching = patching;
    }

    public BundleParser getParser() {
        return parser;
    }

    public void setParser(BundleParser parser) {
        this.parser = parser;
    }

    //______________________________________________________________________________
    // Internal class

    private static class LoadBundleThread extends Thread {
        JSONArray bundleDescs;

        public LoadBundleThread(JSONArray bundles) {
            this.bundleDescs = bundles;
        }
        @Override
        public void run() {
            // Instantiate bundle
            loadBundles(bundleDescs);
            sHandler.obtainMessage(MSG_COMPLETE).sendToTarget();
        }
    }

    private static void loadBundles(JSONArray bundleDescs) {
        List<Bundle> bundles = new ArrayList<Bundle>(bundleDescs.length());
        for (int i = 0; i < bundleDescs.length(); i++) {
            try {
                JSONObject object = bundleDescs.getJSONObject(i);
                Bundle bundle = new Bundle(object);
                bundles.add(bundle);
            } catch (JSONException e) {
                // Ignored
            }
        }
        sPreloadBundles = bundles;

        // Prepare bundle
        for (Bundle bundle : bundles) {
            bundle.prepareForLaunch();
        }
    }

    protected static void postInitWebViewMessage(String url) {
        sHandler.obtainMessage(MSG_INIT_WEBVIEW, url).sendToTarget();
    }

    private static class LoadBundleHandler extends Handler {
        private Small.OnCompleteListener mListener;

        public LoadBundleHandler(Small.OnCompleteListener listener) {
            mListener = listener;
        }
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_COMPLETE:
                    if (mListener != null) {
                        mListener.onComplete();
                    }
                    mListener = null;
                    sThread = null;
                    sHandler = null;
                    break;
                case MSG_INIT_WEBVIEW:
                    String url = (String) msg.obj;
                    WebViewPool.getInstance().alloc(url);
                    break;
            }
        }
    }

}
