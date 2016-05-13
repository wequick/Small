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
import android.content.SharedPreferences;
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
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.PrintWriter;
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
    private static final String BUNDLE_MANIFEST_NAME = "bundle.json";
    private static final String VERSION_KEY = "version";
    private static final String BUNDLES_KEY = "bundles";
    private static final String HOST_PACKAGE = "main";

    private static final class Manifest {
        String version;
        List<Bundle> bundles;
    }

    private static List<BundleLauncher> sBundleLaunchers = null;
    private static List<Bundle> sPreloadBundles = null;
    private static List<Bundle> sUpdatingBundles = null;
    private static File sPatchManifestFile = null;
    private static String sUserBundlesPath = null;
    private static boolean sIs64bit = false;

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

    /**
     * @deprecated Use {@link Small#getBundle} instead.
     * @param name
     * @return
     */
    public static Bundle findByName(String name) {
        Bundle bundle = findBundle(name, sPreloadBundles);
        if (bundle != null) return bundle;
        return findBundle(name, sUpdatingBundles);
    }

    private static Bundle findBundle(String name, List<Bundle> bundles) {
        if (name == null) return null;
        if (bundles == null) return null;
        for (Bundle bundle : bundles) {
            if (bundle.mPackageName == null) continue;
            if (bundle.mPackageName.equals(name)) return bundle;
        }
        return null;
    }

    /**
     * Update bundle.json and apply settings
     * @param data the manifest JSON object
     * @param force <tt>true</tt> if force to update current bundles
     * @return <tt>true</tt> if successfully updated
     */
    public static boolean updateManifest(JSONObject data, boolean force) {
        if (data == null) return false;

        Manifest manifest = parseManifest(data);
        if (manifest == null) return false;

        String manifestJson;
        try {
            manifestJson = data.toString(2);
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }

        if (force) {
            // Save to file
            File manifestFile = getPatchManifestFile();
            try {
                PrintWriter pw = new PrintWriter(new FileOutputStream(manifestFile));
                pw.print(manifestJson);
                pw.flush();
                pw.close();
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            // Update bundles
            for (Bundle bundle : manifest.bundles) {
                Bundle preloadBundle = findBundle(bundle.getPackageName(), sPreloadBundles);
                if (preloadBundle != null) {
                    // Update bundle
                    preloadBundle.uriString = bundle.uriString;
                    preloadBundle.uri = bundle.uri;
                    preloadBundle.rules = bundle.rules;
                }
            }
        } else {
            // Temporary add bundle
            for (Bundle bundle : manifest.bundles) {
                Bundle preloadBundle = findBundle(bundle.getPackageName(), sPreloadBundles);
                if (preloadBundle == null) {
                    if (sUpdatingBundles == null) {
                        sUpdatingBundles = new ArrayList<Bundle>();
                    }
                    sUpdatingBundles.add(bundle);
                }
            }
            // Save to `SharedPreference'
            setCacheManifest(manifestJson);
        }
        return true;
    }

    private static String getCacheManifest() {
        return Small.getSharedPreferences().getString(BUNDLE_MANIFEST_NAME, null);
    }

    private static void setCacheManifest(String text) {
        SharedPreferences small = Small.getSharedPreferences();
        SharedPreferences.Editor editor = small.edit();
        if (text == null) {
            editor.remove(BUNDLE_MANIFEST_NAME);
        } else {
            editor.putString(BUNDLE_MANIFEST_NAME, text);
        }
        editor.apply();
    }

    protected static boolean is64bit() {
        return sIs64bit;
    }

    /**
     * Load bundles from manifest
     */
    protected static void loadLaunchableBundles(Small.OnCompleteListener listener) {
        Context context = Small.getContext();

        if (listener == null) {
            loadBundles(context);
            return;
        }

        // Asynchronous
        if (sThread == null) {
            sThread = new LoadBundleThread(context);
            sHandler = new LoadBundleHandler(listener);
            sThread.start();
        }
    }

    private static File getPatchManifestFile() {
        if (sPatchManifestFile == null) {
            sPatchManifestFile = new File(Small.getContext().getFilesDir(), BUNDLE_MANIFEST_NAME);
        }
        return sPatchManifestFile;
    }

    private static void loadBundles(Context context) {
        JSONObject manifestData;
        try {
            File patchManifestFile = getPatchManifestFile();
            String manifestJson = getCacheManifest();
            if (manifestJson != null) {
                // Load from cache and save as patch
                if (!patchManifestFile.exists()) patchManifestFile.createNewFile();
                PrintWriter pw = new PrintWriter(new FileOutputStream(patchManifestFile));
                pw.print(manifestJson);
                pw.flush();
                pw.close();
                // Clear cache
                setCacheManifest(null);
            } else if (patchManifestFile.exists()) {
                // Load from patch
                BufferedReader br = new BufferedReader(new FileReader(patchManifestFile));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }

                br.close();
                manifestJson = sb.toString();
            } else {
                // Load from built-in `assets/bundle.json'
                InputStream builtinManifestStream = context.getAssets().open(BUNDLE_MANIFEST_NAME);
                int builtinSize = builtinManifestStream.available();
                byte[] buffer = new byte[builtinSize];
                builtinManifestStream.read(buffer);
                builtinManifestStream.close();
                manifestJson = new String(buffer, 0, builtinSize);
            }

            // Parse manifest file
            manifestData = new JSONObject(manifestJson);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        Manifest manifest = parseManifest(manifestData);
        if (manifest == null) return;

        loadBundles(manifest.bundles);
    }

    protected static Boolean isLoadingAsync() {
        return (sThread != null);
    }

    private static Manifest parseManifest(JSONObject data) {
        try {
            String version = data.getString(VERSION_KEY);
            return parseManifest(version, data);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Manifest parseManifest(String version, JSONObject data) {
        if (version.equals("1.0.0")) {
            try {
                JSONArray bundleDescs = data.getJSONArray(BUNDLES_KEY);
                int N = bundleDescs.length();
                List<Bundle> bundles = new ArrayList<Bundle>(N);
                for (int i = 0; i < N; i++) {
                    try {
                        JSONObject object = bundleDescs.getJSONObject(i);
                        Bundle bundle = new Bundle(object);
                        bundles.add(bundle);
                    } catch (JSONException e) {
                        // Ignored
                    }
                }
                Manifest manifest = new Manifest();
                manifest.version = version;
                manifest.bundles = bundles;
                return manifest;
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        }

        throw new UnsupportedOperationException("Unknown version " + version);
    }

    protected static List<Bundle> getLaunchableBundles() {
        return sPreloadBundles;
    }

    protected static void registerLauncher(BundleLauncher launcher) {
        if (sBundleLaunchers == null) {
            sBundleLaunchers = new ArrayList<BundleLauncher>();
        }
        sBundleLaunchers.add(launcher);
    }

    protected static void setupLaunchers(Context context) {
        if (sBundleLaunchers == null) return;
        for (BundleLauncher launcher : sBundleLaunchers) {
            launcher.setUp(context);
        }
    }

    protected static Bundle getLaunchableBundle(Uri uri) {
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
        if (sUserBundlesPath == null) { // Lazy init
            sUserBundlesPath = Small.getContext().getApplicationInfo().nativeLibraryDir;
            sIs64bit = sUserBundlesPath.contains("64");
        }

        String pkg = map.getString("pkg");
        if (pkg != null && !pkg.equals(HOST_PACKAGE)) {
            String soName = "lib" + pkg.replaceAll("\\.", "_") + ".so";
            mBuiltinFile = new File(sUserBundlesPath, soName);
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

    protected void prepareForLaunch() {
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

    protected void launchFrom(Context context) {
        if (mApplicableLauncher != null) {
            mApplicableLauncher.launchBundle(this, context);
        }
    }

    protected Intent createIntent(Context context) {
        if (mApplicableLauncher == null) {
            prepareForLaunch();
        }
        if (mApplicableLauncher != null) {
            mApplicableLauncher.prelaunchBundle(this);
        }

        return mIntent;
    }

    protected Intent getIntent() { return mIntent; }
    protected void setIntent(Intent intent) { mIntent = intent; }

    protected String getPackageName() {
        return mPackageName;
    }

    protected Uri getUri() {
        return uri;
    }

    protected void setURL(URL url) {
        this.url = url;
    }

    protected URL getURL() {
        return url;
    }

    protected File getBuiltinFile() {
        return mBuiltinFile;
    }

    public File getPatchFile() {
        return mPatchFile;
    }

    protected String getType() {
        return type;
    }

    protected void setType(String type) {
        this.type = type;
    }

    protected String getQuery() {
        return query;
    }

    protected void setQuery(String query) {
        this.query = query;
    }

    protected String getPath() {
        return path;
    }

    protected void setPath(String path) {
        this.path = path;
    }

    protected void setVersionCode(int versionCode) {
        this.versionCode = versionCode;
        Small.setBundleVersionCode(this.mPackageName, versionCode);
    }

    protected boolean isLaunchable() {
        return launchable && enabled;
    }

    protected void setLaunchable(boolean flag) {
        this.launchable = flag;
    }

    protected String getEntrance() {
        return entrance;
    }

    protected void setEntrance(String entrance) {
        this.entrance = entrance;
    }

    protected <T> T createObject(Context context, String type) {
        if (mApplicableLauncher == null) {
            prepareForLaunch();
        }
        if (mApplicableLauncher == null) return null;
        return mApplicableLauncher.createObject(this, context, type);
    }

    protected boolean isEnabled() {
        return enabled;
    }

    protected void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    protected boolean isPatching() {
        return patching;
    }

    protected void setPatching(boolean patching) {
        this.patching = patching;
    }

    protected BundleParser getParser() {
        return parser;
    }

    protected void setParser(BundleParser parser) {
        this.parser = parser;
    }

    //______________________________________________________________________________
    // Internal class

    private static class LoadBundleThread extends Thread {

        Context mContext;

        public LoadBundleThread(Context context) {
            mContext = context;
        }

        @Override
        public void run() {
            // Instantiate bundle
            loadBundles(mContext);
            sHandler.obtainMessage(MSG_COMPLETE).sendToTarget();
        }
    }

    private static void loadBundles(List<Bundle> bundles) {
        sPreloadBundles = bundles;

        // Prepare bundle
        for (Bundle bundle : bundles) {
            bundle.prepareForLaunch();
        }

        // Notify `postSetUp' to all launchers
        for (BundleLauncher launcher : sBundleLaunchers) {
            launcher.postSetUp();
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
