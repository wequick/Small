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

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.ArrayMap;
import android.util.DisplayMetrics;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

/**
 * This class consists exclusively of static methods that accelerate reflections.
 */
public class ReflectAccelerator {
    // AssetManager.addAssetPath
    private static Method sAssetManager_addAssetPath_method;
    private static Method sAssetManager_addAssetPaths_method;
    // ActivityClientRecord
    private static Field sActivityClientRecord_intent_field;
    private static Field sActivityClientRecord_activityInfo_field;

    private ReflectAccelerator() { /** cannot be instantiated */ }

    private static final class V9_13 {

        private static Field sDexClassLoader_mFiles_field;
        private static Field sDexClassLoader_mPaths_field;
        private static Field sDexClassLoader_mZips_field;
        private static Field sDexClassLoader_mDexs_field;
        private static Field sPathClassLoader_libraryPathElements_field;

        public static boolean expandDexPathList(ClassLoader cl,
                                                String[] dexPaths, DexFile[] dexFiles) {
            ZipFile[] zips = null;
            try {
            /*
             * see https://android.googlesource.com/platform/libcore/+/android-2.3_r1/dalvik/src/main/java/dalvik/system/DexClassLoader.java
             */
                if (sDexClassLoader_mFiles_field == null) {
                    sDexClassLoader_mFiles_field = getDeclaredField(cl.getClass(), "mFiles");
                    sDexClassLoader_mPaths_field = getDeclaredField(cl.getClass(), "mPaths");
                    sDexClassLoader_mZips_field = getDeclaredField(cl.getClass(), "mZips");
                    sDexClassLoader_mDexs_field = getDeclaredField(cl.getClass(), "mDexs");
                }
                if (sDexClassLoader_mFiles_field == null
                        || sDexClassLoader_mPaths_field == null
                        || sDexClassLoader_mZips_field == null
                        || sDexClassLoader_mDexs_field == null) {
                    return false;
                }

                int N = dexPaths.length;
                Object[] files = new Object[N];
                Object[] paths = new Object[N];
                zips = new ZipFile[N];
                for (int i = 0; i < N; i++) {
                    String path = dexPaths[i];
                    files[i] = new File(path);
                    paths[i] = path;
                    zips[i] = new ZipFile(path);
                }

                expandArray(cl, sDexClassLoader_mFiles_field, files, true);
                expandArray(cl, sDexClassLoader_mPaths_field, paths, true);
                expandArray(cl, sDexClassLoader_mZips_field, zips, true);
                expandArray(cl, sDexClassLoader_mDexs_field, dexFiles, true);
            } catch (Exception e) {
                e.printStackTrace();
                if (zips != null) {
                    for (ZipFile zipFile : zips) {
                        try {
                            zipFile.close();
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                    }
                }
                return false;
            }
            return true;
        }

        public static void expandNativeLibraryDirectories(ClassLoader classLoader,
                                                          List<File> libPaths) {
            if (sPathClassLoader_libraryPathElements_field == null) {
                sPathClassLoader_libraryPathElements_field = getDeclaredField(
                        classLoader.getClass(), "libraryPathElements");
            }
            List<String> paths = getValue(sPathClassLoader_libraryPathElements_field, classLoader);
            if (paths == null) return;
            for (File libPath : libPaths) {
                paths.add(libPath.getAbsolutePath() + File.separator);
            }
        }
    }

    private static class V14_ { // API 14 and upper

        // DexPathList
        protected static Field sPathListField;
        private static Constructor sDexElementConstructor;
        private static Class sDexElementClass;
        private static Field sDexElementsField;

        public static boolean expandDexPathList(ClassLoader cl,
                                                String[] dexPaths, DexFile[] dexFiles) {
            try {
                int N = dexPaths.length;
                Object[] elements = new Object[N];
                for (int i = 0; i < N; i++) {
                    String dexPath = dexPaths[i];
                    File pkg = new File(dexPath);
                    DexFile dexFile = dexFiles[i];
                    elements[i] = makeDexElement(pkg, dexFile);
                }

                fillDexPathList(cl, elements);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }

        /**
         * Make dex element
         * @see <a href="https://android.googlesource.com/platform/libcore-snapshot/+/ics-mr1/dalvik/src/main/java/dalvik/system/DexPathList.java">DexPathList.java</a>
         * @param pkg archive android package with any file extensions
         * @param dexFile
         * @return dalvik.system.DexPathList$Element
         */
        private static Object makeDexElement(File pkg, DexFile dexFile) throws Exception {
            return makeDexElement(pkg, false, dexFile);
        }

        protected static Object makeDexElement(File dir) throws Exception {
            return makeDexElement(dir, true, null);
        }

        private static Object makeDexElement(File pkg, boolean isDirectory, DexFile dexFile) throws Exception {
            if (sDexElementClass == null) {
                sDexElementClass = Class.forName("dalvik.system.DexPathList$Element");
            }
            if (sDexElementConstructor == null) {
                sDexElementConstructor = sDexElementClass.getConstructors()[0];
            }
            Class<?>[] types = sDexElementConstructor.getParameterTypes();
            switch (types.length) {
                case 3:
                    if (types[1].equals(ZipFile.class)) {
                        // Element(File apk, ZipFile zip, DexFile dex)
                        ZipFile zip;
                        try {
                            zip = new ZipFile(pkg);
                        } catch (IOException e) {
                            throw e;
                        }
                        try {
                            return sDexElementConstructor.newInstance(pkg, zip, dexFile);
                        } catch (Exception e) {
                            zip.close();
                            throw e;
                        }
                    } else {
                        // Element(File apk, File zip, DexFile dex)
                        return sDexElementConstructor.newInstance(pkg, pkg, dexFile);
                    }
                case 4:
                default:
                    // Element(File apk, boolean isDir, File zip, DexFile dex)
                    if (isDirectory) {
                        return sDexElementConstructor.newInstance(pkg, true, null, null);
                    } else {
                        return sDexElementConstructor.newInstance(pkg, false, pkg, dexFile);
                    }
            }
        }

        private static void fillDexPathList(ClassLoader cl, Object[] elements)
                throws NoSuchFieldException, IllegalAccessException {
            if (sPathListField == null) {
                sPathListField = getDeclaredField(DexClassLoader.class.getSuperclass(), "pathList");
            }
            Object pathList = sPathListField.get(cl);
            if (sDexElementsField == null) {
                sDexElementsField = getDeclaredField(pathList.getClass(), "dexElements");
            }
            expandArray(pathList, sDexElementsField, elements, true);
        }

        public static void removeDexPathList(ClassLoader cl, int deleteIndex) {
            try {
                if (sPathListField == null) {
                    sPathListField = getDeclaredField(DexClassLoader.class.getSuperclass(), "pathList");
                }
                Object pathList = sPathListField.get(cl);
                if (sDexElementsField == null) {
                    sDexElementsField = getDeclaredField(pathList.getClass(), "dexElements");
                }
                sliceArray(pathList, sDexElementsField, deleteIndex);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static final class V9_20 {

        private static Method sInstrumentation_execStartActivityV20_method;

        public static Instrumentation.ActivityResult execStartActivity(
                Instrumentation instrumentation, Context who, IBinder contextThread, IBinder token,
                Activity target, Intent intent, int requestCode) {
            if (sInstrumentation_execStartActivityV20_method == null) {
                Class[] types = new Class[] {Context.class, IBinder.class, IBinder.class,
                        Activity.class, Intent.class, int.class};
                sInstrumentation_execStartActivityV20_method = getMethod(Instrumentation.class,
                        "execStartActivity", types);
            }
            if (sInstrumentation_execStartActivityV20_method == null) return null;
            return invoke(sInstrumentation_execStartActivityV20_method, instrumentation,
                    who, contextThread, token, target, intent, requestCode);
        }
    }

    private static final class V21_ {

        private static Method sInstrumentation_execStartActivityV21_method;

        public static Instrumentation.ActivityResult execStartActivity(
                Instrumentation instrumentation, Context who, IBinder contextThread, IBinder token,
                Activity target, Intent intent, int requestCode, Bundle options) {
            if (sInstrumentation_execStartActivityV21_method == null) {
                Class[] types = new Class[] {Context.class, IBinder.class, IBinder.class,
                        Activity.class, Intent.class, int.class, android.os.Bundle.class};
                sInstrumentation_execStartActivityV21_method = getMethod(Instrumentation.class,
                        "execStartActivity", types);
            }
            if (sInstrumentation_execStartActivityV21_method == null) return null;
            return invoke(sInstrumentation_execStartActivityV21_method, instrumentation,
                    who, contextThread, token, target, intent, requestCode, options);
        }
    }

    private static class V14_22 extends V14_ {

        protected static Field sDexPathList_nativeLibraryDirectories_field;

        public static void expandNativeLibraryDirectories(ClassLoader classLoader,
                                                          List<File> libPaths) {
            if (sPathListField == null) return;

            Object pathList = getValue(sPathListField, classLoader);
            if (pathList == null) return;

            if (sDexPathList_nativeLibraryDirectories_field == null) {
                sDexPathList_nativeLibraryDirectories_field = getDeclaredField(
                        pathList.getClass(), "nativeLibraryDirectories");
                if (sDexPathList_nativeLibraryDirectories_field == null) return;
            }

            try {
                // File[] nativeLibraryDirectories
                Object[] paths = libPaths.toArray();
                expandArray(pathList, sDexPathList_nativeLibraryDirectories_field, paths, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static final class V23_ extends V14_22 {

        private static Field sDexPathList_nativeLibraryPathElements_field;

        public static void expandNativeLibraryDirectories(ClassLoader classLoader,
                                                          List<File> libPaths) {
            if (sPathListField == null) return;

            Object pathList = getValue(sPathListField, classLoader);
            if (pathList == null) return;

            if (sDexPathList_nativeLibraryDirectories_field == null) {
                sDexPathList_nativeLibraryDirectories_field = getDeclaredField(
                        pathList.getClass(), "nativeLibraryDirectories");
                if (sDexPathList_nativeLibraryDirectories_field == null) return;
            }

            try {
                // List<File> nativeLibraryDirectories
                List<File> paths = getValue(sDexPathList_nativeLibraryDirectories_field, pathList);
                if (paths == null) return;
                paths.addAll(libPaths);

                // Element[] nativeLibraryPathElements
                if (sDexPathList_nativeLibraryPathElements_field == null) {
                    sDexPathList_nativeLibraryPathElements_field = getDeclaredField(
                            pathList.getClass(), "nativeLibraryPathElements");
                }
                if (sDexPathList_nativeLibraryPathElements_field == null) return;

                int N = libPaths.size();
                Object[] elements = new Object[N];
                for (int i = 0; i < N; i++) {
                    Object dexElement = makeDexElement(libPaths.get(i));
                    elements[i] = dexElement;
                }

                expandArray(pathList, sDexPathList_nativeLibraryPathElements_field, elements, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //______________________________________________________________________________________________
    // API

    public static AssetManager newAssetManager() {
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
        return assets;
    }

    public static int addAssetPath(AssetManager assets, String path) {
        if (sAssetManager_addAssetPath_method == null) {
            sAssetManager_addAssetPath_method = getMethod(AssetManager.class,
                    "addAssetPath", new Class[]{String.class});
        }
        if (sAssetManager_addAssetPath_method == null) return 0;
        Integer ret = invoke(sAssetManager_addAssetPath_method, assets, path);
        if (ret == null) return 0;
        return ret;
    }

    public static int[] addAssetPaths(AssetManager assets, String[] paths) {
        if (sAssetManager_addAssetPaths_method == null) {
            sAssetManager_addAssetPaths_method = getMethod(AssetManager.class,
                    "addAssetPaths", new Class[]{String[].class});
        }
        if (sAssetManager_addAssetPaths_method == null) return null;
        return invoke(sAssetManager_addAssetPaths_method, assets, new Object[]{paths});
    }

    public static void mergeResources(Application app, Object activityThread, String[] assetPaths) {
        AssetManager newAssetManager;
        if (Build.VERSION.SDK_INT < 24) {
            newAssetManager = newAssetManager();
        } else {
            // On Android 7.0+, this should contains a WebView asset as base. #347
            newAssetManager = app.getAssets();
        }
        addAssetPaths(newAssetManager, assetPaths);

        try {
            Method mEnsureStringBlocks = AssetManager.class.getDeclaredMethod("ensureStringBlocks", new Class[0]);
            mEnsureStringBlocks.setAccessible(true);
            mEnsureStringBlocks.invoke(newAssetManager, new Object[0]);

            Collection<WeakReference<Resources>> references;

            if (Build.VERSION.SDK_INT >= 19) {
                Class<?> resourcesManagerClass = Class.forName("android.app.ResourcesManager");
                Method mGetInstance = resourcesManagerClass.getDeclaredMethod("getInstance", new Class[0]);
                mGetInstance.setAccessible(true);
                Object resourcesManager = mGetInstance.invoke(null, new Object[0]);
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
                    //check if rom change resourceImpl may be a subclass of resourceImpl like miui8
                    Field implAssets;
                    try {
                        implAssets = resourceImpl.getClass().getDeclaredField("mAssets");
                    } catch (NoSuchFieldException e) {
                        implAssets = resourceImpl.getClass().getSuperclass().getDeclaredField("mAssets");
                    }
                    implAssets.setAccessible(true);
                    implAssets.set(resourceImpl, newAssetManager);
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

    public static Object getActivityThread(Context context) {
        try {
            Class activityThread = Class.forName("android.app.ActivityThread");
            // ActivityThread.currentActivityThread()
            Method m = activityThread.getMethod("currentActivityThread", new Class[0]);
            m.setAccessible(true);
            Object thread = m.invoke(null, new Object[0]);
            if (thread != null) return thread;

            // context.@mLoadedApk.@mActivityThread
            Field mLoadedApk = context.getClass().getField("mLoadedApk");
            mLoadedApk.setAccessible(true);
            Object apk = mLoadedApk.get(context);
            Field mActivityThreadField = apk.getClass().getDeclaredField("mActivityThread");
            mActivityThreadField.setAccessible(true);
            return mActivityThreadField.get(apk);
        } catch (Throwable ignore) {
            throw new RuntimeException("Failed to get mActivityThread from context: " + context);
        }
    }

    public static Application getApplication() {
        try {
            Class activityThread = Class.forName("android.app.ActivityThread");
            // ActivityThread.currentActivityThread()
            Method m = activityThread.getMethod("currentApplication", new Class[0]);
            m.setAccessible(true);
            return (Application) m.invoke(null, new Object[0]);
        } catch (Throwable ignore) {
            throw new RuntimeException("Failed to get current application!");
        }
    }

    public static boolean expandDexPathList(ClassLoader cl, String[] dexPaths, DexFile[] dexFiles) {
        if (Build.VERSION.SDK_INT < 14) {
            return V9_13.expandDexPathList(cl, dexPaths, dexFiles);
        } else {
            return V14_.expandDexPathList(cl, dexPaths, dexFiles);
        }
    }

    public static void expandNativeLibraryDirectories(ClassLoader classLoader, List<File> libPath) {
        int v = Build.VERSION.SDK_INT;
        if (v < 14) {
            V9_13.expandNativeLibraryDirectories(classLoader, libPath);
        } else if (v < 23) {
            V14_22.expandNativeLibraryDirectories(classLoader, libPath);
        } else {
            V23_.expandNativeLibraryDirectories(classLoader, libPath);
        }
    }

    public static Instrumentation.ActivityResult execStartActivity(
            Instrumentation instrumentation,
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, android.os.Bundle options) {
        return V21_.execStartActivity(instrumentation,
                who, contextThread, token, target, intent, requestCode, options);
    }

    public static Instrumentation.ActivityResult execStartActivity(
            Instrumentation instrumentation,
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode) {
        return V9_20.execStartActivity(instrumentation,
                who, contextThread, token, target, intent, requestCode);
    }

    public static Intent getIntent(Object/*ActivityClientRecord*/ r) {
        if (sActivityClientRecord_intent_field == null) {
            sActivityClientRecord_intent_field = getDeclaredField(r.getClass(), "intent");
        }
        return getValue(sActivityClientRecord_intent_field, r);
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

    /**
     * Add elements to Object[] with reflection
     * @see <a href="https://github.com/casidiablo/multidex/blob/publishing/library/src/android/support/multidex/MultiDex.java">MultiDex</a>
     * @param target
     * @param arrField
     * @param extraElements
     * @param push true=push to array head, false=append to array tail
     * @throws IllegalAccessException
     */
    private static void expandArray(Object target, Field arrField,
                                    Object[] extraElements, boolean push)
            throws IllegalAccessException {
        Object[] original = (Object[]) arrField.get(target);
        Object[] combined = (Object[]) Array.newInstance(
                original.getClass().getComponentType(), original.length + extraElements.length);
        if (push) {
            System.arraycopy(extraElements, 0, combined, 0, extraElements.length);
            System.arraycopy(original, 0, combined, extraElements.length, original.length);
        } else {
            System.arraycopy(original, 0, combined, 0, original.length);
            System.arraycopy(extraElements, 0, combined, original.length, extraElements.length);
        }
        arrField.set(target, combined);
    }

    private static void sliceArray(Object target, Field arrField, int deleteIndex)
            throws  IllegalAccessException {
        Object[] original = (Object[]) arrField.get(target);
        if (original.length == 0) return;

        Object[] sliced = (Object[]) Array.newInstance(
                original.getClass().getComponentType(), original.length - 1);
        if (deleteIndex > 0) {
            // Copy left elements
            System.arraycopy(original, 0, sliced, 0, deleteIndex);
        }
        int rightCount = original.length - deleteIndex - 1;
        if (rightCount > 0) {
            // Copy right elements
            System.arraycopy(original, deleteIndex + 1, sliced, deleteIndex, rightCount);
        }
        arrField.set(target, sliced);
    }

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
        try {
            return (T) field.get(target);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void setValue(Field field, Object target, Object value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException e) {
            // Ignored
            e.printStackTrace();
        }
    }
}
