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

/**
 * Gradle plugin class to package 'application' or 'library' project as a .so plugin.
 */
abstract class BundlePlugin extends AndroidPlugin {

    void apply(Project project) {
        super.apply(project)
    }

    @Override
    protected Class<? extends BaseExtension> getExtensionClass() {
        return BundleExtension.class
    }

    @Override
    protected BundleExtension getSmall() {
        return super.getSmall()
    }

    @Override
    protected void configureProject() {
        super.configureProject()
        project.afterEvaluate {
            // Copy host signing configs
            if (isBuildingRelease()) {
                Project hostProject = project.rootProject.findProject('app')
                hostProject.android.buildTypes.each {
                    def bt = project.android.buildTypes[it.name]
                    bt.signingConfig = it.signingConfig
                }
            }
        }
    }

    @Override
    protected void configureReleaseVariant(variant) {
        super.configureReleaseVariant(variant)

        // Set output file (*.so)
        def outputFile = getOutputFile(variant)
        BundleExtension ext = small
        ext.outputFile = outputFile
        variant.outputs.each { out ->
            out.outputFile = outputFile
        }
    }

    @Override
    protected void createTask() {
        super.createTask()

        project.task('cleanBundle', dependsOn: 'clean')
        project.task('buildBundle', dependsOn: 'assembleRelease')
    }

    @Override
    protected String getSmallCompileType() {
        return 'debugCompile'
    }

    /** Check if is building a module in release mode */
    protected boolean isBuildingRelease() {
        def sp = project.gradle.startParameter
        def p = sp.projectDir
        def t = sp.taskNames[0]
        def pn = null

        if (t == null) { // Nothing to do
            return false
        }

        if (p == null) {
            if (t.startsWith(':')) {
                // gradlew :app.main:assembleRelease
                def tArr = t.split(':')
                if (tArr.length == 3) { // ['', 'app.main', 'assembleRelease']
                    pn = tArr[1]
                    t = tArr[2]
                }
            }
        } else if (p != project.rootProject.projectDir) {
            // gradlew -p [project.name] assembleRelease
            pn = p.name
        }

        if (pn == null) {
            // gradlew buildLibs | buildBundles
            return small.type == PluginType.Library ?
                    (t == 'buildLib') : (t == 'buildBundle')
        } else {
            return (pn == project.name && (t == 'assembleRelease' || t == 'aR'))
        }
    }

    /** Check if is building any libs */
    protected boolean isBuildingLibs() {
        def sp = project.gradle.startParameter
        def p = sp.projectDir
        def t = sp.taskNames[0]
        if (p == null || p == project.rootProject.projectDir) {
            // ./gradlew buildLibs
            return (t == 'buildLib')
        } else {
            // ./gradlew -p [lib.*] [task]
            return (p.name.startsWith('lib.') && (t == 'assembleRelease' || t == 'aR'))
        }
    }

    protected def getOutputFile(variant) {
        def appId = variant.applicationId
        if (appId == null) return null

        def arch = System.properties['bundle.arch'] // Get from command line (-Dbundle.arch=xx)
        if (arch == null) {
            // Read from local.properties (bundle.arch=xx)
            def prop = new Properties()
            prop.load(project.rootProject.file('local.properties').newDataInputStream())
            arch = prop.getProperty('bundle.arch')
            if (arch == null) arch = 'armeabi' // Default
        }
        def so = "lib${appId.replaceAll('\\.', '_')}.so"
        RootExtension rootExt = project.rootProject.small
        def outputDir = rootExt.outputBundleDir
        return new File(outputDir, "$arch/$so")
    }
}
