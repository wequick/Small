package net.wequick.gradle

import org.gradle.api.Project

class LibraryPlugin extends AppPlugin {

    private File mBakBuildFile

    void apply(Project project) {
        super.apply(project)
    }

    @Override
    protected PluginType getPluginType() {
        return PluginType.Library
    }

    @Override
    protected void configureProject() {
        super.configureProject()

        if (!isBuildingRelease()) return

        mBakBuildFile = new File(project.buildFile.parentFile, "${project.buildFile.name}~")

        project.beforeEvaluate {
            // Change android plugin from `lib' to `application' dynamically
            // FIXME: Any better way without edit file?

            if (mBakBuildFile.exists()) {
                // With `tidyUp', should not reach here
                throw new Exception("Conflict buildFile, please delete file $mBakBuildFile or " +
                        "${project.buildFile}")
            }

            def text = project.buildFile.text.replaceAll(
                    'com\\.android\\.library', 'com.android.application')
            project.buildFile.renameTo(mBakBuildFile)
            project.buildFile.write(text)
        }
        project.afterEvaluate {
            // Set application id
            def manifest = new XmlParser().parse(project.android.sourceSets.main.manifestFile)
            project.android.defaultConfig.applicationId = manifest.@package
        }
    }

    @Override
    protected void createTask() {
        super.createTask()
        project.task('cleanLib', dependsOn: 'clean')
        project.task('buildLib', dependsOn: 'assembleRelease')

        project.tasks.remove(project.cleanBundle)
        project.tasks.remove(project.buildBundle)
    }

    @Override
    protected void configureReleaseVariant(variant) {
        super.configureReleaseVariant(variant)

        small.jar = project.jarReleaseClasses

        // Generate jar file to root pre-jar directory
        variant.assemble.doLast {
            RootExtension rootExt = project.rootProject.small
            def jarName = getJarName(project)
            def jarFile = new File(rootExt.preLibsJarDir, jarName)
            project.ant.jar(baseDir: small.javac.destinationDir, destFile: jarFile)
        }
    }

    @Override
    protected void tidyUp() {
        super.tidyUp()
        // Restore library module's android plugin to `com.android.library'
        if (mBakBuildFile == null) return
        if (mBakBuildFile.exists()) {
            project.buildFile.delete()
            mBakBuildFile.renameTo(project.buildFile)
        }
    }
}
