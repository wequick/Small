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

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.tasks.MergeResources
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip

// FIXME: internals
import com.android.build.gradle.internal.TaskContainerAdaptor
import com.android.build.gradle.internal.TaskManager.MergeType

/** Class to resolve project dependencies */
final class DependenciesUtils {


    static Set<ResolvedDependency> getAllCompileDependencies(Project project) {
        def c = getCompileConfiguration(project)
        return getAllDependencies(c)
    }

    static Set<ResolvedDependency> getAllDependencies(Project project, String config) {
        if (project == null) return null
        return getAllDependencies(project, config, null)
    }

    static Set<ResolvedDependency> getAllDependencies(Project project, String config, Closure filter) {
        Configuration configuration = getConfiguration(project, config)
        return getAllDependencies(configuration, filter)
    }

    static Set<ResolvedDependency> getAllDependencies(Configuration configuration) {
        return getAllDependencies(configuration, null)
    }

    static Set<ResolvedDependency> getAllDependencies(Configuration configuration, Closure filter) {
        ResolvedConfiguration resolvedConfiguration = configuration.resolvedConfiguration
        def firstLevelDependencies = resolvedConfiguration.firstLevelModuleDependencies
        Set<ResolvedDependency> allDependencies = new HashSet<>()
        firstLevelDependencies.each {
            collectDependencies(it, filter, allDependencies)
        }
        return allDependencies
    }

    static Configuration getCompileConfiguration(Project project) {
        def c = getConfiguration(project, 'releaseCompileClasspath') // Android gradle plugin 3.0+
        if (c == null) {
            c = getConfiguration(project, 'compile')
        }
        return c
    }

    static Configuration getConfiguration(Project project, String config) {
        try {
            return project.configurations[config]
        } catch (UnknownConfigurationException ignored) {
            return null
        }
    }


    static void createAarProvider(Project lib) {
        def variants = AndroidPluginUtils.getVariants(lib)
        if (variants == null) {
            lib.afterEvaluate {
                variants = AndroidPluginUtils.getVariants(lib)
                assert (variants != null)
                createAarProvider(lib, variants, true)
            }
            return
        }

        createAarProvider(lib, variants, true)
    }

    static void createJarProvider(Project lib) {
        def variants = AndroidPluginUtils.getVariants(lib)
        if (variants == null) {
            lib.afterEvaluate {
                variants = AndroidPluginUtils.getVariants(lib)
                assert (variants != null)
                createAarProvider(lib, variants, false)
            }
            return
        }

        createAarProvider(lib, variants, false)
    }

    static void createAarProvider(Project lib, Collection<BaseVariant> variants, boolean providesRes) {
        def repo = lib.rootProject
        def libName = lib.name.replace('.', '').capitalize()
        def smallAar = null
        if (providesRes) {
            smallAar = "smallAar$libName"
        }

        def smallJar = "smallJar$libName"
        repo.configurations.maybeCreate(smallJar)

        def smallBuildDir = new File(lib.buildDir, 'small')
        variants.all { BaseVariant variant ->
            if (variant.buildType.name != 'release') return

            def smallJarTask = lib.task('smallJar')
            def variantName = variant.name
            def taskSuffix = variantName.capitalize()
            def javac = variant.javaCompile

            // Create task `smallJarRelease`
            Jar jar = lib.task("$smallJarTask.name$taskSuffix", dependsOn: javac, type: Jar) {
                def srcDir = javac.destinationDir
                def destDir = new File(smallBuildDir, 'jar')

                from srcDir
                destinationDir destDir
                archiveName "${lib.name}-${variantName}.jar"

                ext.outFile = new File(destDir, archiveName)
                inputs.dir javac.destinationDir
                outputs.file ext.outFile
            }

            smallJarTask.dependsOn jar
            repo.artifacts.add(smallJar, [file: jar.outFile, builtBy: jar])
            // Create a temporary `smallJar` file to ensure the dependencies resolver works.
//            createStubFile(jar.outFile)

            if (!providesRes) {
                return
            }

            def smallAarTask = lib.task('smallAar')
            def bundleDir = new File(smallBuildDir, "bundles/$variant.dirName")
            // Create task `packageReleaseResources`
            // Note: We had used some private or hidden API here.
            // TODO: Handle the merging from `src/main/res`, `src/$variant/res` to `small/$variant/res` by self.
            MergeResources packageResourcesTask = null
            if (lib.hasProperty('packageReleaseResources')) {
                packageResourcesTask = lib.packageReleaseResources
                bundleDir = packageResourcesTask.outputDir.parentFile
            } else {
                def androidPlugin = lib.plugins.findPlugin(AppPlugin.class)
                if (androidPlugin != null) {
                    def taskFactory = new TaskContainerAdaptor(lib.tasks)
                    def variantManager = androidPlugin.variantManager
                    def taskManager = variantManager.taskManager
                    def variantData = variant.variantData
                    def variantScope = variantData.getScope()
                    def resOutputDir = new File(bundleDir, 'res')

                    def mergeResources = taskManager.basicCreateMergeResourcesTask(taskFactory, variantScope,
                            MergeType.PACKAGE, resOutputDir, false, false, false)
                    packageResourcesTask = mergeResources.get(taskFactory)
                }
            }

            assert (packageResourcesTask != null)

            // Create task `smallBundleRelease`
            def zip = lib.task("$smallAarTask.name$taskSuffix", type: Zip) {
                def srcDir = bundleDir
                def destDir = new File(smallBuildDir, 'aar')

                from srcDir
                destinationDir destDir
                archiveName "${lib.name}-${variantName}.aar"

                ext.outFile = new File(destDir, archiveName)
                inputs.dir javac.destinationDir
                outputs.file ext.outFile
            }

            zip.dependsOn packageResourcesTask
            smallAarTask.dependsOn zip
//            repo.artifacts.add(smallAar, [file: zip.outFile, builtBy: zip])

            // Create a temporary `smallAar` file to ensure the dependencies resolver works.
            createStubFile(zip.outFile)
        }

        // Add the `smallAar` repo
        repo.allprojects {
            repositories {
                flatDir {
                    dirs new File(smallBuildDir, 'aar')
                }
            }
        }
    }

    static void provideAar(Project app, Project lib) {
        provideProject(app, lib, true)
    }

    static void provideJar(Project app, Project lib) {
        provideProject(app, lib, false)
    }

    private static void createStubFile(File file) {
        if (!file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }
        if (!file.exists()) {
            file.createNewFile()
        }
    }

    private static void provideProject(Project app, Project lib, boolean providesRes) {
        def repoPath = lib.rootProject.path
        def libName = lib.name.replace('.', '').capitalize()
        // Temporary-compile a small aar (contains the `res` directory only) from `lib.*`
        AndroidPluginUtils.getVariants(app).all { variant ->
            if (variant.buildType.name != 'release') return

            def taskSuffix = variant.name.capitalize()
            if (providesRes) {
                variant.preBuild.dependsOn "${lib.path}:smallAar$taskSuffix"
            }

            variant.javaCompiler.dependsOn "$lib.path:smallJar$taskSuffix"
//            variant.preBuild.dependsOn "${lib.path}:smallJar$taskSuffix"
        }

        if (providesRes) {
            compile(app, [group: 'small', name: "${lib.name}-release", ext: 'aar'])
        }
        // Provide the jar of `lib.*`
//        provide(app, lib.fileTree(dir: "build/small/jar", includes: ["${lib.name}-release.jar"]))
        provide(app, lib.fileTree(dir: "libs", includes: ['*.jar']))
        provide(app, app.dependencies.project(path: repoPath, configuration: "smallJar$libName"))
    }

    static void compile(Project app, Object o) {
        def dependencies = app.dependencies
        if (isAndroidGradlePlugin3(app)) {
            dependencies.add('implementation', o)
        } else {
            dependencies.add('compile', o)
        }
    }

    static void provide(Project app, Object o) {
        def dependencies = app.dependencies
        if (isAndroidGradlePlugin3(app)) {
            dependencies.add('compileOnly', o)
        } else {
            dependencies.add('provided', o)
        }
    }

    private static boolean isAndroidGradlePlugin3(Project app) {
        return app.configurations.collect { it.name }.contains('implementation')
    }

    private static void collectDependencies(ResolvedDependency node, Closure filter, Set<ResolvedDependency> out) {
        if (filter != null && !filter(node)) {
            return
        }

        if (out.find { addedNode -> addedNode.name == node.name } == null) {
            out.add(node)
        }
        // Recursively
        node.children.each { newNode ->
            collectDependencies(newNode, filter, out)
        }
    }
}