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
import android.app.ActivityThread;
import android.app.Application;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.pm.ProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import net.wequick.small.util.ReflectAccelerator;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

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
class ApkBundleLauncher extends SoBundleLauncher {

    private static final String TAG = "ApkBundleLauncher";
    private static final String FD_STORAGE = "storage";
    private ApkClassLoader mApkLoader;
    private ApkInstrumentation mInstrumentation;

    @Override
    public void onCreate(Application app) {
        super.onCreate(app);

        ActivityThread thread = ActivityThread.currentActivityThread();
        List<ProviderInfo> providers;
        Field f;

        // Get providers
        Object/*AppBindData*/ data;
        try {
            f = ActivityThread.class.getDeclaredField("mBoundApplication");
            f.setAccessible(true);
            data = f.get(thread);
            f = data.getClass().getDeclaredField("providers");
            f.setAccessible(true);
            providers = (List<ProviderInfo>) f.get(data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get providers from thread: " + thread, e);
        }

        // Replace instrumentation
        try {
            f = ActivityThread.class.getDeclaredField("mInstrumentation");
            f.setAccessible(true);
            Instrumentation base = thread.getInstrumentation();
            mInstrumentation = new ApkInstrumentation(base, thread, providers);
            f.set(thread, mInstrumentation);
        } catch (Exception e) {
            throw new RuntimeException("Failed to replace instrumentation for thread: " + thread, e);
        }

        // Replace host class loader
        try {
            f = data.getClass().getDeclaredField("info");
            f.setAccessible(true);
            Object/*ApkInfo*/ apk = f.get(data);

            f = apk.getClass().getDeclaredField("mClassLoader");
            f.setAccessible(true);
            ClassLoader parent = (ClassLoader) f.get(apk);

            mApkLoader = new ApkClassLoader(parent, mInstrumentation);
            f.set(apk, mApkLoader);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to replace host class loader!", e);
        }

        ReflectAccelerator.setActivityThread(thread);
    }

    @Override
    public void setUp(Context context) {
        super.setUp(context);

        // AOP for pending intent
        try {
            Field f = TaskStackBuilder.class.getDeclaredField("IMPL");
            f.setAccessible(true);
            final Object impl = f.get(TaskStackBuilder.class);
            InvocationHandler aop = new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    Intent[] intents = (Intent[]) args[1];
                    for (Intent intent : intents) {
                        mInstrumentation.wrapIntent(intent);
                        intent.setAction(Intent.ACTION_MAIN);
                        intent.addCategory(Intent.CATEGORY_LAUNCHER);
                    }
                    return method.invoke(impl, args);
                }
            };
            Object newImpl = Proxy.newProxyInstance(context.getClassLoader(), impl.getClass().getInterfaces(), aop);
            f.set(TaskStackBuilder.class, newImpl);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
    }

    @Override
    public void postSetUp() {
        super.postSetUp();

        if (mApkLoader.isEmpty()) {
            Log.e(TAG, "Could not find any APK bundles!");
            return;
        }

        // Set up the loader
        mApkLoader.setUp();

        // Lazy install content providers
        mInstrumentation.lazyInstallProviders();
    }

    @Override
    protected String[] getSupportingTypes() {
        return new String[] {"app", "lib"};
    }

    @Override
    public File getExtractPath(Bundle bundle) {
        Context context = Small.getContext();
        File packagePath = context.getFileStreamPath(FD_STORAGE);
        return new File(packagePath, bundle.getPackageName());
    }

    @Override
    public File getExtractFile(Bundle bundle, String entryName) {
        if (!entryName.endsWith(".so")) return null;

        return new File(bundle.getExtractPath(), entryName);
    }

    @Override
    public void loadBundle(Bundle bundle) {
        String packageName = bundle.getPackageName();

        BundleParser parser = bundle.getParser();
        parser.collectActivities();
        PackageInfo pluginInfo = parser.getPackageInfo();

        // Load the bundle
        if (!mApkLoader.hasApk(packageName)) {
            mApkLoader.addApk(packageName, bundle);
        }

        if (pluginInfo.activities == null) {
            return;
        }

        // Record activities for intent redirection
        mInstrumentation.addActivities(pluginInfo.activities);

        // Record intent-filters for implicit action
        mInstrumentation.addIntentFilters(parser.getIntentFilters());

        // Set entrance activity
        bundle.setEntrance(parser.getDefaultActivityName());
    }

    @Override
    public void prelaunchBundle(Bundle bundle) {
        super.prelaunchBundle(bundle);
        Intent intent = new Intent();
        bundle.setIntent(intent);

        // Intent extras - class
        String activityName = bundle.getActivityName();
        if (!ActivityLauncher.containsActivity(activityName)) {
            activityName = mInstrumentation.resolveActivity(activityName);
        }
        intent.setComponent(new ComponentName(Small.getContext(), activityName));

        // Intent extras - params
        String query = bundle.getQuery();
        if (query != null) {
            intent.putExtra(Small.KEY_QUERY, '?'+query);
        }
    }

    @Override
    public void launchBundle(Bundle bundle, Context context) {
        prelaunchBundle(bundle);
        super.launchBundle(bundle, context);
    }

    @Override
    public <T> T createObject(Bundle bundle, Context context, String type) {
        if (type.startsWith("fragment")) {
            if (!(context instanceof Activity)) {
                return null; // context should be an activity which can be add resources asset path
            }
            String packageName = bundle.getPackageName();
            if (packageName == null) return null;
            String fname = bundle.getPath();
            if (fname == null || fname.equals("")) {
                fname = packageName + ".MainFragment"; // default
            } else {
                char c = fname.charAt(0);
                if (c == '.') {
                    fname = packageName + fname;
                } else if (c >= 'A' && c <= 'Z') {
                    fname = packageName + "." + fname;
                } else {
                    // TODO: check the full quality fragment class name
                }
            }
            if (type.endsWith("v4")) {
                return (T) android.support.v4.app.Fragment.instantiate(context, fname);
            }
            return (T) android.app.Fragment.instantiate(context, fname);
        }
        return super.createObject(bundle, context, type);
    }

    void wrapIntent(Intent intent) {
        mInstrumentation.wrapIntent(intent);
    }
}
