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
import android.content.pm.PackageManager;

import net.wequick.small.util.FileUtils;
import net.wequick.small.util.SignUtils;

import java.io.File;
import java.io.InputStream;

/**
 * Class to launch .so bundle.
 * Created by galen on 16/1/13.
 */
public abstract class SoBundleLauncher extends BundleLauncher {

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

        // Check if has been built-in
        String soName = "lib" + packageName.replaceAll("\\.", "_") + ".so";
        File plugin = new File(Bundle.getUserBundlesPath(), soName);
        if (!plugin.exists()) return false;

        // Get package info
        PackageManager pm = Small.getContext().getPackageManager();
        PackageInfo pluginInfo = pm.getPackageArchiveInfo(plugin.getPath(),
                PackageManager.GET_SIGNATURES);
        if (pluginInfo == null) return false;

        // Verify signatures
        if (!SignUtils.verifyPlugin(pluginInfo)) {
            bundle.setEnabled(false);
            return true; // Got it, but disabled
        }

        // Check if exists a patch
        File patch = new File(FileUtils.getDownloadBundlePath(), soName);
        if (patch.exists()) {
            PackageInfo patchInfo = pm.getPackageArchiveInfo(plugin.getPath(),
                    PackageManager.GET_SIGNATURES);
            if (patchInfo == null || !SignUtils.verifyPlugin(patchInfo)) {
                patch.delete(); // Invalid patch
            } else {
                // Currently only support replacing
                // TODO: Incremental patch

            }
        }

        bundle.setFileName(soName);
        bundle.setFile(plugin);
        // Record version code for upgrade
        bundle.setVersionCode(pluginInfo.versionCode);
        Small.setBundleVersionCode(packageName, pluginInfo.versionCode);

        return true;
    }
}
