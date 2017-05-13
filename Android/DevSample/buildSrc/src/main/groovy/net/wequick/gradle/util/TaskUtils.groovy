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

    public static void collectAarBuildCacheDir(PrepareLibraryTask task, Map<String, File> outDirs) {
        AarPath aarPath = getBuildCache(task)
        String key = aarPath.module.path
        File dir = aarPath.outputDir
        if (key == null || dir == null) return

        outDirs.put(key, dir)
    }

    public static AarPath getBuildCache(PrepareLibraryTask task){
        File explodedDir
        if (task.hasProperty("explodedDir")) {
            explodedDir = (File) task.properties["explodedDir"]
        } else {
            try {
                Field explodedDirField = PrepareLibraryTask.class.getDeclaredField("explodedDir")
                explodedDirField.setAccessible(true)
                explodedDir = explodedDirField.get(task)
            } catch (Exception ignored) {
                throw new RuntimeException("[${task.project.name}] Cannot get 'explodedDir' from task $task.name")
            }
        }

        return new AarPath(task.project, explodedDir)
    }

}