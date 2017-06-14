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
package net.wequick.gradle.test

import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.sdklib.BuildToolInfo
import groovy.io.FileType
import net.wequick.gradle.RootExtension
import net.wequick.gradle.util.AnsiUtils
import net.wequick.gradle.util.Log
import org.gradle.api.Project

class DuplicateClassesTests extends UnitTests {

    static final String CLASS_DESCRIPTOR = 'Class descriptor'
    static final int CLASS_DESCRIPTOR_LEN = CLASS_DESCRIPTOR.length()

    RootExtension rootSmall

    DuplicateClassesTests(Project project) {
        super(project)
        rootSmall = project.rootProject.small
    }

    @Override
    void setUp() {
//        Log.action('Cleaning', 'lib.* bundles')
//        gradlew('cleanLib', true, true)
//
//        Log.action('Cleaning', 'app.* bundles')
//        gradlew('cleanBundle', true, true)
//
//        Log.action('Building', 'lib.* bundles')
//        gradlew('buildLib', true, false)
//
//        Log.action('Building', 'app.* bundles')
//        gradlew('buildBundle', true, true)

        Log.action('Compiling', 'host classes')
        def hostProject = rootSmall.hostProject
        def flavorName = 'Release'
        AppExtension android = hostProject.android
        if (android.productFlavors.size() > 0) {
            flavorName = android.productFlavors[0].name.capitalize() + 'Release'
        }
        String hostDexTaskName = ":$rootSmall.hostModuleName:transformClassesWithDexFor$flavorName"
        gradlew(hostDexTaskName, true, true)

        super.setUp()
    }

    def testNoDuplicateClasses() {
        def projects = new HashSet<Project>()
        projects.add(rootSmall.hostProject)
        projects.addAll(rootSmall.libProjects)
        projects.addAll(rootSmall.appProjects)

        def tasks = rootSmall.hostProject.tasks.withType(ProcessAndroidResources)
        def aapt = tasks[0]
        def buildToolInfo = aapt.buildTools
        def dexDump = buildToolInfo.getPath(BuildToolInfo.PathId.DEXDUMP)

        def classOwners = new HashMap<String, Set>()
        def duplicateClasses = new HashSet<String>()

        projects.each {
            def dex = it.tasks.withType(TransformTask.class).find {
                it.transform.name == 'dex' &&
                        (it.variantName == 'release' || it.variantName.contains('Release'))
            }

            def dexDir
            if (dex == null) {
                dexDir = new File(it.buildDir, 'intermediates/transforms/dex/release')
            } else {
                dexDir = dex.streamOutputFolder
            }

            if (!dexDir.exists()) {
                throw new RuntimeException("Failed to find the dex directory at path: $dexDir. " +
                        "Please run 'buildLib' and 'buildBundle' first.")
            }

            File dexFile = null
            dexDir.eachFileRecurse(FileType.FILES, {
                if (it.name == 'classes.dex') {
                    dexFile = it
                }
            })
            if (dexFile == null) {
                throw new RuntimeException("Failed to find the dex file under path: $dexDir. " +
                        "Please run 'buildLib' and 'buildBundle' first.")
            }

            def out = new ByteArrayOutputStream()
            project.exec {
                executable dexDump
                args dexFile.path

                // store the output instead of printing to the console
                standardOutput = out
            }

            def str = out.toString()
            str.eachLine { line ->
                // line: "  Class descriptor  : 'Landroid/support/annotation/AnimRes;'"
                int loc = line.indexOf(CLASS_DESCRIPTOR)
                if (loc < 0) return

                line = line.substring(loc + CLASS_DESCRIPTOR_LEN + 1)
                loc = line.indexOf("'")
                if (loc < 0) return

                line = line.substring(loc + 1)
                line = line.substring(0, line.length() - 2)

                LinkedList<String> owners = (LinkedList<String>) classOwners[line]
                if (owners == null) {
                    classOwners[line] = owners = new LinkedList<String>()
                } else {
                    duplicateClasses.add(line)
                }
                owners.add(it.name)
            }
        }

        int duplicateCount = duplicateClasses.size()
        if (duplicateCount > 0) {
            def details = ''
            for (int i = 0; i < duplicateCount; i++) {
                String clazz = duplicateClasses[i]
                Set owners = classOwners[clazz]
                details += "        - ["
                details += AnsiUtils.white(owners[0])
                for (int j = 1; j < owners.size(); j++) {
                    details += ", "
                    details += AnsiUtils.white(owners[j])
                }
                details += "] "
                clazz = clazz.substring(1).replace('/', '.')
                details += AnsiUtils.red(clazz)
                details += "\n"
            }
            tAssert(false, 'Classes duplicate', details)
        }
    }
}