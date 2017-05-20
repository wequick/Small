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

import java.io.File;
import java.io.IOException;

import dalvik.system.DexFile;

class ApkInfo {
    String packageName;
    File packagePath;
    String applicationName;
    String path;
    DexFile dexFile;
    String optDexPath;
    String libraryPath;
    boolean nonResources;
    /**
     * no resources.arsc
     */
    boolean lazy;
    boolean resourcesMerged;

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
}
