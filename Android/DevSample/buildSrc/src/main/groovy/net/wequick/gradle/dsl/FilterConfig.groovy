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
package net.wequick.gradle.dsl

import org.gradle.api.Project

class FilterConfig {

    static class ResourcesConfig {
        List<String> entries

        void entry(String attribute) {
            if (entries == null) {
                entries = []
            }
            entries.add attribute
        }

        void entries(List<String> attrs) {
            if (entries == null) {
                entries = []
            }
            entries.addAll attrs
        }
    }

    static class ManifestConfig {
        List<String> attributes

        void attribute(String attribute) {
            if (attributes == null) {
                attributes = []
            }
            attributes.add attribute
        }

        void attributes(List<String> attrs) {
            if (attributes == null) {
                attributes = []
            }
            attributes.addAll attrs
        }
    }

    Project project

    ManifestConfig mManifestConfig
    ManifestConfig getManifestConfig() {
        if (mManifestConfig == null) {
            mManifestConfig = new ManifestConfig()
        }
        return mManifestConfig
    }
    ManifestConfig manifest(@DelegatesTo(value = ManifestConfig) Closure closure) {
        project.configure(getManifestConfig(), closure)
    }

    ResourcesConfig mResourcesConfig
    ResourcesConfig getResourcesConfig() {
        if (mResourcesConfig == null) {
            mResourcesConfig = new ResourcesConfig()
        }
        return mResourcesConfig
    }
    ResourcesConfig resources(@DelegatesTo(value = ResourcesConfig) Closure closure) {
        project.configure(getResourcesConfig(), closure)
    }
}
