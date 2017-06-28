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

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;

import net.wequick.small.util.ReflectAccelerator;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;

import dalvik.system.DexFile;

/**
 * This class use to load APK dex and resources.
 * If a bundle was marked as <t>lazy</t> in bundle.json, then we will lazy-load the APK
 * until the class of the APK was firstly required.
 */
class ApkClassLoader extends ClassLoader {

    private ArrayList<ApkElement> mApks;
    private String[] mMergedAssetPaths;
    private ApkInstrumentation mInstrumentation;

    /**
     * Create an apk class loader to load resource and class in multi apk bundles.
     *
     * This will resort the class loader link from:
     *
     *          host ->             boot
     * to:
     *
     *  this -> host -> multiDex -> boot
     *  ^^^^                              load resource and find library in apk bundles
     *          ^^^^                      the original host class loader
     *                  ^^^^^^^^          load dex in apk bundles
     *                              ^^^^  the boot class loader
     *
     */
    ApkClassLoader(ClassLoader host, ApkInstrumentation instrumentation)
            throws NoSuchFieldException, IllegalAccessException {
        super(host);

        Field f = ClassLoader.class.getDeclaredField("parent");
        f.setAccessible(true);
        ClassLoader boot = host.getParent();
        MultiDexClassLoader multiDex = new MultiDexClassLoader(boot);
        f.set(host, multiDex);

        mInstrumentation = instrumentation;
    }

    void addApk(String packageName, Bundle bundle) {
        ApkElement apk = new ApkElement(packageName, bundle);

        // Add to loading queue
        addApk(apk);
    }

    private void addApk(final ApkElement apk) {
        if (mApks == null) {
            mApks = new ArrayList<>();
        }
        mApks.add(apk);

        if (!apk.lazy) {
            Bundle.postIO(new Runnable() {
                @Override
                public void run() {
                    loadApk(apk);
                }
            });
        }
    }

    void setUp() {
        Application app = Small.getContext();

        // Merge all the resources in bundles and replace the host one
        String[] paths = new String[mApks.size() + 1];
        paths[0] = app.getPackageResourcePath(); // add host asset path
        int i = 1;
        for (ApkElement apk : mApks) {
            if (apk.nonResources) continue; // ignores the empty entry to fix #62

            paths[i++] = apk.path; // add plugin asset path
            apk.resourcesMerged = true;
        }
        if (i != paths.length) {
            paths = Arrays.copyOf(paths, i);
        }
        mMergedAssetPaths = paths;
        ReflectAccelerator.mergeResources(app, paths, false);

        // Trigger all the bundle application `onCreate' event
        final ArrayList<ApkElement> lazyApks = new ArrayList<ApkElement>();
        for (ApkElement apk : mApks) {
            if (apk.lazy) {
                lazyApks.add(apk);
                continue;
            }

            createApplication(apk, app);
        }

        // Load the `lazy' dex files in background
        if (lazyApks.size() == 0) return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                for (ApkElement apk : lazyApks) {
                    loadApkLocked(apk);
                }
            }
        }, "net.wequick.small.apk.preload").start();
    }

    private void loadApk(ApkElement apk) {
        if (apk.dexFile != null) return;

        apk.initDexFile();

        if (apk.lazy) {
            // Merge the apk asset to the host
            appendAsset(apk);

            // Initialize the apk application.
            createApplication(apk, Small.getContext());
        }
    }

    private DexFile loadApkLocked(ApkElement apk) {
        return apk.loadDexFileLocked();
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        return super.findClass(name);
    }

    @Override
    protected String findLibrary(String libraryName) {
        String fileName = System.mapLibraryName(libraryName);

        for (ApkElement apk : mApks) {
            if (apk.libraryPath == null) continue;

            File lib = new File(apk.libraryPath, fileName);
            if (lib.exists() && lib.isFile() && lib.canRead()) {
                return lib.getPath();
            }
        }

        return null;
    }

    @Override
    protected URL findResource(String name) {
        for (ApkElement apk : mApks) {
            URL url = apk.findResource(name);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        // codes from
        // https://android.googlesource.com/platform/libcore-snapshot/+/ics-mr1/dalvik/src/main/java/dalvik/system/DexPathList.java#349
        ArrayList<URL> result = new ArrayList<URL>();
        for (ApkElement apk : mApks) {
            URL url = apk.findResource(name);
            if (url != null) {
                result.add(url);
            }
        }
        return Collections.enumeration(result);
    }

    boolean isEmpty() {
        return mApks == null;
    }

    boolean hasApk(String packageName) {
        if (mApks == null) return false;

        for (ApkElement apk : mApks) {
            if (apk.packageName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private void appendAsset(ApkElement apk) {
        if (apk.nonResources) return;
        if (apk.resourcesMerged) return;

        Application app = Small.getContext();
        int N = mMergedAssetPaths.length;
        String[] paths = Arrays.copyOf(mMergedAssetPaths, N + 1);
        paths[N] = apk.path;
        ReflectAccelerator.mergeResources(app, paths, true);

        apk.resourcesMerged = true;
        mInstrumentation.setNeedsRecreateActivities();
    }

    private void createApplication(final ApkElement apk, final Context base) {
        String clazz = apk.applicationName;
        if (clazz == null) return;

        try {
            final Class applicationClass = loadClass(clazz);
            Bundle.postUI(new Runnable() {
                @Override
                public void run() {
                    try {
                        ApkContext appContext = new ApkContext(base, apk);
                        Application bundleApplication = Instrumentation.newApplication(
                                applicationClass, appContext);
                        mInstrumentation.callApplicationOnCreate(bundleApplication);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to create application: " + clazz, e);
        }
    }

    private class MultiDexClassLoader extends ClassLoader {
        MultiDexClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            Class<?> clazz;

            if (mApks == null) return null;

            // Find class in loaded bundles
            for (ApkElement bundle : mApks) {
                if (bundle.dexFile != null) {
                    clazz = bundle.dexFile.loadClass(name, ApkClassLoader.this);
                    if (clazz != null) {
                        return clazz;
                    }
                }
            }

            // Find class in lazy-load bundles
            for (ApkElement apk : mApks) {
                if (apk.dexFile != null) continue;
                // FIXME: Check if the class is in a apk
                // As now, we simply check if the class name is starts with the apk package name,
                // but there are cases that classes from multi-package are compiled into one apk.
                boolean isInBundle = name.startsWith(apk.packageName);
                if (!isInBundle) continue;

                DexFile dexFile = loadApkLocked(apk);
                if (dexFile != null) {
                    clazz = dexFile.loadClass(name, ApkClassLoader.this);
                    if (clazz != null) {
                        return clazz;
                    }
                }
            }

            return super.findClass(name);
        }

    }
}
