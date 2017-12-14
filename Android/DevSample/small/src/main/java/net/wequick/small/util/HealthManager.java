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
package net.wequick.small.util;

import android.app.Activity;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;
import android.util.SparseArray;
import android.view.ContextThemeWrapper;
import android.view.InflateException;

import net.wequick.small.Small;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * The health manager report the known issue and print tips for fixing.
 */
public class HealthManager {

    private static final String TAG = "HealthManager";

    public static boolean fixException(Object obj, Throwable e) {
        Class exceptionClass = e.getClass();
        Log.e(TAG, obj.getClass().getName() + " throws " + e.getClass().getName() + " exception.");
        if (exceptionClass.equals(IllegalStateException.class)) {
            // You need to use a Theme.AppCompat theme (or descendant) with this activity.
            if (e.getMessage().startsWith("You need to use a Theme.AppCompat")) {
                dumpAssets(obj, true);
            }
        } else if (exceptionClass.equals(InflateException.class)) {
            dumpAssets(obj, false);
        } else if (exceptionClass.equals(Resources.NotFoundException.class)) {
            dumpAssets(obj, false);
        }

        return false;
    }

    private static void dumpAssets(Object obj, boolean isThemeError) {
        if (!(obj instanceof Activity)) {
            return;
        }

        Activity activity = (Activity) obj;
        int themeId = 0;
        String err = "";
        if (isThemeError) {
            try {
                Field f = ContextThemeWrapper.class.getDeclaredField("mThemeResource");
                f.setAccessible(true);
                themeId = (int) f.get(activity);

                err += "Failed to link theme " + String.format("0x%08x", themeId) + "!\n";
            } catch (Exception ignored) { }
        }

        AssetManager assets = activity.getAssets();
        AssetManager appAssets = activity.getApplication().getAssets();
        if (!assets.equals(appAssets)) {
            err += "The activity assets are different from application.\n";
            err += getAssetPathsDebugInfo(appAssets, themeId, "Application") + "\n";
            err += getAssetPathsDebugInfo(assets, themeId, "Activity");
        } else {
            err += getAssetPathsDebugInfo(assets, themeId, "Activity");
        }
        Log.e(TAG, err);
    }

    private static List<String> getAssetPaths(AssetManager assets) {
        List<String> assetPaths = null;
        try {
            Method m = AssetManager.class.getDeclaredMethod("getStringBlockCount");
            m.setAccessible(true);
            int assetCount = (int) m.invoke(assets);
            assetPaths = new ArrayList<>(assetCount);

            m = AssetManager.class.getDeclaredMethod("getCookieName", int.class);
            m.setAccessible(true);
            for (int i = 1; i <= assetCount; i++) {
                String assetPath = (String) m.invoke(assets, i);
                assetPaths.add(assetPath);
            }
        } catch (Exception ignored) {

        }
        return assetPaths;
    }

    private static String getAssetPathsDebugInfo(AssetManager assets, int themeId, String header) {
        List<String> assetPaths = getAssetPaths(assets);
        if (assetPaths == null) return "";

        File baseApk = new File(Small.getContext().getApplicationInfo().sourceDir);
        String hostPath = baseApk.getParent();
        String patchBundlePath = FileUtils.getDownloadBundlePath().getAbsolutePath();
        String builtinBundlePath;
        boolean isApk = Small.isLoadFromAssets();
        if (isApk) {
            builtinBundlePath = FileUtils.getInternalBundlePath().getAbsolutePath();
        } else {
            builtinBundlePath = Small.getContext().getApplicationInfo().nativeLibraryDir;
        }
        int hostPathLen = hostPath.length() + 1;
        int builtinPathLen = builtinBundlePath.length() + 1;
        int patchPathLen = patchBundlePath.length() + 1;

        int themePackageId = (themeId >> 24) & 0xff;
        boolean found = false;

        StringBuilder sb = new StringBuilder(header);
        sb.append(" assets: \n");
        for (String assetPath : assetPaths) {
            boolean isBuiltBundle = false;
            boolean isPatchBundle = false;
            if (assetPath.startsWith(builtinBundlePath)) {
                isBuiltBundle = true;
            } else if (assetPath.startsWith(patchBundlePath)) {
                isPatchBundle = true;
            } else if (assetPath.startsWith(hostPath)) {
                sb.append("  - ").append(assetPath.substring(hostPathLen)).append(" (host)\n");
            } else {
                sb.append("  - ").append(assetPath).append(" (system)\n");
            }

            if (isBuiltBundle || isPatchBundle) {
                String bundleName = assetPath.substring(isBuiltBundle ? builtinPathLen : patchPathLen);
                String packageName = getPackageName(bundleName, isApk);
                int packageId = getPackageId(assets, packageName);
                if (packageId != 0) {
                    if (packageId == themePackageId) {
                        found = true;
                        sb.append("  > ");
                    } else {
                        sb.append("  - ");
                    }
                    sb.append(String.format("[0x%02x] ", packageId));
                } else {
                    sb.append("  - ");
                }
                sb.append(bundleName);
                sb.append(" (").append(isBuiltBundle ? "builtin" : "patch").append(")\n");
            }
        }

        if (found) {
            sb.append("Did find the bundle with package id '")
                    .append(String.format("0x%02x", themePackageId))
                    .append("' \n");
        } else {
            sb.append("\nCannot find the bundle with package id '")
                    .append(String.format("0x%02x", themePackageId))
                    .append("'. Please check if you had declare it in 'bundle.json'!\n");
        }
        return sb.toString();
    }

    private static String getPackageName(String fileName, boolean isApk) {
        if (isApk) {
            return fileName.substring(0, fileName.length() - 4);
        } else {
            String pkg = fileName.substring(0, fileName.length() - 3);
            pkg = pkg.replaceAll("_", ".");
            return pkg;
        }
    }

    private static int getPackageId(AssetManager assets, String packageName) {
        try {
            Method m = AssetManager.class.getDeclaredMethod("getAssignedPackageIdentifiers");
            m.setAccessible(true);
            SparseArray<String> pkgIds = (SparseArray<String>) m.invoke(assets);
            int id = 0;
            for (int i = 0; i < pkgIds.size(); i++) {
                String pkg = pkgIds.valueAt(i);
                if (pkg.equals(packageName)) {
                    id = pkgIds.keyAt(i);
                    break;
                }
            }
            return id;
        } catch (Exception ignored) {

        }
        return 0;
    }
}
