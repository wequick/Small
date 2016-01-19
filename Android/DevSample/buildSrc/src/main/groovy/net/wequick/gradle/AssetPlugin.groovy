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
package net.wequick.gradle

import net.wequick.gradle.aapt.Aapt
import org.gradle.api.Project
import org.gradle.api.tasks.Copy

class AssetPlugin extends BundlePlugin {
    void apply(Project project) {
        super.apply(project)
    }

    @Override
    protected Class<? extends BaseExtension> getExtensionClass() {
        return AssetExtension.class
    }

    @Override
    protected PluginType getPluginType() {
        return PluginType.Asset
    }

    protected AssetExtension getSmall() {
        return (AssetExtension) project.small
    }

    @Override
    protected void configureProject() {
        super.configureProject()

        project.afterEvaluate {
            // Task for log
            def orgGroup = project.preBuild.group // Keep original task group
            project.task('preBuild', group: orgGroup, overwrite: true)

            orgGroup = project.assembleRelease.group
            project.task('assembleRelease', group: orgGroup, overwrite: true) << {
                tidyUp()
            }
        }
    }

    @Override
    protected void configureReleaseVariant(Object variant) {
        super.configureReleaseVariant(variant)

        project.task('prepareAsset', dependsOn: 'preBuild', type: Copy) {
            ext {
                srcDir = project.android.sourceSets.main.assetsDirectories[0]
                destDir = small.assetsDir
            }
            inputs.dir srcDir
            outputs.dir destDir

            from srcDir
            into destDir
        } << {
            // Generate AndroidManifest.xml
            Aapt aapt = new Aapt(destDir, null, null)
            def aaptTask = project.processReleaseResources
            def aaptExe
            aaptTask.buildTools.mPaths.each { k, v ->
                if ((String) k == 'AAPT') { // k.class = `com.android.sdklib.BuildToolInfo$PathId'
                    aaptExe = v
                    return
                }
            }
            def cf = project.android.defaultConfig
            def baseAsset = new File(project.android.getSdkDirectory(),
                    "platforms/android-${cf.targetSdkVersion.mApiLevel}/android.jar")
            aapt.manifest(project, [packageName: cf.applicationId,
                                    versionName: cf.versionName, versionCode: cf.versionCode,
                                    aaptExe: aaptExe, baseAsset: baseAsset.path]
            )
        }

        def sc = project.android.buildTypes.release.signingConfig
        project.task('packageAsset', dependsOn: 'prepareAsset') {
            ext {
                srcDir = project.prepareAsset.destDir
                destFile = (sc == null) ? small.outputFile : small.unsignedFile
            }
            inputs.dir srcDir
            outputs.file destFile
        } << {
            project.ant.zip(baseDir: srcDir, destFile: destFile)
        }
        if (sc == null) {
            project.assembleRelease.dependsOn project.packageAsset
            return
        }

        // If contains a release signing config, sign the package with ant's SignJar task
        project.task('signAsset', dependsOn: 'packageAsset') {
            ext {
                srcFile = project.packageAsset.destFile
                destFile = small.outputFile
            }
        } << {
            def dir = destFile.parentFile
            if (!dir.exists()) dir.mkdirs()
            ant.signjar(jar: srcFile, signedjar: destFile, keystore: sc.storeFile.path,
                    storepass: sc.storePassword, alias: sc.keyAlias, keypass: sc.keyPassword,
                    digestalg: 'SHA1', sigalg: 'MD5withRSA') // Fix issue #13
        }
        project.assembleRelease.dependsOn project.signAsset
    }
}
