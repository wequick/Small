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
package net.wequick.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class CleanBundleTask extends DefaultTask {

    @TaskAction
    def run() {
        File buildDir = project.buildDir
        if (!buildDir.exists()) return

        // Clean tmp dir
        File tmpDir = new File(project.buildDir, 'tmp')
        if (tmpDir.exists()) {
            tmpDir.deleteDir()
        }

        // Clean all dirs created by release mode
        def releaseDirs = []
        buildDir.eachDirRecurse {
            if (it.name == 'release' || it.name.contains('Release')) {
                releaseDirs.add(it)
            }
        }
        releaseDirs.each { File it ->
            it.deleteDir()
        }

        // Clean all files created by release mode
        buildDir.eachFileRecurse {
            if (it.name.contains('-release')) {
                it.delete()
            }
        }
    }
}