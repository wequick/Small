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
package net.wequick.gradle.util

public class AarPath {

    private static final String CACHE_DIR = "build-cache"
    private static final String CACHE_INPUTS_FILE = "inputs"
    private static final String CACHE_FILE_PATH_KEY = "FILE_PATH"
    private static final int CACHE_FILE_PATH_INDEX = CACHE_FILE_PATH_KEY.length() + 1

    private String mSrc
    private boolean isCache

    public AarPath(File path) {
        mSrc = path.absolutePath
        if (mSrc.contains(CACHE_DIR)) {
            this.initWithCachePath(path)
        }
    }

    private void initWithCachePath(File path) {
        while (path.parentFile.name != CACHE_DIR) {
            path = path.parentFile
        }

        File input = new File(path, CACHE_INPUTS_FILE)
        if (!input.exists()) {
            return
        }

        def src = null
        input.eachLine {
            if (it.startsWith(CACHE_FILE_PATH_KEY)) {
                src = it.substring(CACHE_FILE_PATH_INDEX)
                return
            }
        }
        if (src == null) {
            return
        }

        mSrc = src
        isCache = true
    }

    public boolean explodedFromAar(Map aar) {
        // ~/.gradle/caches/modules-2/files-2.1/net.wequick.small/small/1.1.0/hash/*.aar
        //                                      ^^^^^^^^^^^^^^^^^ ^^^^^
        def moduleAarDir = "$aar.group$File.separator$aar.name"
        if (mSrc.contains(moduleAarDir)) {
            return true
        }

        // [sdk]/extras/android/m2repository/com/android/support/support-core-ui/25.1.0/*.aar
        //                                   ^^^^^^^^^^^^^^^^^^^ ^^^^^^^^^^^^^^^
        def sep = File.separator
        if (sep == '\\') {
            sep = '\\\\' // compat for windows
        }
        def repoGroup = aar.group.replaceAll('\\.', sep)
        def repoAarPath = "$repoGroup$File.separator$aar.name"
        return mSrc.contains(repoAarPath)
    }
}