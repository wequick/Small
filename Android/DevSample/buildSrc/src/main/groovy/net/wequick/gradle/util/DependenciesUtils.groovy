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

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.UnknownConfigurationException

/** Class to resolve project dependencies */
public final class DependenciesUtils {

    public static List getAllDependencies(Project project, String config) {
        Configuration configuration
        try {
            configuration = project.configurations[config]
        } catch (UnknownConfigurationException ignored) {
            return null
        }

        ResolvedConfiguration resolvedConfiguration = configuration.resolvedConfiguration
        def firstLevelDependencies = resolvedConfiguration.firstLevelModuleDependencies
        def allDependencies = []
        firstLevelDependencies.findAll { it.parents[0].configuration == config }.each {
            collectDependencies(it, allDependencies)
        }
        return allDependencies
    }

    private static def collectDependencies(ResolvedDependency node, List out) {
        if (out.find { addedNode -> addedNode.name == node.name } == null) {
            out.add(node)
        }
        // Recursively
        node.children.each { newNode ->
            collectDependencies(newNode, out)
        }
    }
}