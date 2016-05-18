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

import groovy.io.FileType
import net.wequick.gradle.aapt.Aapt
import net.wequick.gradle.aapt.SymbolParser
import net.wequick.gradle.util.JNIUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency

class AppPlugin extends BundlePlugin {

    private static def sPackageIds = [:] as LinkedHashMap<String, Integer>
    private static final int UNSET_TYPEID = 99
    private static final int UNSET_ENTRYID = -1

    protected Set<Project> mDependentLibProjects

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
            def libs = project.configurations.compile.dependencies.findAll {
                it.hasProperty('dependencyProject') &&
                        it.dependencyProject.name.startsWith('lib.')
            }
            mDependentLibProjects = libs.collect { it.dependencyProject }
            if (isBuildingLibs()) {
                // While building libs, `lib.*' modules are changing to be an application
                // module and cannot be depended by any other modules. To avoid warnings,
                // remove the `compile project(':lib.*')' dependencies temporary.
                project.configurations.compile.dependencies.removeAll(libs)
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
        RootExtension rootExt = project.rootProject.small

        // Pre-split shared libraries at release mode
        //  - host, appcompat and etc.
        def baseJars = project.fileTree(dir: rootExt.preBaseJarDir, include: ['*.jar'])
        project.dependencies.add('provided', baseJars)
        //  - lib.*
        def libJarNames = []
        mDependentLibProjects.each {
            libJarNames += getJarName(it)
        }
        if (libJarNames.size() > 0) {
            // Collect the jars with absolute file path, fix issue #65
            def libJars = project.files(libJarNames.collect{
                new File(rootExt.preLibsJarDir, it).path
            })
            project.dependencies.add('provided', libJars)
        }

        // Pre-split all the jar dependencies (deep level)
        def compile = project.configurations.compile
        compile.exclude group: 'com.android.support', module: 'support-annotations'
        rootExt.preLinkJarDir.listFiles().each { file ->
            if (!file.name.endsWith('D.txt')) return
            file.eachLine { line ->
                def module = line.split(':')
                compile.exclude group: module[0], module: module[1]
            }
        }

        // Check if dependents by appcompat library which contains theme resource and
        // cannot be pre-split
        def appcompat = compile.dependencies.find {
            it.group.equals('com.android.support') && it.name.startsWith('appcompat')
        }
        if (appcompat == null) {
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
        def variantName = variant.name.capitalize()
        def newDexTaskName = 'transformClassesWithDexFor' + variantName
        def dexTask = project.hasProperty(newDexTaskName) ? project.tasks[newDexTaskName] : variant.dex
        File mergerDir = variant.mergeResources.incrementalFolder

        small.with {
            javac = variant.javaCompile
            dex = dexTask
            processManifest = project.tasks['process' + variantName + 'Manifest']

            packageName = variant.applicationId
            packagePath = packageName.replaceAll('\\.', '/')
            classesDir = javac.destinationDir
            bkClassesDir = new File(classesDir.parentFile, "${classesDir.name}~")

            aapt = project.tasks['process' + variantName + 'Resources']
            apFile = aapt.packageOutputFile

            File symbolDir = aapt.textSymbolOutputDir
            File sourceDir = aapt.sourceOutputDir

            symbolFile = new File(symbolDir, 'R.txt')
            rJavaFile = new File(sourceDir, "${packagePath}/R.java")

            splitRJavaFile = new File(sourceDir.parentFile, "small/${packagePath}/R.java")

            mergerXml = new File(mergerDir, 'merger.xml')
        }

        hookVariantTask()
    }

    /** Collect the vendor aars (has resources) compiling in current bundle */
    protected void collectVendorAars(Set<Map> outFirstLevelAars,
                                     Set<Map> outTransitiveAars) {
        project.configurations.compile.resolvedConfiguration.firstLevelModuleDependencies.each {
            collectVendorAars(it, outFirstLevelAars, outTransitiveAars)
        }
    }

    protected boolean collectVendorAars(ResolvedDependency node,
                                        Set<Map> outFirstLevelAars,
                                        Set<Map> outTransitiveAars) {
        def group = node.moduleGroup,
            name = node.moduleName,
            version = node.moduleVersion

        if (group == '' && version == '') {
            // Ignores the dependency of local aar
            return false
        }
        if (small.splitAars.find { aar -> group == aar.group && name == aar.name } != null) {
            // Ignores the dependency which has declared in host or lib.*
            return false
        }
        if (small.retainedAars.find { aar -> group == aar.group && name == aar.name } != null) {
            // Ignores the dependency of normal modules
            return false
        }

        def path = "$group/$name/$version"
        def aar = [path: path, name: node.name]
        def resDir = new File(small.aarDir, "$path/res")
        // If the dependency has resources, collect it
        if (resDir.exists() && resDir.list().size() > 0) {
            if (outFirstLevelAars != null && !outFirstLevelAars.contains(aar)) {
                outFirstLevelAars.add(aar)
            }
            if (!outTransitiveAars.contains(aar)) {
                outTransitiveAars.add(aar)
            }
            node.children.each { next ->
                collectVendorAars(next, null, outTransitiveAars)
            }
            return true
        }

        // Otherwise, check it's children for recursively collecting
        boolean flag = false
        node.children.each { next ->
            flag |= collectVendorAars(next, null, outTransitiveAars)
        }
        if (!flag) return false

        if (outFirstLevelAars != null && !outFirstLevelAars.contains(aar)) {
            outFirstLevelAars.add(aar)
        }
        return true
    }

    /**
     * Prepare retained resource types and resource id maps for package slicing
     */
    protected void prepareSplit() {
        def idsFile = small.symbolFile
        if (!idsFile.exists()) return

        RootExtension rootExt = project.rootProject.small

        // Check if has any vendor aars
        def firstLevelVendorAars = [] as Set<Map>
        def transitiveVendorAars = [] as Set<Map>
        collectVendorAars(firstLevelVendorAars, transitiveVendorAars)
        if (firstLevelVendorAars.size() > 0) {
            if (rootExt.strictSplitResources) {
                def err = new StringBuilder('In strict mode, we do not allow vendor aars, ')
                err.append('please declare them in host build.gradle:\n')
                firstLevelVendorAars.each {
                    err.append("    - compile('${it.name}')\n")
                }
                err.append('or turn off the strict mode in root build.gradle:\n')
                err.append('    small {\n')
                err.append('        strictSplitResources = false\n')
                err.append('    }')
                throw new UnsupportedOperationException(err.toString())
            } else {
                def aars = firstLevelVendorAars.collect{ it.name }.join('; ')
                Log.warn("Using vendor aar(s): $aars")
            }
        }

        // Prepare id maps (bundle resource id -> library resource id)
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

        bundleEntries.each { k, Map be ->
            be._typeId = UNSET_TYPEID // for sort
            be._entryId = UNSET_ENTRYID

            Map le = publicEntries.get(k)
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

            // TODO: handle the resources addition by aar version conflict or something
//            if (be.type != 'id') {
//                throw new Exception(
//                        "Missing library resource entry: \"$k\", try to cleanLib and buildLib.")
//            }
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
                    // Replace `e.idStr' with `newResIdStr'
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

        // Collect all the resources for generating a temporary full edition R.java
        // which required in javac.
        // TODO: Do this only for the modules who's code really use R.xx of lib.*
        def allTypes = []
        def allStyleables = []
        def addedTypes = [:]
        libEntries.each { k, e ->
            if (reservedKeys.contains(k)) return

            if (e.isStyleable) {
                allStyleables.add(e);
            } else {
                if (!addedTypes.containsKey(e.type)) {
                    // New type
                    currType = [type: e.vtype, name: e.type, entries: []]
                    allTypes.add(currType)
                    addedTypes.put(e.type, currType)
                } else {
                    currType = addedTypes[e.type]
                }

                def entry = [name: e.key, _vs: e.idStr]
                currType.entries.add(entry)
            }
        }
        retainedTypes.each { t ->
            def at = addedTypes[t.name]
            if (at != null) {
                at.entries.addAll(t.entries)
            } else {
                allTypes.add(t)
            }
        }
        allStyleables.addAll(retainedStyleables)

        // Collect vendor types and styleables if needed
        def vendorEntries = [:]
        def vendorStyleableKeys = [:]
        transitiveVendorAars.each { aar ->
            String path = aar.path
            File dir = new File(small.aarDir, path)
            File vendorIdsFile = new File(dir, 'R.txt')
            def entries = []
            def styleables = []

            SymbolParser.collectResourceKeys(vendorIdsFile, entries, styleables)

            vendorEntries.put(path, entries)
            vendorStyleableKeys.put(path, styleables)
        }

        def vendorTypes = [:]
        def vendorStyleables = [:]
        vendorEntries.each { name, es ->
            allTypes.each { t ->
                t.entries.each { e ->
                    def ve = es.find { it.type == t.name && it.name == e.name }
                    if (ve != null) {
                        def vendorType
                        def vts = vendorTypes[name]
                        if (vts == null) {
                            vts = vendorTypes[name] = []
                        } else {
                            vendorType = vts.find { it.name == t.name }
                        }
                        if (vendorType == null) {
                            vendorType = [:]
                            vendorType.putAll(t)
                            vendorType.entries = []
                            vts.add(vendorType)
                        }
                        vendorType.entries.add(e)
                    }
                }
            }
        }
        vendorStyleableKeys.each { name, vs ->
            allStyleables.each { s ->
                if (vs.contains(s.key)) {
                    if (vendorStyleables[name] == null) {
                        vendorStyleables[name] = []
                    }
                    vendorStyleables[name].add(s)
                    return
                }
            }
        }

        small.idMaps = staticIdMaps
        small.idStrMaps = staticIdStrMaps
        small.retainedTypes = retainedTypes
        small.retainedStyleables = retainedStyleables

        small.allTypes = allTypes
        small.allStyleables = allStyleables

        small.vendorTypes = vendorTypes
        small.vendorStyleables = vendorStyleables
    }

    protected int getABIFlag() {
        def abis = []

        def jniDirs = project.android.sourceSets.main.jniLibs.srcDirs
        if (jniDirs == null) jniDirs = []
        // Collect ABIs from AARs
        small.explodeAarDirs.each { dir ->
            File jniDir = new File(dir, 'jni')
            if (!jniDir.exists()) return
            jniDirs.add(jniDir)
        }
        def filters = project.android.defaultConfig.ndkConfig.abiFilters
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

    protected void hookVariantTask() {
        RootExtension rootExt = project.rootProject.small

        // Hook preBuild task to resolve dependent AARs
        project.preBuild.doFirst {
            // Collect dependent AARs
            def smallLibAars = new HashSet() // the aars compiled in host or lib.*
            rootExt.preLinkAarDir.listFiles().each { file ->
                if (!file.name.endsWith('D.txt')) return
                if (file.name.startsWith(project.name)) return

                file.eachLine { line ->
                    def module = line.split(':')
                    if (module.size() == 3) {
                        smallLibAars.add(group: module[0], name: module[1], version: module[2])
                    } else {
                        // If using local aar, the version may be unspecific
                        smallLibAars.add(group: module[0], name: module[1], version: '')
                    }
                }
            }
            def userLibAars = new HashSet() // user modules who's name are not in Small way - `*.*'
            project.rootProject.subprojects {
                if (it.name.startsWith('lib.')) {
                    smallLibAars.add(group: it.group, name: it.name, version: it.version)
                } else if (it.name != 'app' && it.name != 'small' && it.name.indexOf('.') < 0) {
                    userLibAars.add(group: it.group, name: it.name, version: it.version)
                }
            }

            small.splitAars = smallLibAars
            small.retainedAars = userLibAars
        }

        // Hook process-manifest task to remove the `android:icon' and `android:label' attribute
        // which declared in the plugin `AndroidManifest.xml' application node. (for #11)
        // To support plugin JNI, we overwrite the `android:label' attribute with the ABIs flag
        // so that we can exactly extract the JNIs under the supported ABI at runtime. (#87, #79)
        small.processManifest.doLast {
            File manifestFile = it.manifestOutputFile
            def sb = new StringBuilder()
            def enteredApplicationNode = false
            def needsFilter = true
            int flag = getABIFlag()
            def abiAttr = flag != 0 ? "android:label=\"$flag\"" : null
            // We don't use XmlParser but simply parse each line cause this should be faster
            manifestFile.eachLine { line ->
                while (true) { // fake loop for less `if ... else' statement
                    if (!needsFilter) break

                    def i = line.indexOf('<application')
                    if (i < 0) {
                        if (!enteredApplicationNode) break

                        if (line.indexOf('>') > 0) needsFilter = false

                        // filter `android:icon' and `android:label'
                        if (line.indexOf('android:icon') > 0 || line.indexOf('android:label') > 0) {
                            if (needsFilter) return

                            line = '>'
                        }
                        break
                    }

                    def j = line.indexOf('<!--')
                    if (j > 0 && j < i) break // ignores the comment line

                    if (line.indexOf('>') > 0) { // <application /> or <application .. > in one line
                        needsFilter = false
                        if (abiAttr != null) {
                            // Insert ABI attribute
                            i += 12 // '<application'.length()
                            line = "${line.substring(0, i)} $abiAttr${line.substring(i)}"
                        }
                        break
                    }

                    enteredApplicationNode = true // mark this for next line
                    if (abiAttr != null) {
                        // Insert ABI attribute
                        line = "$line $abiAttr"
                    }
                    break
                }

                sb.append(line).append(System.lineSeparator())
            }
            manifestFile.write(sb.toString(), 'utf-8')
        }

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
            File sourceOutputDir = it.sourceOutputDir
            File rJavaFile = new File(sourceOutputDir, "${small.packagePath}/R.java")
            def rev = project.android.buildToolsRevision
            Aapt aapt = new Aapt(unzipApDir, rJavaFile, symbolFile, rev)
            if (small.retainedTypes != null) {
                aapt.filterResources(small.retainedTypes)
                Log.success "[${project.name}] split library res files..."

                aapt.filterPackage(small.retainedTypes, small.packageId, small.idMaps,
                        small.retainedStyleables)

                Log.success "[${project.name}] slice asset package and reset package id..."

                String pkg = small.packageName
                // Overwrite the aapt-generated R.java with full edition
                aapt.generateRJava(small.rJavaFile, pkg, small.allTypes, small.allStyleables)
                // Also generate a split edition for later re-compiling
                aapt.generateRJava(small.splitRJavaFile, pkg,
                        small.retainedTypes, small.retainedStyleables)

                // Overwrite the retained vendor R.java
                def retainedRFiles = [small.rJavaFile]
                small.vendorTypes.each { name, types ->
                    File aarDir = new File(small.aarDir, name)
                    File manifestFile = new File(aarDir, 'AndroidManifest.xml')
                    def manifest = new XmlParser().parse(manifestFile)
                    String aarPkg = manifest.@package
                    String pkgPath = aarPkg.replaceAll('\\.', '/')
                    File r = new File(sourceOutputDir, "$pkgPath/R.java")
                    retainedRFiles.add(r)

                    def styleables = small.vendorStyleables[name]
                    aapt.generateRJava(r, aarPkg, types, styleables)
                }

                // Remove unused R.java to fix the reference of shared library resource, issue #63
                sourceOutputDir.eachFileRecurse(FileType.FILES) { file ->
                    if (!retainedRFiles.contains(file)) {
                        file.delete()
                    }
                }

                Log.success "[${project.name}] split library R.java files..."
            } else {
                aapt.resetPackage(small.packageId, small.packageIdStr, small.idMaps)
                Log.success "[${project.name}] reset resource package id..."
            }

            // Repack resources.ap_
            project.ant.zip(baseDir: unzipApDir, destFile: apFile)
        }

        // Hook javac task to split libraries' R.class
        small.javac.doFirst { t ->
            // Dynamically provided jars
            def baseJars = project.fileTree(dir: rootExt.preBaseJarDir, include: ['*.jar'])
            t.classpath += baseJars
        }
        small.javac.doLast {
            if (!small.splitRJavaFile.exists()) return

            File classesDir = it.destinationDir
            File dstDir = new File(classesDir, small.packagePath)

            // Delete the original generated R$xx.class
            dstDir.listFiles().each { f ->
                if (f.name.startsWith('R$')) {
                    f.delete()
                }
            }
            // Re-compile the split R.java to R.class
            project.ant.javac(srcdir: small.splitRJavaFile.parentFile,
                    source: it.sourceCompatibility,
                    target: it.targetCompatibility,
                    destdir: classesDir)

            Log.success "[${project.name}] split R.class..."
        }

        // Hook dex task to split all aar classes.jar
        small.dex.doFirst {
            small.bkAarDir.mkdir()
            small.splitAars.each {
                // TODO: Resolve the version conflict
                String path = "${it.group}/${it.name}" // /${it.version}
                File dir = new File(small.aarDir, path)
                if (dir.exists()) {
                    File todir = new File(small.bkAarDir, path)
                    project.ant.move(file: dir, tofile: todir)
                }
            }
            Log.success "[${project.name}] split aar classes..."
        }

        // Hook clean task to unset package id
        project.clean.doLast {
            sPackageIds.remove(project.name)
        }
    }

    @Override
    protected void tidyUp() {
        super.tidyUp()
        if (small.bkAarDir.exists()) {
            project.ant.move(file: small.bkAarDir, tofile: small.aarDir)
            small.bkAarDir.delete()
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
