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
import android.content.pm.PackageInfo;

import net.wequick.small.util.ReflectAccelerator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import dalvik.system.DexFile;

/**
 * This class use to load APK dex and resources.
 * If a bundle was marked as <t>lazy</t> in bundle.json, then we will lazy-load the APK
 * until the class of the APK was firstly required.
 */
class ApkClassLoader extends ClassLoader {

    private static final String FILE_DEX = "bundle.dex";

    private ArrayList<ApkInfo> mApks;
    private String[] mMergedAssetPaths;
    private ApkInstrumentation mInstrumentation;

    ApkClassLoader(ClassLoader parent, ApkInstrumentation instrumentation) {
        super(parent);
        mInstrumentation = instrumentation;
    }

    void addApk(String packageName, Bundle bundle) {
        ApkInfo apk = new ApkInfo();
        BundleParser parser = bundle.getParser();
        PackageInfo pluginInfo = parser.getPackageInfo();

        apk.packageName = packageName;
        apk.path = parser.getSourcePath();
        apk.nonResources = parser.isNonResources();
        if (pluginInfo.applicationInfo != null) {
            apk.applicationName = pluginInfo.applicationInfo.className;
        }
        apk.packagePath = bundle.getExtractPath();
        apk.optDexPath = new File(apk.packagePath, FILE_DEX).getAbsolutePath();
        apk.lazy = bundle.isLazy();

        // Record the native libraries path with specify ABI
        String libDir = parser.getLibraryDirectory();
        if (libDir != null) {
            apk.libraryPath = new File(apk.packagePath, libDir).getAbsolutePath();
        }

        // Add to loading queue
        addApk(apk);
    }

    private void addApk(final ApkInfo apk) {
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

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> clazz;

        if (mApks == null) return super.findClass(name);

        // Find class in loaded bundles
        for (ApkInfo bundle : mApks) {
            if (bundle.dexFile != null) {
                clazz = bundle.dexFile.loadClass(name, this);
                if (clazz != null) {
                    return clazz;
                }
            }
        }

        // Find class in lazy-load bundles
        for (ApkInfo apk : mApks) {
            if (apk.dexFile != null) continue;
            // FIXME: Check if the class is in a apk
            // As now, we simply check if the class name is starts with the apk package name,
            // but there are cases that classes from multi-package are compiled into one apk.
            boolean isInBundle = name.startsWith(apk.packageName);
            if (!isInBundle) continue;

            loadApk(apk);
            clazz = apk.dexFile.loadClass(name, this);
            if (clazz != null) {
                return clazz;
            }
        }

        return super.findClass(name);
    }

    private void loadApk(ApkInfo apk) {
        if (apk.dexFile != null) return;

        try {
            apk.dexFile = DexFile.loadDex(apk.path, apk.optDexPath, 0);

            if (apk.lazy) {
                // Merge the apk asset to the host
                appendAsset(apk);

                // Initialize the apk application.
                createApplication(apk, Small.getContext());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load dex for apk: '" +
                    apk.packageName + "'!", e);
        }
    }

    @Override
    protected String findLibrary(String libname) {
        for (ApkInfo apk : mApks) {
            if (apk.libraryPath == null) continue;

            File lib = new File(apk.libraryPath, "lib" + libname + ".so");
            if (lib.exists()) {
                System.out.println("!!! Find library " + libname + " from " + apk.packageName);
                return lib.getAbsolutePath();
            }
        }

        return super.findLibrary(libname);
    }

    boolean isEmpty() {
        return mApks == null;
    }

    boolean hasApk(String packageName) {
        if (mApks == null) return false;

        for (ApkInfo apk : mApks) {
            if (apk.packageName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    void mergeResources() {
        Application app = Small.getContext();
        String[] paths = new String[mApks.size() + 1];
        paths[0] = app.getPackageResourcePath(); // add host asset path
        int i = 1;
        for (ApkInfo apk : mApks) {
            if (apk.nonResources) continue; // ignores the empty entry to fix #62

            paths[i++] = apk.path; // add plugin asset path
            apk.resourcesMerged = true;
        }
        if (i != paths.length) {
            paths = Arrays.copyOf(paths, i);
        }
        mMergedAssetPaths = paths;
        ReflectAccelerator.mergeResources(app, paths, false);
    }

    private void appendAsset(ApkInfo apk) {
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

    void createApplications() {
        Application app = Small.getContext();
        for (ApkInfo apk : mApks) {
            if (apk.lazy) continue;

            createApplication(apk, app);
        }
    }

    private void createApplication(final ApkInfo apk, final Context base) {
        String clazz = apk.applicationName;
        if (clazz == null) return;

        try {
            final Class applicationClass = findClass(clazz);
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
}
