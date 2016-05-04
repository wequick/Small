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

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.util.List;

/**
 * This class consists exclusively of static methods that operate on application.
 */
public class ApplicationUtils {

    private ApplicationUtils() { /** cannot be instantiated */}

    /**
     * This method check if an <tt>uri</tt> can be opened by android system.
     * @param uri the intent uri
     * @param context current context
     * @return <tt>true</tt> if <tt>uri</tt> can be opened by android system.
     */
    public static boolean canOpenUri(Uri uri, Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> resolvedActivities = packageManager.queryIntentActivities(intent, 0);
        if (resolvedActivities.size() > 0) {
            return true;
        }
        return false;
    }

    /**
     * Wrap an system action intent by the <tt>uri</tt>
     * @param uri the intent uri
     * @return
     */
    public static Intent getIntentOfUri(Uri uri) {
        return new Intent(Intent.ACTION_VIEW, uri);
    }

    /**
     * Start an activity related to the <tt>uri</tt>
     * @param uri the intent uri
     * @param context current context
     */
    public static void openUri(Uri uri, Context context) {
        Intent intent = getIntentOfUri(uri);
        context.startActivity(intent);
    }
}
