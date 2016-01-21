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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;

import net.wequick.small.util.ApplicationUtils;
import net.wequick.small.webkit.WebView;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by galen on 15/1/28.
 */
public final class Small {
    public static final String EVENT_OPENURI = "small-open";
    public static final String KEY_QUERY = "small-query";
    public static final String EXTRAS_KEY_RET = "small-ret";
    public static final String SHARED_PREFERENCES_SMALL = "small";
    public static final String SHARED_PREFERENCES_KEY_UPGRADE = "upgrade";
    public static final String SHARED_PREFERENCES_KEY_VERSION = "version";
    public static final String SHARED_PREFERENCES_BUNDLE_VERSIONS = "small.app-versions";
    public static final String SHARED_PREFERENCES_BUNDLE_URLS = "small.app-urls";
    public static final String SHARED_PREFERENCES_BUNDLE_MODIFIES = "small.app-modifies";
    public static final String SHARED_PREFERENCES_BUNDLE_UPGRADES = "small.app-upgrades";
    public static final int REQUEST_CODE_DEFAULT = 10000;

    private static Context sContext = null;
    private static HashMap<String, Class<?>> sActivityClasses;
    private static String sBaseUri = ""; // base url of uri
    private static String sBaseSrc = ""; // source url for remote bundle
    private static boolean sIsNewHostApp; // 首次安装或升级
    private static int sWebActivityTheme;

    private static WebView.OnLoadListener onLoadListener;

    public static Context getContext() {
        if (sContext == null) {
            try {
                final Class<?> activityThreadClass =
                        Class.forName("android.app.ActivityThread");
                final Method method = activityThreadClass.getMethod("currentApplication");
                sContext = (Context) method.invoke(null, (Object[]) null);
            } catch (Exception ignored) { }
        }
        return sContext;
    }

    public static void setBaseUri(String url) {
        sBaseUri = url;
    }

    public static String getBaseUri() {
        return sBaseUri;
    }

    public static boolean getIsNewHostApp() {
        return sIsNewHostApp;
    }

    public static void setUp(Context context) {
        setUp(context, null);
    }

    public static void setUp(Context context, Bundle.OnLoadListener listener) {
        Context appContext = context.getApplicationContext();
        sContext = appContext;
        saveActivityClasses(appContext);
        LocalBroadcastManager.getInstance(appContext).registerReceiver(new OpenUriReceiver(),
                new IntentFilter(EVENT_OPENURI));

        int backupHostVersion = getHostVersionCode();
        int currHostVersion = 0;
        try {
            PackageInfo pi = appContext.getPackageManager().getPackageInfo(
                    appContext.getPackageName(), 0);
            currHostVersion = pi.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        if (backupHostVersion != currHostVersion) {
            sIsNewHostApp = true;
            setHostVersionCode(currHostVersion);
            clearAppCache(appContext);
        } else {
            sIsNewHostApp = false;
        }
        // Register default bundle launchers
        registerLauncher(new ActivityLauncher());
        registerLauncher(new ApkBundleLauncher());
        registerLauncher(new WebBundleLauncher());
        Bundle.setupLaunchers(context);
        // Load bundles
        Bundle.loadLaunchableBundles(listener);
    }

    public static void setWebViewOnLoadListener(WebView.OnLoadListener listener) {
        onLoadListener = listener;
        WebView.setOnLoadListener(listener);
    }

    public static WebView.OnLoadListener gettWebViewOnLoadListener() {
        return onLoadListener;
    }

    public static Map<String, Integer> getBundleVersions() {
        return (Map<String, Integer>) getContext().
                getSharedPreferences(SHARED_PREFERENCES_BUNDLE_VERSIONS, 0).getAll();
    }

    public static int getHostVersionCode() {
        return getContext().getSharedPreferences(SHARED_PREFERENCES_SMALL, 0).
                getInt(SHARED_PREFERENCES_KEY_VERSION, 0);
    }

    public static void setHostVersionCode(int versionCode) {
        SharedPreferences small = getContext().getSharedPreferences(SHARED_PREFERENCES_SMALL, 0);
        SharedPreferences.Editor editor = small.edit();
        editor.putInt(SHARED_PREFERENCES_KEY_VERSION, versionCode);
        editor.commit();
    }

    public static boolean getNeedsUpgradeBundle() {
        return getContext().getSharedPreferences(SHARED_PREFERENCES_SMALL, 0).
                getBoolean(SHARED_PREFERENCES_KEY_UPGRADE, false);
    }

    public static void setNeedsUpgradeBundle(boolean flag) {
        SharedPreferences small = getContext().getSharedPreferences(SHARED_PREFERENCES_SMALL, 0);
        SharedPreferences.Editor editor = small.edit();
        editor.putBoolean(SHARED_PREFERENCES_KEY_UPGRADE, flag);
        editor.commit();
    }

    public static Map<String, String> getBundleUpgradeUrls() {
        return (Map<String, String>) getContext().
                getSharedPreferences(SHARED_PREFERENCES_BUNDLE_URLS, 0).getAll();
    }

    public static void setBundleUpgradeUrls(Map<String, String> urls) {
        SharedPreferences sp = getContext().getSharedPreferences(SHARED_PREFERENCES_BUNDLE_URLS, 0);
        SharedPreferences.Editor editor = sp.edit();
        editor.clear();
        if (urls != null) {
            Iterator<Map.Entry<String, String>> it = urls.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> entry = it.next();
                editor.putString(entry.getKey(), entry.getValue());
            }
        }
        editor.commit();
    }

    public static void setBundleVersionCode(String bundleName, int versionCode) {
        SharedPreferences bundlesInfo = getContext().
                getSharedPreferences(SHARED_PREFERENCES_BUNDLE_VERSIONS, 0);
        SharedPreferences.Editor editor = bundlesInfo.edit();
        editor.putInt(bundleName, versionCode);
        editor.commit();
    }

    public static void setBundleLastModified(String bundleName, long lastModified) {
        SharedPreferences sp = getContext().
                getSharedPreferences(SHARED_PREFERENCES_BUNDLE_MODIFIES, 0);
        SharedPreferences.Editor editor = sp.edit();
        editor.putLong(bundleName, lastModified);
        editor.commit();
    }

    public static long getBundleLastModified(String bundleName) {
        SharedPreferences sp = getContext().
                getSharedPreferences(SHARED_PREFERENCES_BUNDLE_MODIFIES, 0);
        if (sp == null) return 0;
        return sp.getLong(bundleName, 0);
    }

    public static void setBundleUpgraded(String bundleName, boolean flag) {
        SharedPreferences sp = getContext().
                getSharedPreferences(SHARED_PREFERENCES_BUNDLE_UPGRADES, 0);
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean(bundleName, flag);
        editor.commit();
    }

    public static boolean getBundleUpgraded(String bundleName) {
        SharedPreferences sp = getContext().
                getSharedPreferences(SHARED_PREFERENCES_BUNDLE_UPGRADES, 0);
        if (sp == null) return false;
        return sp.getBoolean(bundleName, false);
    }

    public static void openUri(String uriString, Context context) {
        openUri(makeUri(uriString), context);
    }

    public static void openUri(Uri uri, Context context) {
        // System url schemes
        if (!uri.getScheme().equals("http")
                && !uri.getScheme().equals("https")
                && !uri.getScheme().equals("file")
                && ApplicationUtils.canOpenUri(uri, context)) {
//                  Log.e("ApplicationUtils","ApplicationUtils");
            ApplicationUtils.openUri(uri, context);
            return;
        }

        // Small url schemes
        Bundle bundle = Bundle.getLaunchableBundle(uri);
        if (bundle != null) {

//                  Log.e("Bundle","Bundle");
            bundle.launchFrom(context);
        }
    }

    public static Intent getIntentOfUri(String uriString, Context context) {
        return getIntentOfUri(makeUri(uriString), context);
    }

    public static Intent getIntentOfUri(Uri uri, Context context) {
        // System url schemes
        if (!uri.getScheme().equals("http")
                && !uri.getScheme().equals("https")
                && !uri.getScheme().equals("file")
                && ApplicationUtils.canOpenUri(uri, context)) {
            return ApplicationUtils.getIntentOfUri(uri);
        }

        // Small url schemes
        Bundle bundle = Bundle.getLaunchableBundle(uri);
        if (bundle != null) {
            return bundle.createIntent(context);
        }
        return null;
    }

    public static <T> T createObject(String type, String uriString, Context context) {
        return createObject(type, makeUri(uriString), context);
    }

    public static <T> T createObject(String type, Uri uri, Context context) {
        Bundle bundle = Bundle.getLaunchableBundle(uri);
        if (bundle != null) {
            return bundle.createObject(context, type);
        }
        return null;
    }

    public static Uri getUri(Activity context) {
        android.os.Bundle extras = context.getIntent().getExtras();
        if (extras == null) {
            return null;
        }
        String query = extras.getString(KEY_QUERY);
        if (query == null) {
            return null;
        }
        return Uri.parse(query);
    }

    public static List<Bundle> getBundles() {
        return Bundle.getLaunchableBundles();
    }

    public static void registerLauncher(BundleLauncher launcher) {
        Bundle.registerLauncher(launcher);
    }

    /**
     * 获取[AndroidManifest.xml]注册的类
     *
     * @param clazz
     * @return
     */
    protected static Class<?> getRegisteredClass(String clazz) {
        Class<?> aClass = null;
        if (sActivityClasses != null) {
            aClass = sActivityClasses.get(clazz);
            if (aClass == null && !clazz.endsWith("Activity")) {
                aClass = sActivityClasses.get(clazz + "Activity");
            }
        }
        return aClass;
    }

    /**
     * 记录[AndroidManifest.xml]注册的类
     */
    private static void saveActivityClasses(Context context) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), PackageManager.GET_ACTIVITIES);
            ActivityInfo[] as = pi.activities;
            if (as != null) {
                sActivityClasses = new HashMap<String, Class<?>>();
                for (int i = 0; i < as.length; i++) {
                    ActivityInfo ai = as[i];
                    int dot = ai.name.lastIndexOf(".");
                    if (dot > 0) {
                        try {
                            Class<?> clazz = Class.forName(ai.name);
                            sActivityClasses.put(ai.name, clazz);
                        } catch (ClassNotFoundException e) {
                            // Ignored
                        }
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {

        }
    }

    public static int getWebActivityTheme() {
        return sWebActivityTheme;
    }

    public static void setWebActivityTheme(int webActivityTheme) {
        sWebActivityTheme = webActivityTheme;
    }

    public static String getBaseSrc() {
        return sBaseSrc;
    }

    public static void setBaseSrc(String baseSrc) {
        sBaseSrc = baseSrc;
    }

    private static class OpenUriReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String uri = intent.getStringExtra("uri");
            openUri(Uri.parse(uri), context);
        }
    }

    /**
     * 清除app缓存
     */
    public static void clearAppCache(Context context) {
        File file = context.getCacheDir();
        if (file != null && file.exists() && file.isDirectory()) {
            for (File item : file.listFiles()) {
                item.delete();
            }
            file.delete();
        }
    }

    //______________________________________________________________________________________________
    // Private

    private static Uri makeUri(String uriString) {
        if (!uriString.startsWith("http://")
                && !uriString.startsWith("https://")
                && !uriString.startsWith("file://")) {
            uriString = sBaseUri + uriString;
        }
        return Uri.parse(uriString);
    }
}
