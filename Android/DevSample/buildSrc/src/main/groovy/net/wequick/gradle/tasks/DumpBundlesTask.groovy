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
package net.wequick.gradle.tasks

import com.android.build.gradle.BaseExtension
import net.wequick.gradle.RootExtension
import net.wequick.gradle.internal.Version
import net.wequick.gradle.util.AndroidPluginUtils
import net.wequick.gradle.util.DependenciesUtils
import net.wequick.gradle.util.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

import java.text.DecimalFormat

class DumpBundlesTask extends DefaultTask {

    @TaskAction
    def run() {

        RootExtension small = project.extensions.findByType(RootExtension.class)
        if (small == null) return

        small.assignPackageIds()

        println()
        println '### Compile-time'
        println ''
        println '```'

        // gradle-small
        print String.format('%24s', 'gradle-small plugin : ')
        def pluginVersion = Version.SMALL_GRADLE_PLUGIN_VERSION
        def pluginProperties = project.file('buildSrc/src/main/resources/META-INF/gradle-plugins/net.wequick.small.properties')
        if (pluginProperties.exists()) {
            println "$pluginVersion (project)"
        } else {
            def config = project.buildscript.configurations['classpath']
            def module = config.resolvedConfiguration.firstLevelModuleDependencies.find {
                it.moduleGroup == 'net.wequick.tools.build' && it.moduleName == 'gradle-small'
            }
            File pluginDir = module.moduleArtifacts.first().file.parentFile
            if (pluginDir.name == module.moduleVersion) {
                // local maven:
                // ~/.m2/repository/net/wequick/tools/build/gradle-small/1.0.0-beta9/gradle-small-1.0.0-beta9.jar
                println "$module.moduleVersion (local maven)"
            } else {
                // remote maven:
                // ~/.gradle/caches/modules-2/files-2.1/net.wequick.tools.build/gradle-small/1.0.0-beta9/8db229545a888ab25e210a9e574c0261e6a7a52d/gradle-small-1.0.0-beta9.jar
                println "$module.moduleVersion (maven)"
            }
        }

        // small
        def dependencies = DependenciesUtils.getAllCompileDependencies(small.hostProject)
        print String.format('%24s', 'small aar : ')
        if (small.smallProject != null) {
            def prop = new Properties()
            prop.load(small.smallProject.file('gradle.properties').newDataInputStream())
            println "${prop.getProperty('version')} (project)"
        } else {
            def module = dependencies.find {
                it.moduleGroup == 'net.wequick.small' && it.moduleName == 'small'
            }
            if (module == null) {
                println 'unspecified'
            } else {
                def aarVersion = module.moduleVersion
                File pluginDir = module.moduleArtifacts.first().file.parentFile
                if (pluginDir.name == module.moduleVersion) {
                    // local maven:
                    // ~/.m2/repository/net/wequick/tools/build/gradle-small/1.0.0-beta9/gradle-small-1.0.0-beta9.jar
                    println "$aarVersion (local maven)"
                } else {
                    // remote maven:
                    // ~/.gradle/caches/modules-2/files-2.1/net.wequick.tools.build/gradle-small/1.0.0-beta9/8db229545a888ab25e210a9e574c0261e6a7a52d/gradle-small-1.0.0-beta9.jar
                    println "$aarVersion (maven)"
                }
            }
        }

        // small databinding
        if (small.hostProject.android.dataBinding.enabled) {
            print String.format('%24s', 'small binding aar : ')
            if (small.smallBindingProject != null) {
                def prop = new Properties()
                prop.load(small.smallBindingProject.file('gradle.properties').newDataInputStream())
                println "${prop.getProperty('version')} (project)"
            } else {
                def module = dependencies.find {
                    it.moduleGroup == 'small.support' && it.moduleName == 'databinding'
                }
                if (module == null) {
                    println 'unspecified'
                } else {
                    def aarVersion = module.moduleVersion
                    File pluginDir = module.moduleArtifacts.first().file.parentFile
                    if (pluginDir.name == module.moduleVersion) {
                        // local maven:
                        // ~/.m2/repository/net/wequick/tools/build/gradle-small/1.0.0-beta9/gradle-small-1.0.0-beta9.jar
                        println "$aarVersion (local maven)"
                    } else {
                        // remote maven:
                        // ~/.gradle/caches/modules-2/files-2.1/net.wequick.tools.build/gradle-small/1.0.0-beta9/8db229545a888ab25e210a9e574c0261e6a7a52d/gradle-small-1.0.0-beta9.jar
                        println "$aarVersion (maven)"
                    }
                }
            }
        }

        // gradle version
        print String.format('%24s', 'gradle core : ')
        println project.gradle.gradleVersion

        // android gradle plugin
        def androidGradlePlugin = project.buildscript.configurations.classpath
                .resolvedConfiguration.firstLevelModuleDependencies.find {
            it.moduleGroup == 'com.android.tools.build' && it.moduleName == 'gradle'
        }
        if (androidGradlePlugin != null)  {
            print String.format('%24s', 'android plugin : ')
            println androidGradlePlugin.moduleVersion
        }

        // OS
        print String.format('%24s', 'OS : ')
        println "${System.properties['os.name']} ${System.properties['os.version']} (${System.properties['os.arch']})"

        println '```'
        println()

        println '### Bundles'
        println()

        // modules
        def rows = []
        def fileTitle = 'file'
        File out = small.outputBundleDir
        boolean isApk = small.buildToAssets
        if (!isApk) {
            out = new File(out, 'armeabi')
            if (!out.exists()) {
                out = new File(out, 'x86')
            }
            if (out.exists()) {
                fileTitle += "($out.name)"
            }
        }
        rows.add(['type', 'name', 'PP', 'sdk', 'aapt', 'support', fileTitle, 'size'])
        def vs = getVersions(small.hostProject)
        rows.add(['host', small.bundleHandler.hostModuleName, '', vs.sdk, vs.aapt, vs.support, '', ''])
        small.stubProjects.each { p ->
            vs = getVersions(p)
            rows.add(['stub', p.name, '', vs.sdk, vs.aapt, vs.support, '', ''])
        }
        small.libProjects.each { p ->
            vs = getVersions(p)
            def pp = small.getPackageIdStr(p)
            def f = getOutputFile(p, out, isApk)
            rows.add(['lib', p.name, pp, vs.sdk, vs.aapt, vs.support, f.name, f.size])
        }
        small.appProjects.each { p ->
            vs = getVersions(p)
            def pp = small.getPackageIdStr(p)
            def f = getOutputFile(p, out, isApk)
            rows.add(['app', p.name, pp, vs.sdk, vs.aapt, vs.support, f.name, f.size])
        }
        small.assetProjects.each { p ->
            vs = getVersions(p)
            def type = p.name.substring(0, p.name.indexOf('.'))
            def f = getOutputFile(p, out, isApk)
            rows.add([type, p.name, '', vs.sdk, vs.aapt, vs.support, f.name, f.size])
        }

        printRows(rows)
        println()
    }

    protected static def getVersions(Project p) {
        BaseExtension android = AndroidPluginUtils.getAndroid(p)
        if (android == null) return [:]

        def sdk = android.getCompileSdkVersion()
        if (sdk.startsWith('android-')) {
            sdk = sdk.substring(8) // bypass 'android-'
        }
        def dependencies = DependenciesUtils.getAllCompileDependencies(p)
        def supportLib = dependencies.find { d ->
            d.moduleGroup == 'com.android.support' && d.moduleName != 'multidex'
        }
        def supportVer = supportLib != null ? supportLib.moduleVersion : ''
        return [sdk: sdk,
                aapt: android.buildToolsVersion,
                support: supportVer]
    }

    protected static Map getOutputFile(Project prj, File out, boolean isApk) {
        def pkg = AndroidPluginUtils.getApplicationId(prj)
        File file
        String name
        if (isApk) {
            file = new File(out, "${pkg}.apk")
            name = '*.' + pkg.split('\\.').last() + '.apk'
        } else {
            name = "lib${pkg.replaceAll('\\.', '_')}.so"
            file = new File(out, name)
            name = '*_' + file.name.split('_').last()
        }

        if (!file.exists()) {
            return [name: '', size: '']
        }
        return [name: name, size: FileUtils.getFormatSize(file)]
    }

    private static void printRows(List rows) {
        def colLens = []
        int nCol = rows[0].size()
        for (int i = 0; i < nCol; i++) {
            colLens[i] = 4
        }

        def nRow = rows.size()
        for (int i = 0; i < nRow; i++) {
            def row = rows[i]
            nCol = row.size()
            for (int j = 0; j < nCol; j++) {
                def col = row[j] ?: ''
                colLens[j] = Math.max(colLens[j], col.length() + 2)
            }
        }

        for (int i = 0; i < nRow; i++) {
            def row = rows[i]
            nCol = row.size()
            def s = ''
            def split = ''
            for (int j = 0; j < nCol; j++) {
                int maxLen = colLens[j]
                String col = row[j] ?: ''
                int len = col.length()

                if (i == 0) {
                    // Center align for title
                    int lp = (maxLen - len) / 2 // left padding
                    int rp = maxLen - lp - len // right padding
                    s += '|'
                    for (int k = 0; k < lp; k++) s += ' '
                    s += col
                    for (int k = 0; k < rp; k++) s += ' '

                    // Add split line
                    split += '|'
                    for (int k = 0; k < maxLen; k++) split += '-'
                } else {
                    // Left align for content
                    int rp = maxLen - 1 - len // right padding
                    s += '| ' + col
                    for (int k = 0; k < rp; k++) s += ' '
                }
            }
            println s + '|'
            if (i == 0) {
                println split + '|'
            }
        }
    }
}