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
package net.wequick.gradle

import org.gradle.api.Project

public class RootExtension extends BaseExtension {

    private static final String PLUGIN_GROUP = 'net.wequick.tools.build'
    private static final String PLUGIN_MODULE = 'gradle-small'
    private static final String FD_BUILD_SMALL = 'build-small'
    private static final String FD_PRE_JAR = 'small-pre-jar'
    private static final String FD_PRE_AP = 'small-pre-ap'
    private static final String FD_PRE_IDS = 'small-pre-ids'

    /** 
     * Version of aar net.wequick.small:small
     * default to `gradle-small' plugin version 
     */
    String aarVersion

    /** Count of libraries */
    protected int libCount

    /** Count of bundles */
    protected int bundleCount

    /** Whether contains project small */
    protected boolean hasSmallProject

    private File preBuildDir
    private File preJarDir
    private File preApDir
    private File preIdsDir

    RootExtension(Project project) {
        super(project)

        preBuildDir = new File(project.projectDir, FD_BUILD_SMALL)
        def interDir = new File(preBuildDir, FD_INTERMEDIATES)
        preJarDir = new File(interDir, FD_PRE_JAR)
        preApDir = new File(interDir, FD_PRE_AP)
        preIdsDir = new File(interDir, FD_PRE_IDS)

        def pluginModule = project.buildscript.configurations.classpath.
                resolvedConfiguration.firstLevelModuleDependencies.find {
            it.moduleGroup == PLUGIN_GROUP && it.moduleName == PLUGIN_MODULE
        }
        aarVersion = pluginModule.moduleVersion
    }

    public File getPreBuildDir() {
        return preBuildDir
    }

    public File getPreJarDir() {
        return preJarDir
    }

    public File getPreApDir() {
        return preApDir
    }

    public File getPreIdsDir() {
        return preIdsDir
    }
}
