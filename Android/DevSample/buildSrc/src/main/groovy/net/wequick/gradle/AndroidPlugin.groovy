package net.wequick.gradle

import com.android.build.gradle.api.BaseVariant
import org.gradle.api.Project

class AndroidPlugin extends BasePlugin {

    void apply(Project project) {
        super.apply(project)
    }

    @Override
    protected Class<? extends BaseExtension> getExtensionClass() {
        return AndroidExtension.class
    }

    protected AndroidExtension getSmall() {
        return (AndroidExtension) project.small
    }

    protected com.android.build.gradle.BaseExtension getAndroid() {
        return project.android
    }

    @Override
    protected void configureProject() {
        super.configureProject()

        project.afterEvaluate {
            if (!android.hasProperty('applicationVariants')) return

            android.applicationVariants.all { BaseVariant variant ->
                if (variant.buildType.name != 'release') {
                    configureDebugVariant(variant)
                    return
                }

                // While release variant created, everything of `Android Plugin' should be ready
                // and then we can do some extensions with it
                configureReleaseVariant(variant)
            }
        }
    }

    protected void configureDebugVariant(BaseVariant variant) { }

    protected void configureReleaseVariant(BaseVariant variant) {
        // Init default output file (*.apk)
        small.outputFile = variant.outputs[0].outputFile
        small.explodeAarDirs = project.tasks.findAll {
            it.hasProperty('explodedDir')
        }.collect { it.explodedDir }

        // Hook variant tasks
        variant.assemble.doLast {
            tidyUp()
        }
    }
}
