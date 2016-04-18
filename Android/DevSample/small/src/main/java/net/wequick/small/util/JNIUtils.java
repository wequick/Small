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

import android.os.Build;

import java.util.HashMap;
import java.util.Map;

/**
 * This class consists exclusively of static methods that operate on JNI.
 */
public final class JNIUtils {

    private static int[] sSupportedABIFlags;
    private static String[] sSupportedABINames;

    /**
     * Get the ABI (Application Binary Interface) name from a specify flag
     *
     * @param flag the flag refer to an ABI, this is automatically set by `gradle-small'
     */
    public static String getExtractABI(int flag, boolean is64bit) {
        if (flag == 0) return null;

        if (sSupportedABIFlags == null) {
            // ABIs on http://developer.android.com/intl/zh-cn/ndk/guides/abis.html
            // FIXME: the flags here should keep updating while any new ABIs born.
            Map<String, Integer> flags = new HashMap<String, Integer>();
            flags.put("armeabi",     1);
            flags.put("armeabi-v7a", 1 << 1);
            flags.put("arm64-v8a",   1 << 2);
            flags.put("x86",         1 << 3);
            flags.put("x86_64",      1 << 4);
            flags.put("mips",        1 << 5);
            flags.put("mips64",      1 << 6);

            String[] abis;
            if (Build.VERSION.SDK_INT >= 21) {
                // Cause we stub all the bundle(*.so) in host, if the host ABI is something
                // 32(64) bit, and then System.loadLibrary cannot accept 64(32) bit ABIs.
                // So we had to choose the related ABI as host.
                // FIXME: any solution?
                if (is64bit) {
                    abis = Build.SUPPORTED_64_BIT_ABIS;
                } else {
                    abis = Build.SUPPORTED_32_BIT_ABIS;
                }
            } else if (Build.CPU_ABI2.equals(Build.UNKNOWN)) {
                abis = new String[] { Build.CPU_ABI };
            } else {
                abis = new String[] { Build.CPU_ABI, Build.CPU_ABI2 };
            }

            final int N = abis.length;
            sSupportedABINames = abis;
            sSupportedABIFlags = new int[N];
            for (int i = 0; i < N; i++) {
                String abi = abis[i];
                sSupportedABIFlags[i] = flags.get(abi);
            }
        }

        final int N = sSupportedABIFlags.length;
        for (int i = 0; i < N; i++) {
            int f = sSupportedABIFlags[i];
            if ((flag & f) != 0) {
                return sSupportedABINames[i];
            }
        }
        return null;
    }
}
