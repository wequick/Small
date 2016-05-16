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
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Application;
import android.app.Instrumentation;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.IBinder;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Window;

import net.wequick.small.internal.InstrumentationInternal;
import net.wequick.small.util.BundleParser;
import net.wequick.small.util.FileUtils;
import net.wequick.small.util.JNIUtils;
import net.wequick.small.util.ReflectAccelerator;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
    private static final String STUB_ACTIVITY_TRANSLUCENT = STUB_ACTIVITY_PREFIX + '1';
    private static final String TAG = "ApkBundleLauncher";
    private static final String FD_STORAGE = "storage";
    private static final String FD_LIBRARY = "lib";
    private static final String FILE_DEX = "bundle.dex";

    private static class LoadedApk {
        public String packageName;
        public File packagePath;
        public String applicationName;
        public String path;
        public int abiFlags;
        public File optDexFile;
        public ActivityInfo[] activities;
    }

    private static ConcurrentHashMap<String, LoadedApk> sLoadedApks;
    private static ConcurrentHashMap<String, ActivityInfo> sLoadedActivities;
    private static ConcurrentHashMap<String, List<IntentFilter>> sLoadedIntentFilters;

    protected static Instrumentation sHostInstrumentation;

    private static final char REDIRECT_FLAG = '>';

    /**
     * Class for restore activity info from Stub to Real
     */
    private static class ActivityThreadHandlerCallback implements Handler.Callback {

        private static final int LAUNCH_ACTIVITY = 100;

        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what != LAUNCH_ACTIVITY) return false;

            Object/*ActivityClientRecord*/ r = msg.obj;
            Intent intent = ReflectAccelerator.getIntent(r);
            String targetClass = unwrapIntent(intent);
            if (targetClass == null) return false;

            // Replace with the REAL activityInfo
            ActivityInfo targetInfo = sLoadedActivities.get(targetClass);
            ReflectAccelerator.setActivityInfo(r, targetInfo);
            return false;
        }
    }

    /**
     * Class for redirect activity from Stub(AndroidManifest.xml) to Real(Plugin)
     */
    private static class InstrumentationWrapper extends Instrumentation
            implements InstrumentationInternal {

        private static final int STUB_ACTIVITIES_COUNT = 4;

        public InstrumentationWrapper() { }

        /** @Override V21+
         * Wrap activity from REAL to STUB */
        public ActivityResult execStartActivity(
                Context who, IBinder contextThread, IBinder token, Activity target,
                Intent intent, int requestCode, android.os.Bundle options) {
            wrapIntent(intent);
            return ReflectAccelerator.execStartActivity(sHostInstrumentation,
                    who, contextThread, token, target, intent, requestCode, options);
        }

        /** @Override V20-
         * Wrap activity from REAL to STUB */
        public ActivityResult execStartActivity(
                Context who, IBinder contextThread, IBinder token, Activity target,
                Intent intent, int requestCode) {
            wrapIntent(intent);
            return ReflectAccelerator.execStartActivity(sHostInstrumentation,
                    who, contextThread, token, target, intent, requestCode);
        }

        @Override
        /** Prepare resources for REAL */
        public void callActivityOnCreate(Activity activity, android.os.Bundle icicle) {
            do {
                if (sLoadedActivities == null) break;
                ActivityInfo ai = sLoadedActivities.get(activity.getClass().getName());
                if (ai == null) break;

                applyActivityInfo(activity, ai);
            } while (false);
            sHostInstrumentation.callActivityOnCreate(activity, icicle);
        }

        @Override
        public void callActivityOnStop(Activity activity) {
            sHostInstrumentation.callActivityOnStop(activity);

            if (!Small.isUpgrading()) return;

            // If is upgrading, we are going to kill self while application turn into background,
            // and while we are back to foreground, all the things(code & layout) will be reload.
            // Don't worry about the data missing in current activity, you can do all the backups
            // with your activity's `onSaveInstanceState' and `onRestoreInstanceState'.
            ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            List<RunningAppProcessInfo> processes = am.getRunningAppProcesses();
            if (processes == null) return;

            String pkg = activity.getApplicationContext().getPackageName();
            ActivityManager.RunningAppProcessInfo self = null;
            for (ActivityManager.RunningAppProcessInfo p : processes) {
                if (p.processName.equals(pkg)) {
                    self = p;
                    break;
                }
            }
            if (self == null) return;
            if (self.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) return;

            final int pid = self.pid;
            // Seems should delay some time to ensure the activity can be successfully
            // restarted after the application restart.
            // FIXME: remove following thread if you find the better place to `killProcess'
            new Thread() {
                @Override
                public void run() {
                    try {
                        sleep(300);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    android.os.Process.killProcess(pid);
                }
            }.start();
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
            sHostInstrumentation.callActivityOnDestroy(activity);
        }

        private void wrapIntent(Intent intent) {
            ComponentName component = intent.getComponent();
            String realClazz;
            if (component == null) {
                // Implicit way to start an activity
                component = intent.resolveActivity(Small.getContext().getPackageManager());
                if (component != null) return; // ignore system or host action

                realClazz = resolveActivity(intent);
                if (realClazz == null) return;
            } else {
                realClazz = component.getClassName();
            }

            if (sLoadedActivities == null) return;

            ActivityInfo ai = sLoadedActivities.get(realClazz);
            if (ai == null) return;

            // Carry the real(plugin) class for incoming `newActivity' method.
            intent.addCategory(REDIRECT_FLAG + realClazz);
            String stubClazz = dequeueStubActivity(ai, realClazz);
            intent.setComponent(new ComponentName(Small.getContext(), stubClazz));
        }

        private String resolveActivity(Intent intent) {
            if (sLoadedIntentFilters == null) return null;

            Iterator<Map.Entry<String, List<IntentFilter>>> it =
                    sLoadedIntentFilters.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, List<IntentFilter>> entry = it.next();
                List<IntentFilter> filters = entry.getValue();
                for (IntentFilter filter : filters) {
                    if (filter.hasAction(Intent.ACTION_VIEW)) {
                        // TODO: match uri
                    }
                    if (filter.hasCategory(Intent.CATEGORY_DEFAULT)) {
                        // custom action
                        if (filter.hasAction(intent.getAction())) {
                            // hit
                            return entry.getKey();
                        }
                    }
                }
            }
            return null;
        }

        private String[] mStubQueue;

        /** Get an usable stub activity clazz from real activity */
        private String dequeueStubActivity(ActivityInfo ai, String realActivityClazz) {
            if (ai.launchMode == ActivityInfo.LAUNCH_MULTIPLE) {
                // In standard mode, the stub activity is reusable.
                // Cause the `windowIsTranslucent' attribute cannot be dynamically set,
                // We should choose the STUB activity with translucent or not here.
                Resources.Theme theme = Small.getContext().getResources().newTheme();
                theme.applyStyle(ai.getThemeResource(), true);
                TypedArray sa = theme.obtainStyledAttributes(
                        new int[] { android.R.attr.windowIsTranslucent });
                boolean translucent = sa.getBoolean(0, false);
                sa.recycle();
                return translucent ? STUB_ACTIVITY_TRANSLUCENT : STUB_ACTIVITY_PREFIX;
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
                    if (availableId == -1) availableId = i;
                } else if (usedActivityClazz.equals(realActivityClazz)) {
                    stubId = i;
                }
            }
            if (stubId != -1) {
                availableId = stubId;
            } else if (availableId != -1) {
                mStubQueue[availableId + offset] = realActivityClazz;
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

    private static String unwrapIntent(Intent intent) {
        Set<String> categories = intent.getCategories();
        if (categories == null) return null;

        // Get plugin activity class name from categories
        Iterator<String> it = categories.iterator();
        while (it.hasNext()) {
            String category = it.next();
            if (category.charAt(0) == REDIRECT_FLAG) {
                return category.substring(1);
            }
        }
        return null;
    }

    @Override
    public void setUp(Context context) {
        super.setUp(context);
        if (sHostInstrumentation == null) {
            try {
                // Inject instrumentation
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

                // Inject handler
                field = activityThreadClass.getDeclaredField("mH");
                field.setAccessible(true);
                Handler ah = (Handler) field.get(thread);
                field = Handler.class.getDeclaredField("mCallback");
                field.setAccessible(true);
                field.set(ah, new ActivityThreadHandlerCallback());
            } catch (Exception ignored) {
                ignored.printStackTrace();
                // Usually, cannot reach here
            }
        }
    }

    @Override
    public void postSetUp() {
        super.postSetUp();

        if (sLoadedApks == null) {
            Log.e(TAG, "Could not find any APK bundles!");
            return;
        }

        Collection<LoadedApk> apks = sLoadedApks.values();

        // Merge all the resources in bundles and replace the host one
        Application app = (Application) Small.getContext();
        Resources res = mergeResources(app.getBaseContext(), apks);
        ReflectAccelerator.setResources(app, res);

        // Merge all the dex into host's class loader
        Context context = Small.getContext();
        ClassLoader cl = context.getClassLoader();
        int i = 0;
        int N = apks.size();
        String[] dexPaths = new String[N];
        String[] optDexPaths = new String[N];
        for (LoadedApk apk : apks) {
            dexPaths[i] = apk.path;
            optDexPaths[i] = apk.optDexFile.getPath();
            if (Small.getBundleUpgraded(apk.packageName)) {
                // If upgraded, delete the opt dex file for recreating
                if (apk.optDexFile.exists()) apk.optDexFile.delete();
                Small.setBundleUpgraded(apk.packageName, false);
            }
            i++;
        }
        ReflectAccelerator.expandDexPathList(cl, dexPaths, optDexPaths);

        // Expand the native library directories if plugin has any JNIs. (#79)
        List<File> libPathList = new ArrayList<File>();
        for (LoadedApk apk : apks) {
            String abiPath = JNIUtils.getExtractABI(apk.abiFlags, Bundle.is64bit());
            if (abiPath != null) {
                // Extract the JNIs with specify ABI
                String libDir = FD_LIBRARY + File.separator + abiPath + File.separator;
                File libPath = new File(apk.packagePath, libDir);
                if (!libPath.exists()) {
                    if (!libPath.mkdirs()) {
                        Log.e(TAG, "Failed to create libPath: " + libPath);
                        continue;
                    }
                }
                try {
                    FileUtils.unZipFolder(new File(apk.path), apk.packagePath, libDir);
                    libPathList.add(libPath);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        if (libPathList.size() > 0) {
            ReflectAccelerator.expandNativeLibraryDirectories(cl, libPathList);
        }

        // Trigger all the bundle application `onCreate' event
        for (LoadedApk apk : apks) {
            String bundleApplicationName = apk.applicationName;
            if (bundleApplicationName == null) continue;

            try {
                Class applicationClass = Class.forName(bundleApplicationName);
                Application bundleApplication = Instrumentation.newApplication(
                        applicationClass, context);
                sHostInstrumentation.callApplicationOnCreate(bundleApplication);
            } catch (Exception e) {
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

        BundleParser parser = bundle.getParser();
        parser.collectActivities();
        PackageInfo pluginInfo = parser.getPackageInfo();

        // Load the bundle
        String apkPath = parser.getSourcePath();
        if (sLoadedApks == null) sLoadedApks = new ConcurrentHashMap<String, LoadedApk>();
        LoadedApk apk = sLoadedApks.get(packageName);
        if (apk == null) {
            apk = new LoadedApk();
            apk.packageName = packageName;
            apk.path = apkPath;
            apk.abiFlags = parser.getABIFlags();
            apk.activities = pluginInfo.activities;
            if (pluginInfo.applicationInfo != null) {
                apk.applicationName = pluginInfo.applicationInfo.className;
            }

            Context context = Small.getContext();
            File packagePath = context.getFileStreamPath(FD_STORAGE);
            packagePath = new File(packagePath, packageName);
            if (!packagePath.exists()) {
                packagePath.mkdirs();
            }
            apk.packagePath = packagePath;
            apk.optDexFile = new File(packagePath, FILE_DEX);
            sLoadedApks.put(packageName, apk);
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

        // Record intent-filters for implicit action
        ConcurrentHashMap<String, List<IntentFilter>> filters = parser.getIntentFilters();
        if (filters != null) {
            if (sLoadedIntentFilters == null) {
                sLoadedIntentFilters = new ConcurrentHashMap<String, List<IntentFilter>>();
            }
            sLoadedIntentFilters.putAll(filters);
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
        } else {
            char c = activityName.charAt(0);
            if (c == '.') {
                activityName = bundle.getPackageName() + activityName;
            } else if (c >= 'A' && c <= 'Z') {
                activityName = bundle.getPackageName() + '.' + activityName;
            }
            if (!sLoadedActivities.containsKey(activityName)) {
                if (activityName.endsWith("Activity")) {
                    throw new ActivityNotFoundException("Unable to find explicit activity class " +
                            "{ " + activityName + " }");
                }

                String tempActivityName = activityName + "Activity";
                if (!sLoadedActivities.containsKey(tempActivityName)) {
                    throw new ActivityNotFoundException("Unable to find explicit activity class " +
                            "{ " + activityName + "(Activity) }");
                }

                activityName = tempActivityName;
            }
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
        // Apply window attributes
        Window window = activity.getWindow();
        window.setSoftInputMode(ai.softInputMode);
        activity.setRequestedOrientation(ai.screenOrientation);
    }

    private static Resources mergeResources(Context context, Collection<LoadedApk> apks) {
        AssetManager assets = ReflectAccelerator.newAssetManager();
        String[] paths = new String[apks.size() + 1];
        paths[0] = context.getPackageResourcePath(); // Add host asset path
        int i = 1;
        for (LoadedApk apk : apks) {
            paths[i++] = apk.path; // Add plugin asset paths
        }
        ReflectAccelerator.addAssetPaths(assets, paths);

        Resources base = context.getResources();
        DisplayMetrics metrics = base.getDisplayMetrics();
        Configuration configuration = base.getConfiguration();
        Class baseClass = base.getClass();
        if (baseClass == Resources.class) {
            return new Resources(assets, metrics, configuration);
        } else {
            // Some crazy manufacturers will modify the application resources class.
            // As Nubia, it use `NubiaResources'. So we had to create a related instance. #135
            return ReflectAccelerator.newResources(baseClass, assets, metrics, configuration);
        }
    }
}
