package net.wequick.gradle

import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.transforms.ProGuardTransform
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
                // Configure ProGuard if needed
                if (variant.buildType.minifyEnabled) {
                    def variantName = variant.name.capitalize()
                    def proguardTaskName = "transformClassesAndResourcesWithProguardFor$variantName"
                    def proguard = (TransformTask) project.tasks[proguardTaskName]
                    def pt = (ProGuardTransform) proguard.getTransform()
                    configureProguard(variant, proguard, pt)
                }

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

    protected void configureProguard(BaseVariant variant, TransformTask proguard, ProGuardTransform pt) {
        // Keep support library
        pt.dontwarn('android.support.**')
        pt.keep('class android.support.** { *; }')
        pt.keep('interface android.support.** { *; }')

        // Keep Small library
        pt.dontwarn('net.wequick.small.**')
        pt.keep('class net.wequick.small.Small { public *; }')
        pt.keep('class net.wequick.small.Bundle { public *; }')
        pt.keep('interface net.wequick.small.** { *; }')

        // Keep classes and interfaces with @Keep annotation
        pt.keep('class android.support.annotation.**')
        pt.keep('@android.support.annotation.Keep class * { public *; }')
        pt.keep('@android.support.annotation.Keep interface * { *; }')
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
