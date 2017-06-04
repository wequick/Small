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

import android.content.pm.PackageInfo;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.zip.ZipFile;

import dalvik.system.DexFile;

class ApkInfo {
    private static final String FILE_DEX = "bundle.dex";

    String packageName;
    File packagePath;
    String applicationName;
    String path;
    File file;
    DexFile dexFile;
    ZipFile zipFile;
    String optDexPath;
    String libraryPath;
    boolean nonResources;
    /**
     * no resources.arsc
     */
    boolean lazy;
    boolean resourcesMerged;
    
    ApkInfo(String pkg, Bundle bundle) {
        BundleParser parser = bundle.getParser();
        PackageInfo pluginInfo = parser.getPackageInfo();

        packageName = pkg;
        nonResources = parser.isNonResources();
        if (pluginInfo.applicationInfo != null) {
            applicationName = pluginInfo.applicationInfo.className;
        }
        packagePath = bundle.getExtractPath();
        optDexPath = new File(packagePath, FILE_DEX).getAbsolutePath();
        lazy = bundle.isLazy();

        path = parser.getSourcePath();
        file = new File(path);
        try {
            zipFile = new ZipFile(path);
        } catch (IOException ex) {
            System.err.println("Unable to open zip file: " + path);
            ex.printStackTrace();
        }

        // Record the native libraries path with specify ABI
        String libDir = parser.getLibraryDirectory();
        if (libDir != null) {
            libraryPath = new File(packagePath, libDir).getAbsolutePath();
        }
    }

    void initDexFile() {
        dexFile = loadDexFile();
    }

    DexFile loadDexFileLocked() {
        if (dexFile == null) {
            synchronized (this) {
                if (dexFile == null) {
                    dexFile = loadDexFile();
                }
            }
        }
        return dexFile;
    }

    private DexFile loadDexFile() {
        try {
            return DexFile.loadDex(path, optDexPath, 0);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load dex for apk: '" +
                    packageName + "'!", e);
        }
    }

    URL findResource(String name) {
        // codes from
        // https://android.googlesource.com/platform/libcore-snapshot/+/ics-mr1/dalvik/src/main/java/dalvik/system/DexPathList.java#398
        if ((zipFile == null) || (zipFile.getEntry(name) == null)) {
            /*
             * Either this element has no zip/jar file (first
             * clause), or the zip/jar file doesn't have an entry
             * for the given name (second clause).
             */
            return null;
        }
        try {
            /*
             * File.toURL() is compliant with RFC 1738 in
             * always creating absolute path names. If we
             * construct the URL by concatenating strings, we
             * might end up with illegal URLs for relative
             * names.
             */
            return new URL("jar:" + file.toURL() + "!/" + name);
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
    }
}
