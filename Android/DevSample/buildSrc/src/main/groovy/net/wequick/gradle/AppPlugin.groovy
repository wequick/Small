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
    private static final int UNSET_TYPEID = 99
    private static final int UNSET_ENTRYID = -1

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

        def libEntries = [:]
        rootExt.preIdsDir.listFiles().each {
            if (it.name.endsWith('R.txt') && !it.name.startsWith(project.name)) {
                libEntries += SymbolParser.getResourceEntries(it)
            }
        }
        def publicEntries = SymbolParser.getResourceEntries(small.publicSymbolFile)
        def bundleEntries = SymbolParser.getResourceEntries(idsFile)
        def staticIdMaps = [:]
        def staticIdStrMaps = [:]
        def retainedEntries = []
        def retainedPublicEntries = []
        def retainedStyleables = []
        def reservedKeys = getReservedResourceKeys()

        bundleEntries.each { k, be ->
            be._typeId = UNSET_TYPEID // for sort
            be._entryId = UNSET_ENTRYID

            def le = publicEntries.get(k)
            if (le != null) {
                // Use last built id
                be._typeId = le.typeId
                be._entryId = le.entryId
                retainedPublicEntries.add(be)
                publicEntries.remove(k)
                return
            }

            if (reservedKeys.contains(k)) {
                be.isStyleable ? retainedStyleables.add(be) : retainedEntries.add(be)
                return
            }

            le = libEntries.get(k)
            if (le != null) {
                // Add static id maps to host or library resources and map it later at
                // compile-time with the aapt-generated `resources.arsc' and `R.java' file
                staticIdMaps.put(be.id, le.id)
                staticIdStrMaps.put(be.idStr, le.idStr)
                return
            }

            if (be.type != 'id') {
                throw new Exception(
                        "Missing library resource entry: \"$k\", try to cleanLib and buildLib.")
            }
            be.isStyleable ? retainedStyleables.add(be) : retainedEntries.add(be)
        }

        // TODO: retain deleted public entries
        if (publicEntries.size() > 0) {
            publicEntries.each { k, e ->
                e._typeId = e.typeId
                e._entryId = e.entryId
                e.entryId = Aapt.ID_DELETED

                def re = retainedPublicEntries.find{it.type == e.type}
                e.typeId = (re != null) ? re.typeId : Aapt.ID_DELETED
            }
            publicEntries.each { k, e ->
                retainedPublicEntries.add(e)
            }
        }
        if (retainedEntries.size() == 0 && retainedPublicEntries.size() == 0) {
            small.retainedTypes = [] // Doesn't have any resources
            return
        }

        // Prepare public types
        def publicTypes = [:]
        def maxPublicTypeId = 0
        def unusedTypeIds = [] as Queue
        if (retainedPublicEntries.size() > 0) {
            retainedPublicEntries.each { e ->
                def typeId = e._typeId
                def entryId = e._entryId
                def type = publicTypes[e.type]
                if (type == null) {
                    publicTypes[e.type] = [id: typeId, maxEntryId: entryId,
                                           entryIds:[entryId], unusedEntryIds:[] as Queue]
                    maxPublicTypeId = Math.max(typeId, maxPublicTypeId)
                } else {
                    type.maxEntryId = Math.max(entryId, type.maxEntryId)
                    type.entryIds.add(entryId)
                }
            }
            if (maxPublicTypeId != publicTypes.size()) {
                for (int i = 1; i < maxPublicTypeId; i++) {
                    if (publicTypes.find{ k, t -> t.id == i } == null) unusedTypeIds.add(i)
                }
            }
            publicTypes.each { k, t ->
                if (t.maxEntryId != t.entryIds.size()) {
                    for (int i = 0; i < t.maxEntryId; i++) {
                        if (!t.entryIds.contains(i)) t.unusedEntryIds.add(i)
                    }
                }
            }
        }

        // First sort with origin(full) resources order
        retainedEntries.sort { a, b ->
            a.typeId <=> b.typeId ?: a.entryId <=> b.entryId
        }

        // Reassign resource type id (_typeId) and entry id (_entryId)
        def lastEntryIds = [:]
        if (retainedEntries.size() > 0) {
            if (retainedEntries[0].type != 'attr') {
                // reserved for `attr'
                if (maxPublicTypeId == 0) maxPublicTypeId = 1
                if (unusedTypeIds.size() > 0) unusedTypeIds.poll()
            }
            def selfTypes = [:]
            retainedEntries.each { e ->
                // Check if the type has been declared in public.txt
                def type = publicTypes[e.type]
                if (type != null) {
                    e._typeId = type.id
                    if (type.unusedEntryIds.size() > 0) {
                        e._entryId = type.unusedEntryIds.poll()
                    } else {
                        e._entryId = ++type.maxEntryId
                    }
                    return
                }
                // Assign new type with unused type id
                type = selfTypes[e.type]
                if (type != null) {
                    e._typeId = type.id
                } else {
                    if (unusedTypeIds.size() > 0) {
                        e._typeId = unusedTypeIds.poll()
                    } else {
                        e._typeId = ++maxPublicTypeId
                    }
                    selfTypes[e.type] = [id: e._typeId]
                }
                // Simply increase the entry id
                def entryId = lastEntryIds[e.type]
                if (entryId == null) {
                    entryId = 0
                } else {
                    entryId++
                }
                e._entryId = lastEntryIds[e.type] = entryId
            }

            retainedEntries += retainedPublicEntries
        } else {
            retainedEntries = retainedPublicEntries
        }

        // Resort with reassigned resources order
        retainedEntries.sort { a, b ->
            a._typeId <=> b._typeId ?: a._entryId <=> b._entryId
        }

        // Resort retained resources
        def retainedTypes = []
        def pid = (small.packageId << 24)
        def currType = null
        retainedEntries.each { e ->
            // Prepare entry id maps for resolving resources.arsc and binary xml files
            if (currType == null || currType.name != e.type) {
                // New type
                currType = [type: e.vtype, name: e.type, id: e.typeId, _id: e._typeId, entries: []]
                retainedTypes.add(currType)
            }
            def newResId = pid | (e._typeId << 16) | e._entryId
            def newResIdStr = "0x${Integer.toHexString(newResId)}"
            staticIdMaps.put(e.id, newResId)
            staticIdStrMaps.put(e.idStr, newResIdStr)

            // Prepare styleable id maps for resolving R.java
            if (retainedStyleables.size() > 0 && e.typeId == 1) {
                retainedStyleables.findAll { it.idStrs != null }.each {
                    def index = it.idStrs.indexOf(e.idStr)
                    if (index >= 0) {
                        it.idStrs[index] = newResIdStr
                        it.mapped = true
                    }
                }
            }

            def entry = [name: e.key, id: e.entryId, _id: e._entryId, v: e.id, _v:newResId,
                         vs: e.idStr, _vs: newResIdStr]
            currType.entries.add(entry)
        }

        // Update the id array for styleables
        retainedStyleables.findAll { it.mapped != null }.each {
            it.idStr = "{ ${it.idStrs.join(', ')} }"
            it.idStrs = null
        }

        small.idMaps = staticIdMaps
        small.idStrMaps = staticIdStrMaps
        small.retainedTypes = retainedTypes
        small.retainedStyleables = retainedStyleables
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

                aapt.filterPackage(small.retainedTypes, small.packageId, small.idMaps,
                        small.retainedStyleables)
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

                    it.children().each {
                        type = it.name()
                        def name = it.@name
                        if (type == 'string') {
                            if (name == 'app_name') return // DON'T NEED IN BUNDLE
                        } else if (type == 'style') {
                            name = name.replaceAll("\\.", "_")
                        } else if (type == 'declare-styleable') {
                            // <declare-styleable name="MyTextView">
                            type = 'styleable'
                            it.children().each { // <attr format="string" name="label"/>
                                def attr = it.@name
                                def key
                                if (attr.startsWith('android:')) {
                                    attr = attr.replaceAll(':', '_')
                                } else {
                                    key = "attr/$attr"
                                    if (!resourceKeys.contains(key)) resourceKeys.add(key)
                                }
                                key = "styleable/${name}_${attr}"
                                if (!resourceKeys.contains(key)) resourceKeys.add(key)
                            }
                        } else if (type.endsWith('-array')) {
                            // string-array or integer-array
                            type = 'array'
                        }

                        def key = "$type/$name"
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
        int maxHash = 0xffff
        int d = maxPP - minPP
        int hash = bundleName.hashCode() & maxHash
        int pp = (hash * d / maxHash) + minPP
        return pp
    }
}
