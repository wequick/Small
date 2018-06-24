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

import groovy.xml.Namespace
import net.wequick.gradle.util.Command
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

class RunHostTask extends DefaultTask {

    static final def MAIN_ACTION = 'android.intent.action.MAIN'
    static final def CATEGORY_LAUNCHER = 'android.intent.category.LAUNCHER'

    Project host
    File apk
    File manifestFile
    Command cmd

    def host(Project host) {
        this.host = host
        this.cmd = Command.with(host)
    }

    def apk(File apk) {
        this.apk = apk
    }

    def manifest(File manifest) {
        this.manifestFile = manifest
    }

    @TaskAction
    def run() {
        // adb install -r $apk
        cmd.adb('install', ['-r', apk.absolutePath])

        // adb shell am start -a android.intent.action.MAIN -n $pkg/$launcher
        def manifest = new XmlParser().parse(manifestFile)
        def pkg = manifest.@package
        assert (pkg != null)

        def android = new Namespace('http://schemas.android.com/apk/res/android', 'android')
        def nameKey = android['name']
        def launcher = null

        def activities = manifest.application[0].activity
        for (Node activity : activities) {
            NodeList intentFilters = activity['intent-filter']
            if (intentFilters.size() == 0) continue

            for (Node filter : intentFilters) {
                NodeList actions = filter.action
                if (actions.size() == 0) continue
                if (actions.find { it.attribute(nameKey) == MAIN_ACTION } == null) continue

                NodeList categories = filter.category
                if (categories.size() == 0) continue
                for (Node category : categories) {
                    if (category.attribute(nameKey) == CATEGORY_LAUNCHER) {
                        launcher = activity.attribute(nameKey)
                        break
                    }
                }

                if (launcher != null) break
            }
            if (launcher != null) break
        }

        assert (launcher != null)
        cmd.adb('shell', ['am start -a', MAIN_ACTION, '-n', "$pkg/$launcher"])
    }
}