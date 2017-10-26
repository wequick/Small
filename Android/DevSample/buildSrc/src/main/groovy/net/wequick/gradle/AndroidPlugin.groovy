package net.wequick.gradle

import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.transforms.ProGuardTransform
import com.android.build.gradle.internal.tasks.PrepareLibraryTask
import com.android.build.gradle.tasks.MergeManifests
import net.wequick.gradle.util.AarPath
import net.wequick.gradle.util.TaskUtils
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

        // Add common ProGuard rules from stub modules
        android.buildTypes.each { buildType ->
            if (buildType.minifyEnabled) {
                rootSmall.hostStubProjects.each { stub ->
                    buildType.proguardFiles.add(stub.file('proguard-rules.pro'))
                }
            }
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
        preBuild.doLast {
            if (!released) {
                removeUnimplementedProviders()
            }
        }
    }

    /**
     * Remove unimplemented content providers in the bundle manifest.
     *
     * On debug mode the `Stub` modules are compiled to each bundle by which
     * the bundles manifest may be contains the `Stub` content provider.
     * If the bundle wasn't implement the provider class, it would raise an exception
     * on running the bundle independently.
     *
     * So we need to remove all the unimplemented content providers from `Stub`.
     */
    protected void removeUnimplementedProviders() {
        if (pluginType == PluginType.Host) return // nothing to do with host

        final def appId = android.defaultConfig.applicationId
        if (appId == null) return // nothing to do with non-app module

        MergeManifests manifests = project.tasks.withType(MergeManifests.class)[0]
        if (manifests.hasProperty('providers')) {
            return // can be simply stripped from providers
        }

        project.tasks.withType(PrepareLibraryTask.class).each {
            it.doLast { PrepareLibraryTask aar ->
                AarPath aarPath = TaskUtils.getBuildCache(aar)
                File aarDir = aarPath.getOutputDir()
                if (aarDir == null) {
                    return
                }

                def aarName = aarPath.module.name
                if (rootSmall.hostStubProjects.find { it.name == aarName } != null) {
                    return
                }

                File manifest = new File(aarDir, 'AndroidManifest.xml')
                def s = ''
                boolean enteredProvider = false
                boolean removed = false
                boolean implemented
                int loc
                String providerLines
                manifest.eachLine { line ->
                    if (!enteredProvider) {
                        loc = line.indexOf('<provider')
                        if (loc < 0) {
                            s += line + '\n'
                            return null
                        }

                        enteredProvider = true
                        implemented = false
                        providerLines = ''
                    }

                    final def nameTag = 'android:name="'
                    loc = line.indexOf(nameTag)
                    if (loc >= 0) {
                        loc += nameTag.length()
                        def tail = line.substring(loc)
                        def nextLoc = tail.indexOf('"')
                        def name = tail.substring(0, nextLoc)
                        implemented = name.startsWith(appId) // is implemented by self
                        providerLines += line + '\n'
                    } else {
                        providerLines += line + '\n'
                    }

                    loc = line.indexOf('>')
                    if (loc >= 0) { // end of <provider>
                        enteredProvider = false
                        if (implemented) {
                            s += providerLines
                        } else {
                            removed = true
                        }
                    }
                    return null
                }

                if (removed) {
                    manifest.write(s, 'utf-8')
                }
            }
        }
    }

    protected void configureProguard(BaseVariant variant, TransformTask proguard, ProGuardTransform pt) {
        // Keep support library
        pt.dontwarn('android.support.**')
        pt.keep('class android.support.** { *; }')
        pt.keep('interface android.support.** { *; }')

        // Don't warn data binding library (cause we strip it from bundles)
        pt.dontwarn('android.databinding.**')

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

        small.buildCaches = new HashMap<String, File>()
        project.tasks.withType(PrepareLibraryTask.class).each {
            TaskUtils.collectAarBuildCacheDir(it, small.buildCaches)
        }

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
