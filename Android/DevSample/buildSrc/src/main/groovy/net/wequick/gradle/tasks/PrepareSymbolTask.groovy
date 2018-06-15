
package net.wequick.gradle.tasks

import net.wequick.gradle.RootExtension
import com.android.build.gradle.api.BaseVariant
import net.wequick.gradle.aapt.SymbolTable
import net.wequick.gradle.util.DependenciesUtils
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.apache.commons.io.FileUtils

class PrepareSymbolTask extends DefaultTask {

    File symbolFile
    File publicSymbolFile
    BaseVariant variant
    RootExtension small
    File fullSymbolFile
    List<Project> libs
    File destinationDir

    PrepareSymbolTask() {
        destinationDir = new File(project.rootProject.buildDir, 'small/symbols')
        symbolFile = new File(destinationDir, "${project.name}.txt")
        outputs.files project.files(symbolFile)

//        publicSymbolFile = new File(project.projectDir, 'public.txt')
    }

    void variant(BaseVariant variant) {
        this.variant = variant
        variant.sourceSets.each {
            it.resDirectories.each {
                inputs.dir project.files(it)
            }
        }

        variant.outputs.all {
            def aapt = it.processResources

            // Ensure generate R.txt
            def fullSymbolFile = aapt.textSymbolOutputFile
            aapt.aaptOptions.additionalParameters '--output-text-symbols', fullSymbolFile.absolutePath

            this.dependsOn aapt
            this.fullSymbolFile = fullSymbolFile
        }
    }

    void small(RootExtension small) {
        this.small = small

        if (project == small.hostProject) return

        def libs = []
        libs.add small.hostProject
        libs.addAll small.getLibDependencies(project)
        libs.each { lib ->
            this.dependsOn ":$lib.name:${this.name}"
        }
        this.libs = libs
    }

    @TaskAction
    void doFullTaskAction() {
        if (libs == null) {
            FileUtils.copyFile(fullSymbolFile, symbolFile)
            return
        }

        def baseProjects = []
        baseProjects.add small.hostProject
        baseProjects.addAll libs

        SymbolTable baseSymbols = new SymbolTable()
        libs.each { lib ->
            baseSymbols.merge(SymbolTable.fromFile(new File(destinationDir, "${lib.name}.txt")))
        }

        println "@@@@@ strip $project.name"
        SymbolTable fullSymbols = SymbolTable.fromFile(fullSymbolFile)
        fullSymbols.strip(baseSymbols)
        fullSymbols.dumpTable()

        assert false
//        small.getLibDepen

//        String packageId = small.getPackageIdStr(project)
//        SymbolTable table = VariantUtils.resolveSymbolTable(variant)
//        table.publicSymbolFile = publicSymbolFile
//        table.assignEntryIds(packageId)
//        table.dumpTable()
//        table.generateTextSymbolsToFile(symbolFile)
    }
}