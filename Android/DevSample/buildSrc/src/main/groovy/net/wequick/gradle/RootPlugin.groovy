package net.wequick.gradle

import net.wequick.gradle.aapt.SymbolParser
import net.wequick.gradle.tasks.LintTask
import net.wequick.gradle.util.DependenciesUtils
import net.wequick.gradle.util.Log
import org.gradle.api.Project
import org.gradle.api.tasks.Delete

import java.text.DecimalFormat

class RootPlugin extends BasePlugin {

    private int buildingLibIndex = 0
    private Map<String, Set<String>> bundleModules = [:]

    void apply(Project project) {
        super.apply(project)
    }

    @Override
    protected Class<? extends BaseExtension> getExtensionClass() {
        return RootExtension.class
    }

    RootExtension getSmall() {
        return (RootExtension) project.small
    }

    @Override
    protected void configureProject() {
        super.configureProject()

        def rootExt = small

        rootExt.appProjects = new HashSet<>()
        rootExt.libProjects = new HashSet<>()
        rootExt.hostStubProjects = new HashSet<>()
        AppPlugin.sPackageIds = [:]

        project.afterEvaluate {

            def userBundleTypes = [:]
            rootExt.bundleModules.each { type, names ->
                names.each {
                    userBundleTypes.put(it, type)
                }
            }

            // Configure versions
            def base = rootExt.android
            if (base != null) {
                project.subprojects { p ->
                    p.afterEvaluate {
                        configVersions(p, base)
                    }
                }
            }

            // Configure sub projects
            project.subprojects {
                if (it.name == 'small') {
                    rootExt.smallProject = it
                    return
                }

                if (it.name == rootExt.hostModuleName) {
                    // Host
                    it.apply plugin: HostPlugin
                    rootExt.outputBundleDir = new File(it.projectDir, SMALL_LIBS)
                    rootExt.hostProject = it
                } else if (it.name.startsWith('app+')) {
                    rootExt.hostStubProjects.add(it)
                    return
                } else {
                    String type = userBundleTypes.get(it.name)
                    if (type == null) {
                        def idx = it.name.indexOf('.')
                        if (idx < 0) return

                        type = it.name.substring(0, idx)
                    }

                    switch (type) {
                        case 'app':
                            it.apply plugin: AppPlugin
                            rootExt.appProjects.add(it)
                            break;
                        case 'stub':
                            rootExt.hostStubProjects.add(it)
                            return;
                        case 'lib':
                            it.apply plugin: LibraryPlugin
                            rootExt.libProjects.add(it)
                            break;
                        case 'web':
                        default: // Default to Asset
                            it.apply plugin: AssetPlugin
                            break;
                    }

                    // Collect for log
                    def modules = bundleModules.get(type)
                    if (modules == null) {
                        modules = new HashSet<String>()
                        bundleModules.put(type, modules)
                    }
                    modules.add(it.name)
                }

                // Hook on project build started and finished for log
                // FIXME: any better way to hooks?
                it.afterEvaluate {
                    it.tasks['preBuild'].doFirst {
                        logStartBuild(it.project)
                        if (it.project.hasProperty('assembleRelease')) {
                            it.project.tasks['assembleRelease'].doLast {
                                logFinishBuild(it.project)
                            }
                        }
                    }
                }

                if (it.hasProperty('buildLib')) {
                    it.small.buildIndex = ++rootExt.libCount
                    it.tasks['buildLib'].doLast {
                        buildLib(it.project)
                    }
                } else if (it.hasProperty('buildBundle')) {
                    it.small.buildIndex = ++rootExt.bundleCount
                }
            }

            if (rootExt.hostProject == null) {
                throw new RuntimeException(
                        "Cannot find host module with name: '${rootExt.hostModuleName}'!")
            }

            if (!rootExt.hostStubProjects.empty) {
                rootExt.hostStubProjects.each { stub ->
                    rootExt.hostProject.afterEvaluate {
                        it.dependencies.add('compile', stub)
                    }
                    rootExt.appProjects.each {
                        it.afterEvaluate {
                            it.dependencies.add('compile', stub)
                        }
                    }
                    rootExt.libProjects.each {
                        it.afterEvaluate {
                            it.dependencies.add('compile', stub)
                        }
                    }
                }
            }
        }
    }

    protected void configVersions(Project p, RootExtension.AndroidConfig base) {
        if (!p.hasProperty('android')) return

        com.android.build.gradle.BaseExtension android = p.android
        if (base.compileSdkVersion != 0) {
            android.compileSdkVersion = base.compileSdkVersion
        }
        if (base.buildToolsVersion != null) {
            android.buildToolsVersion = base.buildToolsVersion
        }
        if (base.supportVersion != null) {
            def sv = base.supportVersion
            def cfg = p.configurations.compile
            def supportDependencies = []
            cfg.dependencies.each { d ->
                if (d.group == 'com.android.support' && d.version != sv) {
                    supportDependencies.add(d)
                }
            }
            cfg.dependencies.removeAll(supportDependencies)
            supportDependencies.each { d ->
                p.dependencies.add('compile', "$d.group:$d.name:$sv")
            }
        }
    }

    @Override
    protected void createTask() {
        super.createTask()
        project.task('cleanLib', group: 'small', description: 'Clean all libraries', type: Delete) {
            delete small.preBuildDir
        }
        project.task('buildLib', group: 'small', description: 'Build all libraries').doFirst {
            buildingLibIndex = 1
        }
        project.task('cleanBundle', group: 'small', description: 'Clean all bundles')
        project.task('buildBundle', group: 'small', description: 'Build all bundles')

        project.task('small') << {

            println()
            println '### Compile-time'
            println ''
            println '```'

            // gradle-small
            print String.format('%24s', 'gradle-small plugin : ')
            def pluginVersion
            def pluginProperties = project.file('buildSrc/gradle.properties')
            if (pluginProperties.exists()) {
                def prop = new Properties()
                prop.load(pluginProperties.newDataInputStream())
                pluginVersion = prop.getProperty('version')
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
            print String.format('%24s', 'small aar : ')
            if (small.smallProject != null) {
                def prop = new Properties()
                prop.load(small.smallProject.file('gradle.properties').newDataInputStream())
                println "${prop.getProperty('version')} (project)"
            } else {
                def aarVersion
                try {
                    aarVersion = small.aarVersion
                } catch (Exception e) {
                    aarVersion = 'unspecific'
                }
                def module = small.hostProject.configurations.compile
                        .resolvedConfiguration.firstLevelModuleDependencies.find {
                    it.moduleGroup == 'net.wequick.small' && it.moduleName == 'small'
                }
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
            if (!small.buildToAssets) {
                out = new File(small.outputBundleDir, 'armeabi')
                if (!out.exists()) {
                    out = new File(small.outputBundleDir, 'x86')
                }
                if (out.exists()) {
                    fileTitle += "($out.name)"
                }
            }
            rows.add(['type', 'name', 'PP', 'sdk', 'aapt', 'support', fileTitle, 'size'])
            def vs = getVersions(small.hostProject)
            rows.add(['host', small.hostModuleName, '', vs.sdk, vs.aapt, vs.support, '', ''])
            small.hostStubProjects.each {
                vs = getVersions(it)
                rows.add(['stub', it.name, '', vs.sdk, vs.aapt, vs.support, '', ''])
            }
            bundleModules.each { type, names ->
                names.each {
                    def file = null
                    def fileName = null
                    def prj = project.rootProject.project(":$it")
                    vs = getVersions(prj)
                    if (out.exists()) {
                        def manifest = new XmlParser().parse(prj.android.sourceSets.main.manifestFile)
                        def pkg = manifest.@package
                        if (small.buildToAssets) {
                            file = new File(out, "${pkg}.apk")
                            fileName = '*.' + pkg.split('\\.').last() + '.apk'
                        } else {
                            fileName = "lib${pkg.replaceAll('\\.', '_')}.so"
                            file = new File(out, fileName)
                            fileName = '*_' + file.name.split('_').last()
                        }
                    }
                    def pp = AppPlugin.sPackageIds.get(it)
                    pp = (pp == null) ? '' : String.format('0x%02x', pp)
                    if (file != null && file.exists()) {
                        rows.add([type, it, pp, vs.sdk, vs.aapt, vs.support, fileName, getFileSize(file)])
                    } else {
                        rows.add([type, it, pp, vs.sdk, vs.aapt, vs.support, '', ''])
                    }
                }
            }

            printRows(rows)
            println()
        }

        project.afterEvaluate {
            small.hostProject.afterEvaluate {
                def flavorName = 'Release'
                com.android.build.gradle.AppExtension android = it.android
                if (android.productFlavors.size() > 0) {
                    flavorName = android.productFlavors[0].name.capitalize() + 'Release'
                }

                def hostDexTaskName = ":app:transformClassesWithDexFor$flavorName"
                project.task('smallLint',
                        type: LintTask,
                        dependsOn: [hostDexTaskName]) {
                    rootSmall = small
                }
            }
        }
    }

    static def getVersions(Project p) {
        com.android.build.gradle.BaseExtension android = p.android
        def sdk = android.getCompileSdkVersion()
        if (sdk.startsWith('android-')) {
            sdk = sdk.substring(8) // bypass 'android-'
        }
        def cfg = p.configurations.compile
        def supportLib = cfg.dependencies.find { d ->
            d.group == 'com.android.support'
        }
        def supportVer = supportLib != null ? supportLib.version : ''
        return [sdk: sdk,
                aapt: android.buildToolsVersion,
                support: supportVer]
    }

    static void printRows(List rows) {
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
                def col = row[j]
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
                String col = row[j]
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

    void buildLib(Project lib) {
        def libName = lib.name
        def ext = (AndroidExtension) lib.small

        // Copy jars
        def preJarDir = small.preBaseJarDir
        if (!preJarDir.exists()) preJarDir.mkdirs()
        //  - copy package.R jar
        if (ext.jar != null) {
            def rJar = ext.jar.archivePath
            project.copy {
                from rJar
                into preJarDir
                rename {"$libName-r.jar"}
            }
        }
        //  - copy dependencies jars
        ext.explodeAarDirs.each {
            // explodedDir: **/exploded-aar/$group/$artifact/$version
            File version = it
            File jarDir = new File(version, 'jars')
            File jarFile = new File(jarDir, 'classes.jar')
            if (!jarFile.exists()) return

            File artifact = version.parentFile
            File group = artifact.parentFile
            File destFile = new File(preJarDir,
                    "${group.name}-${artifact.name}-${version.name}.jar")
            if (destFile.exists()) return

            project.copy {
                from jarFile
                into preJarDir
                rename {destFile.name}
            }

            // Check if exists `jars/libs/*.jar' and copy
            File libDir = new File(jarDir, 'libs')
            libDir.listFiles().each { jar ->
                if (!jar.name.endsWith('.jar')) return

                destFile = new File(preJarDir, "${group.name}-${artifact.name}-${jar.name}")
                if (destFile.exists()) return

                project.copy {
                    from jar
                    into preJarDir
                    rename {destFile.name}
                }
            }
        }

        // Copy *.ap_
        def aapt = ext.aapt
        def preApDir = small.preApDir
        if (!preApDir.exists()) preApDir.mkdir()
        def apFile = aapt.packageOutputFile
        def preApName = "$libName-resources.ap_"
        project.copy {
            from apFile
            into preApDir
            rename {preApName}
        }
        // Copy R.txt
        def preIdsDir = small.preIdsDir
        if (!preIdsDir.exists()) preIdsDir.mkdir()
        def srcIdsFile = new File(aapt.textSymbolOutputDir, 'R.txt')
        if (srcIdsFile.exists()) {
            def idsFileName = "${libName}-R.txt"
            def keysFileName = 'R.keys.txt'
            def dstIdsFile = new File(preIdsDir, idsFileName)
            def keysFile = new File(preIdsDir, keysFileName)
            def addedKeys = []
            if (keysFile.exists()) {
                keysFile.eachLine { s ->
                    addedKeys.add(SymbolParser.getResourceDeclare(s))
                }
            }
            def idsPw = new PrintWriter(dstIdsFile.newWriter(true)) // true=append mode
            def keysPw = new PrintWriter(keysFile.newWriter(true))
            srcIdsFile.eachLine { s ->
                def key = SymbolParser.getResourceDeclare(s)
                if (addedKeys.contains(key)) return
                idsPw.println(s)
                keysPw.println(key)
            }
            idsPw.flush()
            idsPw.close()
            keysPw.flush()
            keysPw.close()
        }

        // Backup dependencies
        if (!small.preLinkAarDir.exists()) small.preLinkAarDir.mkdirs()
        if (!small.preLinkJarDir.exists()) small.preLinkJarDir.mkdirs()
        def linkFileName = "$libName-D.txt"
        File aarLinkFile = new File(small.preLinkAarDir, linkFileName)
        File jarLinkFile = new File(small.preLinkJarDir, linkFileName)

        def allDependencies = DependenciesUtils.getAllDependencies(lib, 'compile')
        if (allDependencies.size() > 0) {
            def aarKeys = []
            if (!aarLinkFile.exists()) {
                aarLinkFile.createNewFile()
            } else {
                aarLinkFile.eachLine {
                    aarKeys.add(it)
                }
            }

            def jarKeys = []
            if (!jarLinkFile.exists()) {
                jarLinkFile.createNewFile()
            } else {
                jarLinkFile.eachLine {
                    jarKeys.add(it)
                }
            }

            def aarPw = new PrintWriter(aarLinkFile.newWriter(true))
            def jarPw = new PrintWriter(jarLinkFile.newWriter(true))

            // Cause the later aar(as fresco) may dependent by 'com.android.support:support-compat'
            // which would duplicate with the builtin 'appcompat' and 'support-v4' library in host.
            // Hereby we also mark 'support-compat' has compiled in host.
            // FIXME: any influence of this?
            if (lib == small.hostProject) {
                aarPw.println "com.android.support:support-compat:+"
                aarPw.println "com.android.support:support-core-utils:+"
            }

            allDependencies.each { d ->
                def isAar = true
                d.moduleArtifacts.each { art ->
                    // Copy deep level jar dependencies
                    File src = art.file
                    if (art.type == 'jar') {
                        isAar = false
                        project.copy {
                            from src
                            into preJarDir
                            rename { "${d.moduleGroup}-${src.name}" }
                        }
                    }
                }
                if (isAar) {
                    if (!aarKeys.contains(d.name)) {
                        aarPw.println d.name
                    }
                } else {
                    if (!jarKeys.contains(d.name)) {
                        jarPw.println d.name
                    }
                }
            }
            jarPw.flush()
            jarPw.close()
            aarPw.flush()
            aarPw.close()
        }
    }

    private void logStartBuild(Project project) {
        BaseExtension ext = project.small
        switch (ext.type) {
            case PluginType.Library:
                LibraryPlugin lp = project.plugins.findPlugin(LibraryPlugin.class)
                if (!lp.isBuildingRelease()) return
            case PluginType.Host:
                if (buildingLibIndex > 0 && buildingLibIndex <= small.libCount) {
                    Log.header "building library ${buildingLibIndex++} of ${small.libCount} - " +
                            "${project.name} (0x${ext.packageIdStr})"
                } else {
                    Log.header "building library ${project.name} (0x${ext.packageIdStr})"
                }
                break
            case PluginType.App:
            case PluginType.Asset:
                Log.header "building bundle ${ext.buildIndex} of ${small.bundleCount} - " +
                        "${project.name} (0x${ext.packageIdStr})"
                break
        }
    }

    private void logFinishBuild(Project project) {
        project.android.applicationVariants.each { variant ->
            if (variant.buildType.name != 'release') return

            variant.outputs.each { out ->
                File outFile = out.outputFile
                Log.footer "-- output: ${outFile.parentFile.name}/${outFile.name} " +
                        "(${outFile.length()} bytes = ${getFileSize(outFile)})"
            }
        }
    }

    private static String getFileSize(File file) {
        long size = file.length()
        if (size <= 0) return '0'

        def units = [ 'B', 'KB', 'MB', 'GB', 'TB' ]
        int level = (int) (Math.log10(size)/Math.log10(1024))
        def formatSize = new DecimalFormat('#,##0.#').format(size/Math.pow(1024, level))
        return "$formatSize ${units[level]}"
    }
}
