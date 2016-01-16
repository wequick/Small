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
import android.app.Instrumentation;
import android.content.ComponentName;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class to launch an apk (which fake as .so).
 * Created by galen on 15/2/3.
 */
public class ApkBundleLauncher extends SoBundleLauncher {
    private static final String PACKAGE_NAME = ApkBundleLauncher.class.getPackage().getName();
    private static final String STUB_ACTIVITY_PREFIX = PACKAGE_NAME + ".A.";
    private static final String TAG = "ApkBundleLauncher";

    private static class PackageSpec {
        public String name;
        public String path;
        public PackageInfo info;
    }

    private static class ActivitySpec {
        public PackageSpec packageSpec;
        public ActivityInfo info;
    }

    private static ConcurrentHashMap<String, ActivitySpec> sActivitySpecs;
    private static ConcurrentHashMap<String, PackageSpec> sPackageSpecs;
    private static ArrayList<String> sAddedAssetPaths = new ArrayList<String>();

    private static Instrumentation sHostInstrumentation;

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
            do {
                if (sActivitySpecs == null) break;
                ActivitySpec as = sActivitySpecs.get(activity.getClass().getName());
                if (as == null) break;
                ensureAddAssetPath(activity, as.packageSpec);
                applyActivityInfo(activity, as);
            } while (false);
            super.callActivityOnCreate(activity, icicle);
        }

        @Override
        public void callActivityOnDestroy(Activity activity) {
            do {
                if (sActivitySpecs == null) break;
                String realClazz = activity.getClass().getName();
                ActivitySpec as = sActivitySpecs.get(realClazz);
                if (as == null) break;
                inqueueStubActivity(as.info, realClazz);
            } while (false);
            super.callActivityOnDestroy(activity);
        }

        private void wrapIntent(Intent intent) {
            String realClazz = intent.getComponent().getClassName();
            ActivitySpec spec = sActivitySpecs.get(realClazz);
            if (spec == null) return;

            intent.addCategory(REDIRECT_FLAG + realClazz);
            String stubClazz = dequeueStubActivity(spec.info, realClazz);
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
                return STUB_ACTIVITY_PREFIX + ai.launchMode;
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
            return STUB_ACTIVITY_PREFIX + ai.launchMode + "$" + availableId;
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
    public void setup(Context context) {
        super.setup(context);
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
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected String[] getSupportingTypes() {
        return new String[] {"app", "lib"};
    }

    @Override
    public void loadBundle(Bundle bundle) {
        String packageName = bundle.getPackageName();
        File plugin = bundle.getFile();

        PackageManager pm = Small.getContext().getPackageManager();
        PackageInfo pluginInfo = pm.getPackageArchiveInfo(plugin.getPath(),
                PackageManager.GET_ACTIVITIES );

        // Load the bundle
        String apkPath = plugin.getPath();
        if (sPackageSpecs == null) sPackageSpecs = new ConcurrentHashMap<String, PackageSpec>();
        PackageSpec ps = sPackageSpecs.get(packageName);
        if (ps == null) {
            ps = new PackageSpec();
            ps.name = packageName;
            ps.path = apkPath;
            ps.info = pluginInfo;
            sPackageSpecs.put(packageName, ps);

            // Add dex element to class loader's pathList
            Context context = Small.getContext();
            File packagePath = context.getFileStreamPath("storage");
            packagePath = new File(packagePath, packageName);
            if (!packagePath.exists()) {
                packagePath.mkdirs();
            }
            String libraryPath = packagePath + "/lib";
            String optDexPath = packagePath + "/bundle.dex";
            ReflectAccelerator.expandDexPathList(
                    context.getClassLoader(), apkPath, libraryPath, optDexPath);
        }

        if (pluginInfo.activities == null) {
            bundle.setLaunchable(false);
            return;
        }

        // Record activities for intent redirection
        bundle.setEntrance(pluginInfo.activities[0].name);
        if (sActivitySpecs == null) sActivitySpecs = new ConcurrentHashMap<String, ActivitySpec>();
        for (ActivityInfo ai : pluginInfo.activities) {
            ActivitySpec as = new ActivitySpec();
            as.packageSpec = ps;
            as.info = ai;
            sActivitySpecs.put(ai.name, as);
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
            if (sPackageSpecs == null) return null;
            PackageSpec ps = sPackageSpecs.get(packageName);
            if (ps == null) return null;
            ensureAddAssetPath((Activity) context, ps);
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
     * @param as
     */
    private static void applyActivityInfo(Activity activity, ActivitySpec as) {
        ActivityInfo ai = as.info;
        // Apply plugin theme
        ReflectAccelerator.setTheme(activity, null);
        activity.setTheme(ai.getThemeResource());
        // Apply plugin softInputMode
        activity.getWindow().setSoftInputMode(ai.softInputMode);
    }

    /**
     * Try to get plugin resource, if failed, add plugin asset path
     * @param activity
     * @param ps
     */
    private static void ensureAddAssetPath(Activity activity, PackageSpec ps) {
        // Fix issue #2 by ymcao:
        // Disappointed to find following cannot support for 6.0+ on some devices such as HTC A9.
//        if (Build.VERSION.SDK_INT >= 21) { // 5.0+
//            if (sAddedAssetPaths.contains(ps.path)) return;
//            ReflectAccelerator.addAssetPath(
//                    activity.getBaseContext().getResources().getAssets(), ps.path);
//        ReflectAccelerator.addAssetPath(
//                activity.getApplicationContext().getResources().getAssets(), ps.path);
//            sAddedAssetPaths.add(ps.path);
//            return;
//        }

        V20.addAssetPaths(activity);
    }

    private static class V20 {
        static void addAssetPaths(Activity activity) {
            ResourcesMerger rm = ResourcesMerger.merge(activity.getBaseContext());
            ReflectAccelerator.setResources(activity, rm);
            // Also replace the resources of application
            ReflectAccelerator.setResources(activity.getApplication(), rm);
        }

        private static class ResourcesMerger extends Resources {
            public ResourcesMerger(AssetManager assets,
                                   DisplayMetrics metrics, Configuration config) {
                super(assets, metrics, config);
            }

            public static ResourcesMerger merge(Context context) {
                AssetManager assets;
                try {
                    assets = AssetManager.class.newInstance();
                } catch (InstantiationException e1) {
                    e1.printStackTrace();
                    return null;
                } catch (IllegalAccessException e1) {
                    e1.printStackTrace();
                    return null;
                }
                // Add plugin asset paths
                for (PackageSpec ps : sPackageSpecs.values()){
                    ReflectAccelerator.addAssetPath(assets, ps.path);
                }
                // Add host asset path
                ReflectAccelerator.addAssetPath(assets, context.getPackageResourcePath());

                Resources base = context.getResources();
                return new ResourcesMerger(assets,
                        base.getDisplayMetrics(), base.getConfiguration());
            }
        }
    }
}
