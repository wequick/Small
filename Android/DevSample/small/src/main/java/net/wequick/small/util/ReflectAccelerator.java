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
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.IBinder;
import android.view.ContextThemeWrapper;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

/**
 * Created by galen on 15/11/2.
 */
public class ReflectAccelerator {
    // AssetManager.addAssetPath
    private static Method sAddAssetPath;
    // DexPathList
    private static Constructor sDexElementConstructor;
    private static Class sDexElementClass;
    private static Field sPathListField;
    private static Field sDexElementsField;
    // ApplicationInfo.resourceDirs
    private static Field sApplicationInfo_resourceDirs_field;
    private static Field sContextThemeWrapper_mTheme_field;
    private static Field sContextThemeWrapper_mResources_field;
    private static Field sContextImpl_mResources_field;
    private static Field sActivity_mMainThread_field;
    private static Method sActivityThread_currentActivityThread_method;
    private static Method sTheme_getNativeTheme_method;
    private static Method sInstrumentation_execStartActivityV21_method;
    private static Method sInstrumentation_execStartActivityV20_method;

    private ReflectAccelerator() { /** cannot be instantiated */ }

    //______________________________________________________________________________________________
    // API

    public static int addAssetPath(AssetManager assets, String path) {
        if (sAddAssetPath == null) {
            sAddAssetPath = getMethod(AssetManager.class,
                    "addAssetPath", new Class[]{String.class});
        }
        if (sAddAssetPath == null) return 0;
        // Fix issue #4 by hpj831112
        Integer ret = invoke(sAddAssetPath, assets, path);
        if (ret == null) return 0;
        return ret;
    }

    //______________________________________________________________________________________________
    // Dex path list

    /**
     * Make dex element
     * @see <a href="https://android.googlesource.com/platform/libcore-snapshot/+/ics-mr1/dalvik/src/main/java/dalvik/system/DexPathList.java">DexPathList.java</a>
     * @param pkg archive android package with any file extensions
     * @param dexFile
     * @return dalvik.system.DexPathList$Element
     */
    private static Object makeDexElement(File pkg, DexFile dexFile) throws Exception {
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
                    ZipFile zip = new ZipFile(pkg);
                    return sDexElementConstructor.newInstance(pkg, zip, dexFile);
                } else {
                    // Element(File apk, File zip, DexFile dex)
                    return sDexElementConstructor.newInstance(pkg, pkg, dexFile);
                }
            case 4:
            default:
                // Element(File apk, boolean isDir, File zip, DexFile dex)
                return sDexElementConstructor.newInstance(pkg, false, pkg, dexFile);
        }
    }

    public static boolean expandDexPathList(ClassLoader cl, String dexPath,
                                     String libraryPath, String optDexPath) {
        try {
            File pkg = new File(dexPath);
            DexFile dexFile = DexFile.loadDex(dexPath, optDexPath, 0);
            Object element = makeDexElement(pkg, dexFile);
            fillDexPathList(cl, element);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Add elements to Object[] with reflection
     * @see <a href="https://github.com/casidiablo/multidex/blob/publishing/library/src/android/support/multidex/MultiDex.java">MultiDex</a>
     * @param target
     * @param arrField
     * @param extraElements
     * @param push true=push to array head, false=append to array tail
     * @throws IllegalAccessException
     */
    public static void expandArray(Object target, Field arrField,
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

    public static void sliceArray(Object target, Field arrField, int deleteIndex)
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

    private static void fillDexPathList(ClassLoader cl, Object element)
            throws NoSuchFieldException, IllegalAccessException {
        if (sPathListField == null) {
            sPathListField = getDeclaredField(DexClassLoader.class.getSuperclass(), "pathList");
        }
        Object pathList = sPathListField.get(cl);
        if (sDexElementsField == null) {
            sDexElementsField = getDeclaredField(pathList.getClass(), "dexElements");
        }
        expandArray(pathList, sDexElementsField, new Object[]{element}, true);
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

    public static void setTheme(Activity activity, Resources.Theme theme) {
        if (sContextThemeWrapper_mTheme_field == null) {
            sContextThemeWrapper_mTheme_field = getDeclaredField(
                    ContextThemeWrapper.class, "mTheme");
        }
        if (sContextThemeWrapper_mTheme_field == null) return;
        setValue(sContextThemeWrapper_mTheme_field, activity, theme);
    }

    public static void setResources(Activity activity, Resources resources) {
        Object target = activity;
        Class targetClass = ContextThemeWrapper.class;
        if (Build.VERSION.SDK_INT <= 16) {
            // wu4321: Fix resource not found bug for API16-
            target = activity.getBaseContext();
            targetClass = target.getClass();
        }
        if (sContextThemeWrapper_mResources_field == null) {
            sContextThemeWrapper_mResources_field = getDeclaredField(targetClass, "mResources");
            if (sContextThemeWrapper_mResources_field == null) return;
        }
        setValue(sContextThemeWrapper_mResources_field, target, resources);
    }

    public static void setResources(Application app, Resources resources) {
        setResources(app.getBaseContext(), resources);
    }

    public static void setResources(Context context, Resources resources) {
        if (sContextImpl_mResources_field == null) {
            sContextImpl_mResources_field = getDeclaredField(
                    context.getClass(), "mResources");
            if (sContextImpl_mResources_field == null) return;
        }
        setValue(sContextImpl_mResources_field, context, resources);
    }

    public static Object getActivityThread(Context context) {
        if (context instanceof Activity) {
            if (sActivity_mMainThread_field == null) {
                sActivity_mMainThread_field = getDeclaredField(Activity.class, "mMainThread");
            }
            if (sActivity_mMainThread_field == null) return null;
            return getValue(sActivity_mMainThread_field, context);
        } else {
            if (sActivityThread_currentActivityThread_method == null) {
                try {
                    Class<?> activityThreadClazz = Class.forName("android.app.ActivityThread");
                    sActivityThread_currentActivityThread_method = getMethod(
                            activityThreadClazz, "currentActivityThread", null);
                } catch (ClassNotFoundException e) {
                    return null;
                }
            }
            return invoke(sActivityThread_currentActivityThread_method, null, (Object[]) null);
        }
    }

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

    public static Instrumentation.ActivityResult execStartActivityV21(
            Instrumentation instrumentation,
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode, android.os.Bundle options) {
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

    public static Instrumentation.ActivityResult execStartActivityV20(
            Instrumentation instrumentation,
            Context who, IBinder contextThread, IBinder token, Activity target,
            Intent intent, int requestCode) {
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

    private static Method getDeclaredMethod(Class cls, String methodName, Class[] types) {
        try {
            Method method = cls.getDeclaredMethod(methodName, types);
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
