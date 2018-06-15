
package net.wequick.gradle

import net.wequick.gradle.util.ModuleUtils
import org.gradle.api.Project

class ResourcePath {

    File sourceResDir
    String destinationAarName

    String moduleName
    String moduleVersion
    String mArtifactName

    static ResourcePath from(Project rootProject, String path) {
        def components = path.split(';')
        if (components.size() != 2) return null

        def resName = components[0]
        def resPath = components[1]
        components = resName.split(':')
        if (components.size() != 2) return null

        return new ResourcePath(rootProject, components[0], components[1], resPath)
    }

    ResourcePath(Project rootProject, String moduleName, String moduleVersion, String path) {
        this.moduleName = moduleName
        this.moduleVersion = moduleVersion
        def libProject = rootProject.findProject(moduleName)
        if (libProject != null) {
            sourceResDir = new File(libProject.projectDir, 'src/main/res')
            destinationAarName = "small-${moduleName}"
        } else {
            sourceResDir = new File(path)
            destinationAarName = "small-${moduleName}-${moduleVersion}"
        }
    }

    @Override
    int hashCode() {
        if (sourceResDir == null) return super.hashCode()
        return sourceResDir.absolutePath.hashCode()
    }

    @Override
    boolean equals(Object o) {
        if (!o instanceof ResourcePath) return false
        ResourcePath other = o
        return sourceResDir.absolutePath == other.sourceResDir.absolutePath
    }

    String getArtifactName() {
        if (mArtifactName != null) {
            return mArtifactName
        }

        def name = this.moduleName + this.moduleVersion
        mArtifactName = ModuleUtils.buildTaskName('small', name)
        return mArtifactName
    }
}