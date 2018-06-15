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

class BundleHandler {

    String hostModuleName
    Map<String, Set<String>> bundleModuleNames

    void host(String moduleName) {
        hostModuleName = moduleName
    }

    void declareModule(String type, String... moduleNames) {
        if (bundleModuleNames == null) {
            bundleModuleNames = [:]
        }
        Set<String> names = bundleModuleNames[type]
        if (names == null) {
            names = bundleModuleNames[type] = []
        }
        names.addAll(moduleNames)
    }

    String getType(String moduleName) {
        if (bundleModuleNames == null) return null

        def e = bundleModuleNames.find { type, names ->
            names.contains(moduleName)
        }
        if (e == null) return null

        return e.key
    }

    void stub(String... moduleNames) {
        declareModule('stub', moduleNames)
    }

    void lib(String... moduleNames) {
        declareModule('lib', moduleNames)
    }

    void app(String... moduleNames) {
        declareModule('app', moduleNames)
    }

}
