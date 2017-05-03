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
import javafx.scene.effect.Reflection
import net.wequick.gradle.AndroidExtension
import org.gradle.api.Project

import java.lang.reflect.Field
import java.util.regex.Pattern

public class TaskUtils {

    public static void collectAarBuildCacheDir(PrepareLibraryTask task,Map<String,File> buildCache) {
        AarPath aarPath = getBuildCache(task)
        String input = aarPath.getInputAarPath()
        if(input == null){
            return
        }
        def group
        def artifact
        def version
        File versionFile
        if (input.contains('m2repository') && input.contains('sdk/extras')) {
            //  sdk/extras/android/m2repository/com/android/support/support-core-ui/25.1.0/*.aar
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
        buildCache.put(key, aarPath.getBuildCacheFile())
    }

    public static AarPath getBuildCache(PrepareLibraryTask task){
        AarPath aarPath
        try{
            Field explodedDirField = PrepareLibraryTask.class.getDeclaredField("explodedDir")
            explodedDirField.setAccessible(true)
            File explodedDir = explodedDirField.get(task)
            aarPath = new AarPath(explodedDir)
        }catch (NoSuchFieldException noSuchFieldException){
            Log.warn "[${task.getProject().name}] NoSuchFieldException Reflect PrepareLibraryTask Field explodedDir failed..."
        }catch(IllegalArgumentException illegalArgumentException){
            Log.warn "[${task.getProject().name}] IllegalArgumentException Reflect PrepareLibraryTask Field explodedDir failed..."
        }catch(Exception exception){
            exception.printStackTrace()
        }

        return aarPath;
    }

}