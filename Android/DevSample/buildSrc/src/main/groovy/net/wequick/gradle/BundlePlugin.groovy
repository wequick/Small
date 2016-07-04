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

import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.dsl.BuildType
import org.gradle.api.Project

/**
 * Gradle plugin class to package 'application' or 'library' project as a .so plugin.
 */
abstract class BundlePlugin extends AndroidPlugin {

    protected String mP // the executing gradle project name
    protected String mT // the executing gradle task name

    void apply(Project project) {
        super.apply(project)
    }

    @Override
    protected Class<? extends BaseExtension> getExtensionClass() {
        return BundleExtension.class
    }

    @Override
    protected BundleExtension getSmall() {
        return project.small
    }

    @Override
    protected void configureProject() {
        super.configureProject()

        // Parse gradle task
        def sp = project.gradle.startParameter
        def t = sp.taskNames[0]
        if (t != null) {
            def p = sp.projectDir
            def pn = null
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
            mP = pn
            mT = t
        }

        project.afterEvaluate {
            if (isBuildingRelease()) {
                BuildType buildType = android.buildTypes.find { it.name == 'release' }

                Project hostProject = rootSmall.hostProject
                com.android.build.gradle.BaseExtension hostAndroid = hostProject.android
                def hostDebugBuildType = hostAndroid.buildTypes.find { it.name == 'debug' }
                def hostReleaseBuildType = hostAndroid.buildTypes.find { it.name == 'release' }

                // Copy host signing configs
                def sc = hostReleaseBuildType.signingConfig ?: hostDebugBuildType.signingConfig
                buildType.setSigningConfig(sc)

                // Enable minify if the command line defined `-Dbundle.minify=true'
                def minify = System.properties['bundle.minify']
                if (minify != null) {
                    buildType.setMinifyEnabled(minify == 'true')
                }
            }
        }
    }

    @Override
    protected void configureReleaseVariant(BaseVariant variant) {
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

    /** Check if is building self in release mode */
    protected boolean isBuildingRelease() {
        if (mT == null) return false // no tasks

        if (mP == null) {
            // gradlew buildLibs | buildBundles
            return small.type == PluginType.Library ?
                    (mT == 'buildLib') : (mT == 'buildBundle')
        } else {
            return (mP == project.name && (mT == 'assembleRelease' || mT == 'aR'))
        }
    }

    /** Check if is building any libs (lib.*) */
    protected boolean isBuildingLibs() {
        if (mT == null) return false // no tasks

        if (mP == null) {
            // ./gradlew buildLib
            return (mT == 'buildLib')
        } else {
            // ./gradlew -p lib.xx aR | ./gradlew :lib.xx:aR
            return (mP.startsWith('lib.') && (mT == 'assembleRelease' || mT == 'aR'))
        }
    }

    /** Check if is building any apps (app.*) */
    protected boolean isBuildingApps() {
        if (mT == null) return false // no tasks

        if (mP == null) {
            // ./gradlew buildBundle
            return (mT == 'buildBundle')
        } else {
            // ./gradlew -p app.xx aR | ./gradlew :app.xx:aR
            return (mP.startsWith('app.') && (mT == 'assembleRelease' || mT == 'aR'))
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
