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
import net.wequick.gradle.util.AarPath
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
        inputs.each {
            // Filter the directories
            it.directoryInputs.each {
                File src = it.file
                AarPath aarPath = new AarPath(project, src)
                for (aar in small.splitAars) {
                    if (aarPath.explodedFromAar(aar)) {
                        return
                    }
                }

                File dest = outputProvider.getContentLocation(
                        it.name, it.contentTypes, it.scopes, Format.DIRECTORY)
                FileUtils.copyDirectory(src, dest)
            }

            // Filter the jars
            it.jarInputs.each {
                // Strip jars in aar or build-cache under android plugin 2.3.0+
                File src = it.file
                AarPath aarPath = new AarPath(project, src)
                for (aar in small.splitAars) {
                    if (aarPath.explodedFromAar(aar)) {
                        return
                    }
                }

                String destName = aarPath.module.fileName
                if (src.parentFile.name == 'libs') {
                    destName += '-' + src.name.substring(0, src.name.lastIndexOf('.'))
                }
                File dest = outputProvider.getContentLocation(
                        destName, it.contentTypes, it.scopes, Format.JAR)
                FileUtils.copyFile(src, dest)
            }
        }
    }
}
