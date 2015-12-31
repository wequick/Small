package net.wequick.gradle

import org.gradle.api.Project

abstract class AndroidPlugin extends BasePlugin {

    void apply(Project project) {
        super.apply(project)
    }

    @Override
    protected void configureProject() {
        super.configureProject()
    }
}
