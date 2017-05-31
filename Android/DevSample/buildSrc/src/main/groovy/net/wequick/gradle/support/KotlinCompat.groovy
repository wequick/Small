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
package net.wequick.gradle.support

import net.wequick.gradle.RootExtension.KotlinConfig
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

class KotlinCompat {

    private static final String ANDROID_PLUGIN_PACKAGE = 'com.android.build.gradle'
    private static final String KOTLIN_PLUGIN_GROUP = 'org.jetbrains.kotlin'
    private static final String KOTLIN_PLUGIN_NAME = 'kotlin-gradle-plugin'
    private static final String KOTLIN_STDLIB_AAR = 'org.jetbrains.kotlin:kotlin-stdlib-jre7'

    public static compat(Project rootProject, KotlinConfig kotlin) {

        def kotlinVersion = kotlin != null ? kotlin.version : null
        if (kotlinVersion == null) {
            // Try to get the version from classpath dependencies
            Configuration classpath = rootProject.buildscript.configurations['classpath']
            def kotlinModule = classpath.resolvedConfiguration.firstLevelModuleDependencies.find {
                it.moduleGroup == KOTLIN_PLUGIN_GROUP && it.moduleName == KOTLIN_PLUGIN_NAME
            }
            if (kotlinModule == null) return

            kotlinVersion = kotlinModule.moduleVersion
        }

        rootProject.subprojects.each { sub ->
            sub.ext.addedKotlinPlugin = false
            sub.plugins.whenPluginAdded { plugin ->
                if (sub.addedKotlinPlugin) return

                if (plugin.class.package.name == ANDROID_PLUGIN_PACKAGE) {
                    // Add the Kotlin Plugin just after Android Plugin
                    //
                    //   com.android.library     -> [package].LibraryPlugin
                    //   com.android.application -> [package].AppPlugin
                    //

                    // Check if contains any *.kt files
                    def hasKt = false
                    sub.android.sourceSets['main'].java.srcDirs.each { File srcDir ->
                        if (!srcDir.exists()) return

                        srcDir.eachFileRecurse(groovy.io.FileType.FILES, { File file ->
                            if (!hasKt && file.name.endsWith('.kt')) {
                                hasKt = true
                            }
                        })
                    }
                    if (!hasKt) return

                    // Add the Kotlin plugin
                    sub.apply plugin: 'kotlin-android'
                    sub.apply plugin: 'kotlin-android-extensions'
                    sub.dependencies.add 'compile', "$KOTLIN_STDLIB_AAR:$kotlinVersion"
                    sub.addedKotlinPlugin = true
                }
            }
        }
    }
}
