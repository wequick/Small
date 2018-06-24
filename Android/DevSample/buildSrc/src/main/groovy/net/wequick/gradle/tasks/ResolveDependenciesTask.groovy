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
package net.wequick.gradle.tasks

import net.wequick.gradle.RootExtension
import net.wequick.gradle.util.DependenciesUtils
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction

class ResolveDependenciesTask extends DefaultTask {

    private FileCollection sourceFiles
    private FileCollection outputFiles
    private File dependenciesFileOutDir

    ResolveDependenciesTask() {
        RootExtension small = project.small
        dependenciesFileOutDir = small.dependenciesDir

        def inputFiles = []
        def outputFiles = []
        inputFiles.add(project.buildFile)
        project.subprojects.each {
            inputFiles.add(it.buildFile)
            outputFiles.add(getProvidedAarDependenciesOutputFile(it))
            outputFiles.add(getProvidedJarDependenciesOutputFile(it))
            outputFiles.add(getCompiledAarDependenciesOutputFile(it))
        }

        sourceFiles = project.files(inputFiles)
        this.outputFiles = project.files(outputFiles)
    }

    @InputFiles
    FileCollection getSourceFiles() {
        return sourceFiles
    }

    @OutputFiles
    FileCollection getOutputFiles() {
        return outputFiles
    }

    File getProvidedAarDependenciesOutputFile(Project bundle) {
        return new File(dependenciesFileOutDir, "provided-aar/${bundle.name}.txt")
    }

    File getProvidedJarDependenciesOutputFile(Project bundle) {
        return new File(dependenciesFileOutDir, "provided-jar/${bundle.name}.txt")
    }

    File getCompiledAarDependenciesOutputFile(Project bundle) {
        return new File(dependenciesFileOutDir, "compiled-aar/${bundle.name}.txt")
    }

    @TaskAction
    def run() {
        RootExtension small = project.extensions.getByType(RootExtension.class)
        if (small == null) return

        def hostProject = small.hostProject
        def baseProjects = small.baseProjects
        def depMap = new HashMap<Project, Set<ResolvedDependency>>()

        project.subprojects.each { child ->
            // Collect aar dependencies
            Set<ResolvedDependency> ds = DependenciesUtils.getAllCompileDependencies(child)
            if (ds == null) return

            depMap.put(child, ds)
        }

        depMap.each { bundle, dependencies ->
            if (!small.bundleProjects.contains(bundle)) return

            def compiledAarNames = new HashSet<String>()
            def strippedAarNames = new HashSet<String>()
            def libJarPaths = new HashSet<String>()
            def libProjects = [hostProject]
            libProjects.addAll small.stubProjects

            dependencies.each { d ->
                def libProject = baseProjects.find { it.name == d.moduleName }
                if (libProject != null) {
                    libProjects.add libProject
                    strippedAarNames.add d.name
                } else if (shouldStripAar(d)) {
                    strippedAarNames.add d.name
                }
                compiledAarNames.add d.name
            }
            libProjects.each { lib ->
                Set<ResolvedDependency> libDs = depMap[lib]
                libDs.each { d ->
                    strippedAarNames.add d.name

                    d.moduleArtifacts.each { art ->
                        if (art.type == 'jar') {
                            libJarPaths.add art.file.absolutePath
                        }
                    }
                }
            }

            // Record provided aar dependencies
            writeDependencies(strippedAarNames, getProvidedAarDependenciesOutputFile(bundle))

            // Record provided jar dependencies
            writeDependencies(libJarPaths, getProvidedJarDependenciesOutputFile(bundle))

            // Record compiled aar dependencies
            compiledAarNames.removeAll(strippedAarNames)
            writeDependencies(compiledAarNames, getCompiledAarDependenciesOutputFile(bundle))
        }
    }

    boolean shouldStripAar(ResolvedDependency aar) {
        if (aar.moduleGroup == 'net.wequick.small') {
            return true
        }

        if (aar.moduleGroup == project.rootProject.name) {
            if (aar.moduleName == 'small') {
                return true
            }
        }

        return false
    }

    static void writeDependencies(Set<String> dependencies, File outputFile) {
        if (dependencies.size() == 0) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            return
        }

        def pw = new PrintWriter(outputFile)
        dependencies.each {
            pw.println it
        }
        pw.flush()
        pw.close()
    }
}