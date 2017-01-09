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

import com.android.build.api.transform.Format
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.pipeline.IntermediateFolderUtils
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.transforms.ProGuardTransform
import com.android.build.gradle.tasks.ProcessTestManifest
import com.android.build.gradle.tasks.MergeManifests
import com.android.build.gradle.tasks.MergeSourceSetFolders
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.builder.dependency.LibraryDependency
import com.android.sdklib.BuildToolInfo
import groovy.io.FileType
import net.wequick.gradle.aapt.Aapt
import net.wequick.gradle.aapt.SymbolParser
import net.wequick.gradle.transform.StripAarTransform
import net.wequick.gradle.util.ClassFileUtils
import net.wequick.gradle.util.JNIUtils
import net.wequick.gradle.util.Log
import net.wequick.gradle.util.ZipUtils
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.FileTree
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.tasks.compile.JavaCompile

class AppPlugin extends BundlePlugin {

    private static final int UNSET_TYPEID = 99
    private static final int UNSET_ENTRYID = -1
    protected static def sPackageIds = [:] as LinkedHashMap<String, Integer>

    protected Set<Project> mDependentLibProjects
    protected Set<Project> mTransitiveDependentLibProjects
    protected Set<Map> mUserLibAars
    protected Set<File> mLibraryJars
    protected File mMinifyJar

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
    protected void afterEvaluate(boolean released) {
        super.afterEvaluate(released)

        // Initialize a resource package id for current bundle
        initPackageId()

        // Get all dependencies with gradle script `compile project(':lib.*')'
        DependencySet compilesDependencies = project.configurations.compile.dependencies
        Set<DefaultProjectDependency> allLibs = compilesDependencies.withType(DefaultProjectDependency.class)
        Set<DefaultProjectDependency> smallLibs = []
        mUserLibAars = []
        mDependentLibProjects = []
        allLibs.each {
            if (rootSmall.isLibProject(it.dependencyProject)) {
                smallLibs.add(it)
                mDependentLibProjects.add(it.dependencyProject)
            } else {
                mUserLibAars.add(group: it.group, name: it.name, version: it.version)
            }
        }

        if (rootSmall.isBuildingLibs()) {
            // While building libs, `lib.*' modules are changing to be an application
            // module and cannot be depended by any other modules. To avoid warnings,
            // remove the `compile project(':lib.*')' dependencies temporary.
            compilesDependencies.removeAll(smallLibs)
        }

        if (!released) return

        // Add custom transformation to split shared libraries
        android.registerTransform(new StripAarTransform())

        resolveReleaseDependencies()
    }

    protected static def getJarName(Project project) {
        def group = project.group
        if (group == project.rootProject.name) group = project.name
        return "$group-${project.version}.jar"
    }

    protected static Set<File> getJarDependencies(Project project) {
        return project.fileTree(dir: 'libs', include: '*.jar').asList()
    }

    protected Set<File> getLibraryJars() {
        if (mLibraryJars != null) return mLibraryJars

        mLibraryJars = new LinkedHashSet<File>()

        // Collect the jars in `build-small/intermediates/small-pre-jar/base'
        def baseJars = project.fileTree(dir: rootSmall.preBaseJarDir, include: ['*.jar'])
        mLibraryJars.addAll(baseJars.files)

        // Collect the jars of `compile project(lib.*)' with absolute file path, fix issue #65
        Set<String> libJarNames = []
        Set<File> libDependentJars = []
        mTransitiveDependentLibProjects.each {
            libJarNames += getJarName(it)
            libDependentJars += getJarDependencies(it)
        }

        if (libJarNames.size() > 0) {
            def libJars = project.files(libJarNames.collect{
                new File(rootSmall.preLibsJarDir, it).path
            })
            mLibraryJars.addAll(libJars.files)
        }

        mLibraryJars.addAll(libDependentJars)

        return mLibraryJars
    }

    protected void resolveReleaseDependencies() {
        // Pre-split all the jar dependencies (deep level)
        def compile = project.configurations.compile
        compile.exclude group: 'com.android.support', module: 'support-annotations'
        rootSmall.preLinkJarDir.listFiles().each { file ->
            if (!file.name.endsWith('D.txt')) return
            if (file.name.startsWith(project.name)) return

            file.eachLine { line ->
                def module = line.split(':')
                compile.exclude group: module[0], module: module[1]
            }
        }
    }

    @Override
    protected void hookPreDebugBuild() {
        super.hookPreDebugBuild()

        // If an app.A dependent by lib.B and both of them declare application@name in their
        // manifests, the `processManifest` task will raise a conflict error. To avoid this,
        // modify the lib.B manifest to remove the attributes before app.A `processManifest`
        // and restore it after the task finished.

        // processDebugManifest
        project.tasks.withType(MergeManifests.class).each {
            if (it.variantName.startsWith('release')) return

            hookProcessDebugManifest(it, it.libraries)
        }

        // processDebugAndroidTestManifest
        project.tasks.withType(ProcessTestManifest.class).each {
            if (it.variantName.startsWith('release')) return

            hookProcessDebugManifest(it, it.libraries)
        }
    }

    protected void collectLibManifests(def lib, Set outFiles) {
        outFiles.add(lib.getManifest())

        if (lib instanceof LibraryDependency) { // android gradle 2.2.0+
            lib.getLibraryDependencies().each {
                collectLibManifests(it, outFiles)
            }
        } else { // android gradle 2.2.0-
            lib.getManifestDependencies().each {
                collectLibManifests(it, outFiles)
            }
        }
    }

    protected void hookProcessDebugManifest(Task processDebugManifest,
                                            List libs) {
        processDebugManifest.doFirst {
            def libManifests = new HashSet<File>()
            libs.each {
                def components = it.name.split(':') // e.g. 'Sample:lib.style:unspecified'
                if (components.size() != 3) return

                def projectName = components[1]
                if (!rootSmall.isLibProject(projectName)) return

                Set<File> allManifests = new HashSet<File>()
                collectLibManifests(it, allManifests)

                libManifests.addAll(allManifests.findAll {
                    // e.g.
                    // '**/Sample/lib.style/unspecified/AndroidManifest.xml
                    // '**/Sample/lib.analytics/unspecified/AndroidManifest.xml
                    def name = it.parentFile.parentFile.name
                    rootSmall.isLibProject(name)
                })
            }

            def filteredManifests = []
            libManifests.each { File manifest ->
                def sb = new StringBuilder()
                def enteredApplicationNode = false
                def needsFilter = true
                def filtered = false
                manifest.eachLine { line ->
                    if (!needsFilter && !filtered) return

                    while (true) { // fake loop for less `if ... else' statement
                        if (!needsFilter) break

                        def i = line.indexOf('<application')
                        if (i < 0) {
                            if (!enteredApplicationNode) break

                            if (line.indexOf('>') > 0) needsFilter = false

                            // filter `android:name'
                            if (line.indexOf('android:name') > 0) {
                                filtered = true
                                if (needsFilter) return

                                line = '>'
                            }
                            break
                        }

                        def j = line.indexOf('<!--')
                        if (j > 0 && j < i) break // ignores the comment line

                        if (line.indexOf('>') > 0) { // <application /> or <application .. > in one line
                            needsFilter = false
                            def k = line.indexOf('android:name="')
                            if (k > 0) {
                                filtered = true
                                def k_ = line.indexOf('"', k + 15) // bypass 'android:name='
                                line = line.substring(0, k) + line.substring(k_ + 1)
                            }
                            break
                        }

                        enteredApplicationNode = true // mark this for next line
                        break
                    }

                    sb.append(line).append(System.lineSeparator())
                }

                if (filtered) {
                    def backupManifest = new File(manifest.parentFile, "${manifest.name}~")
                    manifest.renameTo(backupManifest)
                    manifest.write(sb.toString(), 'utf-8')
                    filteredManifests.add(overwrite: manifest, backup: backupManifest)
                }
            }
            ext.filteredManifests = filteredManifests
        }
        processDebugManifest.doLast {
            ext.filteredManifests.each {
                it.backup.renameTo(it.overwrite)
            }
        }
    }

    @Override
    protected void configureReleaseVariant(BaseVariant variant) {
        super.configureReleaseVariant(variant)

        // Fill extensions
        def variantName = variant.name.capitalize()
        File mergerDir = variant.mergeResources.incrementalFolder

        small.with {
            javac = variant.javaCompile
            processManifest = project.tasks["process${variantName}Manifest"]

            packageName = variant.applicationId
            packagePath = packageName.replaceAll('\\.', '/')
            classesDir = javac.destinationDir
            bkClassesDir = new File(classesDir.parentFile, "${classesDir.name}~")

            aapt = (ProcessAndroidResources) project.tasks["process${variantName}Resources"]
            apFile = aapt.packageOutputFile

            File symbolDir = aapt.textSymbolOutputDir
            File sourceDir = aapt.sourceOutputDir

            symbolFile = new File(symbolDir, 'R.txt')
            rJavaFile = new File(sourceDir, "${packagePath}/R.java")

            splitRJavaFile = new File(sourceDir.parentFile, "small/${packagePath}/R.java")

            mergerXml = new File(mergerDir, 'merger.xml')
        }

        hookVariantTask(variant)
    }

    @Override
    protected void configureProguard(BaseVariant variant, TransformTask proguard, ProGuardTransform pt) {
        super.configureProguard(variant, proguard, pt)

        // Keep R.*
        // FIXME: the `configuration' field is protected, may be depreciated
        pt.configuration.keepAttributes = ['InnerClasses']
        pt.keep("class ${variant.applicationId}.R")
        pt.keep("class ${variant.applicationId}.R\$* { <fields>; }")

        // Add reference libraries
        proguard.doFirst {
            getLibraryJars().findAll{ it.exists() }.each {
                // FIXME: the `libraryJar' method is protected, may be depreciated
                pt.libraryJar(it)
            }
        }
        // Split R.class
        proguard.doLast {
            if (small.splitRJavaFile == null || !small.splitRJavaFile.exists()) {
                return
            }

            def minifyJar = IntermediateFolderUtils.getContentLocation(
                    proguard.streamOutputFolder, 'main', pt.outputTypes, pt.scopes, Format.JAR)
            if (!minifyJar.exists()) return

            mMinifyJar = minifyJar // record for `LibraryPlugin'

            Log.success("[$project.name] Strip aar classes...")

            // Unpack the minify jar to split the R.class
            File unzipDir = new File(minifyJar.parentFile, 'main')
            project.copy {
                from project.zipTree(minifyJar)
                into unzipDir
            }

            def javac = small.javac
            File pkgDir = new File(unzipDir, small.packagePath)

            // Delete the original generated R$xx.class
            pkgDir.listFiles().each { f ->
                if (f.name.startsWith('R$')) {
                    f.delete()
                }
            }

            // Re-compile the split R.java to R.class
            project.ant.javac(srcdir: small.splitRJavaFile.parentFile,
                    source: javac.sourceCompatibility,
                    target: javac.targetCompatibility,
                    destdir: unzipDir)

            // Repack the minify jar
            project.ant.zip(baseDir: unzipDir, destFile: minifyJar)

            Log.success "[${project.name}] split R.class..."
        }
    }

    /** Collect the vendor aars (has resources) compiling in current bundle */
    protected void collectVendorAars(Set<ResolvedDependency> outFirstLevelAars,
                                     Set<Map> outTransitiveAars) {
        project.configurations.compile.resolvedConfiguration.firstLevelModuleDependencies.each {
            collectVendorAars(it, outFirstLevelAars, outTransitiveAars)
        }
    }

    protected boolean collectVendorAars(ResolvedDependency node,
                                        Set<ResolvedDependency> outFirstLevelAars,
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
        def aar = [path: path, name: node.name, version: version]
        def resDir = new File(small.aarDir, "$path/res")
        // If the dependency has resources, collect it
        if (resDir.exists() && resDir.list().size() > 0) {
            if (outFirstLevelAars != null && !outFirstLevelAars.contains(node)) {
                outFirstLevelAars.add(node)
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

        if (outFirstLevelAars != null && !outFirstLevelAars.contains(node)) {
            outFirstLevelAars.add(node)
        }
        return true
    }

    protected void collectTransitiveAars(ResolvedDependency node,
                                         Set<ResolvedDependency> outAars) {
        def group = node.moduleGroup,
            name = node.moduleName

        if (small.splitAars.find { aar -> group == aar.group && name == aar.name } == null) {
            outAars.add(node)
        }

        node.children.each {
            collectTransitiveAars(it, outAars)
        }
    }

    /**
     * Prepare retained resource types and resource id maps for package slicing
     */
    protected void prepareSplit() {
        def idsFile = small.symbolFile
        if (!idsFile.exists()) return

        // Check if has any vendor aars
        def firstLevelVendorAars = [] as Set<ResolvedDependency>
        def transitiveVendorAars = [] as Set<Map>
        collectVendorAars(firstLevelVendorAars, transitiveVendorAars)
        if (firstLevelVendorAars.size() > 0) {
            if (rootSmall.strictSplitResources) {
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
                Set<ResolvedDependency> reservedAars = new HashSet<>()
                firstLevelVendorAars.each {
                    Log.warn("Using vendor aar '$it.name'")

                    // If we don't split the aar then we should reserved all it's transitive aars.
                    collectTransitiveAars(it, reservedAars)
                }
                reservedAars.each {
                    mUserLibAars.add(group: it.moduleGroup, name: it.moduleName, version: it.moduleVersion)
                }
            }
        }

        // Add user retained aars for generating their R.java, fix #194
        if (small.retainedAars != null) {
            transitiveVendorAars.addAll(small.retainedAars.collect {
                [path: "$it.group/$it.name/$it.version", version: it.version]
            })
        }

        // Prepare id maps (bundle resource id -> library resource id)
        // Map to `lib.**` resources id first, and then the host one.
        def libEntries = [:]
        File hostSymbol = new File(rootSmall.preIdsDir, "${rootSmall.hostModuleName}-R.txt")
        if (hostSymbol.exists()) {
            libEntries += SymbolParser.getResourceEntries(hostSymbol)
        }
        mTransitiveDependentLibProjects.each {
            File libSymbol = new File(it.projectDir, 'public.txt')
            libEntries += SymbolParser.getResourceEntries(libSymbol)
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
            throw new RuntimeException("No support deleting resources on lib.* now!\n" +
                    "  - ${publicEntries.keySet().join(", ")}\n" +
                    "see https://github.com/wequick/Small/issues/53 for more information.")

//            publicEntries.each { k, e ->
//                e._typeId = e.typeId
//                e._entryId = e.entryId
//                e.entryId = Aapt.ID_DELETED
//
//                def re = retainedPublicEntries.find{it.type == e.type}
//                e.typeId = (re != null) ? re.typeId : Aapt.ID_DELETED
//            }
//            publicEntries.each { k, e ->
//                retainedPublicEntries.add(e)
//            }
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

        // Collect vendor types and styleables
        def vendorEntries = new HashMap<String, HashSet<SymbolParser.Entry>>()
        def vendorStyleableKeys = new HashMap<String, HashSet<String>>()
        transitiveVendorAars.each { aar ->
            String path = aar.path
            File aarPath = new File(small.aarDir, path)
            String resPath = new File(aarPath, 'res').absolutePath
            File symbol = new File(aarPath, 'R.txt')
            Set<SymbolParser.Entry> resTypeEntries = new HashSet<>()
            Set<String> resStyleableKeys = new HashSet<>()

            // Collect the resource entries declared in the aar res directory
            // This is all the arr's own resource: `R.layout.*', `R.string.*' and etc.
            collectReservedResourceKeys(aar.version, resPath, resTypeEntries, resStyleableKeys)

            // Collect the id entries for the aar, fix #230
            // This is all the aar id references: `R.id.*'
            def idEntries = []
            def libIdKeys = []
            libEntries.each { k, v ->
                if (v.type == 'id') {
                    libIdKeys.add(v.key)
                }
            }
            SymbolParser.collectResourceKeys(symbol, 'id', libIdKeys, idEntries, null)
            resTypeEntries.addAll(idEntries)

            // Collect the resource references from *.class
            // This is all the aar coding-referent fields: `R.*.*'
            // We had to parse this cause the aar maybe referenced to the other external aars like
            // `AppCompat' and so on, so that we should keep those external `R.*.*' for current aar.
            // Fix issue #271.
            File jar = new File(aarPath, 'jars/classes.jar')
            if (jar.exists()) {
                def codedTypeEntries = []
                def codedStyleableKeys = []

                File aarSymbolsDir = new File(small.aarDir.parentFile, 'small-symbols')
                File refDir = new File(aarSymbolsDir, path)
                File refFile = new File(refDir, 'R.txt')
                if (refFile.exists()) {
                    // Parse from file
                    SymbolParser.collectAarResourceKeys(refFile, codedTypeEntries, codedStyleableKeys)
                } else {
                    // Parse classes
                    if (!refDir.exists()) refDir.mkdirs()

                    File unzipDir = new File(refDir, 'classes')
                    project.copy {
                        from project.zipTree(jar)
                        into unzipDir
                    }
                    Set<Map> resRefs = []
                    unzipDir.eachFileRecurse(FileType.FILES, {
                        if (!it.name.endsWith('.class')) return

                        ClassFileUtils.collectResourceReferences(it, resRefs)
                    })

                    // TODO: read the aar package name once and store
                    File manifestFile = new File(aarPath, 'AndroidManifest.xml')
                    def manifest = new XmlParser().parse(manifestFile)
                    String aarPkg = manifest.@package.replaceAll('\\.', '/')

                    def pw = new PrintWriter(new FileWriter(refFile))
                    resRefs.each {
                        if (it.pkg != aarPkg) {
                            println "Unresolved refs: $it.pkg/$it.type/$it.name for $aarPkg"
                            return
                        }

                        def type = it.type
                        def name = it.name
                        def key = "$type/$name"
                        if (type == 'styleable') {
                            codedStyleableKeys.add(type)
                        } else {
                            codedTypeEntries.add(new SymbolParser.Entry(type, name))
                        }
                        pw.println key
                    }
                    pw.flush()
                    pw.close()
                }

                resTypeEntries.addAll(codedTypeEntries)
                resStyleableKeys.addAll(codedStyleableKeys)
            }

            vendorEntries.put(path, resTypeEntries)
            vendorStyleableKeys.put(path, resStyleableKeys)
        }

        def vendorTypes = new HashMap<String, List<Map>>()
        def vendorStyleables = [:]
        vendorEntries.each { name, es ->
            if (es.isEmpty()) return

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
            if (vs.isEmpty()) return

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

        def jniDirs = android.sourceSets.main.jniLibs.srcDirs
        if (jniDirs == null) jniDirs = []
        // Collect ABIs from AARs
        small.explodeAarDirs.each { dir ->
            File jniDir = new File(dir, 'jni')
            if (!jniDir.exists()) return
            jniDirs.add(jniDir)
        }
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

    protected void hookVariantTask(BaseVariant variant) {
        hookMergeAssets(variant.mergeAssets)

        hookProcessManifest(small.processManifest)

        hookAapt(small.aapt)

        hookJavac(small.javac, variant.buildType.minifyEnabled)

        def mergeJniLibsTask = project.tasks.withType(TransformTask.class).find {
            it.transform.name == 'mergeJniLibs' && it.variantName == variant.name
        }
        hookMergeJniLibs(mergeJniLibsTask)

        // Hook clean task to unset package id
        project.clean.doLast {
            sPackageIds.remove(project.name)
        }
    }

    /**
     * Hook merge-jniLibs task to ignores the lib.* native libraries
     * TODO: filter the native libraries while exploding aar
     */
    def hookMergeJniLibs(TransformTask t) {
        stripAarFiles(t, { splitPaths ->
            t.streamInputs.each {
                def version = it.parentFile
                def name = version.parentFile
                def group = name.parentFile
                def root = group.parentFile
                if (root.name != 'exploded-aar') return

                def aar = [group: group.name, name: name.name, version: version.name]
                if (mUserLibAars.contains(aar)) {
                    // keep the user libraries
                    return
                }

                splitPaths.add(it)
            }
        })
    }

    /**
     * Hook merge-assets task to ignores the lib.* assets
     * TODO: filter the assets while exploding aar
     */
    private void hookMergeAssets(MergeSourceSetFolders t) {
        stripAarFiles(t, { paths ->
            t.inputDirectorySets.each {
                if (it.configName == 'main' || it.configName == 'release') return

                it.sourceFiles.each {
                    def version = it.parentFile
                    def name = version.parentFile
                    def group = name.parentFile
                    def aar = [group: group.name, name: name.name, version: version.name]
                    if (mUserLibAars.contains(aar)) return

                    paths.add(it)
                }
            }
        })
    }

    /**
     * A hack way to strip aar files:
     *  - Strip the task inputs before the task execute
     *  - Restore the inputs after the task executed
     * by what the task doesn't know what happen, and will be considered as 'UP-TO-DATE'
     * at next time it be called. This means a less I/O.
     * @param t the task who will merge aar files
     * @param closure the function to gather all the paths to be stripped
     */
    private static void stripAarFiles(Task t, Closure closure) {
        t.doFirst {
            List<File> stripPaths = []
            closure(stripPaths)

            Set<Map> strips = []
            stripPaths.each {
                def backup = new File(it.parentFile, "$it.name~")
                strips.add(org: it, backup: backup)
                it.renameTo(backup)
            }
            it.extensions.add('strips', strips)
        }
        t.doLast {
            Set<Map> strips = (Set<Map>) it.extensions.getByName('strips')
            strips.each {
                it.backup.renameTo(it.org)
            }
        }
    }

    protected static void collectAars(File d, Project src, Set outAars) {
        if (!d.exists()) return

        d.eachLine { line ->
            def module = line.split(':')
            def N = module.size()
            def aar = [group: module[0], name: module[1], version: (N == 3) ? module[2] : '']
            if (!outAars.contains(aar)) {
                outAars.add(aar)
            }
        }
    }

    protected void collectLibProjects(Project project, Set<Project> outLibProjects) {
        DependencySet compilesDependencies = project.configurations.compile.dependencies
        Set<DefaultProjectDependency> allLibs = compilesDependencies.withType(DefaultProjectDependency.class)
        allLibs.each {
            def dependency = it.dependencyProject
            if (rootSmall.isLibProject(dependency)) {
                outLibProjects.add(dependency)
                collectLibProjects(dependency, outLibProjects)
            }
        }
    }

    @Override
    protected void hookPreReleaseBuild() {
        super.hookPreReleaseBuild()

        // Ensure generating text symbols - R.txt
        // --------------------------------------
        def symbolsPath = small.aapt.textSymbolOutputDir.path
        android.aaptOptions.additionalParameters '--output-text-symbols', symbolsPath

        // Resolve dependent AARs
        // ----------------------
        def smallLibAars = new HashSet() // the aars compiled in host or lib.*

        // Collect transitive dependent `lib.*' projects
        mTransitiveDependentLibProjects = new HashSet<>()
        mTransitiveDependentLibProjects.addAll(mDependentLibProjects)
        mDependentLibProjects.each {
            collectLibProjects(it, mTransitiveDependentLibProjects)
        }

        // Collect aar(s) in lib.*
        mTransitiveDependentLibProjects.each { lib ->
            // lib.* dependencies
            collectAarsOfProject(lib, smallLibAars)

            // lib.* self
            smallLibAars.add(group: lib.group, name: lib.name, version: lib.version)
        }

        // Collect aar(s) in host
        collectAarsOfProject(rootSmall.hostProject, smallLibAars)

        small.splitAars = smallLibAars
        small.retainedAars = mUserLibAars
    }

    protected def collectAarsOfProject(Project project, HashSet outAars) {
        String dependenciesFileName = "$project.name-D.txt"

        // Pure aars
        File file = new File(rootSmall.preLinkAarDir, dependenciesFileName)
        collectAars(file, project, outAars)

        // Jar-only aars
        file = new File(rootSmall.preLinkJarDir, dependenciesFileName)
        collectAars(file, project, outAars)
    }

    private def hookProcessManifest(Task processManifest) {
        // If an app.A dependent by lib.B and both of them declare application@name in their
        // manifests, the `processManifest` task will raise an conflict error.
        // Cause the release mode doesn't need to merge the manifest of lib.*, simply split
        // out the manifest dependencies from them.
        processManifest.doFirst { MergeManifests it ->
            if (pluginType != PluginType.App) return

            def libs = it.libraries
            def smallLibs = []
            libs.each {
                def components = it.name.split(':') // e.g. 'Sample:lib.style:unspecified'
                if (components.size() != 3) return

                def projectName = components[1]
                if (!rootSmall.isLibProject(projectName)) return

                smallLibs.add(it)
            }
            libs.removeAll(smallLibs)
            it.libraries = libs
        }
        // Hook process-manifest task to remove the `android:icon' and `android:label' attribute
        // which declared in the plugin `AndroidManifest.xml' application node. (for #11)
        processManifest.doLast { MergeManifests it ->
            File manifestFile = it.manifestOutputFile
            def sb = new StringBuilder()
            def enteredApplicationNode = false
            def needsFilter = true
            def filterKeys = [
                    'android:icon', 'android:label',
                    'android:allowBackup', 'android:supportsRtl'
            ]

            // We don't use XmlParser but simply parse each line cause this should be faster
            manifestFile.eachLine { line ->
                while (true) { // fake loop for less `if ... else' statement
                    if (!needsFilter) break

                    def i = line.indexOf('<application')
                    if (i < 0) {
                        if (!enteredApplicationNode) break

                        int endPos = line.indexOf('>')
                        if (endPos > 0) needsFilter = false

                        // filter unused keys
                        def filtered = false
                        filterKeys.each {
                            if (line.indexOf(it) > 0) {
                                filtered = true
                                return
                            }
                        }
                        if (filtered) {
                            if (needsFilter) return

                            if (line.charAt(endPos - 1) == '/' as char) {
                                line = '/>'
                            } else {
                                line = '>'
                            }
                        }
                        break
                    }

                    def j = line.indexOf('<!--')
                    if (j > 0 && j < i) break // ignores the comment line

                    if (line.indexOf('>') > 0) { // <application /> or <application .. > in one line
                        needsFilter = false
                        break
                    }

                    enteredApplicationNode = true // mark this for next line
                    break
                }

                sb.append(line).append(System.lineSeparator())
            }
            manifestFile.write(sb.toString(), 'utf-8')
        }
    }

    /**
     * Hook aapt task to slice asset package and resolve library resource ids
     */
    private def hookAapt(ProcessAndroidResources aaptTask) {
        aaptTask.doLast { ProcessAndroidResources it ->
            // Unpack resources.ap_
            File apFile = it.packageOutputFile
            FileTree apFiles = project.zipTree(apFile)
            File unzipApDir = new File(apFile.parentFile, 'ap_unzip')
            unzipApDir.delete()
            project.copy {
                from apFiles
                into unzipApDir

                include 'AndroidManifest.xml'
                include 'resources.arsc'
                include 'res/**/*'
            }

            // Modify assets
            prepareSplit()
            File symbolFile = (small.type == PluginType.Library) ?
                    new File(it.textSymbolOutputDir, 'R.txt') : null
            File sourceOutputDir = it.sourceOutputDir
            File rJavaFile = new File(sourceOutputDir, "${small.packagePath}/R.java")
            def rev = android.buildToolsRevision
            int noResourcesFlag = 0
            def filteredResources = new HashSet()
            def updatedResources = new HashSet()

            // Collect the DynamicRefTable [pkgId => pkgName]
            def libRefTable = [:]
            mTransitiveDependentLibProjects.each {
                def libAapt = it.tasks.withType(ProcessAndroidResources.class).find {
                    it.variantName.startsWith('release')
                }
                def pkgName = libAapt.packageForR
                def pkgId = sPackageIds[it.name]
                libRefTable.put(pkgId, pkgName)
            }

            Aapt aapt = new Aapt(unzipApDir, rJavaFile, symbolFile, rev)
            if (small.retainedTypes != null && small.retainedTypes.size() > 0) {
                aapt.filterResources(small.retainedTypes, filteredResources)
                Log.success "[${project.name}] split library res files..."

                aapt.filterPackage(small.retainedTypes, small.packageId, small.idMaps, libRefTable,
                        small.retainedStyleables, updatedResources)

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
                    // TODO: read the aar package name once and store
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
                noResourcesFlag = 1
                if (aapt.deleteResourcesDir(filteredResources)) {
                    Log.success "[${project.name}] remove resources dir..."
                }

                if (aapt.deletePackage(filteredResources)) {
                    Log.success "[${project.name}] remove resources.arsc..."
                }

                if (sourceOutputDir.deleteDir()) {
                    Log.success "[${project.name}] remove R.java..."
                }

                small.symbolFile.delete() // also delete the generated R.txt
            }

            int abiFlag = getABIFlag()
            int flags = (abiFlag << 1) | noResourcesFlag
            if (aapt.writeSmallFlags(flags, updatedResources)) {
                Log.success "[${project.name}] add flags: ${Integer.toBinaryString(flags)}..."
            }

            String aaptExe = small.aapt.buildTools.getPath(BuildToolInfo.PathId.AAPT)

            // Delete filtered entries.
            // Cause there is no `aapt update' command supported, so for the updated resources
            // we also delete first and run `aapt add' later.
            filteredResources.addAll(updatedResources)
            ZipUtils.with(apFile).deleteAll(filteredResources)

            // Re-add updated entries.
            // $ aapt add resources.ap_ file1 file2 ...
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

    /**
     * Hook javac task to split libraries' R.class
     */
    private def hookJavac(Task javac, boolean minifyEnabled) {
        javac.doFirst { JavaCompile it ->
            // Dynamically provided jars
            it.classpath += project.files(getLibraryJars().findAll{ it.exists() })
        }
        javac.doLast { JavaCompile it ->
            if (minifyEnabled) return // process later in proguard task
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
    }

    /**
     * Get reserved resource keys of project. For making a smaller slice, the unnecessary
     * resource `mipmap/ic_launcher' and `string/app_name' are excluded.
     */
    protected def getReservedResourceKeys() {
        Set<SymbolParser.Entry> outTypeEntries = new HashSet<>()
        Set<String> outStyleableKeys = new HashSet<>()
        collectReservedResourceKeys(null, null, outTypeEntries, outStyleableKeys)
        def keys = []
        outTypeEntries.each {
            keys.add("$it.type/$it.name")
        }
        outStyleableKeys.each {
            keys.add("styleable/$it")
        }
        return keys
    }

    protected void collectReservedResourceKeys(config, path,
                                               Set<SymbolParser.Entry> outTypeEntries,
                                               Set<String> outStyleableKeys) {
        def merger = new XmlParser().parse(small.mergerXml)
        def filter = config == null ? {
            it.@config == 'main' || it.@config == 'release'
        } : {
            it.@config = config
        }
        def dataSets = merger.dataSet.findAll filter
        dataSets.each { // <dataSet config="main" generated-set="main$Generated">
            it.source.each { // <source path="**/${project.name}/src/main/res">
                if (path != null && it.@path != path) return

                it.file.each {
                    String type = it.@type
                    if (type != null) { // <file name="activity_main" ... type="layout"/>
                        def name = it.@name
                        if (type == 'mipmap' && name == 'ic_launcher') return // NO NEED IN BUNDLE
                        def key = new SymbolParser.Entry(type, name) // layout/activity_main
                        outTypeEntries.add(key)
                        return
                    }

                    it.children().each {
                        type = it.name()
                        String name = it.@name
                        if (type == 'string') {
                            if (name == 'app_name') return // DON'T NEED IN BUNDLE
                        } else if (type == 'style') {
                            name = name.replaceAll("\\.", "_")
                        } else if (type == 'declare-styleable') {
                            // <declare-styleable name="MyTextView">
                            it.children().each { // <attr format="string" name="label"/>
                                def attr = it.@name
                                if (attr.startsWith('android:')) {
                                    attr = attr.replaceAll(':', '_')
                                } else {
                                    def key = new SymbolParser.Entry('attr', attr)
                                    outTypeEntries.add(key)
                                }
                                String key = "${name}_${attr}"
                                outStyleableKeys.add(key)
                            }
                            outStyleableKeys.add(name)
                            return
                        } else if (type.endsWith('-array')) {
                            // string-array or integer-array
                            type = 'array'
                        }

                        def key = new SymbolParser.Entry(type, name)
                        outTypeEntries.add(key)
                    }
                }
            }
        }
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
