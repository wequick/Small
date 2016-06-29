package net.wequick.example.lib.analytics;

import android.content.Context;
import android.os.Build;

import com.umeng.analytics.MobclickAgent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created by galen on 16/5/31.
 */
public class AnalyticsManager {

    private static Map<String, String> sDeviceInfo;

    public static void onEventValue(Context context, String key,
                                    Map<String, String> extensions,
                                    int count) {
        MobclickAgent.onEventValue(context, key, extensions, count);
    }

    public static void traceTime(Context context, String key, int time) {
        if (sDeviceInfo == null) {
            sDeviceInfo = new HashMap<String, String>();
            sDeviceInfo.put("model", Build.MODEL);
            sDeviceInfo.put("os", Build.VERSION.RELEASE + "(SDK" + Build.VERSION.SDK_INT + ")");
            sDeviceInfo.put("cpu", getCpuName());
            sDeviceInfo.put("cores", getNumCores() + "");
        }
        onEventValue(context, key, sDeviceInfo, time);
    }

    private static String getCpuName() {
        try {
            FileReader fr = new FileReader("/proc/cpuinfo");
            BufferedReader br = new BufferedReader(fr);
            String text = br.readLine();
            String[] array = text.split(":\\s+", 2);
            for (int i = 0; i < array.length; i++) {
            }
            return array[1];
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static int getNumCores() {
        // Private Class to display only CPU devices in the directory listing
        class CpuFilter implements FileFilter {
            @Override
            public boolean accept(File pathname) {
                // Check if filename is "cpu", followed by a single digit number
                return (Pattern.matches("cpu[0-9]", pathname.getName()));
            }
        }

        try {
            // Get directory containing CPU info
            File dir = new File("/sys/devices/system/cpu/");
            // Filter to only list the devices we care about
            File[] files = dir.listFiles(new CpuFilter());
            // Return the number of cores (virtual CPU devices)
            return files.length;
        } catch (Exception e) {
            // Default to return 1 core
            return 1;
        }
    }
}
