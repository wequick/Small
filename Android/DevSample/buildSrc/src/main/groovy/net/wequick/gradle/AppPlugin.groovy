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

import net.wequick.gradle.aapt.Aapt
import net.wequick.gradle.aapt.SymbolParser
import org.gradle.api.Project

class AppPlugin extends BundlePlugin {

    private static def sPackageIds = [:] as LinkedHashMap<String, Integer>

    protected def compileLibs

    void apply(Project project) {
        super.apply(project)
    }

    @Override
    protected Class<? extends BaseExtension> getExtensionClass() {
        return AppExtension.class
    }

    @Override
    protected PluginType getPluginType() {
        return PluginType.App
    }

    @Override
    protected void createExtension() {
        super.createExtension()
    }

    @Override
    protected AppExtension getSmall() {
        return super.getSmall()
    }

    @Override
    protected void configureProject() {
        super.configureProject()

        project.afterEvaluate {
            // Get all dependencies with gradle script `compile project(':lib.*')'
            compileLibs = project.configurations.compile.dependencies.findAll {
                it.hasProperty('dependencyProject') &&
                        it.dependencyProject.name.startsWith('lib.')
            }
            if (isBuildingLibs()) {
                // While building libs, `lib.*' modules are changing to be an application
                // module and cannot be depended by any other modules. To avoid warnings,
                // remove the `compile project(':lib.*')' dependencies temporary.
                project.configurations.compile.dependencies.removeAll(compileLibs)
            }
        }

        if (!isBuildingRelease()) return

        project.afterEvaluate {
            initPackageId()
            resolveReleaseDependencies()

            project.android.dexOptions {
                preDexLibraries = false // !important, this makes classes.dex splitable
            }
        }
    }

    protected static def getJarName(Project project) {
        def group = project.group
        if (group == project.rootProject.name) group = project.name
        return "$group-${project.version}.jar"
    }

    protected void resolveReleaseDependencies() {
        // Pre-split shared libraries at release mode
        //  - host, appcompat and etc.
        RootExtension rootExt = project.rootProject.small
        def baseJars = project.fileTree(dir: rootExt.preBaseJarDir, include: ['*.jar'])
        project.dependencies.add('provided', baseJars)
        //  - lib.*
        def libJarNames = []
        compileLibs.each {
            libJarNames += getJarName(it.dependencyProject)
        }
        if (libJarNames.size() > 0) {
            def libJars = project.fileTree(dir: rootExt.preLibsJarDir, include: libJarNames)
            project.dependencies.add('provided', libJars)
        }

        // Pre-split the `support-annotations' library which would be combined into `classes.dex'
        // by Dex task: `variant.dex' on gradle 1.3.0 or
        // `project.transformClassesWithDexForRelease' on gradle 1.5.0+
        project.configurations {
            all*.exclude group: 'com.android.support', module: 'support-annotations'
        }

        // Check if dependents by appcompat library which contains theme resource and
        // cannot be pre-split
        def canPreSplit = project.configurations.compile.dependencies.find {
            it.group.equals('com.android.support') && it.name.startsWith('appcompat')
        } == null

        if (canPreSplit) {
            // Pre-split classes and resources.
            project.rootProject.small.preApDir.listFiles().each {
                project.android.aaptOptions.additionalParameters '-I', it.path
            }
            // Ensure generating text symbols - R.txt
            project.preBuild.doLast {
                def symbolsPath = project.processReleaseResources.textSymbolOutputDir.path
                project.android.aaptOptions.additionalParameters '--output-text-symbols',
                        symbolsPath
            }
        }
    }

    @Override
    protected void configureReleaseVariant(variant) {
        super.configureReleaseVariant(variant)

        // Fill extensions
        def dexTask = project.hasProperty('transformClassesWithDexForRelease') ?
                project.transformClassesWithDexForRelease : variant.dex
        small.with {
            javac = variant.javaCompile
            dex = dexTask

            packagePath = variant.applicationId.replaceAll('\\.', '/')
            classesDir = javac.destinationDir
            bkClassesDir = new File(classesDir.parentFile, "${classesDir.name}~")

            aapt = project.processReleaseResources
            apFile = aapt.packageOutputFile

            symbolFile = new File(aapt.textSymbolOutputDir, 'R.txt')
            rJavaFile = new File(aapt.sourceOutputDir, "${packagePath}/R.java")

            mergerXml = new File(variant.mergeResources.incrementalFolder, 'merger.xml')
        }

        hookVariantTask()
    }

    /**
     * Prepare retained resource types and resource id maps for package slicing
     */
    protected void prepareSplit() {
        // Prepare id maps (bundle resource id -> library resource id)
        def idsFile = small.symbolFile
        if (!idsFile.exists()) return

        RootExtension rootExt = (RootExtension) project.rootProject.small

        def staticLibEntries = [:]
        def dynamicLibEntries = [:]
        rootExt.preIdsDir.listFiles().each {
            if (it.name.endsWith('R.txt') && !it.name.startsWith(project.name)) {
                def entries = SymbolParser.getResourceEntries(it)
                if (it.name.startsWith('lib.')) {
                    dynamicLibEntries += entries
                } else {
                    staticLibEntries += entries
                }
            }
        }
        def bundleEntries = SymbolParser.getResourceEntries(idsFile)
        def staticIdMaps = [:]
        def staticIdStrMaps = [:]
        def dynamicIds = []
        def retainedEntries = []
        def reservedKeys = getReservedResourceKeys()
        bundleEntries.each { k, be ->
            if (reservedKeys.contains(k)) {
                retainedEntries.add(be)
            } else {
                def le = staticLibEntries.get(k)
                if (le != null) {
                    // Static Lib: the lib compiles into host apk like `appcompat', `design',
                    // add static id maps to host resources and map it later at compiletime
                    // with the aapt-generated `resources.arsc' and `R.java' file
                    if (be.id != le.id) {
                        staticIdMaps.put(be.id, le.id)
                        staticIdStrMaps.put(be.idStr, le.idStr)
                    }
                } else {
                    le = dynamicLibEntries.get(k)
                    if (le != null) {
                        // TODO: Dynamic Lib: the lib compiles alone as `[package]_lib_*.so',
                        // TODO: add dynamic id and map it later at runtime
//                            dynamicIds.add(be.idStr)
                        retainedEntries.add(be)
                    } else {
                        retainedEntries.add(be)
                    }
                }
            }
        }
        if (retainedEntries.size() == 0) {
            small.retainedTypes = [] // Doesn't have any resources
            return
        }

        // Resort retained resources
        def retainedTypes = []
        retainedEntries.sort { a, b ->
            a.typeId <=> b.typeId ?: a.entryId <=> b.entryId
        }
        def pid = (small.packageId << 24)
        def tid = 0
        def eid
        def currType = null
        // Ensure first type is `attr'
        def attrEntry = retainedEntries[0]
        if (attrEntry.type != 'attr') tid = 1 // skip `attr'
        retainedEntries.each { e ->
            if (currType == null || currType.id != e.typeId) {
                // New type
                currType = [type: e.vtype, name: e.type, id: e.typeId, _id: tid++, entries: []]
                retainedTypes.add(currType)
                eid = 0
            } else {
                // Previous type
                eid++
            }
            def newResId = pid | (tid << 16) | eid
            def newResIdStr = "0x${Integer.toHexString(newResId)}"
            if (e.id != newResId) {
                staticIdMaps.put(e.id, newResId)
                staticIdStrMaps.put(e.idStr, newResIdStr)
            }
            currType.entries.add([name: e.key, id: e.entryId, _id: eid, v: e.id, _v:newResId,
                                  vs: e.idStr, _vs: newResIdStr])
        }
        small.idMaps = staticIdMaps
        small.idStrMaps = staticIdStrMaps
        small.retainedTypes = retainedTypes
        small.dynamicIds = dynamicIds
    }

    protected void hookVariantTask() {
        // Hook aapt task to slice asset package and resolve library resource ids
        small.aapt.doLast {
            // Unpack resources.ap_
            File apFile = it.packageOutputFile
            File unzipApDir = new File(apFile.parentFile, 'ap_unzip')
            project.copy {
                from project.zipTree(apFile)
                into unzipApDir
            }

            // Modify assets
            prepareSplit()
            File symbolFile = (small.type == PluginType.Library) ?
                    new File(it.textSymbolOutputDir, 'R.txt') : null
            File rJavaFile = new File(it.sourceOutputDir, "${small.packagePath}/R.java")
            Aapt aapt = new Aapt(unzipApDir, rJavaFile, symbolFile)
            if (small.retainedTypes != null) {
                aapt.filterResources(small.retainedTypes)
                Log.success "[${project.name}] split library res files..."

                aapt.filterPackage(small.retainedTypes, small.packageId, small.idMaps)
                Log.success "[${project.name}] slice asset package and reset package id..."
            } else {
                aapt.resetPackage(small.packageId, small.packageIdStr, small.idMaps)
                Log.success "[${project.name}] reset resource package id..."
            }

            // Repack resources.ap_
            project.ant.zip(baseDir: unzipApDir, destFile: apFile)
        }

        // Hook javac task to split libraries' R.class
        small.javac.doLast {
            File classesDir = it.destinationDir
            File tempDir = new File(classesDir.parentFile, "${classesDir.name}~")
            // Filter [package] classes
            classesDir.renameTo(tempDir)
            def srcDir = new File(tempDir, small.packagePath)
            def dstDir = new File(classesDir, small.packagePath)
            project.copy {
                from project.fileTree(srcDir)
                into dstDir
            }
            tempDir.deleteDir()

            Log.success "[${project.name}] split library R.class files..."
        }

        // Hook dex task to split all aar classes.jar
        small.dex.doFirst {
            small.aarDir.renameTo(small.bkAarDir)
            Log.success "[${project.name}] split aar classes..."
        }
        small.dex.doLast {
            small.bkAarDir.renameTo(small.aarDir)
        }

        // Hook clean task to unset package id
        project.clean.doLast {
            sPackageIds.remove(project.name)
        }
    }

    @Override
    protected void tidyUp() {
        super.tidyUp()
        if (!small.aarDir.exists()) {
            small.bkAarDir.renameTo(small.aarDir)
        }
    }

    /**
     * Get reserved resource keys of project. For making a smaller slice, the unnecessary
     * resource `mipmap/ic_launcher' and `string/app_name' are excluded.
     */
    protected def getReservedResourceKeys() {
        def merger = new XmlParser().parse(small.mergerXml)
        def dataSets = merger.dataSet.findAll {
            it.@config == 'main' || it.@config == 'release'
        }
        def resourceKeys = []
        dataSets.each { // <dataSet config="main" generated-set="main$Generated">
            it.source.each { // <source path="**/${project.name}/src/main/res">
                it.file.each {
                    def type = it.@type
                    if (type != null) { // <file name="activity_main" ... type="layout"/>
                        def key = "$type/${it.@name}" // layout/activity_main
                        if (key == 'mipmap/ic_launcher') return // DON'T NEED IN BUNDLE
                        if (!resourceKeys.contains(key)) resourceKeys.add(key)
                        return
                    }
                    it.children().each { // <color name="colorAccent">#FF4081</color>
                        def key = "${it.name()}/${it.@name}" // color/colorAccent
                        if (key == 'string/app_name') return // DON'T NEED IN BUNDLE
                        if (!resourceKeys.contains(key)) resourceKeys.add(key)
                    }
                }
            }
        }
        return resourceKeys
    }

    /**
     * Init package id for bundle, if has not explicitly set in 'build.gradle' or
     * 'gradle.properties', generate a random one
     */
    protected void initPackageId() {
        Integer pp
        String ppStr = null
        Integer usingPP = sPackageIds.get(project.name)
        boolean addsNewPP = true
        // Get user defined package id
        if (project.hasProperty('packageId')) {
            def userPP = project.packageId
            if (userPP instanceof Integer) {
                // Set in build.gradle with 'ext.packageId=0x7e' as an Integer
                pp = userPP
            } else {
                // Set in gradle.properties with 'packageId=7e' as a String
                ppStr = userPP
                pp = Integer.parseInt(ppStr, 16)
            }

            if (usingPP != null && pp != usingPP) {
                // TODO: clean last build
                throw new Exception("Package id for ${project.name} has changed! " +
                        "You should call clean first.")
            }
        } else {
            if (usingPP != null) {
                pp = usingPP
                addsNewPP = false
            } else {
                pp = genRandomPackageId(project.name)
            }
        }

        small.packageId = pp
        small.packageIdStr = ppStr != null ? ppStr : String.format('%02x', pp)
        if (!addsNewPP) return

        // Check if the new package id has been used
        sPackageIds.each { name, id ->
            if (id == pp) {
                throw new Exception("Duplicate package id 0x${String.format('%02x', pp)} " +
                        "with $name and ${project.name}!\nPlease redefine one of them " +
                        "in build.gradle (e.g. 'ext.packageId=0x7e') " +
                        "or gradle.properties (e.g. 'packageId=7e').")
            }
        }
        sPackageIds.put(project.name, pp)
    }

    /**
     * Generate a random package id in range [0x03, 0x7e] by bundle's name.
     * [0x00, 0x02] reserved for android system resources.
     * [0x03, 0x0f] reserved for the fucking crazy manufacturers.
     */
    private static int genRandomPackageId(String bundleName) {
        int minPP = 0x10
        int maxPP = 0x7e
        int d = maxPP - minPP
        int hash = bundleName.hashCode() & 0x000000ff
        int pp = (hash * d / 0xff) + minPP
        return pp
    }
}
