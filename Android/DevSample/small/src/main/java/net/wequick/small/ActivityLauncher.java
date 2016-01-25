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

    @Override
    public boolean preloadBundle(Bundle bundle) {
        String packageName = bundle.getPackageName();
        Context context = Small.getContext();
        if (packageName == null || packageName.equals("main")) {
            packageName = context.getPackageName();
        }
        String activityName = bundle.getPath();
        if (activityName == null || activityName.equals("")) {
            activityName = "MainActivity";
        }
        Class activityClass = Small.getRegisteredClass(packageName + "." + activityName);
        if (activityClass == null) return false;

        Intent intent = new Intent(context, activityClass);
        bundle.setIntent(intent);
        return true;
    }
}
