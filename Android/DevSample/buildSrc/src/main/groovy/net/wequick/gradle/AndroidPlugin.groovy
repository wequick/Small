package net.wequick.gradle

import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.transforms.ProGuardTransform
import com.android.build.gradle.internal.tasks.PrepareLibraryTask
import org.gradle.api.Project

class AndroidPlugin extends BasePlugin {

    protected boolean released

    void apply(Project project) {
        super.apply(project)

        released = isBuildingRelease()
    }

    @Override
    protected Class<? extends BaseExtension> getExtensionClass() {
        return AndroidExtension.class
    }

    protected AndroidExtension getSmall() {
        return (AndroidExtension) project.small
    }

    protected RootExtension getRootSmall() {
        return project.rootProject.small
    }

    protected com.android.build.gradle.BaseExtension getAndroid() {
        return project.android
    }

    protected String getSmallCompileType() { return null }

    @Override
    protected void configureProject() {
        super.configureProject()

        project.beforeEvaluate {
            beforeEvaluate(released)
        }

        project.afterEvaluate {
            afterEvaluate(released)

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

                // While variant created, everything of `Android Plugin' should be ready
                // and then we can do some extensions with it
                if (variant.buildType.name != 'release') {
                    if (!released) {
                        configureDebugVariant(variant)
                    }
                } else {
                    if (released) {
                        configureReleaseVariant(variant)
                    }
                }
            }
        }
    }

    protected void beforeEvaluate(boolean released) { }

    protected void afterEvaluate(boolean released) {
        // Automatic add `small' dependency
        if (rootSmall.smallProject != null) {
            project.dependencies.add(smallCompileType, rootSmall.smallProject)
        } else {
            project.dependencies.add(smallCompileType, "${SMALL_AAR_PREFIX}$rootSmall.aarVersion")
        }

        def preBuild = project.tasks['preBuild']
        if (released) {
            preBuild.doFirst {
                hookPreReleaseBuild()
            }
        } else {
            preBuild.doFirst {
                hookPreDebugBuild()
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

    protected void hookPreDebugBuild() { }

    protected void hookPreReleaseBuild() { }

    protected void configureDebugVariant(BaseVariant variant) { }

    protected void configureReleaseVariant(BaseVariant variant) {
        // Init default output file (*.apk)
        small.outputFile = variant.outputs[0].outputFile
        small.explodeAarDirs = project.tasks
                .withType(PrepareLibraryTask.class)
                .collect { it.explodedDir }

        // Hook variant tasks
        variant.assemble.doLast {
            tidyUp()
        }
    }

    /** Check if is building self in release mode */
    protected boolean isBuildingRelease() {
        def mT = rootSmall.mT
        def mP = rootSmall.mP
        if (mT == null) return false // no tasks

        if (mP == null) {
            // gradlew buildLibs | buildBundles
            return (small.type == PluginType.Library || small.type == PluginType.Host) ?
                    (mT == 'buildLib') : (mT == 'buildBundle')
        } else {
            return (mP == project.name && (mT == 'assembleRelease' || mT == 'aR'))
        }
    }
}
