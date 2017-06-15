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
package net.wequick.gradle.test

import groovy.json.JsonSlurper
import net.wequick.gradle.RootExtension
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet

class BundleManifestTests extends UnitTests {

    RootExtension rootSmall
    File manifestFile

    BundleManifestTests(Project project) {
        rootSmall = project.rootProject.small
        def hostProject = rootSmall.hostProject
        manifestFile = new File(hostProject.projectDir, 'src/main/assets/bundle.json')
    }

    def testManifest() {
        tAssert(manifestFile.exists(), "Missing bundle.json")

        def json = new JsonSlurper()
        def manifest = json.parseText(manifestFile.text)

        tAssert(manifest.version == '1.0.0', "Version should be 1.0.0")

        def bundles = manifest.bundles
        bundles.each { bundle ->
            File out = rootSmall.getBundleOutput(bundle.pkg)
            if (!out.exists()) {
                String outName = out.path.substring(rootSmall.hostProject.projectDir.path.length() + 1)
                tAssert(out.exists(),
                        "Declare bundle '$bundle.uri' but missing output '$outName'",
                        "        Please check if:\n" +
                        "          - you had run buildLib and buildBundle, and\n" +
                        "          - the package name is correctly specified" +
                                " in your bundle's AndroidManifest.xml as '$bundle.pkg'")
            }
        }
    }
}