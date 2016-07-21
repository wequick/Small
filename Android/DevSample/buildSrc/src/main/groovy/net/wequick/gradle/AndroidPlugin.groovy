package net.wequick.gradle

import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.transforms.ProGuardTransform
import com.android.build.gradle.internal.tasks.PrepareLibraryTask
import org.gradle.api.Project
import org.gradle.api.Task

class AndroidPlugin extends BasePlugin {

    protected String mP // the executing gradle project name
    protected String mT // the executing gradle task name

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

        // Parse gradle task
        def sp = project.gradle.startParameter
        def t = sp.taskNames[0]
        if (t != null) {
            def p = sp.projectDir
            def pn = null
            if (p == null) {
                if (t.startsWith(':')) {
                    // gradlew :app.main:assembleRelease
                    def tArr = t.split(':')
                    if (tArr.length == 3) { // ['', 'app.main', 'assembleRelease']
                        pn = tArr[1]
                        t = tArr[2]
                    }
                }
            } else if (p != project.rootProject.projectDir) {
                // gradlew -p [project.name] assembleRelease
                pn = p.name
            }
            mP = pn
            mT = t
        }

        project.afterEvaluate {
            if (!android.hasProperty('applicationVariants')) return

            def released = isBuildingRelease()

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

            if (released) {
                project.tasks['preBuild'].doFirst {
                    hookPreReleaseBuild()
                }
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
        if (mT == null) return false // no tasks

        if (mP == null) {
            // gradlew buildLibs | buildBundles
            return (small.type == PluginType.Library || small.type == PluginType.Host) ?
                    (mT == 'buildLib') : (mT == 'buildBundle')
        } else {
            return (mP == project.name && (mT == 'assembleRelease' || mT == 'aR'))
        }
    }

    /** Check if is building any libs (lib.*) */
    protected boolean isBuildingLibs() {
        if (mT == null) return false // no tasks

        if (mP == null) {
            // ./gradlew buildLib
            return (mT == 'buildLib')
        } else {
            // ./gradlew -p lib.xx aR | ./gradlew :lib.xx:aR
            return (mP.startsWith('lib.') && (mT == 'assembleRelease' || mT == 'aR'))
        }
    }

    /** Check if is building any apps (app.*) */
    protected boolean isBuildingApps() {
        if (mT == null) return false // no tasks

        if (mP == null) {
            // ./gradlew buildBundle
            return (mT == 'buildBundle')
        } else {
            // ./gradlew -p app.xx aR | ./gradlew :app.xx:aR
            return (mP.startsWith('app.') && (mT == 'assembleRelease' || mT == 'aR'))
        }
    }
}
