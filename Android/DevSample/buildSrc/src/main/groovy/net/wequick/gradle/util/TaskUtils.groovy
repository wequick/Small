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
import net.wequick.gradle.AndroidExtension
import org.gradle.api.Project

import java.lang.reflect.Field
import java.util.regex.Pattern

public class TaskUtils {

    public static void getAarExplodedDir(Project project,PrepareLibraryTask task) {
        AarPath aarPath = getAarOutput(task)
        String input = aarPath.getAarPath()
        if(input == null){
            return
        }
        def group
        def artifact
        def version
        File versionFile
        if (input.contains('m2repository') && input.contains('sdk')) {
            //  /extras/android/m2repository/com/android/support/support-core-ui/25.1.0/*.aar
            //                               ^^^^^^^^^^^^^^^^^^^ ^^^^^^^^^^^^^^^ ^^^^^^
            versionFile = new File(input).parentFile

            File group3 = versionFile.parentFile.parentFile
            File group2 = group3.parentFile
            File group1 = group2.parentFile
            group = group1.name+"."+group2.name+"."+group3.name
        } else {
            // /caches/modules-2/files-2.1/net.wequick.small/small/1.1.0/hash/*.aar
            //                             ^^^^^^^^^^^^^^^^^ ^^^^^ ^^^^^
            versionFile  = new File(input).parentFile.parentFile
            group = versionFile.parentFile.parentFile.name
        }
        version = versionFile.name
        artifact = versionFile.parentFile.name
        String key = "$group/$artifact/$version"
        ((AndroidExtension)project.small).explodeAarDirs.put(key, aarPath.getExplodePath())
    }

    public static AarPath getAarOutput(PrepareLibraryTask task){
        Field explodedDirField = PrepareLibraryTask.class.getDeclaredField("explodedDir")
        explodedDirField.setAccessible(true)
        File explodedDir = explodedDirField.get(task)

        AarPath aarPath = new AarPath(explodedDir)
        return aarPath;
    }

}