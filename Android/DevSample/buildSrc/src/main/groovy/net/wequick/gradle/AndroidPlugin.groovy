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
        preBuild.doLast {
            reassignProviderAuthorities()
        }
    }

    /**
     * Reassign `android:authorities` value of a ContentProvider.
     *
     * consider that on debug mode:
     * - `Small` module is compiled to host and each `app.*`
     * - `Stub` modules are compiled to each `app.*`
     * we need to ensure each of them have an unique authorities, so that they can be
     * ran on one device together without namespace conflict.
     */
    protected void reassignProviderAuthorities() {
        if (pluginType == PluginType.Library) return // nothing to do with `lib.*`

        project.tasks.withType(PrepareLibraryTask.class).findAll {
            def name = it.explodedDir.parentFile.name
            if (name == 'small') {
                it.extensions.add('numberOfProviders', 1) // known that only one `SetUpProvider`
                return true
            }

            if (pluginType == PluginType.Host) return false // keep the stub authorities for host
            boolean isStub = (rootSmall.hostStubProjects.find { it.name == name } != null)
            if (isStub) {
                it.extensions.add('numberOfProviders', 999) // unknown amount
                return true
            }
            return false
        }.each {
            it.doLast { PrepareLibraryTask aar ->
                File manifest = new File(aar.explodedDir, 'AndroidManifest.xml')
                def s = ''
                boolean enteredProvider = false
                boolean reassigned = false
                boolean needsReassign
                int loc
                int numberOfProviders = (int) aar.extensions.getByName('numberOfProviders')
                String reassignProviderLines
                String originalProviderLines
                manifest.eachLine { line ->
                    if (numberOfProviders <= 0) {
                        s += line + '\n'
                        return null
                    }

                    if (!enteredProvider) {
                        loc = line.indexOf('<provider')
                        if (loc < 0) {
                            s += line + '\n'
                            return null
                        }

                        enteredProvider = true
                        needsReassign = true
                        reassignProviderLines = originalProviderLines = ''
                    }

                    final def appId = android.defaultConfig.applicationId
                    final def uniqueId = appId.hashCode()
                    final def nameTag = 'android:name="'
                    final def authTag = 'android:authorities="'
                    loc = line.indexOf(nameTag)
                    if (loc >= 0) {
                        loc += nameTag.length()
                        def tail = line.substring(loc)
                        def nextLoc = tail.indexOf('"')
                        def name = tail.substring(0, nextLoc)
                        needsReassign = !name.startsWith(appId) // isn't implemented by self
                        reassignProviderLines += line + '\n'
                        originalProviderLines += line + '\n'
                    } else if ((loc = line.indexOf(authTag)) > 0) {
                        loc += authTag.length()
                        def head = line.substring(0, loc)
                        def tail = line.substring(loc)
                        def nextLoc = tail.indexOf('"')
                        def authorities = "${tail.substring(0, nextLoc)}.${uniqueId}"
                        def reassignLine = "${head}${authorities}${tail.substring(nextLoc)}"
                        reassignProviderLines += reassignLine + '\n'
                        originalProviderLines += line + '\n'
                    } else {
                        reassignProviderLines += line + '\n'
                        originalProviderLines += line + '\n'
                    }

                    loc = line.indexOf('>')
                    if (loc >= 0) { // end of <provider>
                        enteredProvider = false
                        if (needsReassign) {
                            s += reassignProviderLines
                            reassigned = true
                        } else {
                            s += originalProviderLines
                        }
                        numberOfProviders--
                    }
                    return null
                }

                if (reassigned) {
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
