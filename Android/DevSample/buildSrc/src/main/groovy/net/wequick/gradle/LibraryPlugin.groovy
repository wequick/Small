package net.wequick.gradle

import net.wequick.gradle.tasks.CleanBundleTask
import org.gradle.api.Project

class LibraryPlugin extends BasePlugin {

    private File mBakBuildFile

    @Override
    protected Class getExtensionClass() {
        return BaseExtension.class
    }

    void apply(Project project) {
        super.apply(project)
        mBakBuildFile = new File(project.buildFile.parentFile, "${project.buildFile.name}~")
    }

    @Override
    protected void beforeEvaluate(boolean released) {
        super.beforeEvaluate(released)
        if (!released) return

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

    @Override
    protected void createTask() {
        super.createTask()

        project.task('cleanLib', type: CleanBundleTask)
        project.task('buildLib', dependsOn: 'assembleRelease')

//        project.tasks.remove(project.cleanBundle)
//        project.tasks.remove(project.buildBundle)
    }

    @Override
    protected void tidyUp() {
        super.tidyUp()
        // Restore library module's android plugin to `com.android.library'
        if (mBakBuildFile != null && mBakBuildFile.exists()) {
            project.buildFile.delete()
            mBakBuildFile.renameTo(project.buildFile)
        }
    }
}
