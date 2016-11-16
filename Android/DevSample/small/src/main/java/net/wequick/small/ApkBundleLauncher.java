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
import android.content.ContextWrapper;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.IBinder;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Message;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.view.Window;

import net.wequick.small.internal.InstrumentationInternal;
import net.wequick.small.util.ReflectAccelerator;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexFile;

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
    private static final String FILE_DEX = "bundle.dex";

    private static class LoadedApk {
        public String packageName;
        public File packagePath;
        public String applicationName;
        public String path;
        public DexFile dexFile;
        public File optDexFile;
        public File libraryPath;
        public boolean nonResources; /** no resources.arsc */
    }

    private static ConcurrentHashMap<String, LoadedApk> sLoadedApks;
    private static ConcurrentHashMap<String, ActivityInfo> sLoadedActivities;
    private static ConcurrentHashMap<String, List<IntentFilter>> sLoadedIntentFilters;

    private static Instrumentation sHostInstrumentation;
    private static InstrumentationWrapper sBundleInstrumentation;

    private static final char REDIRECT_FLAG = '>';

    private static Object sActivityThread;
    private static List<ProviderInfo> sProviders;
    private static List<ProviderInfo> mLazyInitProviders;

    /**
     * Class for restore activity info from Stub to Real
     */
    private static class ActivityThreadHandlerCallback implements Handler.Callback {

        private static final int LAUNCH_ACTIVITY = 100;
        private static final int CREATE_SERVICE = 114;

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case LAUNCH_ACTIVITY:
                    redirectActivity(msg);
                    break;

                case CREATE_SERVICE:
                    ensureServiceClassesLoadable(msg);
                    break;

                default:
                    break;
            }

            return false;
        }

        private void redirectActivity(Message msg) {
            Object/*ActivityClientRecord*/ r = msg.obj;
            Intent intent = ReflectAccelerator.getIntent(r);
            String targetClass = unwrapIntent(intent);
            boolean hasSetUp = Small.hasSetUp();
            if (targetClass == null) {
                // The activity was register in the host.
                if (hasSetUp) return; // nothing to do

                if (intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
                    // The launcher activity will setup Small.
                    return;
                }

                // Launching an activity in remote process. Set up Small for it.
                Small.setUpOnDemand();
                return;
            }

            if (!hasSetUp) {
                // Restarting an activity after application recreated,
                // maybe upgrading or somehow the application was killed in background.
                Small.setUp();
            }

            // Replace with the REAL activityInfo
            ActivityInfo targetInfo = sLoadedActivities.get(targetClass);
            ReflectAccelerator.setActivityInfo(r, targetInfo);
        }

        private void ensureServiceClassesLoadable(Message msg) {
            // Cause Small is only setup in current application process, if a service is specified
            // with a different process('android:process=xx'), then we should also setup Small for
            // that process so that the service classes can be successfully loaded.
            Small.setUpOnDemand();
        }
    }

    /**
     * Class for redirect activity from Stub(AndroidManifest.xml) to Real(Plugin)
     */
    protected static class InstrumentationWrapper extends Instrumentation
            implements InstrumentationInternal {

        private Instrumentation mBase;
        private static final int STUB_ACTIVITIES_COUNT = 4;

        public InstrumentationWrapper(Instrumentation base) {
            mBase = base;
        }

        /** @Override V21+
         * Wrap activity from REAL to STUB */
        public ActivityResult execStartActivity(
                Context who, IBinder contextThread, IBinder token, Activity target,
                Intent intent, int requestCode, android.os.Bundle options) {
            wrapIntent(intent);
            return ReflectAccelerator.execStartActivity(mBase,
                    who, contextThread, token, target, intent, requestCode, options);
        }

        /** @Override V20-
         * Wrap activity from REAL to STUB */
        public ActivityResult execStartActivity(
                Context who, IBinder contextThread, IBinder token, Activity target,
                Intent intent, int requestCode) {
            wrapIntent(intent);
            return ReflectAccelerator.execStartActivity(mBase,
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

            // Reset activity instrumentation if it was modified by some other applications #245
            if (sBundleInstrumentation != null) {
                try {
                    Field f = Activity.class.getDeclaredField("mInstrumentation");
                    f.setAccessible(true);
                    Object instrumentation = f.get(activity);
                    if (instrumentation != sBundleInstrumentation) {
                        f.set(activity, sBundleInstrumentation);
                    }
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void callActivityOnStop(Activity activity) {
            sHostInstrumentation.callActivityOnStop(activity);

            if (!Small.isUpgrading()) return;

            // If is upgrading, we are going to kill self while application turn into background,
            // and while we are back to foreground, all the things(code & layout) will be reload.
            // Don't worry about the data missing in current activity, you can do all the backups
            // with your activity's `onSaveInstanceState' and `onRestoreInstanceState'.

            // Get all the processes of device (1)
            ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            List<RunningAppProcessInfo> processes = am.getRunningAppProcesses();
            if (processes == null) return;

            // Gather all the processes of current application (2)
            // Above 5.1.1, this may be equals to (1), on the safe side, we also
            // filter the processes with current package name.
            String pkg = activity.getApplicationContext().getPackageName();
            final List<RunningAppProcessInfo> currentAppProcesses = new ArrayList<>(processes.size());
            for (RunningAppProcessInfo p : processes) {
                if (p.pkgList == null) continue;

                boolean match = false;
                int N = p.pkgList.length;
                for (int i = 0; i < N; i++) {
                    if (p.pkgList[i].equals(pkg)) {
                        match = true;
                        break;
                    }
                }
                if (!match) continue;

                currentAppProcesses.add(p);
            }
            if (currentAppProcesses.isEmpty()) return;

            // The top process of current application processes.
            RunningAppProcessInfo currentProcess = currentAppProcesses.get(0);
            if (currentProcess.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) return;

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
                    for (RunningAppProcessInfo p : currentAppProcesses) {
                        android.os.Process.killProcess(p.pid);
                    }
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

        @Override
        public boolean onException(Object obj, Throwable e) {
            if (sProviders != null && e.getClass().equals(ClassNotFoundException.class)) {
                boolean errorOnInstallProvider = false;
                StackTraceElement[] stacks = e.getStackTrace();
                for (StackTraceElement st : stacks) {
                    if (st.getMethodName().equals("installProvider")) {
                        errorOnInstallProvider = true;
                        break;
                    }
                }

                if (errorOnInstallProvider) {
                    // We'll reinstall this content provider later, so just ignores it!!!
                    // FIXME: any better way to get the class name?
                    String msg = e.getMessage();
                    final String prefix = "Didn't find class \"";
                    if (msg.startsWith(prefix)) {
                        String providerClazz = msg.substring(prefix.length());
                        providerClazz = providerClazz.substring(0, providerClazz.indexOf("\""));
                        for (ProviderInfo info : sProviders) {
                            if (info.name.equals(providerClazz)) {
                                if (mLazyInitProviders == null) {
                                    mLazyInitProviders = new ArrayList<ProviderInfo>();
                                }
                                mLazyInitProviders.add(info);
                                break;
                            }
                        }
                    }
                    return true;
                }
            }

            return super.onException(obj, e);
        }

        private void wrapIntent(Intent intent) {
            ComponentName component = intent.getComponent();
            String realClazz;
            if (component == null) {
                // Try to resolve the implicit action which has registered in host.
                component = intent.resolveActivity(Small.getContext().getPackageManager());
                if (component != null) {
                    // A system or host action, nothing to be done.
                    return;
                }

                // Try to resolve the implicit action which has registered in bundles.
                realClazz = resolveActivity(intent);
                if (realClazz == null) {
                    // Cannot resolved, nothing to be done.
                    return;
                }
            } else {
                realClazz = component.getClassName();
                if (realClazz.startsWith(STUB_ACTIVITY_PREFIX)) {
                    // Re-wrap to ensure the launch mode works.
                    realClazz = unwrapIntent(intent);
                }
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

        private void setStubQueue(String mode, String realActivityClazz) {
            int launchMode = mode.charAt(0) - '0';
            int stubIndex = mode.charAt(1) - '0';
            int offset = (launchMode - 1) * STUB_ACTIVITIES_COUNT + stubIndex;
            if (mStubQueue == null) {
                mStubQueue = new String[STUB_ACTIVITIES_COUNT * 3];
            }
            mStubQueue[offset] = realActivityClazz;
        }
    }

    public static void wrapIntent(Intent intent) {
        sBundleInstrumentation.wrapIntent(intent);
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

    /**
     * A context wrapper that redirect some host environments to plugin
     */
    private static final class BundleApplicationContext extends ContextWrapper {

        private LoadedApk mApk;

        public BundleApplicationContext(Context base, LoadedApk apk) {
            super(base);
            mApk = apk;
        }

        @Override
        public String getPackageName() {
            return mApk.packageName;
        }

        @Override
        public String getPackageResourcePath() {
            return mApk.path;
        }

        @Override
        public ApplicationInfo getApplicationInfo() {
            ApplicationInfo ai = super.getApplicationInfo();
            // TODO: Read meta-data in bundles and merge to the host one
            // ai.metaData.putAll();
            return ai;
        }
    }

    @Override
    public void onCreate(Application app) {
        super.onCreate(app);

        Object/*ActivityThread*/ thread;
        List<ProviderInfo> providers;
        Instrumentation base;
        ApkBundleLauncher.InstrumentationWrapper wrapper;
        Field f;

        // Get activity thread
        thread = ReflectAccelerator.getActivityThread(app);

        // Replace instrumentation
        try {
            f = thread.getClass().getDeclaredField("mInstrumentation");
            f.setAccessible(true);
            base = (Instrumentation) f.get(thread);
            wrapper = new ApkBundleLauncher.InstrumentationWrapper(base);
            f.set(thread, wrapper);
        } catch (Exception e) {
            throw new RuntimeException("Failed to replace instrumentation for thread: " + thread);
        }

        // Inject message handler
        try {
            f = thread.getClass().getDeclaredField("mH");
            f.setAccessible(true);
            Handler ah = (Handler) f.get(thread);
            f = Handler.class.getDeclaredField("mCallback");
            f.setAccessible(true);
            f.set(ah, new ApkBundleLauncher.ActivityThreadHandlerCallback());
        } catch (Exception e) {
            throw new RuntimeException("Failed to replace message handler for thread: " + thread);
        }

        // Get providers
        try {
            f = thread.getClass().getDeclaredField("mBoundApplication");
            f.setAccessible(true);
            Object/*AppBindData*/ data = f.get(thread);
            f = data.getClass().getDeclaredField("providers");
            f.setAccessible(true);
            providers = (List<ProviderInfo>) f.get(data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get providers from thread: " + thread);
        }

        sActivityThread = thread;
        sProviders = providers;
        sHostInstrumentation = base;
        sBundleInstrumentation = wrapper;
    }

    @Override
    public void setUp(Context context) {
        super.setUp(context);

        Field f;

        // AOP for pending intent
        try {
            f = TaskStackBuilder.class.getDeclaredField("IMPL");
            f.setAccessible(true);
            final Object impl = f.get(TaskStackBuilder.class);
            InvocationHandler aop = new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    Intent[] intents = (Intent[]) args[1];
                    for (Intent intent : intents) {
                        sBundleInstrumentation.wrapIntent(intent);
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

        if (sLoadedApks == null) {
            Log.e(TAG, "Could not find any APK bundles!");
            return;
        }

        Collection<LoadedApk> apks = sLoadedApks.values();

        // Merge all the resources in bundles and replace the host one
        final Application app = Small.getContext();
        String[] paths = new String[apks.size() + 1];
        paths[0] = app.getPackageResourcePath(); // add host asset path
        int i = 1;
        for (LoadedApk apk : apks) {
            if (apk.nonResources) continue; // ignores the empty entry to fix #62
            paths[i++] = apk.path; // add plugin asset path
        }
        if (i != paths.length) {
            paths = Arrays.copyOf(paths, i);
        }
        ReflectAccelerator.mergeResources(app, sActivityThread, paths);

        // Merge all the dex into host's class loader
        ClassLoader cl = app.getClassLoader();
        i = 0;
        int N = apks.size();
        String[] dexPaths = new String[N];
        DexFile[] dexFiles = new DexFile[N];
        for (LoadedApk apk : apks) {
            dexPaths[i] = apk.path;
            dexFiles[i] = apk.dexFile;
            if (Small.getBundleUpgraded(apk.packageName)) {
                // If upgraded, delete the opt dex file for recreating
                if (apk.optDexFile.exists()) apk.optDexFile.delete();
                Small.setBundleUpgraded(apk.packageName, false);
            }
            i++;
        }
        ReflectAccelerator.expandDexPathList(cl, dexPaths, dexFiles);

        // Expand the native library directories for host class loader if plugin has any JNIs. (#79)
        List<File> libPathList = new ArrayList<File>();
        for (LoadedApk apk : apks) {
            if (apk.libraryPath != null) {
                libPathList.add(apk.libraryPath);
            }
        }
        if (libPathList.size() > 0) {
            ReflectAccelerator.expandNativeLibraryDirectories(cl, libPathList);
        }

        // Trigger all the bundle application `onCreate' event
        for (final LoadedApk apk : apks) {
            String bundleApplicationName = apk.applicationName;
            if (bundleApplicationName == null) continue;

            try {
                final Class applicationClass = Class.forName(bundleApplicationName);
                Bundle.postUI(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            BundleApplicationContext appContext = new BundleApplicationContext(app, apk);
                            Application bundleApplication = Instrumentation.newApplication(
                                    applicationClass, appContext);
                            sHostInstrumentation.callApplicationOnCreate(bundleApplication);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Lazy init content providers
        if (mLazyInitProviders != null) {
            try {
                Method m = sActivityThread.getClass().getDeclaredMethod(
                        "installContentProviders", Context.class, List.class);
                m.setAccessible(true);
                m.invoke(sActivityThread, app, mLazyInitProviders);
            } catch (Exception e) {
                throw new RuntimeException("Failed to lazy init content providers: " + mLazyInitProviders);
            }
        }

        // Free temporary variables
        sLoadedApks = null;
        sProviders = null;
        sActivityThread = null;
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
        String apkPath = parser.getSourcePath();
        if (sLoadedApks == null) sLoadedApks = new ConcurrentHashMap<String, LoadedApk>();
        LoadedApk apk = sLoadedApks.get(packageName);
        if (apk == null) {
            apk = new LoadedApk();
            apk.packageName = packageName;
            apk.path = apkPath;
            apk.nonResources = parser.isNonResources();
            if (pluginInfo.applicationInfo != null) {
                apk.applicationName = pluginInfo.applicationInfo.className;
            }
            apk.packagePath = bundle.getExtractPath();
            apk.optDexFile = new File(apk.packagePath, FILE_DEX);

            // Load dex
            final LoadedApk fApk = apk;
            Bundle.postIO(new Runnable() {
                @Override
                public void run() {
                    try {
                        fApk.dexFile = DexFile.loadDex(fApk.path, fApk.optDexFile.getPath(), 0);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });

            // Extract native libraries with specify ABI
            String libDir = parser.getLibraryDirectory();
            if (libDir != null) {
                apk.libraryPath = new File(apk.packagePath, libDir);
            }
            sLoadedApks.put(packageName, apk);
        }

        if (pluginInfo.activities == null) {
            bundle.setLaunchable(false);
            return;
        }

        // Record activities for intent redirection
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
}
