package net.wequick.gradle

import com.android.build.gradle.api.BaseVariant
import org.gradle.api.Project

class HostPlugin extends AndroidPlugin {

    void apply(Project project) {
        super.apply(project)
    }

    @Override
    protected void configureProject() {
        super.configureProject()
        
        project.afterEvaluate {
            // Configure libs dir
            def sourceSet = project.android.sourceSets.main
            def source = rootSmall.buildToAssets ? sourceSet.assets : sourceSet.jniLibs
            if (source.srcDirs == null) {
                source.srcDirs = [SMALL_LIBS]
            } else {
                source.srcDirs += SMALL_LIBS
            }
            // If contains release signing config, all bundles will be signed with it,
            // copy the config to debug type to ensure the signature-validating works
            // while launching application from IDE.
            def releaseSigningConfig = android.buildTypes.release.signingConfig
            if (releaseSigningConfig != null) {
                android.buildTypes.debug.signingConfig = releaseSigningConfig
            }

            // Add a build config to specify whether load-from-assets or not.
            android.defaultConfig.buildConfigField(
                    "boolean", "LOAD_FROM_ASSETS", rootSmall.buildToAssets ? "true" : "false")
        }
    }

    @Override
    protected PluginType getPluginType() {
        return PluginType.Host
    }

    @Override
    protected String getSmallCompileType() {
        return 'compile'
    }

    @Override
    protected void createTask() {
        super.createTask()

        project.task('cleanLib', dependsOn: 'clean')
        project.task('buildLib')
    }

    @Override
    protected void configureReleaseVariant(BaseVariant variant) {
        super.configureReleaseVariant(variant)

        if (small.jar != null) return // Handle once for multi flavors

        def flavor = variant.flavorName
        if (flavor != null) {
            flavor = flavor.capitalize()
            small.jar = project.tasks["jar${flavor}ReleaseClasses"]
            small.aapt = project.tasks["process${flavor}ReleaseResources"]
        } else {
            small.jar = project.jarReleaseClasses
            small.aapt = project.processReleaseResources
        }
        project.buildLib.dependsOn small.jar
    }
}
