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

import android.content.pm.PackageInfo;
import android.util.Log;

import java.io.File;

/**
 * This class resolve the bundle file with the extension of ".so".
 *
 * <p>All the bundle files are built in at application lib path ({@code bundle.getBuiltinFile()}).
 *
 * <p>All the bundles can be upgraded after you download the patch file to
 * {@code bundle.getPatchFile()} and call {@code bundle.upgrade()}.
 *
 * There are two primary implements of this class:
 * <ul>
 *     <li>{@link ApkBundleLauncher} resolve the apk bundle</li>
 *     <li>{@link WebBundleLauncher} resolve the native web bundle</li>
 * </ul>
 */
public abstract class SoBundleLauncher extends BundleLauncher implements BundleExtractor {

    private static final String TAG = "SoBundle";

    /** Types that support */
    protected abstract String[] getSupportingTypes();

    @Override
    public boolean preloadBundle(Bundle bundle) {
        String packageName = bundle.getPackageName();
        if (packageName == null) return false;

        // Check if supporting
        String[] types = getSupportingTypes();
        if (types == null) return false;

        boolean supporting = false;
        String bundleType = bundle.getType();
        if (bundleType != null) {
            // Consider user-defined type in `bundle.json'
            for (String type : types) {
                if (type.equals(bundleType)) {
                    supporting = true;
                    break;
                }
            }
        } else {
            // Consider explicit type specify in package name as following:
            //  - com.example.[type].any
            //  - com.example.[type]any
            String[] pkgs = packageName.split("\\.");
            int N = pkgs.length;
            String aloneType = N > 1 ? pkgs[N - 2] : null;
            String lastComponent = pkgs[N - 1];
            for (String type : types) {
                if ((aloneType != null && aloneType.equals(type))
                        || lastComponent.startsWith(type)) {
                    supporting = true;
                    break;
                }
            }
        }
        if (!supporting) return false;

        // Initialize the extract path
        File extractPath = getExtractPath(bundle);
        if (extractPath != null) {
            if (!extractPath.exists()) {
                extractPath.mkdirs();
            }
            bundle.setExtractPath(extractPath);
        }

        // Select the bundle entry-point, `built-in' or `patch'
        File plugin = bundle.getBuiltinFile();
        BundleParser parser = BundleParser.parsePackage(plugin, packageName);
        File patch = bundle.getPatchFile();
        BundleParser patchParser = BundleParser.parsePackage(patch, packageName);
        if (parser == null) {
            if (patchParser == null) {
                return false;
            } else {
                parser = patchParser; // use patch
                plugin = patch;
            }
        } else if (patchParser != null) {
            if (patchParser.getPackageInfo().versionCode <= parser.getPackageInfo().versionCode) {
                Log.d(TAG, "Patch file should be later than built-in!");
                patch.delete();
            } else {
                parser = patchParser; // use patch
                plugin = patch;
            }
        }
        bundle.setParser(parser);

        // Check if the plugin has not been modified
        long lastModified = plugin.lastModified();
        long savedLastModified = Small.getBundleLastModified(packageName);
        if (savedLastModified != lastModified) {
            // If modified, verify (and extract) each file entry for the bundle
            if (!parser.verifyAndExtract(bundle, this)) {
                bundle.setEnabled(false);
                return true; // Got it, but disabled
            }
            Small.setBundleLastModified(packageName, lastModified);
        }

        // Record version code for upgrade
        PackageInfo pluginInfo = parser.getPackageInfo();
        bundle.setVersionCode(pluginInfo.versionCode);
        bundle.setVersionName(pluginInfo.versionName);

        return true;
    }

    @Override
    public File getExtractPath(Bundle bundle) {
        return null;
    }

    @Override
    public File getExtractFile(Bundle bundle, String entryName) {
        return null;
    }
}
