/*
 * Copyright 2015-present wequick.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package net.wequick.gradle

import org.gradle.api.Project
import org.gradle.api.Plugin

abstract class BasePlugin<T extends BaseExtension> implements Plugin<Project> {
    protected Project project
    protected T mSmall

    void apply(Project project) {
        this.project = project

        createExtension()

        configureProject()

        createTask()
    }

    protected void createExtension() {
        // Add the 'small' extension object
        mSmall = project.extensions.create('small', getExtensionClass(), project)
    }

    protected void configureProject() {
        project.gradle.buildFinished {
            tidyUp()
        }
        project.beforeEvaluate {
            beforeEvaluate(rootSmall.isBuildingLib)
        }
    }

    protected void createTask() {

    }

    protected RootExtension getRootSmall() {
        return project.rootProject.extensions.getByName('small')
    }

    protected T getSmall() {
        return (T) mSmall
    }

    protected void tidyUp() {

    }

    protected void beforeEvaluate(boolean released) {

    }

    abstract protected Class<T> getExtensionClass()
}