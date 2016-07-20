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

import java.util.HashSet;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

/**
 * This class launch the plugin activity by it's class name.
 *
 * <p>This class resolve the bundle who's <tt>pkg</tt> is specified as
 * <i>"*.app.*"</i> or <i>*.lib.*</i> in <tt>bundle.json</tt>.
 *
 * <ul>
 * <li>The <i>app</i> plugin contains some activities usually, while launching,
 * takes the bundle's <tt>uri</tt> as default activity. the other activities
 * can be specified by the bundle's <tt>rules</tt>.</li>
 *
 * <li>The <i>lib</i> plugin which can be included by <i>app</i> plugin
 * consists exclusively of global methods that operate on your product services.</li>
 * </ul>
 *
 * @see ActivityLauncher
 */
public class ActivityLauncher extends BundleLauncher {

    private static HashSet<String> sActivityClasses;

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
            sActivityClasses = new HashSet<>();
            int N = as.length;
            for (int i = 0; i < N; i++) {
                ActivityInfo ai = as[i];
                int dot = ai.name.lastIndexOf(".");
                if (dot > 0) {
                    sActivityClasses.add(ai.name);
                }
            }
        }
    }

    @Override
    public boolean preloadBundle(Bundle bundle) {
        if (bundle.getBuiltinFile() != null && bundle.getBuiltinFile().exists()) {
            return false;
        }

        String activityName = bundle.getPath();
        if (activityName == null || activityName.isEmpty()) return false;
        if (!sActivityClasses.contains(activityName)) return false;

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(Small.getContext(), activityName));
        bundle.setIntent(intent);
        return true;
    }

    @Override
    public void prelaunchBundle(Bundle bundle) {
        super.prelaunchBundle(bundle);

        // Intent extras - class
        String activityName = bundle.getPath();
        if (activityName == null || activityName.equals("")) {
            activityName = bundle.getEntrance();
        } else {
            char c = activityName.charAt(0);
            if (c == '.') {
                activityName = bundle.getPackageName() + activityName;
            } else if (c >= 'A' && c <= 'Z') {
                activityName = bundle.getPackageName() + '.' + activityName;
            }
            if (!sActivityClasses.contains(activityName)) {
                if (activityName.endsWith("Activity")) {
                    throw new ActivityNotFoundException("Unable to find explicit activity class " +
                            "{ " + activityName + " }");
                }

                String tempActivityName = activityName + "Activity";
                if (!sActivityClasses.contains(tempActivityName)) {
                    throw new ActivityNotFoundException("Unable to find explicit activity class " +
                            "{ " + activityName + "(Activity) }");
                }

                activityName = tempActivityName;
            }
        }
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(Small.getContext(), activityName));
        // Intent extras - params
        String query = bundle.getQuery();
        if (query != null) {
            intent.putExtra(Small.KEY_QUERY, '?' + query);
        }
        bundle.setIntent(intent);
    }

    @Override
    public void launchBundle(Bundle bundle, Context context) {
        prelaunchBundle(bundle);
        super.launchBundle(bundle, context);
    }
}
