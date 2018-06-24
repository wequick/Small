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
package net.wequick.gradle.util

import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.sdklib.BuildToolInfo
import net.wequick.gradle.RootExtension
import org.gradle.api.Project

class Command {
    Project project

    static Command with(Project p) {
        def command = new Command()
        command.project = p
        return command
    }

    def execute(String exe, List<String> theArgs) {
        return execute(exe, theArgs, false)
    }

    def execute(String exe, List<String> theArgs, boolean logs) {
        def out = null
        def redirectsOutput = !logs
        if (redirectsOutput) {
            out = new ByteArrayOutputStream()
        }

        if (logs) {
            println AnsiUtils.yellow("\t\$ $exe ${theArgs.join(' ')}")
        }

        project.exec {
            commandLine exe
            args = theArgs
            if (redirectsOutput) {
                standardOutput = out
            }
        }

        return out
    }

    def gradlew(String taskName, boolean quiet, boolean parallel) {
        def args = []
        def exe = './gradlew'
        if (System.properties['os.name'].toLowerCase().contains('windows')) {
            exe = 'cmd'
            args.add('/c')
            args.add('gradlew.bat')
        }

        args.add(taskName)
        if (quiet) {
            args.add('-q')
        }
        args.add('-Dorg.gradle.daemon=true')
        args.add("-Dorg.gradle.parallel=${parallel ? 'true' : 'false'}")

        execute(exe, args, true)

//        GradleBuild gradlew = project.task('__temp_gradlew', type: GradleBuild)
//        gradlew.tasks = [taskName]
//        gradlew.startParameter.systemPropertiesArgs.putAll(
//                'org.gradle.daemon': 'true',
//                'org.gradle.parallel': parallel ? 'true' : 'false')
//        gradlew.startParameter.logLevel = quiet ? LogLevel.QUIET : LogLevel.LIFECYCLE
//
//        gradlew.execute()
//
//        project.tasks.remove(gradlew)
    }

    def adb(String command, List<String> args) {
        def android = AndroidPluginUtils.getAndroid(project)
        def adb = android.adbExecutable.absolutePath
        def newArgs = []
        newArgs.add command
        newArgs.addAll args
        execute(adb, newArgs, true)
    }

    def aapt(List<String> args) {
        def aapt = this.buildToolInfo.getPath(BuildToolInfo.PathId.AAPT)
        execute(aapt, args, true)
    }

    BuildToolInfo getBuildToolInfo() {
        RootExtension small = project.rootProject.small
        def tasks = small.hostProject.tasks.withType(ProcessAndroidResources)
        def aapt = tasks[0]
        return aapt.buildTools
    }
}