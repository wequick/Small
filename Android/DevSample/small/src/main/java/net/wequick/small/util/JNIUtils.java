package net.wequick.small.util;

import android.os.Build;

import java.util.HashMap;
import java.util.Map;

/**
 * This class
 */
public final class JNIUtils {

    private static int[] sSupportedABIFlags;
    private static String[] sSupportedABINames;

    /**
     * Get the ABI (Application Binary Interface) name from a specify flag
     *
     * @param flag the flag refer to an ABI, this is automatically set by `gradle-small'
     */
    public static String getExtractABI(int flag) {
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
                abis = Build.SUPPORTED_ABIS;
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
