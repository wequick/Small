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
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.ContextWrapper;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.IBinder;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.DisplayMetrics;
import android.util.Log;

import net.wequick.small.util.ReflectAccelerator;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
public class ApkBundleLauncher extends SoBundleLauncher {

    private static final String PACKAGE_NAME = ApkBundleLauncher.class.getPackage().getName();
    private static final String STUB_ACTIVITY_PREFIX = PACKAGE_NAME + ".A";
    private static final String TAG = "ApkBundleLauncher";
    private static final String FD_STORAGE = "storage";
    private static final String FD_LIBRARY = "lib";
    private static final String FILE_DEX = "bundle.dex";

    private static class LoadedApk {
        public String assetPath;
        public int dexElementIndex;
        public File dexFile;
        public ActivityInfo[] activities;
    }

    private static ConcurrentHashMap<String, LoadedApk> sLoadedApks;
    private static ConcurrentHashMap<String, ActivityInfo> sLoadedActivities;

    protected static Instrumentation sHostInstrumentation;

    /**
     * Class for redirect activity from Stub(AndroidManifest.xml) to Real(Plugin)
     */
    private static class InstrumentationWrapper extends Instrumentation {

        private static final char REDIRECT_FLAG = '>';
        private static final int STUB_ACTIVITIES_COUNT = 4;

        public InstrumentationWrapper() { }

        /** @Override V21+
         * Wrap activity from REAL to STUB */
        public ActivityResult execStartActivity(
                Context who, IBinder contextThread, IBinder token, Activity target,
                Intent intent, int requestCode, android.os.Bundle options) {
            wrapIntent(intent);
            return ReflectAccelerator.execStartActivityV21(sHostInstrumentation,
                    who, contextThread, token, target, intent, requestCode, options);
        }

        /** @Override V20-
         * Wrap activity from REAL to STUB */
        public ActivityResult execStartActivity(
                Context who, IBinder contextThread, IBinder token, Activity target,
                Intent intent, int requestCode) {
            wrapIntent(intent);
            return ReflectAccelerator.execStartActivityV20(sHostInstrumentation,
                    who, contextThread, token, target, intent, requestCode);
        }

        @Override
        /** Unwrap activity from STUB to REAL */
        public Activity newActivity(ClassLoader cl, String className, Intent intent)
                throws InstantiationException, IllegalAccessException, ClassNotFoundException {
            // Stub -> Real
            if (!className.startsWith(STUB_ACTIVITY_PREFIX)) {
                return super.newActivity(cl, className, intent);
            }
            className = unwrapIntent(intent, className);
            Activity activity = super.newActivity(cl, className, intent);
            return activity;
        }

        @Override
        /** Prepare resources for REAL */
        public void callActivityOnCreate(Activity activity, android.os.Bundle icicle) {
            boolean needsRestoreInstanceState = false;
            do {
                if (sLoadedActivities == null) break;
                ActivityInfo ai = sLoadedActivities.get(activity.getClass().getName());
                if (ai == null) break;

                ensureAddAssetPath(activity);
                applyActivityInfo(activity, ai);

                if (icicle != null) break;

                // If killed in background, we are now redirecting by `net.wequick.small.A'.
                android.os.Bundle extras = activity.getIntent().getExtras();
                if (extras != null) {
                    icicle = extras.getBundle(Small.KEY_SAVED_INSTANCE_STATE);
                    needsRestoreInstanceState = (icicle != null);
                }
            } while (false);

            super.callActivityOnCreate(activity, icicle);
            if (needsRestoreInstanceState) {
                super.callActivityOnRestoreInstanceState(activity, icicle);
            }
        }

        @Override
        public void callActivityOnSaveInstanceState(Activity activity, android.os.Bundle outState) {
            super.callActivityOnSaveInstanceState(activity, outState);
            // If killed in background, save the REAL activity for `net.wequick.small.A' to jump.
            outState.putString(Small.KEY_ACTIVITY, activity.getClass().getName());
            android.os.Bundle extras = activity.getIntent().getExtras();
            if (extras != null) {
                outState.putString(Small.KEY_QUERY, extras.getString(Small.KEY_QUERY));
            }
        }

        @Override
        public void callActivityOnDestroy(Activity activity) {
            do {
                if (sLoadedActivities == null) break;
                String realClazz = activity.getClass().getName();
                ActivityInfo ai = sLoadedActivities.get(realClazz);
                if (ai == null) break;
                inqueueStubActivity(ai, realClazz);
            } while (false);
            super.callActivityOnDestroy(activity);
        }

        private void wrapIntent(Intent intent) {
            String realClazz = intent.getComponent().getClassName();
            if (sLoadedActivities == null) return;

            ActivityInfo ai = sLoadedActivities.get(realClazz);
            if (ai == null) return;

            intent.addCategory(REDIRECT_FLAG + realClazz);
            String stubClazz = dequeueStubActivity(ai, realClazz);
            intent.setComponent(new ComponentName(Small.getContext(), stubClazz));
        }

        private String unwrapIntent(Intent intent, String className) {
            Set<String> categories = intent.getCategories();
            if (categories == null) return className;

            // Get plugin activity class name from categories
            Iterator<String> it = categories.iterator();
            String realClazz = null;
            while (it.hasNext()) {
                String category = it.next();
                if (category.charAt(0) == REDIRECT_FLAG) {
                    realClazz = category.substring(1);
                    break;
                }
            }
            if (realClazz == null) return className;
            return realClazz;
        }

        private String[] mStubQueue;

        /** Get an usable stub activity clazz from real activity */
        private String dequeueStubActivity(ActivityInfo ai, String realActivityClazz) {
            if (ai.launchMode == ActivityInfo.LAUNCH_MULTIPLE) {
                // In standard mode, the stub activity is reusable.
                return STUB_ACTIVITY_PREFIX;
            }

            int availableId = -1;
            int stubId = -1;
            int countForMode = STUB_ACTIVITIES_COUNT;
            int countForAll = countForMode * 3; // 3=[singleTop, singleTask, singleInstance]
            if (mStubQueue == null) {
                // Lazy init
                mStubQueue = new String[countForAll];
            }
            int offset = (ai.launchMode - 1) * countForMode;
            for (int i = 0; i < countForMode; i++) {
                String usedActivityClazz = mStubQueue[i + offset];
                if (usedActivityClazz == null) {
                    availableId = i;
                } else if (usedActivityClazz.equals(realActivityClazz)) {
                    stubId = i;
                }
            }
            if (stubId != -1) {
                availableId = stubId;
            } else if (availableId != -1) {
                mStubQueue[availableId] = realActivityClazz;
            } else {
                // TODO:
                Log.e(TAG, "Launch mode " + ai.launchMode + " is full");
            }
            return STUB_ACTIVITY_PREFIX + ai.launchMode + availableId;
        }

        /** Unbind the stub activity from real activity */
        private void inqueueStubActivity(ActivityInfo ai, String realActivityClazz) {
            if (ai.launchMode == ActivityInfo.LAUNCH_MULTIPLE) return;
            if (mStubQueue == null) return;

            int countForMode = STUB_ACTIVITIES_COUNT;
            int offset = (ai.launchMode - 1) * countForMode;
            for (int i = 0; i < countForMode; i++) {
                String stubClazz = mStubQueue[i + offset];
                if (stubClazz != null && stubClazz.equals(realActivityClazz)) {
                    mStubQueue[i + offset] = null;
                    break;
                }
            }
        }
    }

    @Override
    public void setUp(Context context) {
        super.setUp(context);
        // Inject instrumentation
        if (sHostInstrumentation == null) {
            try {
                final Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
                final Method method = activityThreadClass.getMethod("currentActivityThread");
                Object thread = method.invoke(null, (Object[]) null);
                Field field = activityThreadClass.getDeclaredField("mInstrumentation");
                field.setAccessible(true);
                sHostInstrumentation = (Instrumentation) field.get(thread);
                Instrumentation wrapper = new InstrumentationWrapper();
                field.set(thread, wrapper);

                if (context instanceof Activity) {
                    field = Activity.class.getDeclaredField("mInstrumentation");
                    field.setAccessible(true);
                    field.set(context, wrapper);
                }
            } catch (Exception ignored) {
                ignored.printStackTrace();
                // Usually, cannot reach here
            }
        }
    }

    @Override
    protected String[] getSupportingTypes() {
        return new String[] {"app", "lib"};
    }

    private void unloadBundle(String packageName) {
        if (sLoadedApks == null) return;
        LoadedApk apk = sLoadedApks.get(packageName);
        if (apk == null) return;

        if (sLoadedActivities != null && apk.activities != null) {
            for (ActivityInfo ai : apk.activities) {
                sLoadedActivities.remove(ai.name);
            }
        }
        sLoadedApks.remove(packageName);

        // Remove asset path from application (Reset resources merger)
        Context appContext = ((ContextWrapper) Small.getContext()).getBaseContext();
        ResourcesMerger rm = ResourcesMerger.merge(appContext);
        ReflectAccelerator.setResources(appContext, rm);
        // TODO: Remove asset path from launching activities

        // Remove dexElement from ClassLoader?
        for (LoadedApk a : sLoadedApks.values()) {
            if (a.dexElementIndex > apk.dexElementIndex) a.dexElementIndex--;
        }
        ReflectAccelerator.removeDexPathList(
                appContext.getClassLoader(), apk.dexElementIndex);
        if (apk.dexFile.exists()) apk.dexFile.delete();
    }

    @Override
    public void loadBundle(Bundle bundle) {
        boolean patching = bundle.isPatching();
        String packageName = bundle.getPackageName();
        File plugin = bundle.getBuiltinFile();
        PackageManager pm = Small.getContext().getPackageManager();
        PackageInfo pluginInfo = pm.getPackageArchiveInfo(plugin.getPath(),
                PackageManager.GET_ACTIVITIES);

        File patch = bundle.getPatchFile();
        if (patch.exists()) {
            PackageInfo patchInfo = pm.getPackageArchiveInfo(patch.getPath(),
                    PackageManager.GET_ACTIVITIES);
            if (patchInfo.versionCode < pluginInfo.versionCode) {
                Log.d(TAG, "Patch file should be later than built-in!");
                patch.delete();
            } else {
                plugin = patch;
                pluginInfo = patchInfo;
            }
        }

        if (patching) {
            // Unload bundle of the package name
            unloadBundle(packageName);
        }

        // Load the bundle
        String apkPath = plugin.getPath();
        if (sLoadedApks == null) sLoadedApks = new ConcurrentHashMap<String, LoadedApk>();
        LoadedApk apk = sLoadedApks.get(packageName);
        if (apk == null) {
            apk = new LoadedApk();
            apk.assetPath = apkPath;
            apk.dexElementIndex = 0; // insert to header
            apk.activities = pluginInfo.activities;

            // Add dex element to class loader's pathList
            Context context = Small.getContext();
            File packagePath = context.getFileStreamPath(FD_STORAGE);
            packagePath = new File(packagePath, packageName);
            if (!packagePath.exists()) {
                packagePath.mkdirs();
            }
            File libDir = new File(packagePath, FD_LIBRARY);
            File optDexFile = new File(packagePath, FILE_DEX);

            // Going to insert dexElement to header, so increase the index of the others
            for (LoadedApk a : sLoadedApks.values()) a.dexElementIndex++;
            if (Small.getBundleUpgraded(packageName)) {
                // If upgraded, delete the opt dex file for recreating
                if (optDexFile.exists()) optDexFile.delete();
                Small.setBundleUpgraded(packageName, false);
            }
            ReflectAccelerator.expandDexPathList(
                    context.getClassLoader(), apkPath, libDir.getPath(), optDexFile.getPath());

            apk.dexFile = optDexFile;
            sLoadedApks.put(packageName, apk);
        }

        // Call bundle application onCreate
        String bundleApplicationName = pluginInfo.applicationInfo.className;
        if (bundleApplicationName != null) {
            try {
                Class applicationClass = Class.forName(bundleApplicationName);
                Application bundleApplication = Instrumentation.newApplication(
                        applicationClass, Small.getContext());
                sHostInstrumentation.callApplicationOnCreate(bundleApplication);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (pluginInfo.activities == null) {
            bundle.setLaunchable(false);
            return;
        }

        // Record activities for intent redirection
        bundle.setEntrance(pluginInfo.activities[0].name);
        if (sLoadedActivities == null) sLoadedActivities = new ConcurrentHashMap<String, ActivityInfo>();
        for (ActivityInfo ai : pluginInfo.activities) {
            sLoadedActivities.put(ai.name, ai);
        }
    }

    @Override
    public void prelaunchBundle(Bundle bundle) {
        super.prelaunchBundle(bundle);
        Intent intent = new Intent();
        bundle.setIntent(intent);
        // Intent extras - class
        String activityName = bundle.getPath();
        if (activityName == null || activityName.equals("")) {
            activityName = bundle.getEntrance();
        } else if (activityName.startsWith(".")) {
            activityName = bundle.getPackageName() + activityName;
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
            } else if (fname.startsWith(".")) {
                fname = packageName + fname;
            } else {
                // TODO: check package name
                assert false;
            }
            ensureAddAssetPath((Activity) context);
            if (type.endsWith("v4")) {
                return (T) android.support.v4.app.Fragment.instantiate(context, fname);
            }
            return (T) android.app.Fragment.instantiate(context, fname);
        }
        return super.createObject(bundle, context, type);
    }

    /**
     * Apply plugin activity info with plugin's AndroidManifest.xml
     * @param activity
     * @param ai
     */
    private static void applyActivityInfo(Activity activity, ActivityInfo ai) {
        // Apply plugin theme
        ReflectAccelerator.setTheme(activity, null);
        activity.setTheme(ai.getThemeResource());
        // Apply plugin softInputMode
        activity.getWindow().setSoftInputMode(ai.softInputMode);
    }

    /**
     * Try to get plugin resource, if failed, add plugin asset path
     * @param activity
     */
    private static void ensureAddAssetPath(Activity activity) {
        Context appBase = activity.getApplication().getBaseContext();
        Resources appRes = appBase.getResources();
        Resources activityRes = activity.getResources();
        if (appRes instanceof ResourcesMerger) {
            // Synchronize resources from application
            if (!activityRes.equals(appRes)) ReflectAccelerator.setResources(activity, appRes);
        } else {
            // Replace resources for application and activity
            ResourcesMerger rm = ResourcesMerger.merge(activity.getBaseContext());
            ReflectAccelerator.setResources(activity.getApplication(), rm);
            ReflectAccelerator.setResources(activity, rm);
        }
    }

    private static class ResourcesMerger extends Resources {
        public ResourcesMerger(AssetManager assets,
                               DisplayMetrics metrics, Configuration config) {
            super(assets, metrics, config);
        }

        public static ResourcesMerger merge(Context context) {
            AssetManager assets = ReflectAccelerator.newAssetManager();

            // Add plugin asset paths
            for (LoadedApk apk : sLoadedApks.values()){
                ReflectAccelerator.addAssetPath(assets, apk.assetPath);
            }
            // Add host asset path
            ReflectAccelerator.addAssetPath(assets, context.getPackageResourcePath());

            Resources base = context.getResources();
            return new ResourcesMerger(assets,
                    base.getDisplayMetrics(), base.getConfiguration());
        }
    }
}
