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
import android.util.Log;
import android.view.ContextThemeWrapper;

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

    static final String TAG = "HealthManager";

    public static boolean fixException(Object obj, Throwable e) {
        Class exceptionClass = e.getClass();
        if (exceptionClass.equals(IllegalStateException.class)) {
            // You need to use a Theme.AppCompat theme (or descendant) with this activity.
            if (e.getMessage().startsWith("You need to use a Theme.AppCompat")) {
                String err = "";
                Activity activity = (Activity) obj;
                try {
                    Field f = ContextThemeWrapper.class.getDeclaredField("mThemeResource");
                    f.setAccessible(true);
                    int theme = (int) f.get(activity);

                    err += "Failed to link theme " + String.format("0x%08x", theme) + "!\n";
                } catch (Exception ignored) { }

                AssetManager assets = activity.getAssets();
                AssetManager appAssets = activity.getApplication().getAssets();
                if (!assets.equals(appAssets)) {
                    err += "The activity assets are different from application.\n";
                    err += getAssetPathsDebugInfo(appAssets, "Application") + "\n";
                    err += getAssetPathsDebugInfo(assets, "Activity");
                } else {
                    err += getAssetPathsDebugInfo(assets, "Activity");
                }
                Log.e(TAG, err);
            }
        }

        return false;
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

    private static String getAssetPathsDebugInfo(AssetManager assets, String header) {
        List<String> assetPaths = getAssetPaths(assets);
        if (assetPaths == null) return "";

        File baseApk = new File(Small.getContext().getApplicationInfo().sourceDir);
        String hostPath = baseApk.getParent();
        String patchBundlePath = FileUtils.getDownloadBundlePath().getAbsolutePath();
        String builtinBundlePath;
        if (Small.isLoadFromAssets()) {
            builtinBundlePath = FileUtils.getInternalBundlePath().getAbsolutePath();
        } else {
            builtinBundlePath = Small.getContext().getApplicationInfo().nativeLibraryDir;
        }
        int hostPathLen = hostPath.length() + 1;
        int builtinPathLen = builtinBundlePath.length() + 1;
        int patchPathLen = patchBundlePath.length() + 1;

        StringBuilder sb = new StringBuilder(header);
        sb.append(" assets: \n");
        for (String assetPath : assetPaths) {
            if (assetPath.startsWith(builtinBundlePath)) {
                sb.append("  - ").append(assetPath.substring(builtinPathLen)).append(" (builtin)\n");
            } else if (assetPath.startsWith(patchBundlePath)) {
                sb.append("  - ").append(assetPath.substring(patchPathLen)).append(" (patch)\n");
            } else if (assetPath.startsWith(hostPath)) {
                sb.append("  - ").append(assetPath.substring(hostPathLen)).append(" (host)\n");
            } else {
                sb.append("  - ").append(assetPath).append(" (system)\n");
            }
        }
        return sb.toString();
    }
}
