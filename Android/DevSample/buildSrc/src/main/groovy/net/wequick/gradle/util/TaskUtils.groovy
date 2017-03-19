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

import com.android.build.gradle.internal.tasks.PrepareLibraryTask

import java.lang.reflect.Field

public class TaskUtils {

    public static File getAarExplodedDir(PrepareLibraryTask task) {
        if (task.hasProperty('explodedDir')) {
            return task.explodedDir
        }

        File version = task.bundle.parentFile
        File name = version.parentFile
        File module = name.parentFile
        String dir = "$module.name/$version.name/$name.name"
        println "bundle $task.bundle"
        while (module.parentFile != null && module.parentFile.name != 'm2repository') {
            module = module.parentFile
            dir = module.name + "." + dir
        }

        return new File(task.project.buildDir, "intermediates/exploded-aar/$dir")
    }
}