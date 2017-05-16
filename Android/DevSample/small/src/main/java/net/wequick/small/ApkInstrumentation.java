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
import android.app.ActivityManager;
import android.app.Application;
import android.app.Instrumentation;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.Window;

import net.wequick.small.Small;
import net.wequick.small.internal.InstrumentationInternal;
import net.wequick.small.util.ReflectAccelerator;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class for redirect activity from Stub(AndroidManifest.xml) to Real(Plugin)
 */
public class ApkInstrumentation extends Instrumentation
        implements InstrumentationInternal {

    private static final String TAG = "ApkInstrumentation";

    private static final String PACKAGE_NAME = "net.wequick.small";
    private static final String STUB_ACTIVITY_PREFIX = PACKAGE_NAME + ".A";
    private static final String STUB_ACTIVITY_TRANSLUCENT = STUB_ACTIVITY_PREFIX + '1';
    private static final char REDIRECT_FLAG = '>';

    private Instrumentation mBase;
    private static final int STUB_ACTIVITIES_COUNT = 4;
    private ArrayList<Integer> mNeedsRecreateActivities;
    private ArrayList<Integer> mCreatedActivities;
    
    private List<ProviderInfo> mAllProviders;
    private List<ProviderInfo> mLazyInitProviders;
    
//    private ConcurrentHashMap<String, ApkInfo> sLoadedApks;
    private ConcurrentHashMap<String, ActivityInfo> mLoadedActivities;
    private ConcurrentHashMap<String, List<IntentFilter>> mLoadedIntentFilters;

    private ActivityThread mThread;
    private ActivityThreadHandlerCallback mHandlerCallback;
    
    public ApkInstrumentation(Instrumentation base,
                              ActivityThread thread,
                              List<ProviderInfo> providers) {
        mBase = base;
        mAllProviders = providers;
        mThread = thread;
        ensureInjectMessageHandler();
    }

    /** @Override V16+
     * Wrap activity from REAL to STUB */
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, android.os.Bundle options) {
        wrapIntent(intent);
        ensureInjectMessageHandler();
        return mBase.execStartActivity(who, contextThread, token, target, intent, requestCode, options);
    }

    /** @Override V15-
     * Wrap activity from REAL to STUB */
    public ActivityResult execStartActivity(
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode) {
        wrapIntent(intent);
        ensureInjectMessageHandler();
        return mBase.execStartActivity(who, contextThread, token, target, intent, requestCode);
    }

    @Override
    public void callApplicationOnCreate(Application app) {
        mBase.callApplicationOnCreate(app);
    }

    @Override
    /** Prepare resources for REAL */
    public void callActivityOnCreate(Activity activity, android.os.Bundle icicle) {
        do {
            if (mLoadedActivities == null) break;
            ActivityInfo ai = mLoadedActivities.get(activity.getClass().getName());
            if (ai == null) break;

            applyActivityInfo(activity, ai);
            ReflectAccelerator.ensureCacheResources();
        } while (false);
        mBase.callActivityOnCreate(activity, icicle);

        if (mCreatedActivities == null) {
            mCreatedActivities = new ArrayList<>();
        }
        mCreatedActivities.add(activity.hashCode());

        // Reset activity instrumentation if it was modified by some other applications #245
        try {
            Field f = Activity.class.getDeclaredField("mInstrumentation");
            f.setAccessible(true);
            Object instrumentation = f.get(activity);
            if (instrumentation != this) {
                f.set(activity, this);
            }
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void callActivityOnStop(Activity activity) {
        mBase.callActivityOnStop(activity);

        if (!Small.isUpgrading()) return;

        // If is upgrading, we are going to kill self while application turn into background,
        // and while we are back to foreground, all the things(code & layout) will be reload.
        // Don't worry about the data missing in current activity, you can do all the backups
        // with your activity's `onSaveInstanceState' and `onRestoreInstanceState'.

        // Get all the processes of device (1)
        ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes == null) return;

        // Gather all the processes of current application (2)
        // Above 5.1.1, this may be equals to (1), on the safe side, we also
        // filter the processes with current package name.
        String pkg = activity.getApplicationContext().getPackageName();
        final List<ActivityManager.RunningAppProcessInfo> currentAppProcesses = new ArrayList<>(processes.size());
        for (ActivityManager.RunningAppProcessInfo p : processes) {
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
        ActivityManager.RunningAppProcessInfo currentProcess = currentAppProcesses.get(0);
        if (currentProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) return;

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
                for (ActivityManager.RunningAppProcessInfo p : currentAppProcesses) {
                    android.os.Process.killProcess(p.pid);
                }
            }
        }.start();
    }

    /**
     * Apply plugin activity info with plugin's AndroidManifest.xml
     * @param activity
     * @param ai
     */
    private void applyActivityInfo(Activity activity, ActivityInfo ai) {
        // Apply window attributes
        Window window = activity.getWindow();
        window.setSoftInputMode(ai.softInputMode);
        activity.setRequestedOrientation(ai.screenOrientation);
    }

    protected void setNeedsRecreateActivities() {
        if (mCreatedActivities == null) return;

        if (mNeedsRecreateActivities == null) {
            mNeedsRecreateActivities = new ArrayList<>();
        }
        for (Integer code : mCreatedActivities) {
            mNeedsRecreateActivities.add(code);
        }
    }

    @Override
    public void callActivityOnResume(Activity activity) {
        mBase.callActivityOnResume(activity);

        if (mNeedsRecreateActivities == null) return;

        int id = activity.hashCode();
        if (!mNeedsRecreateActivities.contains(id)) return;

        // Appended some assets, needs to recreate the activity
        while (activity.getParent() != null) {
            activity = activity.getParent();
        }
        activity.recreate();

        mNeedsRecreateActivities.remove(id);
        if (mNeedsRecreateActivities.size() == 0) {
            mNeedsRecreateActivities = null;
        }
    }

    @Override
    public void callActivityOnDestroy(Activity activity) {
        do {
            if (mLoadedActivities == null) break;
            String realClazz = activity.getClass().getName();
            ActivityInfo ai = mLoadedActivities.get(realClazz);
            if (ai == null) break;
            inqueueStubActivity(ai, realClazz);
        } while (false);
        mBase.callActivityOnDestroy(activity);
    }

    @Override
    public boolean onException(Object obj, Throwable e) {
        if (mAllProviders != null && e.getClass().equals(ClassNotFoundException.class)) {
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
                    for (ProviderInfo info : mAllProviders) {
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

    public void wrapIntent(Intent intent) {
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

        if (mLoadedActivities == null) return;

        ActivityInfo ai = mLoadedActivities.get(realClazz);
        if (ai == null) return;

        // Carry the real(plugin) class for incoming `newActivity' method.
        intent.addCategory(REDIRECT_FLAG + realClazz);
        String stubClazz = dequeueStubActivity(ai, realClazz);
        intent.setComponent(new ComponentName(Small.getContext(), stubClazz));
    }

    public void lazyInstallProviders() {
        if (mLazyInitProviders != null) {
            mThread.installSystemProviders(mLazyInitProviders);
            mLazyInitProviders = null;
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

    private String resolveActivity(Intent intent) {
        if (mLoadedIntentFilters == null) return null;

        Iterator<Map.Entry<String, List<IntentFilter>>> it =
                mLoadedIntentFilters.entrySet().iterator();
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

    private void ensureInjectMessageHandler() {
        try {
            Field f = ActivityThread.class.getDeclaredField("mH");
            f.setAccessible(true);
            Handler ah = (Handler) f.get(mThread);
            f = Handler.class.getDeclaredField("mCallback");
            f.setAccessible(true);

            boolean needsInject = false;
            if (mHandlerCallback == null) {
                mHandlerCallback = new ActivityThreadHandlerCallback();
                needsInject = true;
            } else {
                Object callback = f.get(ah);
                if (callback != mHandlerCallback) {
                    needsInject = true;
                }
            }

            if (needsInject) {
                // Inject message handler
                f.set(ah, mHandlerCallback);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to replace message handler for thread: " + mThread);
        }
    }

    public void addActivities(ActivityInfo[] activities) {
        if (mLoadedActivities == null) {
            mLoadedActivities = new ConcurrentHashMap<String, ActivityInfo>();
        }
        for (ActivityInfo ai : activities) {
            mLoadedActivities.put(ai.name, ai);
        }
    }

    public void addIntentFilters(ConcurrentHashMap<String, List<IntentFilter>> filters) {
        if (filters == null) return;

        if (mLoadedIntentFilters == null) {
            mLoadedIntentFilters = new ConcurrentHashMap<String, List<IntentFilter>>();
        }
        mLoadedIntentFilters.putAll(filters);
    }

    public String resolveActivity(String activityName) {
        if (mLoadedActivities == null) {
            throw new ActivityNotFoundException("Unable to find explicit activity class " +
                    "{ " + activityName + " }");
        }

        if (!mLoadedActivities.containsKey(activityName)) {
            if (activityName.endsWith("Activity")) {
                throw new ActivityNotFoundException("Unable to find explicit activity class " +
                        "{ " + activityName + " }");
            }

            String tempActivityName = activityName + "Activity";
            if (!mLoadedActivities.containsKey(tempActivityName)) {
                throw new ActivityNotFoundException("Unable to find explicit activity class " +
                        "{ " + activityName + "(Activity) }");
            }

            activityName = tempActivityName;
        }

        return activityName;
    }

    /**
     * Class for restore activity info from Stub to Real
     */
    private class ActivityThreadHandlerCallback implements Handler.Callback {

        private static final int LAUNCH_ACTIVITY = 100;
        private static final int CREATE_SERVICE = 114;
        private static final int CONFIGURATION_CHANGED = 118;
        private static final int ACTIVITY_CONFIGURATION_CHANGED = 125;

        private Configuration mApplicationConfig;

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case LAUNCH_ACTIVITY:
                    redirectActivity(msg);
                    break;

                case CREATE_SERVICE:
                    ensureServiceClassesLoadable(msg);
                    break;

                case CONFIGURATION_CHANGED:
                    recordConfigChanges(msg);
                    break;

                case ACTIVITY_CONFIGURATION_CHANGED:
                    return relaunchActivityIfNeeded(msg);

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
            ActivityInfo targetInfo = mLoadedActivities.get(targetClass);
            ReflectAccelerator.setActivityInfo(r, targetInfo);
        }

        private void ensureServiceClassesLoadable(Message msg) {
            Object/*ActivityThread$CreateServiceData*/ data = msg.obj;
            ServiceInfo info = ReflectAccelerator.getServiceInfo(data);
            if (info == null) return;

            String appProcessName = Small.getContext().getApplicationInfo().processName;
            if (!appProcessName.equals(info.processName)) {
                // Cause Small is only setup in current application process, if a service is specified
                // with a different process('android:process=xx'), then we should also setup Small for
                // that process so that the service classes can be successfully loaded.
                Small.setUpOnDemand();
            }
        }

        private void recordConfigChanges(Message msg) {
            mApplicationConfig = (Configuration) msg.obj;
        }

        private boolean relaunchActivityIfNeeded(Message msg) {
            try {
                // Get activity token
                Object /*ActivityThread$ActivityConfigChangeData*/ data = msg.obj;
                IBinder token;
                Field f;
                if (data instanceof IBinder) {
                    token = (IBinder) data;
                } else {
                    f = data.getClass().getDeclaredField("activityToken");
                    f.setAccessible(true);
                    token = (IBinder) f.get(data);
                }

                // Check if is a bundle activity
                Activity activity = mThread.getActivity(token);
                Intent intent = activity.getIntent();
                String bundleActivityName = unwrapIntent(intent);
                if (bundleActivityName == null) {
                    return false;
                }

                // Get the configuration of the activity
                f = Activity.class.getDeclaredField("mCurrentConfig");
                f.setAccessible(true);
                Configuration activityConfig = (Configuration) f.get(activity);

                // Calculate the changes of activity configuration with the application one
                int configDiff = activityConfig.diff(mApplicationConfig);
                if (configDiff == 0) {
                    return false;
                }

                // Check if the activity can handle the changes
                ActivityInfo bundleActivityInfo = mLoadedActivities.get(bundleActivityName);
                if ((configDiff & (~bundleActivityInfo.configChanges)) == 0) {
                    return false;
                }

                // The activity isn't handling the change, relaunch it.
                activity.recreate();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return false;
        }
    }
}
