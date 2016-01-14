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

import org.gradle.api.Project

public class AssetExtension extends BundleExtension {

    /** */
    protected File assetsDir

    protected File unsignedFile

    AssetExtension(Project project) {
        super(project)

        File interDir = new File(project.buildDir, FD_INTERMEDIATES)
        assetsDir = new File(interDir, 'small-asset')
        unsignedFile = new File(interDir, 'outputs/asset-unsigned.zip')
    }
}
