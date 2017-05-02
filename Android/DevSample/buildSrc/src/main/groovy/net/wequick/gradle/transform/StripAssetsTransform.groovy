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

import com.android.build.api.transform.*
import com.android.build.api.transform.QualifiedContent.ContentType
import com.android.build.api.transform.QualifiedContent.Scope
import com.android.build.gradle.internal.pipeline.TransformManager
import net.wequick.gradle.BaseExtension
import net.wequick.gradle.PluginType
import net.wequick.gradle.util.ZipUtils
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.api.Task

import java.util.zip.ZipFile

public class StripAssetsTransform extends Transform {

    @Override
    String getName() {
        return "smallAssetsStripped"
    }

    @Override
    Set<ContentType> getInputTypes() {
        return TransformManager.CONTENT_RESOURCES
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
        inputs.each {

            // Bypass the directories
            it.directoryInputs.each {
                File dest = outputProvider.getContentLocation(
                        it.name, it.contentTypes, it.scopes, Format.DIRECTORY);
                FileUtils.copyDirectory(it.file, dest)
            }

            // Filter the jars
            it.jarInputs.each {
                if (project.rootProject.small.isBuildingApps()){
                    //prevent assets in jar package in apps which already package in libs
                    return
                }

                def dest = outputProvider.getContentLocation(it.name,
                        it.contentTypes, it.scopes, Format.JAR)

                FileUtils.copyFile(it.file, dest)
            }
        }
    }
}
