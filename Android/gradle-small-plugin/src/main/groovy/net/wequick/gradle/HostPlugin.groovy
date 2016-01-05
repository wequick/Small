package net.wequick.gradle

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
            def jniDirs = project.android.sourceSets.main.jniLibs.srcDirs
            if (jniDirs == null) {
                project.android.sourceSets.main.jniLibs.srcDirs = [SMALL_LIBS]
            } else {
                project.android.sourceSets.main.jniLibs.srcDirs += SMALL_LIBS
            }
            // If contains release signing config, all bundles will be sign with it,
            // copy the config to debug type to ensure the signature-validating works
            // while launching application from IDE.
            def releaseSigningConfig = project.android.buildTypes.release.signingConfig
            if (releaseSigningConfig != null) {
                project.android.buildTypes.debug.signingConfig = releaseSigningConfig
            }
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

        project.task('buildLib', dependsOn:'jarReleaseClasses')
    }
}
