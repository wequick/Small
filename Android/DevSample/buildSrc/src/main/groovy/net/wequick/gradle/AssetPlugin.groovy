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

import com.android.build.gradle.api.BaseVariant
import com.android.sdklib.BuildToolInfo
import net.wequick.gradle.aapt.Aapt
import net.wequick.gradle.util.AndroidPluginUtils
import net.wequick.gradle.util.FileUtils
import net.wequick.gradle.util.Log
import org.gradle.api.Project
import org.gradle.api.tasks.Copy

class AssetPlugin extends BasePlugin<AssetExtension> {
    void apply(Project project) {
        super.apply(project)
    }

    @Override
    protected Class<? extends BaseExtension> getExtensionClass() {
        return AssetExtension.class
    }

    @Override
    protected void configureProject() {
        super.configureProject()

        project.afterEvaluate {
            // Task for log
            def orgGroup = project.preBuild.group // Keep original task group
            project.task('preBuild', group: orgGroup, overwrite: true)
            project.task('buildBundle')

            def variants = AndroidPluginUtils.getVariants(project)
            variants.all { variant ->
                if (variant.buildType.name != 'release') return

                configureReleaseVariant(variant)
            }
        }
    }

    protected void configureReleaseVariant(BaseVariant variant) {
        def android = AndroidPluginUtils.getAndroid(project)
        def outputFile = rootSmall.getBundleOutput(variant.applicationId)

        project.task('prepareAsset', type: Copy) {
            ext {
                srcDir = android.sourceSets.main.assetsDirectories[0]
                destDir = small.assetsDir
            }
            inputs.dir srcDir
            outputs.dir destDir

            from srcDir
            into destDir
        }.doLast {
            // Generate AndroidManifest.xml
            Aapt aapt = new Aapt(destDir, null, null, android.buildToolsRevision)
            def aaptTask = project.processReleaseResources
            def aaptExe = aaptTask.buildTools.getPath(BuildToolInfo.PathId.AAPT)
            def cf = android.defaultConfig
            def baseAsset = new File(android.getSdkDirectory(),
                    "platforms/${android.getCompileSdkVersion()}/android.jar")
            aapt.manifest(project, [packageName: cf.applicationId,
                                    versionName: cf.versionName, versionCode: cf.versionCode,
                                    aaptExe: aaptExe, baseAsset: baseAsset.path]
            )
        }

        def sc = android.buildTypes.release.signingConfig
        def packageAsset = project.task('packageAsset', dependsOn: 'prepareAsset') {
            ext {
                srcDir = project.prepareAsset.destDir
                destFile = (sc == null) ? outputFile : small.unsignedFile
            }
            inputs.dir srcDir
            outputs.file destFile
        }.doLast {
            project.ant.zip(baseDir: srcDir, destFile: destFile)
        }
        if (sc == null) {
            project.buildBundle.dependsOn packageAsset
            packageAsset.doLast {
                logOutput(outputFile)
            }
            return
        }

        // If contains a release signing config, sign the package with ant's SignJar task
        def signAsset = project.task('signAsset', dependsOn: 'packageAsset') {
            ext {
                srcFile = project.packageAsset.destFile
                destFile = outputFile
            }
        }.doLast {
            def dir = destFile.parentFile
            if (!dir.exists()) dir.mkdirs()
            ant.signjar(jar: srcFile, signedjar: destFile, keystore: sc.storeFile.path,
                    storepass: sc.storePassword, alias: sc.keyAlias, keypass: sc.keyPassword,
                    digestalg: 'SHA1', sigalg: 'MD5withRSA') // Fix issue #13
        }

        project.buildBundle.dependsOn signAsset
        signAsset.doLast {
            logOutput(outputFile)
        }
    }

    def logOutput(File apk) {
        def outPath = "${rootSmall.outputBundlePath}/$apk.name"
        def outSize = FileUtils.getFormatSize(apk)
        Log.footer("[$project.name] copy to $outPath (${apk.length()} bytes = $outSize)")
    }
}
