package net.wequick.gradle

import net.wequick.gradle.aapt.SymbolParser
import net.wequick.gradle.util.DependenciesUtils
import org.gradle.api.Project
import org.gradle.api.tasks.Delete

import java.text.DecimalFormat

class RootPlugin extends BasePlugin {

    private int buildingLibIndex = 0

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

        // Configure sub projects
        project.subprojects {
            if (it.name == 'small') {
                rootExt.hasSmallProject = true
                return
            }

            if (it.name == 'app') {
                // Host
                it.apply plugin: HostPlugin
                rootExt.outputBundleDir = new File(it.projectDir, SMALL_LIBS)
            } else {
                def idx = it.name.indexOf('.')
                if (idx < 0) return // Small bundle should has a name with format "$type.$name"

                def type = it.name.substring(0, idx)
                switch (type) {
                    case 'app':
                    case 'bundle': // Depreciated
                        it.apply plugin: AppPlugin
                        break;
                    case 'lib':
                        it.apply plugin: LibraryPlugin
                        break;
                    case 'web':
                    default: // Default to Asset
                        it.apply plugin: AssetPlugin
                        break;
                }
            }

            // Hook on project build started and finished for log
            // FIXME: any better way to hooks?
            it.afterEvaluate {
                it.preBuild.doFirst {
                    logStartBuild(it.project)
                }
                it.assembleRelease.doLast {
                    logFinishBuild(it.project)
                }
            }

            if (it.hasProperty('buildLib')) {
                it.small.buildIndex = ++rootExt.libCount
                it.buildLib.doLast {
                    buildLib(it.project)
                }
            } else if (it.hasProperty('buildBundle')) {
                it.small.buildIndex = ++rootExt.bundleCount
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
        lib.tasks.findAll {
            it.hasProperty('explodedDir')
        }.each {
            // explodedDir: **/exploded-aar/$group/$artifact/$version
            File version = it.explodedDir
            File jarFile = new File(version, 'jars/classes.jar')
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

        // Backup R.txt to public.txt
        if (libName != 'app') {
            AppExtension appExt = (AppExtension) ext
            def publicIdsPw = new PrintWriter(appExt.publicSymbolFile.newWriter(false))
            appExt.symbolFile.eachLine { s ->
                if (!s.contains("styleable")) {
                    publicIdsPw.println(s)
                }
            }
            publicIdsPw.flush()
            publicIdsPw.close()
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
