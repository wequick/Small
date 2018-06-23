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

import org.gradle.api.DomainObjectSet
import org.gradle.api.Project
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.build.gradle.tasks.MergeManifests

class AndroidPluginUtils {

    static BaseExtension getAndroid(Project project) {
        return project.extensions.findByName('android')
    }

    static DomainObjectSet<BaseVariant> getVariants(Project project) {
        BaseExtension android = getAndroid(project)
        if (android == null) return null

        if (android instanceof LibraryExtension) {
            LibraryExtension lib = android
            return lib.libraryVariants
        } else if (android instanceof AppExtension) {
            AppExtension app = android
            return app.applicationVariants
        }
        return null
    }

    static BaseVariant getReleaseVariant(Project project) {
        def variants = getVariants(project)
        if (variants == null) return null

        return variants.find {
            it.buildType.name == 'release'
        }
    }

    static String getApplicationId(Project project) {
        def variant = getReleaseVariant(project)
        if (variant == null) return null

        return variant.applicationId
    }

    static boolean isAndroidPlugin3(Project project) {
        return project.configurations.collect { it.name }.contains('implementation')
    }

    static File getSymbolFile(ProcessAndroidResources aapt) {
        if (aapt.hasProperty('textSymbolOutputFile')) {
            // 3.0+
            return aapt.textSymbolOutputFile
        }
        // 3.0-
        return new File(aapt.textSymbolOutputDir, 'R.txt')
    }

    static File getManifestFile(MergeManifests processManifest) {
        if (processManifest.hasProperty('manifestOutputDirectory')) {
            // 3.0+
            return new File(processManifest.manifestOutputDirectory, 'AndroidManifest.xml')
        }
        // 3.0-
        return processManifest.manifestOutputFile
    }
}