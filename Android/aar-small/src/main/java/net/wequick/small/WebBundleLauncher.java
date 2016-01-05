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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import net.wequick.small.util.FileUtils;
import net.wequick.small.util.SignUtils;
import net.wequick.small.webkit.WebActivity;
import net.wequick.small.webkit.WebViewPool;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by galen on 15/1/28.
 */
public class WebBundleLauncher extends BundleLauncher {

    private static final String TAG = "WebBundleLauncher";

    @Override
    public boolean preloadBundle(Bundle bundle) {
        // Check if exists a `web' plugin
        String packageName = bundle.getPackageName();
        if (packageName == null) return false;
        if (!packageName.contains(".web.")) return false;

        String soName = "lib" + packageName.replaceAll("\\.", "_") + ".so";
        File plugin = new File(Bundle.getUserBundlesPath() + soName);
        if (plugin == null || !plugin.exists()) return false;

        // Check if is an apk bundle
        PackageManager pm = Small.getContext().getPackageManager();
        PackageInfo pluginInfo = pm.getPackageArchiveInfo(plugin.getPath(),
                PackageManager.GET_SIGNATURES );
        if (pluginInfo == null) return false;

        // Record version code for upgrade
        bundle.setVersionCode(pluginInfo.versionCode);
        Small.setBundleVersionCode(packageName, pluginInfo.versionCode);

        // Validate built-in plugin signatures
        if (!SignUtils.verifyPlugin(pluginInfo)) {
            bundle.setEnabled(false);
            return true;
        }

        // Unzip the built-in plugin
        String webPath = FileUtils.getWebBundlePath();
        File unzipDir = new File(webPath + "/" + packageName);
        long soLastModified = plugin.lastModified();
        boolean needsUnzip;
        if (!unzipDir.exists()) {
            unzipDir.mkdir();
            needsUnzip = true;
        } else {
            long lastModified = Small.getBundleLastModified(packageName);
            needsUnzip = (soLastModified > lastModified);
        }
        if (needsUnzip) {
            try {
                FileUtils.unZipFolder(plugin, unzipDir.getPath());
            } catch (Exception e) {
                Log.e(TAG, "Failed to unzip plugin: " + plugin);
                return false;
            }
            Small.setBundleLastModified(packageName, soLastModified);
        }
        // Check if contains index page
        File indexFile = new File(unzipDir, "index.html");
        if (!indexFile.exists()) {
            Log.e(TAG, "Missing index.html for bundle " + packageName);
            return false;
        }

        // Overlay patch bundle
        File patchPlugin = new File(FileUtils.getDownloadBundlePath() + "/" + soName);
        if (patchPlugin.exists() && SignUtils.verifyPlugin(patchPlugin)) {
            try {
                FileUtils.unZipFolder(plugin, unzipDir.getPath());
                patchPlugin.delete();
            } catch (Exception ignored) {
                Log.e(TAG, "Failed to overlay patch for bundle " + packageName);
            }
        }

        // Prepare index url
        String uri = indexFile.toURI().toString();
        if (bundle.getQuery() != null) {
            uri += "?" + bundle.getQuery();
        }
        URL url;
        try {
            url = new URL(uri);
        } catch (MalformedURLException e) {
            Log.e(TAG, "Failed to parse url " + uri + " for bundle " + packageName);
            return false;
        }
        String scheme = url.getProtocol();
        if (!scheme.equals("http") &&
                !scheme.equals("https") &&
                !scheme.equals("file")) {
            Log.e(TAG, "Unsupported scheme " + scheme + " for bundle " + packageName);
            return false;
        }
        bundle.setURL(url);

        // Preload content
        if (Bundle.isLoadingAsync()) {
            Bundle.postInitWebViewMessage(url.toString());
        } else {
            WebViewPool.getInstance().alloc(url.toString());
        }
        return true;
    }

    @Override
    public void prelaunchBundle(Bundle bundle) {
        super.prelaunchBundle(bundle);
        Intent intent = bundle.getIntent();
        if (intent == null) {
            intent = new Intent(Small.getContext(), WebActivity.class);
            intent.putExtra("url", bundle.getURL().toString());
            // Intent extras - params
            String query = bundle.getQuery();
            if (query != null) {
                intent.putExtra(Small.KEY_QUERY, '?'+query);
            }
            bundle.setIntent(intent);
        }
    }

    @Override
    public void launchBundle(Bundle bundle, Context context) {
        prelaunchBundle(bundle);
        Intent intent = bundle.getIntent();
        intent.putExtra("url", bundle.getURL().toString());
        // Intent extras - params
        String query = bundle.getQuery();
        if (query != null) {
            intent.putExtra(Small.KEY_QUERY, '?'+query);
        }
        super.launchBundle(bundle, context);
    }
}
