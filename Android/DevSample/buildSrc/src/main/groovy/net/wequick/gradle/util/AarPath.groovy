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

import org.gradle.api.Project

public class AarPath {

    private static final String CACHE_DIR = "build-cache"
    private static final String CACHE_INPUTS_FILE = "inputs"
    private static final String CACHE_FILE_PATH_KEY = "FILE_PATH"
    private static final int CACHE_FILE_PATH_INDEX = CACHE_FILE_PATH_KEY.length() + 1

    private static final String _ = File.separator
    private static final String LOCAL_MAVEN2_CACHE_PATH = '.m2' + _ + 'repository'
    private static final String MAVEN2_CACHE_PATH = 'm2repository'
    private static final String GRADLE_CACHE_PATH = 'caches' + _ + 'modules'

    private Project project
    private File mInputFile
    private File mOutputDir

    public static class Module {
        String group
        String name
        String version
        String path
        String fileName

        public String getPath() {
            if (path == null) {
                path = "$group/$name/$version"
            }
            return path
        }

        public String getFileName() {
            if (fileName == null) {
                fileName = "$name-$version"
            }
            return fileName
        }
    }

    private Module mModule

    public AarPath(Project project, File path) {
        this.project = project
        mOutputDir = path
        mInputFile = parseInputFile(path)
    }

    private static File parseInputFile(File outputDir) {
        // Find the build cache root which should be something as
        // `~/.android/build-cache` on Android Plugin 2.3.0+
        File cacheDir = outputDir
        while (cacheDir.parentFile != null && cacheDir.parentFile.name != CACHE_DIR) {
            cacheDir = cacheDir.parentFile
        }

        if (cacheDir.parentFile == null) {
            // Isn't using `buildCache`, just take the output as input
            return outputDir
        }

        File input = new File(cacheDir, CACHE_INPUTS_FILE)
        if (!input.exists()) {
            return null
        }

        String inputPath = null
        input.eachLine {
            if (inputPath == null && it.startsWith(CACHE_FILE_PATH_KEY)) {
                inputPath = it.substring(CACHE_FILE_PATH_INDEX)
            }
        }
        if (inputPath == null) return null

        return new File(inputPath)
    }

    private Module parseInputModule(File inputFile) {
        Module module = new Module()
        if (inputFile == null) {
            return module
        }

        File temp
        File versionFile = inputFile
        String inputPath = inputFile.absolutePath
        String parentName = inputFile.parentFile.name
        if (parentName == 'jars') {
            // **/appcompat-v7/23.2.1/jars/classes.jar
            // => appcompat-v7-23.2.1.jar
            temp = inputFile.parentFile.parentFile
            module.version = temp.name; temp = temp.parentFile
            module.name = temp.name; temp = temp.parentFile
            module.group = temp.name
        } else if (parentName == 'libs') {
            // Sample/lib.utils/libs/mylib.jar
            //        ^^^^^^^^^ project
            temp = inputFile.parentFile.parentFile
            if (temp.name == 'default') {
                // Sample/lib.utils/build/intermediates/bundles/default/libs/assets.jar
                temp = temp.parentFile.parentFile.parentFile.parentFile
            }
            Project libProject = project.rootProject.findProject(temp.name)
            if (libProject != null) {
                module.version = libProject.version
                module.name = libProject.name
                module.group = libProject.group ?: temp.parentFile.name

                def name = inputFile.name
                name = name.substring(0, name.lastIndexOf('.'))
                module.fileName = "$module.name-$name"
            }
        } else if (parentName == 'default') {
            // Compat for android plugin 2.3.0
            // Sample/jni_plugin/build/intermediates/bundles/default/classes.jar
            //        ^^^^^^^^^^ project
            temp = inputFile.parentFile.parentFile.parentFile.parentFile.parentFile
            Project libProject = project.rootProject.findProject(temp.name)
            if (libProject != null) {
                module.version = libProject.version
                module.name = libProject.name
                module.group = libProject.group ?: temp.parentFile.name

                module.fileName = "$module.name-default"
            }
        } else {
            Project aarProject = project.rootProject.findProject(parentName)
            if (aarProject != null) {
                // Local AAR
                // Sample/vendor-aar/vendor-aar.aar
                //        ^^^^^^^^^^ project
                module.version = aarProject.version
                module.name = aarProject.name
                module.group = aarProject.group ?: inputFile.parentFile.parentFile.name
            } else if (inputPath.contains('exploded-aar')) {
                // [BUILD_DIR]/intermediates/exploded-aar/com.android.support/support-v4/25.1.0
                //                                        ^^^^^^^^^^^^^^^^^^^ ^^^^^^^^^^ ^^^^^^
                temp = versionFile
                module.version = temp.name; temp = temp.parentFile
                module.name = temp.name; temp = temp.parentFile
                module.group = temp.name
            } else if (inputPath.contains(MAVEN2_CACHE_PATH)
                    || inputPath.contains(LOCAL_MAVEN2_CACHE_PATH)) {
                // [SDK_HOME]/extras/android/m2repository/com/android/support/support-core-ui/25.1.0/*.aar
                //                                        ^^^^^^^^^^^^^^^^^^^ ^^^^^^^^^^^^^^^ ^^^^^^
                temp = inputFile.parentFile
                module.version = temp.name; temp = temp.parentFile
                module.name = temp.name; temp = temp.parentFile
                module.group = temp.name
                while ((temp = temp.parentFile) != null && temp.name != MAVEN2_CACHE_PATH) {
                    module.group = temp.name + '.' + module.group
                }
            } else if (inputPath.contains(GRADLE_CACHE_PATH)) {
                // ~/.gradle/caches/modules-2/files-2.1/net.wequick.small/small/1.1.0/hash/*.aar
                //                                      ^^^^^^^^^^^^^^^^^ ^^^^^ ^^^^^
                temp = inputFile.parentFile.parentFile
                module.version = temp.name; temp = temp.parentFile
                module.name = temp.name; temp = temp.parentFile
                module.group = temp.name

                def hash = inputFile.parentFile.name
                module.fileName = "$module.name-$module.version-$hash"
            }
        }

        if (module.group == null) {
            throw new RuntimeException("Failed to parse aar module from $inputFile")
        }

        return module
    }

    public boolean explodedFromAar(Map aar) {
        if (mInputFile == null) return false

        String inputPath = mInputFile.absolutePath

        def group = aar.group
        if (group == project.rootProject.name) {
            def lib = project.rootProject.findProject(aar.name)
            group = lib.projectDir.parentFile.name
        }

        // ~/.gradle/caches/modules-2/files-2.1/net.wequick.small/small/1.1.0/hash/*.aar
        //                                      ^^^^^^^^^^^^^^^^^ ^^^^^
        def moduleAarDir = "$group$File.separator$aar.name"
        if (inputPath.contains(moduleAarDir)) {
            return true
        }

        // [SDK_HOME]/extras/android/m2repository/com/android/support/support-core-ui/25.1.0/*.aar
        //                                        ^^^^^^^^^^^^^^^^^^^ ^^^^^^^^^^^^^^^
        def sep = File.separator
        if (sep == '\\') {
            sep = '\\\\' // compat for windows
        }
        def repoGroup = group.replaceAll('\\.', sep)
        def repoAarPath = "$repoGroup$File.separator$aar.name"
        return inputPath.contains(repoAarPath)
    }

    public File getInputFile() {
        return mInputFile
    }

    public File getOutputDir() {
        return mOutputDir
    }

    public Module getModule() {
        if (mModule == null) {
            mModule = parseInputModule(mInputFile)
        }
        return mModule
    }
}