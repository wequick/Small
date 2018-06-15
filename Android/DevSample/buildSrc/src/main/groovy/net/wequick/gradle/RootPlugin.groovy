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
import com.android.build.gradle.tasks.MergeManifests
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.sdklib.BuildToolInfo
import groovy.io.FileType
import groovy.xml.Namespace
import net.wequick.gradle.aapt.Aapt
import net.wequick.gradle.aapt.SymbolTable
import net.wequick.gradle.compat.databinding.DataBindingCompat
import net.wequick.gradle.dsl.AndroidConfig
import net.wequick.gradle.internal.Version
import net.wequick.gradle.migrate.MigrateAGP3Task
import net.wequick.gradle.tasks.DumpBundlesTask
import net.wequick.gradle.tasks.PrepareAarTask
import net.wequick.gradle.tasks.ResolveDependenciesTask
import net.wequick.gradle.util.AndroidPluginUtils
import net.wequick.gradle.util.DependenciesUtils
import net.wequick.gradle.util.FileUtils
import net.wequick.gradle.util.JNIUtils
import net.wequick.gradle.util.Log
import net.wequick.gradle.util.Module
import net.wequick.gradle.util.ModuleUtils
import net.wequick.gradle.util.VariantUtils
import net.wequick.gradle.util.ZipUtils
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.util.VersionNumber

class RootPlugin extends BasePlugin<RootExtension> {

    @Override
    protected Class<RootExtension> getExtensionClass() {
        return RootExtension.class
    }

    @Override
    protected void configureProject() {
        super.configureProject()

        project.afterEvaluate {

            migrateAGP3()

            addCommonDependencies()

            dynamicLibOrAppPlugin()

            syncDependencyVersions()

//            resolveManifestConflict()

            supportDataBinding()

            if (!small.isBuildingBundle) {
                createPrepareTasks()
                return
            }

            small.assignPackageIds()

            syncSigningConfig()

            // Strip the aar or jar dependencies
            stripAllDependencies()

            // Split the hex arsc intermediates
            splitAllIntermediates()

            // Add tasks to copy generated APKs
            addCopyGeneratedApkTasks()

            // Assemble dependencies
            small.bundleProjects.each {
                it.task('buildBundle', dependsOn: 'assembleRelease')
            }

            // Semi-instant run
            if (small.isRunningHost) {
                // Before running host, build all the bundles first.
                small.hostProject.afterEvaluate {
                    AndroidPluginUtils.getVariants(it).all { variant ->
                        small.bundleProjects.each { bundle ->
                            it.mergeDebugAssets.dependsOn "$bundle.path:assembleRelease"
                        }
                    }
                }

//                small.bundleProjects.each { bundle ->
//                    bundle.afterEvaluate {
//                        AndroidPluginUtils.getVariants(bundle).all { variant ->
//                            variant.preBuild.dependsOn "$bundle.path:buildLib"
//                        }
//                    }
//                }
            }
        }
    }

    void migrateAGP3() {
        project.subprojects.each {
            File script = it.buildFile
            def enterDependencies = false
            def leaveDependencies = false
            def migrated = false
            def text = ''
            script.eachLine { line ->
                if (leaveDependencies) {
                    text += line + System.lineSeparator()
                    return
                }

                if (enterDependencies) {
                    if (line.startsWith('}')) {
                        leaveDependencies = true
                        text += line + System.lineSeparator()
                        return
                    }

                    def bakLine = line
                    line = line.replace('compile', 'implementation')
                    line = line.replace('testCompile', 'testImplementation')
                    line = line.replace('provided', 'compileOnly')
                    if (bakLine.length() != line.length()) {
                        migrated = true
                    }
                } else {
                    if (line.startsWith('dependencies {')) {
                        enterDependencies = true
                    }
                }

                text += line + System.lineSeparator()
            }

            if (migrated) {
                script.write(text)
                Log.success("[$it.name] Migrate to android gradle plugin 3.0")
            }
        }
    }

    void syncDependencyVersions() {
        project.subprojects.each { sub ->
            sub.afterEvaluate {
                configVersions(sub, small.androidConfig)
            }
        }
    }

    static void configVersions(Project p, AndroidConfig base) {
        def android = AndroidPluginUtils.getAndroid(p)
        if (android == null) return

        if (base.compileSdkVersion != 0) {
            android.compileSdkVersion = base.compileSdkVersion
        }
        if (base.buildToolsVersion != null) {
            android.buildToolsVersion = base.buildToolsVersion
        }
        if (base.supportVersion != null) {
            p.configurations.all { cfg ->
                cfg.resolutionStrategy {
                    eachDependency { details ->
                        def module = details.requested
                        if (module.group == 'com.android.support' && module.name != 'multidex') {
                            details.useVersion base.supportVersion
                        }
                    }
                }
            }
        }
    }

    void dynamicLibOrAppPlugin() {
        small.libProjects.each { lib ->
            lib.beforeEvaluate {
                if (small.isBuildingBundle) {
                    lib.extensions.add('smallApp', true)
                }
                def script = lib.buildFile.text
                def lines = script.split(System.lineSeparator())
                def firstLine = lines[0]
                if (!firstLine.startsWith('apply plugin: "com.android.$')) {
                    lines[0] = 'apply plugin: "com.android.${hasProperty(\'smallApp\')?\'application\':\'library\'}"'
                    script = lines.join(System.lineSeparator())
                    lib.buildFile.write(script)
                }
            }
        }
    }

    void resolveManifestConflict() {
//        small.bundleProjects.each { bundle ->
//            AndroidPluginUtils.getVariants(bundle).all { variant ->
//                if (variant.buildType.name == 'release') return
//
//                variant.outputs.all { out ->
//                    out.processManifest.get
//                    hookProcessDebugManifest(out.processManifest)
//                }
//            }
//        }
    }

    void supportDataBinding() {
        small.hostProject.afterEvaluate { host ->
            DataBindingCompat.compileSmallDataBindingJar(host)

            AndroidPluginUtils.getVariants(host).all { variant ->
                DataBindingCompat.generateBaseDataBinderMapperClass(host,
                        variant.javaCompile,
                        variant.dirName)
            }
        }

        small.bundleProjects.each { bundle ->
            bundle.afterEvaluate {
                com.android.build.gradle.BaseExtension android = AndroidPluginUtils.getAndroid(bundle)
                if (!small.isBuildingBundle) return

                DataBindingCompat.provideSmallDataBindingJar(bundle) // FIXME: strip before

                if (!android.dataBinding.enabled) return

                AndroidPluginUtils.getVariants(bundle).all { variant ->
                    DataBindingCompat.generateBundleDataBinderMapperClass(bundle, variant)
                }
            }
        }
    }

//    void hookProcessDebugManifest()

    void syncSigningConfig() {
        def hostProject = small.hostProject
        small.bundleProjects.each { bundle ->
            bundle.afterEvaluate {
                def hostAndroid = AndroidPluginUtils.getAndroid(hostProject)
                def hostDebugBuildType = hostAndroid.buildTypes.find { it.name == 'debug' }
                def hostReleaseBuildType = hostAndroid.buildTypes.find { it.name == 'release' }
                def hostSigningConfig = hostReleaseBuildType.signingConfig ?: hostDebugBuildType.signingConfig

                def android = AndroidPluginUtils.getAndroid(bundle)
                def buildType = android.buildTypes.find { it.name == 'release' }

                // Copy host signing configs
                buildType.setSigningConfig(hostSigningConfig)

                // Enable minify if the command line defined `-Dbundle.minify=true'
                def minify = System.properties['bundle.minify']
                if (minify != null) {
                    buildType.setMinifyEnabled(minify == 'true')
                }
            }
        }
    }

    @Override
    protected void createTask() {
        super.createTask()

        project.task('small', type: DumpBundlesTask)
        project.task('cleanLib', type: Delete) {
            delete new File(project.buildDir, 'small')
        }
        project.task('cleanSmallCache', type: Delete) {
            delete small.explodedAarDir
            delete small.strippedAarDir
        }
        project.task('buildLib', type: ResolveDependenciesTask)
        project.task('buildBundle')

        project.task('migrate3', type: MigrateAGP3Task)
    }

    void addCommonDependencies() {
        def cfg = (small.isBuildingBundle || small.isBuildingLib) ? 'default' : ''

        def apps = [] as List<Project>
        apps.add small.hostProject
        apps.addAll small.libProjects
        apps.addAll small.appProjects

        if (small.smallProject) {
            apps.each { app ->
                app.afterEvaluate {
                    DependenciesUtils.compile(app,
                            app.dependencies.project(path: ':small', configuration: cfg))
                }
            }
        } else {
            def smallAar = "net.wequick.small:small:$Version.SMALL_CORE_AAR_VERSION"
            apps.each {
                it.afterEvaluate {
                    DependenciesUtils.compile(it, smallAar)
                }
            }
        }

        apps.each { app ->
            app.afterEvaluate {
                small.stubProjects.each { stub ->
                    DependenciesUtils.compile(app,
                            app.dependencies.project(path: stub.path, configuration: cfg))
                }
            }
        }

        small.hostProject.apply plugin: HostPlugin
    }

    void createPrepareTasks() {
        small.baseProjects.each { base ->
            base.plugins.whenPluginAdded {
                if (it.class == com.android.build.gradle.LibraryPlugin.class
                        || it.class == com.android.build.gradle.AppPlugin.class) {
                    // After apply plugin: 'com.android.library'
                    base.afterEvaluate {
                        def variants = AndroidPluginUtils.getVariants(base)
                        variants.all { variant ->
                            if (variant.buildType.name != 'release') return

                            createPrepareTasks(base, variant)
                        }
                    }
                }
            }
        }
    }

    static void createPrepareTasks(Project base, BaseVariant variant) {
        def buildLib = base.task('buildLib')
        createPrepareResourceAarTasks(base, buildLib)
    }

    void addStrippedAarRepositories() {
        def bundlePath = small.strippedAarDir
        def modules = new HashSet<Module>()
        small.providedAarDependencies.each { name, aars ->
            modules.addAll(aars)
        }

        project.allprojects {
            repositories {
                modules.each { module ->
                    flatDir {
                        dirs new File(bundlePath, module.path)
                    }
                }
            }
        }

        small.aarProjects.each { lib ->
            DependenciesUtils.createAarProvider(lib)
        }

        if (small.smallProject) {
            DependenciesUtils.createJarProvider(small.smallProject)
        }
        if (small.smallBindingProject) {
            DependenciesUtils.createJarProvider(small.smallBindingProject)
        }
    }

    static void createPrepareResourceAarTasks(Project lib, Task prepareDependencies) {
        def rootProject = lib.rootProject
        def compileDependencies = DependenciesUtils.getAllCompileDependencies(lib)
        compileDependencies.each { d ->

            if (rootProject.findProject(d.moduleName) != null) {
                // Ignores dependent project
                return
            }

            def taskName = ModuleUtils.buildTaskName('smallPrepare', d.name, 'Library')
            if (rootProject.hasProperty(taskName)) {
                // Created
                return
            }

            // Collect *.aar outputs
            def aars = []
            d.moduleArtifacts.each { m ->
                if (m.type == 'aar') {
                    aars.add m.file
                }
            }
            if (aars.size() > 0) {
                // Create aar exploder task
                def preAar = rootProject.task(taskName, type: PrepareAarTask) {
                    it.module(d, aars)
                }
                prepareDependencies.dependsOn preAar
            }
        }
    }

    void stripAllDependencies() {
        addStrippedAarRepositories()

        small.bundleProjects.each { bundle ->
            bundle.afterEvaluate {
                stripDependencies(bundle)
            }
        }
    }

    void stripDependencies(Project app) {
        def compile = DependenciesUtils.getCompileConfiguration(app)
        if (compile == null) return

        // Remove all compiled dependencies
        def aars = small.providedAarDependencies[app.name]
        def libs = small.projectDependencies[app.name]
        aars.each {
            if (it.group == 'com.android.databinding') {
                // Cannot directly exclude the databinding library so fast
                // cause it will lead to the javac failed on process databinding???
                // Then just keep it here and strip it later in the `StripDataBindingTransform`.
                // FIXME: Figure out why
                return
            }

            compile.exclude(group: it.group, module: it.name)
        }

        // Provide all the `lib.*` aars
        libs.each { lib ->
            DependenciesUtils.provideAar(app, lib)
        }

        if (small.smallProject) {
            DependenciesUtils.provideJar(app, small.smallProject)
        }
        if (small.smallBindingProject) {
            DependenciesUtils.provideJar(app, small.smallBindingProject)
        }

        // Provide vendor jars in the path exploded by Small
        def bundlePath = small.explodedAarDir
        aars.each {
            def aarDir = new File(bundlePath, "$it.path")
            if (!aarDir.exists()) return

            aarDir.eachFileRecurse(FileType.FILES) {
                if (it.name.endsWith('.jar')) {
                    DependenciesUtils.provide(app, app.files(it))
                }
            }
        }

        // Provide vendor jars in the path exploded by Gradle
        def jars = small.providedJarDependencies[app.name]
        jars.each {
            DependenciesUtils.provide(app, app.files(it))
        }

        // Temporary-compile all the `small` vendor aars (res only)
        bundlePath = small.strippedAarDir
        aars.each {
            def aarDir = new File(bundlePath, "$it.path")
            if (!aarDir.exists()) return

            aarDir.eachFileRecurse(FileType.FILES) {
                if (it.name.endsWith('.aar')) {
                    def name = it.name.substring(0, it.name.length() - 4)
                    DependenciesUtils.compile(app, [group:'small', name: name, ext: 'aar'])
                }
            }
        }

//        if (!AndroidPluginUtils.isAndroidPlugin3(app)) {
//            app.tasks.whenObjectAdded { Task t ->
//                if (t.name.startsWith('prepareSmall') && t.name.endsWith('Library')) {
//                    t.doLast {
//                        println "???? $app.name - $t.path $t.name"
//                        String name = t.bundle.name
//                        name = name.substring(0, name.length() - 4)
//                        File explodedDir = new File(app.buildDir, "intermediates/exploded-aar/small/$name")
//                        if (!explodedDir.exists()) return
//
//                        File manifest = new File(explodedDir, 'AndroidManifest.xml')
//                        if (!manifest.exists()) {
//                            manifest.createNewFile()
//                        }
//                        manifest.write("<manifest package=\"small.abc.${app.name}\"></manifest>")
//                    }
//                }
//            }
//        }
    }

    void splitAllIntermediates() {
        small.bundleProjects.each { bundle ->
            bundle.afterEvaluate {
                def variants = AndroidPluginUtils.getVariants(bundle)
                variants.all { variant ->
                    if (variant.buildType.name != 'release') return

                    splitIntermediates(bundle, variant)
                }
            }
        }
    }

    void splitIntermediates(Project bundle, BaseVariant variant) {
        splitRJava(bundle, variant)
        splitApFile(bundle, variant)
    }

    void splitRJava(Project bundle, BaseVariant variant) {
        File miniRJavaFile
        String packagePath
        String packageId = small.getPackageIdStr(bundle)
        List<Project> libs = []
        libs.add small.hostProject
        libs.addAll small.projectDependencies[bundle.name]

        VariantUtils.allOutputs variant, { out ->
            ProcessAndroidResources aapt = out.processResources
            libs.each { lib ->
                lib.tasks.all { libAapt ->
                    if (libAapt.name.startsWith('process') && libAapt.name.endsWith('ReleaseResources')) {
                        aapt.dependsOn libAapt
                        if (!lib.hasProperty('smallAapt')) {
                            lib.extensions.add('smallAapt', libAapt)
                        } else {

                        }
                    }
                }
            }

            MergeManifests processManifest = out.processManifest
            processManifest.doLast {
                def manifestFile = AndroidPluginUtils.getManifestFile(processManifest)
                stripAndInjectManifest(manifestFile)
            }

            def packageName = aapt.packageForR
            def fullOutputDir = aapt.sourceOutputDir
            def miniOutputDir = new File(fullOutputDir.parentFile, 'small')
            packagePath = packageName.replace('.', '/')
            miniRJavaFile = new File(miniOutputDir, "$packagePath/R.java")

            def miniSymbolFile = new File(bundle.projectDir, 'public.txt')

            // Ensure generate R.txt
            def fullSymbolFile = AndroidPluginUtils.getSymbolFile(aapt)
            aapt.aaptOptions.additionalParameters '--output-text-symbols', fullSymbolFile.absolutePath

            // Ensure generate R.java
            def fullRJavaFile = new File(fullOutputDir, "$packagePath/R.java")
            if (!fullRJavaFile.parentFile.exists()) {
                fullRJavaFile.parentFile.mkdirs()
            }
//            aapt.aaptOptions.additionalParameters '-J', fullRJavaFile.parentFile.absolutePath

            // After the full-R.java generated, also make a split one
            aapt.outputs.files(aapt.project.files(miniRJavaFile))
            aapt.outputs.files(aapt.project.files(miniSymbolFile))
            aapt.doLast {
//                println "-- ${fullRJavaFile.exists()}, $fullRJavaFile"
//                assert false
                def fullSymbols = SymbolTable.fromFile(fullSymbolFile)
                def idSymbols = fullSymbols.copy().retainTypes(['id']) // with `R.id.*`

                // Prepare a stripped mini symbols
                def miniSymbols = new SymbolTable() // without `R.id.*`
                variant.mergeResources.sourceFolderInputs.each {
                    miniSymbols.merge(SymbolTable.fromResDir(it))
                }
                // Strip unused application-related resources
                miniSymbols.strip(small.unusedSymbols)

                def baseSymbols = new SymbolTable() // host -> lib.* -> app.x
                libs.each { lib ->
                    ProcessAndroidResources libAapt = lib.extensions.getByName('smallAapt')
                    def libSymbols = SymbolTable.fromFile(libAapt.textSymbolOutputFile)
                    baseSymbols.merge(libSymbols)
                }
                def baseIdSymbols = baseSymbols.copy().retainTypes(['id'])
                idSymbols.strip(baseIdSymbols)

                miniSymbols.merge(idSymbols)
                miniSymbols.assignEntryIds(packageId)

                // Store for later `splitApFile`
                aapt.extensions.add('fullSymbols', fullSymbols)
                aapt.extensions.add('miniSymbols', miniSymbols)

                // Generate a fat merged R.java
                baseSymbols.replaceIds(miniSymbols)
                baseSymbols.merge(miniSymbols)
                baseSymbols.generateRJavaToFile(fullRJavaFile, packageName)
                Log.success "[${project.name}] concat full R.java..."

                miniSymbols.mapStyleableReferences(fullSymbols)
                miniSymbols.generateRJavaToFile(miniRJavaFile, packageName)
                Log.success "[${project.name}] generate mini R.java..."

                miniSymbols.generateTextSymbolsToFile(miniSymbolFile)
                Log.success "[${project.name}] generate publix.txt..."
            }
        }

        def javac = variant.javaCompile
        javac.doFirst {
            // While incremental building,
            // the `R.class` should be revert to the full version temporary.
            def classDir = new File(javac.destinationDir, packagePath)
            def fullBackupRClassDir = new File(javac.destinationDir, '../__full_backup')
            if (fullBackupRClassDir.exists()) {
                fullBackupRClassDir.listFiles().each { classFile ->
                    File originalFile = new File(classDir, classFile.name)
                    if (originalFile.exists()) {
                        originalFile.delete()
                    }
                    org.apache.commons.io.FileUtils.moveFileToDirectory(
                            classFile, classDir, false)
                }
            }
        }
        javac.doLast {
            // After `javac' done, the full-R.class is no need any more,
            // replace it with the split one
            def fullRClassDir = new File(javac.destinationDir, packagePath)
            def fullBackupRClassDir = new File(javac.destinationDir, '../__full_backup')
            fullRClassDir.listFiles().each {
                if (it.name.startsWith('R.') || it.name.startsWith('R$')) {
                    org.apache.commons.io.FileUtils.moveFileToDirectory(
                            it, fullBackupRClassDir, true)
                }
            }

            bundle.ant.javac(srcdir: miniRJavaFile.parentFile,
                    source: javac.sourceCompatibility,
                    target: javac.targetCompatibility,
                    destdir: javac.destinationDir,
                    includeAntRuntime: false,
                    nowarn: true)
            Log.success "[${project.name}] compile mini R.class..."
        }
    }

    static void stripAndInjectManifest(File manifestFile) {
        def manifest = new XmlParser().parse(manifestFile)
        def android = new Namespace('http://schemas.android.com/apk/res/android', 'android')

        // Strip unused <uses-sdk> node
        manifest['uses-sdk'].each {
            manifest.remove(it)
        }
        // Strip unused <application android:*=..> attributes
        manifest.application.each { Node app ->
            def attrs = app.attributes()
            def unusedKeys = ['icon', 'roundIcon', 'label', 'allowBackup', 'supportsRtl']
            unusedKeys.each { key ->
                attrs.remove(android[key])
            }
        }

        // Inject the gradle-small version
        def attrs = manifest.attributes()
        attrs.put('smallBuild', Version.SMALL_GRADLE_PLUGIN_VERSION)

        // Inject the smallFlags
        attrs.put('smallFlags', 0)

        def pw = new XmlNodePrinter(manifestFile.newPrintWriter())
        pw.preserveWhitespace = true
        pw.print(manifest)
    }

    void splitApFile(Project bundle, BaseVariant variant) {
        VariantUtils.allOutputs variant, { out ->
            ProcessAndroidResources aapt = out.processResources
            aapt.doLast {
                def variantType = variant.buildType.name
                def apPath = "intermediates/res/$variantType/resources-${variantType}.ap_"
                def apFile = new File(aapt.project.buildDir, apPath)
                def fullSymbols = aapt.extensions.getByName('fullSymbols')
                def miniSymbols = aapt.extensions.getByName('miniSymbols')

                def aaptExe = aapt.buildTools.getPath(BuildToolInfo.PathId.AAPT)
                splitAndroidPackageFile(apFile, fullSymbols, miniSymbols,
                        bundle, aapt.buildToolsVersion, aaptExe)
            }
        }
    }

    void splitAndroidPackageFile(File apFile,
                                 SymbolTable fullSymbols,
                                 SymbolTable strippedSymbols,
                                 Project project,
                                 String buildToolsVersion,
                                 String aaptExe) {
        // Unpack the resources.ap_ file
        def unzipApDir = new File(apFile.parentFile, 'ap_')
        unzipApDir.delete()
        project.ant.unzip(src: apFile, dest: unzipApDir)

        // Collect the lib.* dependencies
        def libProjects = small.projectDependencies[project.name]

        // Collect the DynamicRefTable [pkgId => pkgName]
        def libRefTable = [:]
        libProjects.each {
            def libAapt = it.tasks.withType(ProcessAndroidResources.class).find {
                it.variantName.startsWith('release')
            }
            def pkgName = libAapt.packageForR
            def pkgId = small.packageIds[it.name]
            libRefTable.put(pkgId, pkgName)
        }

        def baseSymbols = new SymbolTable()
        File hostSymbolFile = small.hostProject.file('build/intermediates/symbols/release/R.txt')
        baseSymbols.merge(SymbolTable.fromFile(hostSymbolFile))
        libProjects.each {
            File publicSymbolFile = it.file('public.txt')
            baseSymbols.merge(SymbolTable.fromFile(publicSymbolFile))
        }

        // Collect id maps
        def idMaps = new HashMap<Integer, Integer>()
        def retainedTypes = []
        def retainedStyleables = null

        baseSymbols.mapIds(fullSymbols, idMaps, null)
        strippedSymbols.mapIds(fullSymbols, idMaps, retainedTypes)

        def rev = VersionNumber.parse(buildToolsVersion)
        int pp = small.getPackageId(project)
        int noResourcesFlag = 0
        def filteredResources = new HashSet()
        def updatedResources = new HashSet()
        def aapt = new Aapt(unzipApDir, null, null, rev)

        if (retainedTypes != null && retainedTypes.size() > 0) {
            aapt.filterResources(retainedTypes, filteredResources)
            Log.success "[${project.name}] strip library res files..."

            aapt.filterPackage(retainedTypes, pp, idMaps, libRefTable,
                    retainedStyleables, updatedResources)

            Log.success "[${project.name}] split asset package and reset package id..."
        } else {
            noResourcesFlag = 1
            if (aapt.deleteResourcesDir(filteredResources)) {
                Log.success "[${project.name}] remove resources dir..."
            }

            if (aapt.deletePackage(filteredResources)) {
                Log.success "[${project.name}] remove resources.arsc..."
            }

//            if (sourceOutputDir.deleteDir()) {
//                Log.success "[${project.name}] remove R.java..."
//            }

//            small.symbolFile.delete() // also delete the generated R.txt
        }


        int abiFlag = getABIFlag(project)
        int flags = (abiFlag << 1) | noResourcesFlag
        if (aapt.writeSmallFlags(flags, updatedResources)) {
            Log.success "[${project.name}] add flags: ${Integer.toBinaryString(flags)}..."
        }

        // Delete filtered entries.
        // Cause there is no `aapt update' command supported, so for the updated resources
        // we also delete first and run `aapt add' later.
        filteredResources.addAll(updatedResources)
        ZipUtils.with(apFile).deleteAll(filteredResources)

        // Re-add updated entries.
        // $ aapt add resources.ap_ file1 file2 ...
        def nullOutput = new ByteArrayOutputStream()
        if (System.properties['os.name'].toLowerCase().contains('windows')) {
            // Avoid the command becomes too long to execute on Windows.
            updatedResources.each { res ->
                project.exec {
                    executable aaptExe
                    workingDir unzipApDir
                    args 'add', apFile.path, res

                    standardOutput = nullOutput
                }
            }
        } else {
            project.exec {
                executable aaptExe
                workingDir unzipApDir
                args 'add', apFile.path
                args updatedResources

                // store the output instead of printing to the console
                standardOutput = new ByteArrayOutputStream()
            }
        }
    }
    
    int getABIFlag(Project project) {
        def abis = []

        def jniDirs = []
        def android = AndroidPluginUtils.getAndroid(project)
        jniDirs.addAll android.sourceSets.main.jniLibs.srcDirs

        // Collect ABIs from AARs
        def aars = small.compiledAarDependencies[project.name]
        aars.each { aar ->
            def aarDir = new File(small.explodedAarDir, aar.path)
            def jniDir = new File(aarDir, 'jni')
            if (jniDir.exists()) {
                jniDirs.add jniDir
            }
        }

        // Filter ABIs
        def filters = android.defaultConfig.ndkConfig.abiFilters
        jniDirs.each { dir ->
            dir.listFiles().each { File d ->
                if (d.isFile()) return

                def abi = d.name
                if (filters != null && !filters.contains(abi)) return
                if (abis.contains(abi)) return

                abis.add(abi)
            }
        }

        return JNIUtils.getABIFlag(abis)
    }

    void addCopyGeneratedApkTasks() {
        small.bundleProjects.each { bundle ->
            bundle.afterEvaluate {
                AndroidPluginUtils.getVariants(bundle).all { BaseVariant variant ->
                    VariantUtils.allOutputs variant, { out ->
                        def copyApkTaskName = "smallCopy${out.name.capitalize()}Apk"
                        if (bundle.hasProperty(copyApkTaskName)) return

                        def apk = out.outputFile
                        def copyTask = bundle.task(copyApkTaskName, type: Copy) {
                            def outName = "${variant.applicationId}.apk"
                            extensions.add('outName', outName)

                            destinationDir = small.outputBundleDir
                            from (apk.parentFile.absolutePath) {
                                include apk.name
                                rename {outName}
                            }
                        }.doLast {
                            def outPath = "${small.outputBundlePath}/$it.outName"
                            def outSize = FileUtils.getFormatSize(apk)
                            Log.footer("[$bundle.name] copy to $outPath (${apk.length()} bytes = $outSize)")
                        }

                        variant.assemble.finalizedBy copyTask
                    }
                }
            }
        }
    }
}