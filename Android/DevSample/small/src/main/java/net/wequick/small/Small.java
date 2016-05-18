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
import android.app.ActivityManager;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import net.wequick.small.util.ApplicationUtils;
import net.wequick.small.webkit.JsHandler;
import net.wequick.small.webkit.WebView;
import net.wequick.small.webkit.WebViewClient;

import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This class consists exclusively of static methods that operate on bundle.
 *
 * <h3>Core APIs</h3>
 * <ul>
 *     <li>{@link #setUp(Context, OnCompleteListener)} resolve the <tt>bundle.json</tt> to setup bundle launchers.</li>
 *     <li>{@link #openUri} launch the bundle with specify activity by the <tt>uri</tt></li>
 *     <li>{@link #createObject} create object from the bundle</li>
 *     <li>{@link #setWebViewClient(WebViewClient)} customize the web view behaviors for web bundle</li>
 *     <li>{@link #registerJsHandler(String, JsHandler)} customize the javascript api for web bundle</li>
 * </ul>
 */
public final class Small {

    public static final String KEY_QUERY = "small-query";
    public static final String EXTRAS_KEY_RET = "small-ret";
    public static final int REQUEST_CODE_DEFAULT = 10000;

    private static final String SHARED_PREFERENCES_SMALL = "small";
    private static final String SHARED_PREFERENCES_KEY_VERSION = "version";
    private static final String SHARED_PREFERENCES_BUNDLE_VERSIONS = "small.app-versions";
    private static final String SHARED_PREFERENCES_BUNDLE_MODIFIES = "small.app-modifies";
    private static final String SHARED_PREFERENCES_BUNDLE_UPGRADES = "small.app-upgrades";

    private static Context sContext = null;
    private static String sBaseUri = ""; // base url of uri
    private static boolean sIsNewHostApp; // first launched or upgraded
    private static int sWebActivityTheme;

    public interface OnCompleteListener {
        void onComplete();
    }

    public static Context getContext() {
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

    public static void preSetUp(Application context) {
        sContext = context;

        // Register default bundle launchers
        registerLauncher(new ActivityLauncher());
        registerLauncher(new ApkBundleLauncher());
        registerLauncher(new WebBundleLauncher());

        PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName();

        // Check if host app is first-installed or upgraded
        int backupHostVersion = getHostVersionCode();
        int currHostVersion = 0;
        try {
            PackageInfo pi = pm.getPackageInfo(packageName, 0);
            currHostVersion = pi.versionCode;
        } catch (PackageManager.NameNotFoundException ignored) {
            // Never reach
        }

        if (backupHostVersion != currHostVersion) {
            sIsNewHostApp = true;
            setHostVersionCode(currHostVersion);
        } else {
            sIsNewHostApp = false;
        }

        // Check if application is started after unexpected exit (killed in background etc.)
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ComponentName launchingComponent = am.getRunningTasks(1).get(0).topActivity;
        ComponentName launcherComponent = pm.getLaunchIntentForPackage(packageName).getComponent();
        if (!launchingComponent.equals(launcherComponent)) {
            // In this case, system launching the last restored activity instead of our launcher
            // activity. Call `setUp' synchronously to ensure `Small' available.
            setUp(context, null);
        }
    }

    public static void setUp(Context context, OnCompleteListener listener) {
        if (sContext == null) {
            // Tips for CODE-BREAKING
            throw new UnsupportedOperationException(
                    "Please call `Small.preSetUp' in your application first");
        }
        Bundle.setupLaunchers(context);
        Bundle.loadLaunchableBundles(listener);
    }

    public static Bundle getBundle(String bundleName) {
        return Bundle.findByName(bundleName);
    }

    public static boolean updateManifest(JSONObject manifest, boolean force) {
        return Bundle.updateManifest(manifest, force);
    }

    public static void setWebViewClient(WebViewClient client) {
        WebView.setWebViewClient(client);
    }

    public static void registerJsHandler(String method, JsHandler handler) {
        WebView.registerJsHandler(method, handler);
    }

    public static SharedPreferences getSharedPreferences() {
        return getContext().getSharedPreferences(SHARED_PREFERENCES_SMALL, 0);
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
        editor.apply();
    }

    public static void setBundleVersionCode(String bundleName, int versionCode) {
        SharedPreferences bundlesInfo = getContext().
                getSharedPreferences(SHARED_PREFERENCES_BUNDLE_VERSIONS, 0);
        SharedPreferences.Editor editor = bundlesInfo.edit();
        editor.putInt(bundleName, versionCode);
        editor.apply();
    }

    public static void setBundleLastModified(String bundleName, long lastModified) {
        SharedPreferences sp = getContext().
                getSharedPreferences(SHARED_PREFERENCES_BUNDLE_MODIFIES, 0);
        SharedPreferences.Editor editor = sp.edit();
        editor.putLong(bundleName, lastModified);
        editor.apply();
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
        editor.apply();
    }

    public static boolean getBundleUpgraded(String bundleName) {
        SharedPreferences sp = getContext().
                getSharedPreferences(SHARED_PREFERENCES_BUNDLE_UPGRADES, 0);
        if (sp == null) return false;
        return sp.getBoolean(bundleName, false);
    }

    public static boolean isUpgrading() {
        SharedPreferences sp = getContext().
                getSharedPreferences(SHARED_PREFERENCES_BUNDLE_UPGRADES, 0);
        Map<String, Boolean> flags = (Map<String, Boolean>) sp.getAll();
        if (flags == null) return false;
        Iterator<Map.Entry<String, Boolean>> it = flags.entrySet().iterator();
        while (it.hasNext()) {
            Boolean flag = it.next().getValue();
            if (flag != null && flag) return true;
        }
        return false;
    }

    public static void openUri(String uriString, Context context) {
        openUri(makeUri(uriString), context);
    }

    public static void openUri(Uri uri, Context context) {
        // System url schemes
        String scheme = uri.getScheme();
        if (scheme != null
                && !scheme.equals("http")
                && !scheme.equals("https")
                && !scheme.equals("file")
                && ApplicationUtils.canOpenUri(uri, context)) {
            ApplicationUtils.openUri(uri, context);
            return;
        }

        // Small url schemes
        Bundle bundle = Bundle.getLaunchableBundle(uri);
        if (bundle != null) {
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

    public static int getWebActivityTheme() {
        return sWebActivityTheme;
    }

    public static void setWebActivityTheme(int webActivityTheme) {
        sWebActivityTheme = webActivityTheme;
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
