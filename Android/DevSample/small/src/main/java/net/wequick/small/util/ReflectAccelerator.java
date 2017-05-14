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

import android.app.ActivityThread;
import android.app.Application;
import android.app.ResourcesManager;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ServiceInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.util.ArrayMap;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

/**
 * This class consists exclusively of static methods that accelerate reflections.
 */
public class ReflectAccelerator {
    // ActivityClientRecord
    private static Field sActivityClientRecord_intent_field;
    private static Field sActivityClientRecord_activityInfo_field;

    private static ArrayMap<Object, WeakReference<Object>> sResourceImpls;
    private static Object/*ResourcesImpl*/ sMergedResourcesImpl;

    private ReflectAccelerator() { /** cannot be instantiated */ }

    //______________________________________________________________________________________________
    // API

    public static void mergeResources(Application app, Object activityThread, String[] assetPaths,
                                      boolean updateActivities) {
        AssetManager newAssetManager;
        if (Build.VERSION.SDK_INT < 24) {
            newAssetManager = new AssetManager();
        } else {
            // On Android 7.0+, this should contains a WebView asset as base. #347
            newAssetManager = app.getAssets();
        }
        newAssetManager.addAssetPaths(assetPaths);

        try {
            Method mEnsureStringBlocks = AssetManager.class.getDeclaredMethod("ensureStringBlocks", new Class[0]);
            mEnsureStringBlocks.setAccessible(true);
            mEnsureStringBlocks.invoke(newAssetManager, new Object[0]);

            Collection<WeakReference<Resources>> references;

            if (Build.VERSION.SDK_INT >= 19) {
                ResourcesManager resourcesManager = ResourcesManager.getInstance();
                Class<?> resourcesManagerClass = ResourcesManager.class;
                try {
                    Field fMActiveResources = resourcesManagerClass.getDeclaredField("mActiveResources");
                    fMActiveResources.setAccessible(true);

                    ArrayMap<?, WeakReference<Resources>> arrayMap = (ArrayMap)fMActiveResources.get(resourcesManager);

                    references = arrayMap.values();
                } catch (NoSuchFieldException ignore) {
                    Field mResourceReferences = resourcesManagerClass.getDeclaredField("mResourceReferences");
                    mResourceReferences.setAccessible(true);

                    references = (Collection) mResourceReferences.get(resourcesManager);
                }

                if (Build.VERSION.SDK_INT >= 24) {
                    Field fMResourceImpls = resourcesManagerClass.getDeclaredField("mResourceImpls");
                    fMResourceImpls.setAccessible(true);
                    sResourceImpls = (ArrayMap)fMResourceImpls.get(resourcesManager);
                }
            } else {
                Field fMActiveResources = activityThread.getClass().getDeclaredField("mActiveResources");
                fMActiveResources.setAccessible(true);

                HashMap<?, WeakReference<Resources>> map = (HashMap)fMActiveResources.get(activityThread);

                references = map.values();
            }

            for (WeakReference<Resources> wr : references) {
                Resources resources = wr.get();
                if (resources == null) continue;

                try {
                    Field mAssets = Resources.class.getDeclaredField("mAssets");
                    mAssets.setAccessible(true);
                    mAssets.set(resources, newAssetManager);
                } catch (Throwable ignore) {
                    Field mResourcesImpl = Resources.class.getDeclaredField("mResourcesImpl");
                    mResourcesImpl.setAccessible(true);
                    Object resourceImpl = mResourcesImpl.get(resources);
                    Field implAssets;
                    try {
                        implAssets = resourceImpl.getClass().getDeclaredField("mAssets");
                    } catch (NoSuchFieldException e) {
                        // Compat for MiUI 8+
                        implAssets = resourceImpl.getClass().getSuperclass().getDeclaredField("mAssets");
                    }
                    implAssets.setAccessible(true);
                    implAssets.set(resourceImpl, newAssetManager);

                    if (Build.VERSION.SDK_INT >= 24) {
                        if (resources == app.getResources()) {
                            sMergedResourcesImpl = resourceImpl;
                        }
                    }
                }

                resources.updateConfiguration(resources.getConfiguration(), resources.getDisplayMetrics());
            }

            if (Build.VERSION.SDK_INT >= 21) {
                for (WeakReference<Resources> wr : references) {
                    Resources resources = wr.get();
                    if (resources == null) continue;

                    // android.util.Pools$SynchronizedPool<TypedArray>
                    Field mTypedArrayPool = Resources.class.getDeclaredField("mTypedArrayPool");
                    mTypedArrayPool.setAccessible(true);
                    Object typedArrayPool = mTypedArrayPool.get(resources);
                    // Clear all the pools
                    Method acquire = typedArrayPool.getClass().getMethod("acquire");
                    acquire.setAccessible(true);
                    while (acquire.invoke(typedArrayPool) != null) ;
                }
            }
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    public static void ensureCacheResources() {
        if (Build.VERSION.SDK_INT < 24) return;
        if (sResourceImpls == null || sMergedResourcesImpl == null) return;

        Set<?> resourceKeys = sResourceImpls.keySet();
        for (Object resourceKey : resourceKeys) {
            WeakReference resourceImpl = (WeakReference)sResourceImpls.get(resourceKey);
            if (resourceImpl != null && resourceImpl.get() == null) {
                // Sometimes? the weak reference for the key was released by what
                // we can not find the cache resources we had merged before.
                // And the system will recreate a new one which only build with host resources.
                // So we needs to restore the cache. Fix #429.
                // FIXME: we'd better to find the way to KEEP the weak reference.
                sResourceImpls.put(resourceKey, new WeakReference<Object>(sMergedResourcesImpl));
            }
        }
    }

    public static Intent getIntent(Object/*ActivityClientRecord*/ r) {
        if (sActivityClientRecord_intent_field == null) {
            sActivityClientRecord_intent_field = getDeclaredField(r.getClass(), "intent");
        }
        return getValue(sActivityClientRecord_intent_field, r);
    }

    public static ServiceInfo getServiceInfo(Object/*ActivityThread$CreateServiceData*/ data) {
        Field f = getDeclaredField(data.getClass(), "info");
        return getValue(f, data);
    }

    public static void setActivityInfo(Object/*ActivityClientRecord*/ r, ActivityInfo ai) {
        if (sActivityClientRecord_activityInfo_field == null) {
            sActivityClientRecord_activityInfo_field = getDeclaredField(
                    r.getClass(), "activityInfo");
        }
        setValue(sActivityClientRecord_activityInfo_field, r, ai);
    }

    //______________________________________________________________________________________________
    // Private

    private static Method getMethod(Class cls, String methodName, Class[] types) {
        try {
            Method method = cls.getMethod(methodName, types);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Field getDeclaredField(Class cls, String fieldName) {
        try {
            Field field = cls.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static <T> T invoke(Method method, Object target, Object... args) {
        try {
            return (T) method.invoke(target, args);
        } catch (Exception e) {
            // Ignored
            e.printStackTrace();
            return null;
        }
    }

    private static <T> T getValue(Field field, Object target) {
        if (field == null) {
            return null;
        }

        try {
            return (T) field.get(target);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void setValue(Field field, Object target, Object value) {
        if (field == null) {
            return;
        }

        try {
            field.set(target, value);
        } catch (IllegalAccessException e) {
            // Ignored
            e.printStackTrace();
        }
    }
}
