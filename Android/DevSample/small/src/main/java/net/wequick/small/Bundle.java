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
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;

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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by galen on 15/1/28.
 */
public class Bundle {
    //______________________________________________________________________________
    // Fields
    public static final String BUNDLE_MANIFEST_NAME = "bundles.json";
    public static final String BUNDLES_KEY = "bundles";

    private static List<BundleLauncher> sBundleLaunchers = null;
    private static List<Bundle> sPreloadBundles = null;

    // Thread & Handler
    private static final int MSG_START = 1;
    private static final int MSG_PROGRESS = 2;
    private static final int MSG_COMPLETE = 3;
    private static final int MSG_INIT_WEBVIEW = 100;
    private static LoadBundleHandler mHandler;
    private static LoadBundleThread mThread;
    private static OnLoadListener mListener;

    private String mPackageName;
    private String uriString;
    private Uri uri;
    private Object preloadData;
    private URL url; // for WebBundleLauncher
    private Intent mIntent;
    private String type; // for ApkBundleLauncher
    private String path;
    private String query;
    private HashMap<String, String> rules;
    private int versionCode;

    private BundleLauncher mApplicableLauncher = null;

    private String mFileName = null;
    private File mFile = null;
    private File mPatchFile = null;
    private long mSize = -1;

    private Drawable mIcon = null;

    private boolean launchable = true;
    private boolean enabled = true;

    private String entrance = null; // Main activity for `apk bundle', index page for `web bundle'

    //______________________________________________________________________________
    // Class methods

    public static String getUserBundlesPath() {
        return Small.getContext().getApplicationInfo().dataDir + "/lib/";
    }

    /**
     * Load bundles from manifest
     */
    public static void loadLaunchableBundles(OnLoadListener listener) {
        mListener = listener;
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
            loadManifest(version, jsonObject);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
    }

    public static Boolean isLoadingAsync() {
        return (mThread != null);
    }

    private static boolean loadManifest(String version, JSONObject jsonObject) {
        if (version.equals("1.0.0")) {
            try {
                JSONArray bundles = jsonObject.getJSONArray(BUNDLES_KEY);
                loadBundles(bundles);
                return true;
            } catch (JSONException e) {
                return false;
            }
        }

        throw new UnsupportedOperationException("Unknown version " + version);
    }

    private static void loadBundles(JSONArray bundles) {
        if (mListener != null) {
            if (mThread == null) {
                mThread = new LoadBundleThread(bundles);
                mHandler = new LoadBundleHandler(mListener);
                mThread.start();
            }
        } else {
            for (int index = 0; index < bundles.length(); index++) {
                JSONObject jsonObject = null;
                try {
                    jsonObject = bundles.getJSONObject(index);
                } catch (JSONException e) {
                    // Ignored
                }
                Bundle bundle = new Bundle(jsonObject);
                bundle.prepareForLaunch();

                if (sPreloadBundles == null) {
                    sPreloadBundles = new ArrayList<Bundle>();
                }
                sPreloadBundles.add(bundle);
            }
        }
    }

    /**
     *
     * @return
     */
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
            launcher.setup(context);
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
        if (!uriString.startsWith(this.uriString)) return false;

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

    private void initWithMap(JSONObject map) throws JSONException {
        mPackageName = map.getString("pkg");
        String uri = map.getString("uri");
        if (!uri.startsWith("http") && Small.getBaseUri() != null) {
            uri = Small.getBaseUri() + uri;
        }
        this.uriString = uri;
        this.uri = Uri.parse(uriString);
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
                this.rules.put(key, rulesObj.getString(key));
            }
        }
    }

    private boolean downloadBundle(URL url, final File file) {
        final Bundle self = this;
        BundleFetcher.getInstance().fetchBundle(url, new BundleFetcher.OnFetchListener() {
            @Override
            public void onFetch(InputStream is, Exception e) {
                if (is != null) {
                    if (file.exists()) {
                        // TODO: 断点续传
                        file.delete();
                    }
                    // Save
                    OutputStream os = null;
                    try {
                        os = new FileOutputStream(file);
                        if (os != null) {
                            byte[] buffer = new byte[1024];
                            int length;
                            while ((length = is.read(buffer)) != -1) {
                                postLoadProgressMessage(self.mPackageName, length);
                                os.write(buffer, 0, length);
                            }
                            os.flush();
                        }
                    } catch (FileNotFoundException e1) {
                        e.printStackTrace();
                    } catch (IOException e1) {
                        e.printStackTrace();
                    } finally {
                        if (os != null) {
                            try {
                                os.close();
                            } catch (IOException e1) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        });

        if (!file.exists()) {
            return false;
        }
        this.mPatchFile = file;
        return true;
    }

    public void prepareForLaunch() {
        if (mIntent != null) return;

        URL url = this.getDownloadUrl();
        if (url != null) {
            downloadBundle(url, this.mPatchFile);
            postLoadProgressMessage(mPackageName, -1);
        }

        if (mApplicableLauncher == null && sBundleLaunchers != null) {
            for (BundleLauncher launcher : sBundleLaunchers) {
                if (launcher.initBundle(this)) {
                    mApplicableLauncher = launcher;
                    break;
                }
            }
        }
    }

    public URL getDownloadUrl() {
        if (mPackageName == null) return null;

        Map<String, String> urls = Small.getBundleUpgradeUrls();
        if (urls == null) return null;

        String src = urls.get(mPackageName);
        if (src == null) return null;

        try {
            return new URL(src);
        } catch (MalformedURLException ignored) {
            // ignored
        }

        return null;
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

    public Object getPreloadData() {
        return preloadData;
    }

    public void setPreloadData(Object preloadData) {
        this.preloadData = preloadData;
    }

    public File getFile() {
        return mFile;
    }

    public Drawable getIcon() {
        return mIcon;
    }

    public void setIcon(Drawable icon) {
        this.mIcon = icon;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getSize() {
        if (this.mSize != -1) {
            return this.mSize;
        }
        URL url = this.getDownloadUrl();
        if (url != null) {
            try {
                HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
                urlConn.setRequestMethod("HEAD");
                urlConn.getInputStream();
                this.mSize = urlConn.getContentLength();
            } catch (IOException e) {
                this.mSize = 0;
                e.printStackTrace();
            }
        } else {
            this.mSize = 0;
        }
        return this.mSize;
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

    public void setFile(File file) {
        this.mFile = file;
    }

    public String getFileName() {
        return mFileName;
    }

    public void setFileName(String mFileName) {
        this.mFileName = mFileName;
    }

    //______________________________________________________________________________
    // Internal class

    public interface OnLoadListener {
        void onStart(int bundleCount, int upgradeBundlesCount, long upgradeBundlesSize);
        void onProgress(int bundleIndex, String bundleName, long loadedSize, long bundleSize);
        void onComplete(Boolean success);
    }

    private static class LoadBundleThread extends Thread {
        JSONArray bundleDescs;

        public LoadBundleThread(JSONArray bundles) {
            this.bundleDescs = bundles;
        }
        @Override
        public void run() {
            List<Bundle> bundles = new ArrayList<Bundle>(bundleDescs.length());
            long totalSize = 0;
            // 1. Calculate size
            for (int i = 0; i < bundleDescs.length(); i++) {
                try {
                    JSONObject object = bundleDescs.getJSONObject(i);
                    Bundle bundle = new Bundle(object);
                    bundles.add(bundle);
                    long size = bundle.getSize();
                    totalSize += size;
                } catch (JSONException e) {
                    // Ignored
                }
            }
            sPreloadBundles = bundles;

            // 2. Prepare bundle | Download
            mHandler.obtainMessage(MSG_START, totalSize).sendToTarget();
            for (Bundle bundle : bundles) {
                bundle.prepareForLaunch();
            }
            // 3. Clear upgrade urls
            Small.setBundleUpgradeUrls(null);
            mHandler.obtainMessage(MSG_COMPLETE).sendToTarget();
        }
    }

    protected static void postLoadProgressMessage(String name, long loadedSize) {
        ProgressMsgObj progress = new ProgressMsgObj();
        progress.name = name;
        progress.size = loadedSize;
        mHandler.obtainMessage(MSG_PROGRESS, progress).sendToTarget();
    }

    protected static void postInitWebViewMessage(String url) {
        mHandler.obtainMessage(MSG_INIT_WEBVIEW, url).sendToTarget();
    }

    private static class ProgressMsgObj {
        public String name;
        public long size;
    }

    private static class LoadBundleHandler extends Handler {
        private OnLoadListener mListener;

        public LoadBundleHandler(OnLoadListener listener) {
            mListener = listener;
        }
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_START:
                    if (mListener != null) {
                        Map<String, String> urls = Small.getBundleUpgradeUrls();
                        int upgradeCount = 0;
                        if (urls != null) {
                            upgradeCount = urls.size();
                        }
                        mListener.onStart(sPreloadBundles.size(), upgradeCount, (Long) msg.obj);
                    }
                    break;
                case MSG_PROGRESS:
                    if (mListener != null) {
                        ProgressMsgObj progress = (ProgressMsgObj) msg.obj;
                        int index = 0;
                        long bundleSize = 0;
                        for (; index < sPreloadBundles.size(); index++) {
                            Bundle bundle = sPreloadBundles.get(index);
                            if (bundle.getPackageName().equals(progress.name)) {
                                bundleSize = bundle.getSize();
                                break;
                            }
                        }
                        long loadedSize = progress.size == -1 ? bundleSize : progress.size;
                        mListener.onProgress(index, progress.name, loadedSize, bundleSize);
                    }
                    break;
                case MSG_COMPLETE:
                    if (mListener != null) {
                        mListener.onComplete(true);
                    }
                    mListener = null;
                    mThread = null;
                    mHandler = null;
                    break;
                case MSG_INIT_WEBVIEW:
                    String url = (String) msg.obj;
                    WebViewPool.getInstance().alloc(url);
                    break;
            }
        }
    }

}
