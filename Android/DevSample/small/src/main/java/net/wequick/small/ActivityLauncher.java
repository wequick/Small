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

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.HashMap;

/**
 * This class launch the host activity by it's class name.
 *
 * <p>This class resolve the bundle who's <tt>pkg</tt> is unspecified
 * or specified as <i>"main"</i> in <tt>bundle.json</tt>.
 *
 * <p>While launching, the class takes the bundle's <tt>uri</tt> as
 * the starting activity's class name.
 *
 * <p>The conversions from <tt>uri</tt> to activity name are as following:
 * <ul>
 *     <li>If <tt>uri</tt> is empty, take as <tt>MainActivity</tt>.</li>
 *     <li>Otherwise, use <tt>uri</tt>. If the class not exists,
 *     add <i>"Activity"</i> suffix and do a second try.</li>
 * </ul>
 */
public class ActivityLauncher extends BundleLauncher {

    private static HashMap<String, Class<?>> sActivityClasses;

    @Override
    public void setUp(Context context) {
        super.setUp(context);

        // Read the registered classes in host's manifest file
        PackageInfo pi;
        try {
            pi = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException ignored) {
            // Never reach
            return;
        }
        ActivityInfo[] as = pi.activities;
        if (as != null) {
            sActivityClasses = new HashMap<String, Class<?>>();
            for (int i = 0; i < as.length; i++) {
                ActivityInfo ai = as[i];
                int dot = ai.name.lastIndexOf(".");
                if (dot > 0) {
                    try {
                        Class<?> clazz = Class.forName(ai.name);
                        sActivityClasses.put(ai.name, clazz);
                    } catch (ClassNotFoundException e) {
                        // Ignored
                    }
                }
            }
        }
    }

    @Override
    public boolean preloadBundle(Bundle bundle) {
        if (bundle.getBuiltinFile() != null && bundle.getBuiltinFile().exists()) return false;

        String packageName = bundle.getPackageName();
        Context context = Small.getContext();
        if (packageName == null) {
            packageName = context.getPackageName();
        }
        String activityName = bundle.getPath();
        if (activityName == null || activityName.equals("")) {
            activityName = "MainActivity";
        }
        Class activityClass = getRegisteredClass(packageName + "." + activityName);
        if (activityClass == null) return false;

        Intent intent = new Intent(context, activityClass);
        bundle.setIntent(intent);
        return true;
    }

    private static Class<?> getRegisteredClass(String clazz) {
        Class<?> aClass = sActivityClasses.get(clazz);
        if (aClass == null && !clazz.endsWith("Activity")) {
            aClass = sActivityClasses.get(clazz + "Activity");
        }
        return aClass;
    }
}
