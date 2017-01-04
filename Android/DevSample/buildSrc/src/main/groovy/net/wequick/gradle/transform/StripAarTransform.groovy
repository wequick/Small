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
package net.wequick.gradle.transform

import com.android.build.api.transform.Context
import com.android.build.api.transform.QualifiedContent.ContentType
import com.android.build.api.transform.QualifiedContent.Scope
import com.android.build.api.transform.Format
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.internal.pipeline.TransformManager
import net.wequick.gradle.AppExtension
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.api.Task

public class StripAarTransform extends Transform {

    @Override
    String getName() {
        return "smallStripped"
    }

    @Override
    Set<ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(Context context, Collection<TransformInput> inputs,
                   Collection<TransformInput> referencedInputs,
                   TransformOutputProvider outputProvider, boolean isIncremental)
            throws IOException, TransformException, InterruptedException {

        Project project = ((Task) context).project
        AppExtension small = project.small

        Set<String> splitPaths = []
        small.splitAars.each { Map<String, String> it ->
            splitPaths.add(it.group + File.separator + it.name)
        }

        inputs.each {

            // Bypass the directories
            it.directoryInputs.each {
                File dest = outputProvider.getContentLocation(
                        it.name, it.contentTypes, it.scopes, Format.DIRECTORY);
                FileUtils.copyDirectory(it.file, dest)
            }

            // Filter the jars
            it.jarInputs.each {
                File src = it.file
                def temp = splitPaths.find { src.absolutePath.contains(it) }
                if (temp != null) {
                    // Ignores the jar that should split
                    return
                }

                // Copy the jar and rename
                File version = src.parentFile
                String versionName = version.name
                String moduleName
                if (versionName == 'jars') {
                    // **/appcompat-v7/23.2.1/jars/classes.jar
                    // => appcompat-v7-23.2.1.jar
                    version = version.parentFile
                    versionName = version.name
                    moduleName = version.parentFile.name
                } else if (versionName == 'libs') {
                    versionName = src.name.substring(0, src.name.length() - 4) // bypass '.jar'
                    if (version.parentFile.name == 'jars') {
                        // **/support-v4/23.2.1/jars/libs/internal_impl-23.2.1.jar
                        // => support-v4-internal_impl-23.2.1.jar
                        moduleName = version.parentFile.parentFile.parentFile.name
                    } else {
                        // [projectDir]/libs/mylib.jar
                        // => [projectName]-mylib.jar
                        moduleName = "${project.name}"
                    }
                } else {
                    moduleName = "${version.parentFile.parentFile.name}-${version.parentFile.name}"
                }
                String destName = "$moduleName-$versionName"
                File dest = outputProvider.getContentLocation(
                        destName, it.contentTypes, it.scopes, Format.JAR)
                FileUtils.copyFile(it.file, dest)
            }
        }
    }
}
