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

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import net.wequick.small.Small;

import java.io.File;
import java.util.HashSet;

import android.content.pm.Signature;

/**
 * This class consists exclusively of static methods that operate on apk signature.
 */
public class SignUtils {

    private static boolean sHostSigned = true;
    private static Signature[] sHostSignatures = null;

    /**
     * This method compare the <tt>plugin</tt> signatures with the hots one.
     * @param plugin the plugin file
     * @return <tt>true</tt> if the <tt>plugin</tt> signatures same with the host's.
     */
    public static boolean verifyPlugin(File plugin) {
        PackageManager pm = Small.getContext().getPackageManager();
        PackageInfo pluginInfo = pm.getPackageArchiveInfo(plugin.getPath(),
                PackageManager.GET_SIGNATURES);
        return verifyPlugin(pluginInfo);
    }

    public static boolean verifyPlugin(PackageInfo pluginInfo) {
        if (!sHostSigned) return true;

        PackageManager pm = Small.getContext().getPackageManager();
        if (sHostSignatures == null) {
            try {
                sHostSignatures = pm.getPackageInfo(Small.getContext().getPackageName(),
                        PackageManager.GET_SIGNATURES).signatures;
            } catch (PackageManager.NameNotFoundException ignored) {
                sHostSigned = false;
            }
        }

        // Match signatures
        if (pluginInfo == null) return false;
        if (pluginInfo.signatures == null) return false;

        return isSameSignatures(sHostSignatures, pluginInfo.signatures);
    }

    private static boolean isSameSignatures(Signature[] a, Signature[] b) {
        HashSet<Signature> sa = new HashSet<Signature>();
        HashSet<Signature> sb = new HashSet<Signature>();
        for (Signature s : a) sa.add(s);
        for (Signature s : b) sb.add(s);
        return (sa.equals(sb));
    }
}
