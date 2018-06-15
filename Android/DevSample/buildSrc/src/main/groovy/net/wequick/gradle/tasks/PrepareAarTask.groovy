
package net.wequick.gradle.tasks

import com.android.build.gradle.api.LibraryVariant
import net.wequick.gradle.RootExtension
import net.wequick.gradle.util.AndroidPluginUtils
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.tasks.TaskAction

class PrepareAarTask extends DefaultTask {

    File destinationDir
    List<File> aarFiles
    String modulePath

    RootExtension small

    PrepareAarTask() {
        small = project.rootProject.extensions.getByName('small')
        destinationDir = small.strippedAarDir
    }

    void module(ResolvedDependency d, List<File> files) {
        def path = "$d.moduleGroup/$d.moduleName/$d.moduleVersion"
        initInputsAndOutputs(files, path)
    }

    void variant(LibraryVariant variant, Project lib) {
        def aarFile = variant.packageLibrary.archivePath
        def group = lib.group ?: lib.rootProject.name
        def version = lib.version ?: 'unspecified'
        initInputsAndOutputs([aarFile], "$group/$lib.name/$version")
    }

    void initInputsAndOutputs(List<File> srcFiles, String destPath) {
        aarFiles = srcFiles
        modulePath = destPath

        def stripFiles = []
        aarFiles.each { srcFile ->
            def destFile = new File(destinationDir, "$modulePath/$srcFile.name")
            stripFiles.add destFile
        }

        inputs.files project.files(aarFiles)
        outputs.files project.files(stripFiles)
    }

    @TaskAction
    void run() {
        aarFiles.each { srcFile ->
            // Unpack
//            println "### export $path"

            def explodedDir = new File(small.explodedAarDir, modulePath)
            project.ant.unzip(src: srcFile.path, dest: explodedDir)

            // Check if exist resources
            def resDir = new File(explodedDir, 'res')
            if (!resDir.exists()) return
            if (resDir.listFiles().size() == 0) return

            // Repack a pure-resources aar which only contains `res' files
            if (AndroidPluginUtils.isAndroidPlugin3(project)) {
                def destFile = new File(destinationDir, "$modulePath/$srcFile.name")
                project.ant.zip(basedir: explodedDir, includes: 'res/**/*', destFile: destFile)
            } else {
                def destFile = new File(destinationDir, "$modulePath/$srcFile.name")
                def manifestFile = new File(explodedDir, 'AndroidManifest.xml')
                def pkg = new XmlParser().parse(manifestFile).@package
                def bakManifestFile = new File(explodedDir, 'AndroidManifest.xml~')
                manifestFile.renameTo(bakManifestFile)
                manifestFile.write("<manifest package=\"$pkg\"/>")

                project.ant.zip(basedir: explodedDir, includes: 'res/**/*,AndroidManifest.xml', destFile: destFile)

                bakManifestFile.renameTo(manifestFile)
            }
        }
    }
}