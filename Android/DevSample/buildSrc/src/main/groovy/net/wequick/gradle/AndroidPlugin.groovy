package net.wequick.gradle

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

    @Override
    protected void configureProject() {
        super.configureProject()

        project.afterEvaluate {
            if (!project.android.hasProperty('applicationVariants')) return

            project.android.applicationVariants.all { variant ->
                if (variant.buildType.name != 'release') return

                // While release variant created, everything of `Android Plugin' should be ready
                // and then we can do some extensions with it
                configureReleaseVariant(variant)
            }
        }
    }

    protected void configureReleaseVariant(variant) {
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
