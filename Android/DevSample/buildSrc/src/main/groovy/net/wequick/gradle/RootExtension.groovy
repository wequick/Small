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

import net.wequick.gradle.aapt.SymbolTable
import net.wequick.gradle.dsl.AndroidConfig
import net.wequick.gradle.dsl.BundleHandler
import net.wequick.gradle.dsl.FilterConfig
import net.wequick.gradle.util.DependenciesUtils
import net.wequick.gradle.util.Module
import org.gradle.api.Project

class RootExtension extends BaseExtension {

    boolean isBuildingLib
    boolean isBuildingBundle
    boolean isRunningHost

    private Map<String, List<Module>> mCompiledAarDependencies
    private Map<String, List<Module>> mProvidedAarDependencies
    private Map<String, List<String>> mProvidedJarDependencies
    private Map<String, List<Project>> mProjectDependencies
    private SymbolTable mUnusedSymbols
    private File mExplodedAarDir
    private File mStrippedAarDir

    Project smallProject
    Project smallBindingProject
    Project hostProject
    List<Project> stubProjects
    List<Project> libProjects
    List<Project> appProjects
    List<Project> assetProjects

    Map<String, Integer> packageIds
    Map<String, String> packageIdStrs

    // Settings
    File outputBundleDir
    boolean buildToAssets
    boolean strictSplitResources

    RootExtension(Project project) {
        super(project)

        def sp = project.gradle.startParameter
        def p = sp.projectDir
        def t = sp.taskNames[0]
        if (sp.systemPropertiesArgs['small'] == 'true') {
            isBuildingBundle = true
        } else if (p == null || p == project.rootProject.projectDir) {
            // gradlew buildLib | buildBundle
            if (t == null) {

            } else if (t == 'buildLib') {
                isBuildingLib = true
            } else if (t == 'buildBundle') {
                isBuildingBundle = true
            } else if (t.startsWith(':app:assemble') && t.endsWith('Debug')) {
                isBuildingBundle = true
                isRunningHost = true
            }
        } else if (t.contains('Release')) {
            isBuildingBundle = true
        }

        project.afterEvaluate {
            sortProjects()
        }
    }

    void sortProjects() {
        def hostModuleName = bundleHandler.hostModuleName ?: 'app'
        stubProjects = []
        libProjects = []
        appProjects = []
        assetProjects = []

        project.subprojects.each {

            String moduleName = it.name

            /* Builtin modules */
            /*-----------------*/
            if (moduleName == 'small') {
                smallProject = it
                return
            }

            if (moduleName == 'small-databinding') {
                smallBindingProject = it
                return
            }

            if (moduleName.startsWith('app+')) {
                stubProjects.add(it)
                return
            }

            /* Custom modules */
            /*----------------*/
            if (moduleName == hostModuleName) {
                hostProject = it
                return
            }

            String type = bundleHandler.getType(moduleName)

            /* Inferred modules */
            /*------------------*/
            if (type == null) {
                def idx = moduleName.indexOf('.')
                if (idx < 0) return

                char c = moduleName.charAt(idx + 1)
                if (c.isDigit()) {
                    // This might be a local aar module composed by name and version
                    // as 'feature-1.1.0'
                    return
                }

                type = moduleName.substring(0, idx)
            }

            switch (type) {
                case 'stub':
                    stubProjects.add(it)
                    break
                case 'lib':
                    libProjects.add(it)
                    break
                case 'app':
                    appProjects.add(it)
                    break
                default: // Default to Asset
                    assetProjects.add(it)
                    break
            }
        }
    }

    Map<String, List<Module>> getProvidedAarDependencies() {
        if (!isBuildingBundle) return null

        if (mProvidedAarDependencies == null) {
            File depDir = new File(project.buildDir, 'small/dependencies/provided-aar')
            def map = new HashMap()
            project.subprojects.each { sub ->
                def modules = []
                def d = new File(depDir, "${sub.name}.txt")
                if (d.exists()) {
                    d.eachLine {
                        Module m = Module.fromFullName(it)
                        if (m != null) {
                            modules.add m
                        }
                    }
                }
                map.put(sub.name, modules)
            }
            mProvidedAarDependencies = map
        }
        return mProvidedAarDependencies
    }

    Map<String, List<Module>> getCompiledAarDependencies() {
        if (!isBuildingBundle) return null

        if (mCompiledAarDependencies == null) {
            File depDir = new File(project.buildDir, 'small/dependencies/compiled-aar')
            def map = new HashMap()
            project.subprojects.each { sub ->
                def modules = []
                def d = new File(depDir, "${sub.name}.txt")
                if (d.exists()) {
                    d.eachLine {
                        Module m = Module.fromFullName(it)
                        if (m != null) {
                            modules.add m
                        }
                    }
                }
                map.put(sub.name, modules)
            }
            mCompiledAarDependencies = map
        }
        return mCompiledAarDependencies
    }

    Map<String, List<Project>> getProjectDependencies() {
        def aars = getProvidedAarDependencies()
        if (aars == null) return null

        if (mProjectDependencies == null) {
            def projects = new HashMap()
            aars.each { name, modules ->
                def libs = []
                libs.addAll stubProjects
                modules.each { module ->
                    def lib = project.findProject(module.name)
                    if (lib != null && libProjects.contains(lib)) {
                        libs.add(lib)
                    }
                }
                projects[name] = libs
            }
            mProjectDependencies = projects
        }
        return mProjectDependencies
    }

    Map<String, List<String>> getProvidedJarDependencies() {
        if (!isBuildingBundle) return null

        if (mProvidedJarDependencies == null) {
            File depDir = new File(project.buildDir, 'small/dependencies/provided-jar')
            def map = new HashMap()
            project.subprojects.each { sub ->
                def paths = []
                def d = new File(depDir, "${sub.name}.txt")
                if (d.exists()) {
                    d.eachLine {
                        paths.add it
                    }
                }
                map.put(sub.name, paths)
            }
            mProvidedJarDependencies = map
        }
        return mProvidedJarDependencies
    }

    File getExplodedAarDir() {
        if (mExplodedAarDir == null) {
            mExplodedAarDir = new File(project.gradle.gradleUserHomeDir, 'small-caches/exploded-aar')
        }
        return mExplodedAarDir
    }

    File getStrippedAarDir() {
        if (mStrippedAarDir == null) {
            mStrippedAarDir = new File(project.gradle.gradleUserHomeDir, 'small-caches/stripped-aar')
        }
        return mStrippedAarDir
    }

    File getOutputBundleDir() {
        if (outputBundleDir == null) {
            outputBundleDir = new File(hostProject.projectDir, 'smallLibs')
        }
        return outputBundleDir
    }

    String getOutputBundlePath() {
        return "$hostProject.projectDir.name/smallLibs"
    }

    List<Project> getCommonModules() {
        def projects = []
        if (smallProject) projects.add smallProject
        projects.addAll stubProjects
        return projects
    }

    List<Project> getCommonProjects() {
        def projects = []
        if (smallProject) projects.add smallProject
        projects.addAll stubProjects
        projects.addAll libProjects
        return projects
    }

    List<Project> getAarProjects() {
        def projects = []
        projects.addAll stubProjects
        projects.addAll libProjects
        return projects
    }

    List<Project> getApkProjects() {
        def projects = []
        projects.add hostProject
        projects.addAll libProjects
        projects.addAll appProjects
        return projects
    }

    List<Project> getBaseProjects() {
        def projects = []
        projects.add hostProject
        projects.addAll stubProjects
        projects.addAll libProjects
        return projects
    }

    List<Project> getBaseSymbolProjects() {
        def projects = []
        projects.add hostProject
        projects.addAll libProjects
        return projects
    }

    List<Project> getBundleProjects() {
        def projects = []
        projects.addAll libProjects
        projects.addAll appProjects
        return projects
    }

    List<Project> getLibDependencies(Project bundle) {
        def libs = []
        def deps = DependenciesUtils.getAllCompileDependencies(bundle)
        deps.each { d ->
            def lib = project.findProject(d.moduleName)
            if (lib != null) {
                libs.add(lib)
            }
        }
        return libs
    }

    SymbolTable getSymbols(Project lib) {
        final String symbolsExtensionName = 'smallSymbols'
        def symbols = lib.extensions.getByName(symbolsExtensionName)
        if (symbols != null) return symbols


    }

    /*
        Android Configurations
     */
    AndroidConfig mAndroid
    AndroidConfig getAndroidConfig() {
        if (mAndroid == null) {
            mAndroid = new AndroidConfig()
        }
        return mAndroid
    }

    AndroidConfig android(@DelegatesTo(value = AndroidConfig) Closure closure) {
        project.configure(getAndroidConfig(), closure)
    }

    /*
        Bundle configurations
     */
    BundleHandler mBundleHandler
    BundleHandler getBundleHandler() {
        if (mBundleHandler == null) {
            mBundleHandler = new BundleHandler()
        }
        return mBundleHandler
    }

    BundleHandler bundles(@DelegatesTo(value = BundleHandler) Closure closure) {
        project.configure(getBundleHandler(), closure)
    }

    /**
     * Filter Configurations
     */
    FilterConfig mFilter
    FilterConfig getFilterConfig() {
        if (mFilter == null) {
            mFilter = new FilterConfig()
            mFilter.project = project
        }
        return mFilter
    }

    FilterConfig filter(@DelegatesTo(value = FilterConfig) Closure closure) {
        project.configure(getFilterConfig(), closure)
    }

    /*
        Package ID Generator
     */
    void assignPackageIds() {
        packageIds = [:]
        packageIdStrs = [:]
        def bundleProjects = getBundleProjects()
        bundleProjects.each { bundle ->
            if (bundle.hasProperty('smallPackageId')) return

            def ppStr = null
            def pp = 0
            if (bundle.hasProperty('packageId')) {
                def id = bundle.packageId
                if (id instanceof String) {
                    ppStr = id
                    pp = Integer.parseInt(ppStr, 16)
                } else if (id instanceof Integer) {
                    pp = id
                }
            }

            if (pp == 0) {
                pp = genRandomPackageId(bundle.name)
            }

            // Check if the new package id has been used
            packageIds.each { name, id ->
                if (id == pp) {
                    throw new Exception("Duplicate package id 0x${String.format('%02x', pp)} " +
                            "with $name and ${bundle.name}!\nPlease redefine one of them " +
                            "in build.gradle (e.g. 'ext.packageId=0x7e') " +
                            "or gradle.properties (e.g. 'packageId=7e').")
                }
            }
            packageIds.put(bundle.name, pp)

            if (ppStr == null) {
                ppStr = String.format('%02x', pp)
            }
            packageIdStrs.put(bundle.name, ppStr)
        }
    }

    int getPackageId(Project project) {
        if (packageIds == null) return 0x7f

        int id = packageIds.get(project.name)
        return id == 0 ? 0x7f : id
    }

    String getPackageIdStr(Project project) {
        if (packageIdStrs == null) return null

        def idStr = packageIdStrs.get(project.name)
        return idStr == null ? '7f' : idStr
    }

    SymbolTable getUnusedSymbols() {
        if (mUnusedSymbols != null) return mUnusedSymbols

        def unusedEntries = ['mipmap/ic_launcher',
                             'mipmap/ic_launcher_round',
                             'drawable/ic_launcher_background',
                             'drawable/ic_launcher_foreground',
                             'string/app_name']
        mUnusedSymbols = SymbolTable.fromEntries(unusedEntries)
        return mUnusedSymbols
    }

    File getBundleOutput(String pkg) {
        String name
        if (buildToAssets) {
            name = "${pkg}.apk"
        } else {
            name = "lib${pkg.replace('.', '_')}.so"
        }
        return new File(getOutputBundleDir(), name)
    }

    /**
     * Generate a random package id in range [0x03, 0x7e] by bundle's name.
     * [0x00, 0x02] reserved for android system resources.
     * [0x03, 0x0f] reserved for the fucking crazy manufacturers.
     */
    protected static int genRandomPackageId(String bundleName) {
        int minPP = 0x10
        int maxPP = 0x7e
        int maxHash = 0xffff
        int d = maxPP - minPP
        int hash = bundleName.hashCode() & maxHash
        int pp = (hash * d / maxHash) + minPP
        if (sPackageIdBlackList.contains(pp)) {
            pp = (pp + maxPP) >> 1
        }
        return pp
    }

    private static sPackageIdBlackList = [
            0x03 // HTC
            ,0x10 // Xiao Mi
    ] as ArrayList<Integer>
}