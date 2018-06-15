
package net.wequick.gradle.tasks

import com.android.build.gradle.api.BaseVariant
import org.gradle.api.tasks.bundling.Jar

class PrepareJarTask extends Jar {

    File outJarFile

    PrepareJarTask() {
        def bundlePath = new File(project.rootProject.buildDir, 'small/bundles')
        destinationDir = new File(bundlePath, "$project.rootProject.name/$project.name/unspecified")
        archiveName = 'classes.jar'
        outJarFile = new File(destinationDir, archiveName)
        outputs.file outJarFile
    }

    void variant(BaseVariant variant) {
        def packagePath = variant.applicationId.replaceAll('\\.', '/')
        def javac = variant.javaCompile
        def classesDir = javac.destinationDir

        dependsOn javac

        inputs.dir classesDir

        from classesDir
//        include "$packagePath/**/*"
    }
}