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

import net.wequick.small.util.BundleParser;
import net.wequick.small.util.SignUtils;

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
public abstract class SoBundleLauncher extends BundleLauncher {

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
        for (String type : types) {
            if (packageName.contains("." + type + ".")) {
                supporting = true;
                break;
            }
        }
        if (!supporting) return false;

        // Check if has builtin
        File plugin = bundle.getBuiltinFile();
        BundleParser parser = BundleParser.parsePackage(plugin, packageName);
        if (parser == null) return false; // required builtin

        // Check if has a patch
        File patch = bundle.getPatchFile();
        BundleParser patchParser = BundleParser.parsePackage(patch, packageName);
        if (patchParser != null) { // has
            if (patchParser.getPackageInfo().versionCode < parser.getPackageInfo().versionCode) {
                Log.d(TAG, "Patch file should be later than built-in!");
                patch.delete();
            } else {
                parser = patchParser; // use patch
            }
        }
        bundle.setParser(parser);

        // Verify signatures
        PackageInfo pluginInfo = parser.getPackageInfo();
        if (!SignUtils.verifyPlugin(pluginInfo)) {
            bundle.setEnabled(false);
            return true; // Got it, but disabled
        }

        // Record version code for upgrade
        bundle.setVersionCode(pluginInfo.versionCode);

        return true;
    }
}
