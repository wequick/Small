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
package net.wequick.gradle.util

public final class JNIUtils {

    private static Map<String, Integer> sSupportedABIFlags;

    /**
     * Get the ABI (Application Binary Interface) flag from names
     *
     * @param names the ABI names
     */
    public static int getABIFlag(names) {
        if (names == null || names.size() == 0) return 0

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
            sSupportedABIFlags = flags
        }

        int flag = 0
        names.each {
            Integer f = sSupportedABIFlags.get(it)
            if (f != null) flag |= f
        }
        return flag;
    }
}