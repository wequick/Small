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

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

/**
 * Created by galen on 15/1/28.
 */
public class BundleLauncher {

    /**
     * Setup launcher, execute on {Small} setup
     */
    public void setup(Context context) { }

    /**
     * Try to preload a bundle
     * @param bundle
     * @return true: can launch bundle, false: otherwise
     */
    public boolean preloadBundle(Bundle bundle) { return false; }

    public void prelaunchBundle(Bundle bundle) { }

    public void launchBundle(Bundle bundle, Context context) {
        if (!bundle.isLaunchable()) {
            // TODO: Exit app

            return;
        }

        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (needsFinishActivity(activity)) {
                activity.finish();
            }
            activity.startActivityForResult(bundle.getIntent(), Small.REQUEST_CODE_DEFAULT);
        } else {
            context.startActivity(bundle.getIntent());
        }
    }

    /**
     * Create object with current launcher
     * @param context
     * @param type object type, like 'fragment', 'intent'
     * @param <T>
     * @return
     */
    public <T> T createObject(Bundle bundle, Context context, String type) {
        return null;
    }

    private boolean needsFinishActivity(Activity activity) {
        Uri uri = Small.getUri(activity);
        if (uri != null) {
            String fullscreen = uri.getQueryParameter("_fullscreen");
            if (!TextUtils.isEmpty(fullscreen)&&"1".equals(fullscreen)) {
                return true;
            }
        }
        return false;
    }
}
