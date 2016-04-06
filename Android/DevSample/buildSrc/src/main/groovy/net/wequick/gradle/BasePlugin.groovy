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
import org.gradle.api.Plugin
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutput.Style
import org.gradle.logging.StyledTextOutputFactory

/**
 *
 */
public abstract class BasePlugin implements Plugin<Project> {

    public static final String SMALL_AAR_PREFIX = "net.wequick.small:small:"
    public static final String SMALL_LIBS = 'smallLibs'

    protected boolean isBuildingBundle
    protected boolean isBuildingLib

    protected Project project

    void apply(Project project) {
        this.project = project

        if (Log.out == null) {
            Log.out = project.gradle.services.get(StyledTextOutputFactory).create('')
        }

        def sp = project.gradle.startParameter
        def p = sp.projectDir
        def t = sp.taskNames[0]
        if (p == null || p == project.rootProject.projectDir) {
            // gradlew buildLib | buildBundle
            if (t == 'buildLib') isBuildingLib = true
            else if (t == 'buildBundle') isBuildingBundle = true
        } else if (t == 'assembleRelease' || t == 'aR') {
            // gradlew -p [project.name] assembleRelease
            if (pluginType == PluginType.Library) isBuildingLib = true
            else isBuildingBundle = true
        }

        createExtension()

        configureProject()

        createTask()
    }

    protected void createExtension() {
        // Add the 'small' extension object
        project.extensions.create('small', getExtensionClass(), project)
        small.type = getPluginType()
    }

    protected void configureProject() {
        // Tidy up while gradle build finished
        project.gradle.buildFinished { result ->
            Log.out = null
            if (result.failure == null) return
            tidyUp()
        }

        // Automatic add `small' dependency
        if (smallCompileType != null) {
            project.afterEvaluate {
                RootPlugin rootPlugin = (RootPlugin) project.rootProject.plugins.
                        withType(RootPlugin.class)[0]
                RootExtension rootExt = rootPlugin.small
                if (rootExt.hasSmallProject) {
                    project.dependencies.add(smallCompileType, project.project(':small'))
                } else {
                    def version = rootExt.aarVersion
                    project.dependencies.add(smallCompileType, "${SMALL_AAR_PREFIX}$version")
                }
            }
        }
    }

    protected void createTask() {}

    protected <T extends BaseExtension> T getSmall() {
        return (T) project.small
    }

    protected PluginType getPluginType() { return PluginType.Unknown }

    /** Restore state for DEBUG mode */
    protected void tidyUp() { }

    protected String getSmallCompileType() { return null }

    protected abstract Class<? extends BaseExtension> getExtensionClass()

    // Following functions for printing colourful text
    protected void printInfo(String text) {

    }

    public final class Log {

        protected static StyledTextOutput out

        public static void header(String text) {
            out.style(Style.UserInput)
            out.withStyle(Style.Info).text('[Small] ')
            out.println(text)
        }

        public static void success(String text) {
            out.style(Style.Normal).format('\t%-64s', text)
            out.withStyle(Style.Identifier).text('[  OK  ]')
            out.println()
        }

        public static void warn(String text) {
            out.style(Style.UserInput).format('\t%s', text).println()
        }

        public static void footer(String text) {
            out.style(Style.UserInput).format('\t%s', text).println()
        }
    }
}
