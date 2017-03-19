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
import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

/**
 * This class resolve the bundle and launch it.
 *
 * <p>In general the movement through a bundle launcher's lifecycle looks like
 * this:</p>
 *
 * <table border="2" width="85%" align="center" frame="hsides" rules="rows">
 *      <colgroup align="left" span="3" />
 *      <colgroup align="left" />
 *      <colgroup align="center" />
 *
 *      <thead>
 *      <tr><th colspan="3">Method</th> <th>Description</th> <th>Next</th></tr>
 *      </thead>
 *
 *      <tbody>
 *      <tr><th colspan="3" align="left" border="0">{@link #setUp(Context)}</th>
 *          <td>Called when the <b>Small</b> is setUp by {@link Small#setUp(Context, Small.OnCompleteListener)}.
 *              This is where you should do all of your normal static set up:
 *              initialize the launcher, prepare for resolving bundle.</td>
 *          <td align="center"><code>preloadBundle(Bundle)</code></td>
 *      </tr>
 *
 *      <tr><td rowspan="2" style="border-left: none; border-right: none;">&nbsp;&nbsp;&nbsp;&nbsp;</td>
 *          <th colspan="2" align="left" border="0">{@link #preloadBundle(Bundle)}</th>
 *          <td>Called when loading bundles by {@link Bundle#loadLaunchableBundles(Small.OnCompleteListener)}.
 *              This is where you should do all of your normal validations and preparations.</td>
 *          <td align="center"><code>loadBundle(Bundle)</code></td>
 *      </tr>
 *
 *      <tr><th colspan="2" align="left" border="0">{@link #loadBundle(Bundle)}</th>
 *          <td>Called when the bundle is becoming resolvable to the launcher.
 *              <p>Followed by <code>preloadBundle(bundle)</code> if the bundle is validated.</td>
 *          <td align="center">postSetUp()</td>
 *      </tr>
 *
 *      <tr><th colspan="3" align="left" border="0">{@link #postSetUp()}</th>
 *          <td>Called when the <b>Small</b> finish setUp by {@link Small#setUp(Context, Small.OnCompleteListener)}.
 *              This is where you should do all of the initialization of bundle collection:
 *              add the bundle paths for your resources/code search path.</td>
 *          <td align="center"><code>nothing</code></td>
 *      </tr>
 *
 *      <tr><td rowspan="2" style="border-left: none;">&nbsp;&nbsp;&nbsp;&nbsp;</td>
 *          <th colspan="2" align="left" border="0">{@link #prelaunchBundle(Bundle)}</th>
 *          <td>Called when the launcher will start an activity from the bundle.
 *              This is where you should do all of the intent build up:
 *              resolve the starting activity class, set the query parameters.
 *              <p>Always followed by <code>Small.openUri(uri, context)</code>.</td>
 *          <td align="center"><code>launchBundle(bundle)</code></td>
 *      </tr>
 *
 *      <tr><th colspan="2" align="left" border="0">{@link #launchBundle(Bundle, Context)}</th>
 *          <td>Called when the bundle is ready to launch. This is typically means the starting activity's
 *              intent has been prepared.
 *              <p>Followed by <code>prelaunchBundle(bundle)</code>.</td>
 *          <td align="center">nothing</td>
 *      </tr>
 *
 *      </tbody>
 *  </table>
 */
public abstract class BundleLauncher {

    /**
     * Called when the launcher is instantiated. This is where most initialization
     * should go: initialize the application, hook some app-wide fields or methods.
     * This method is called before the application onCreate method, to make a better performance,
     * do as less thing in it as possible.
     *
     * @param app the starting application.
     */
    public void onCreate(Application app) { }

    /**
     * Called when Small is setUp by {@link Small#setUp}. This is where most initialization
     * should go: initialize the launcher context, prepare for resolving bundle.
     *
     * @param context the context passed by {@link Small#setUp}
     */
    public void setUp(Context context) { }

    /**
     * Called when Small is finish setUp by {@link Small#setUp}. This is where most completion
     * should go: do something with the loaded bundles, free the memory of temporary variables.
     */
    public void postSetUp() { }

    /**
     * Called when loading bundles by {@link Bundle#loadLaunchableBundles(Small.OnCompleteListener)}.
     *
     * <p>This method try to preload a bundle, if succeed, load it later.
     *
     * @hide
     * @param bundle the loading bundle
     * @return <tt>true</tt> if the <tt>bundle</tt> is resolved
     */
    public boolean resolveBundle(Bundle bundle) {
        if (!preloadBundle(bundle)) return false;

        loadBundle(bundle);
        return true;
    }

    /**
     * Called when loading bundles by {@link Bundle#loadLaunchableBundles(Small.OnCompleteListener)}.
     * This is where validation and preparation should go:
     * validate the <tt>bundle</tt> package name,
     * validate the <tt>bundle</tt> signatures, record the <tt>bundle</tt> version for upgrade.
     *
     * @param bundle the loading bundle
     * @return <tt>true</tt> if the <tt>bundle</tt> can be loaded
     */
    public boolean preloadBundle(Bundle bundle) {
        return true;
    }

    /**
     * Called after {@link #preloadBundle(Bundle)} succeed. This is where most I/O
     * should go: parse the bundle file and resolve the bundle activities or asset files,
     * prepare the main entrance of the bundle to start.
     *
     * @param bundle the loading bundle
     */
    public void loadBundle(Bundle bundle) { }

    /**
     * Called when launching a bundle by {@link Small#openUri}. This is where most initialization
     * should go: prepare the intent of starting activity and passing parameters by the
     * <tt>uri</tt> from {@link Small#openUri}.
     *
     * @param bundle the launching <tt>bundle</tt>
     */
    public void prelaunchBundle(Bundle bundle) { }

    /**
     * Called after {@link #prelaunchBundle(Bundle)}.
     *
     * This is usually starting an activity.
     *
     * @param bundle the launching bundle
     * @param context current context
     */
    public void launchBundle(Bundle bundle, Context context) {
        if (!bundle.isLaunchable()) {
            // TODO: Exit app

            return;
        }

        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (shouldFinishPreviousActivity(activity)) {
                activity.finish();
            }
            activity.startActivityForResult(bundle.getIntent(), Small.REQUEST_CODE_DEFAULT);
        } else {
            context.startActivity(bundle.getIntent());
        }
    }

    /**
     * Upgrade the bundle.
     *
     * <p>This method should be called after you have downloaded the <tt>bundle</tt>'s
     * patch file witch code like {@code downloadFile(url, bundle.getPatchFile());}.
     *
     * <p>Currently, we only set a flag in this method and do upgrading while the application
     * launched at next time.
     *
     * TODO: Accomplish hotfix on application running.
     *
     * @param bundle the bundle to upgrade
     */
    public void upgradeBundle(Bundle bundle) {
        // Set flag to tell Small to upgrade bundle while launching application at next time
        Small.setBundleUpgraded(bundle.getPackageName(), true);
        // TODO: Hotfix
//        bundle.setPatching(true);
//        resolveBundle(bundle);
//        bundle.setPatching(false);
    }

    /**
     * Create object with current launcher.
     *
     * @param bundle the launching bundle
     * @param context current context
     * @param type object type, like 'fragment', 'intent'
     * @param <T> the return type
     * @return the object created
     */
    public <T> T createObject(Bundle bundle, Context context, String type) {
        return null;
    }

    /**
     * Called when starting a new activity. This is where to check if should finish previous
     * activity before start a new one.
     *
     * @param activity
     * @return <tt>true</tt> if should finish previous activity first
     */
    private boolean shouldFinishPreviousActivity(Activity activity) {
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
