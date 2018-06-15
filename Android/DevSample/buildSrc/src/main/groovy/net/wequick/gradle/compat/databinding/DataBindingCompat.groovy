package net.wequick.gradle.compat.databinding

import com.android.build.gradle.BaseExtension
import groovy.io.FileType
import com.android.build.gradle.api.BaseVariant
import net.wequick.gradle.internal.Version
import net.wequick.gradle.util.AndroidPluginUtils
import net.wequick.gradle.util.DependenciesUtils
import net.wequick.gradle.util.Log
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.compile.JavaCompile

class DataBindingCompat {
    private static String ARTIFACT_NAME = 'smallDataBindingJar'

    static void compileSmallDataBindingJar(Project project) {
        addSmallDataBindingJarDependency(project, false)
    }

    static void provideSmallDataBindingJar(Project project) {
        addSmallDataBindingJarDependency(project, true)

        def android = AndroidPluginUtils.getAndroid(project)
        android.registerTransform(new StripDataBindingTransform())
    }

    /**
     * Make the host `DataBinderMapper` class extends to `small.databinding.DataBinderMapper`
     * which supports dispatching android data binding to each bundle `DataBinderMapper` in runtime.
     *
     * @param host the host project
     * @param javac the javac task
     * @param variantDirName current variant dir
     */
    static void generateBaseDataBinderMapperClass(Project host,
                                                  JavaCompile javac,
                                                  String variantDirName) {
        def android = AndroidPluginUtils.getAndroid(host)
        if (!android.dataBinding.enabled) return

        javac.dependsOn "$host.rootProject.path$ARTIFACT_NAME"
        javac.doLast {
            // Recompile android.databinding.DataBinderMapper
            File aptDir = new File(host.buildDir, "generated/source/apt/$variantDirName")
            if (!aptDir.exists()) {
                return
            }

            File bindingPkgDir = new File(aptDir, 'android/databinding')
            File dataBinderMapperJava = new File(bindingPkgDir, 'DataBinderMapper.java')
            InputStreamReader ir = new InputStreamReader(new FileInputStream(dataBinderMapperJava))
            String code = ''
            String line
            while ((line = ir.readLine()) != null) {
                if (line.startsWith('class DataBinderMapper')) {
                    code += 'class DataBinderMapper extends small.databinding.DataBinderMapper {\n'
                    continue
                }
                if (line.startsWith('    public DataBinderMapper()')) {
                    code += line + '\n'
                    break
                }
                code += line + '\n'
            }
            code += '    }\n}'
            ir.close()

            File bak = new File(bindingPkgDir, 'DataBinderMapper.java~')
            dataBinderMapperJava.renameTo(bak)

            dataBinderMapperJava.createNewFile()
            dataBinderMapperJava.write(code)

            def bootClassPath = android.bootClasspath.collect{it.absolutePath}.join(';')
            host.ant.javac(srcdir: bindingPkgDir,
                    source: javac.sourceCompatibility,
                    target: javac.targetCompatibility,
                    destdir: javac.destinationDir,
                    includes: 'DataBinderMapper.java',
                    classpath: javac.classpath.asPath,
                    bootclasspath: bootClassPath,
                    includeantruntime: false)

            bak.renameTo(dataBinderMapperJava)
        }
    }

    /**
     * Refactor the `android.databinding.DataBinderMapper` to
     * `${bundle.applicationId}.databinding.DataBinderMapper` for the bundle.
     * Also implements the `small.databinding.DataBinderMappable` interface to make it
     * friendly call by the base `small.databinding.DataBinderMapper`.
     *
     * @param project
     * @param javac
     * @param variantDirName
     * @param packageName
     * @param packagePath
     */
    static void generateBundleDataBinderMapperClass(Project project,
                                                    BaseVariant variant) {
        // Move android.databinding.DataBinderMapper to [pkg].databinding.DataBinderMapper

        String variantDirName = variant.dirName
        String packageName = variant.applicationId
        String packagePath = packageName.replace('.', '/')
        BaseExtension android = AndroidPluginUtils.getAndroid(project)
        JavaCompile javac = variant.javaCompile

        javac.dependsOn "$project.rootProject.path$ARTIFACT_NAME"
        javac.doLast {
            final String targetJavaName = 'DataBinderMapper.java'
            File genSourceDir = new File(project.buildDir, 'generated/source')
            File aptDir = new File(genSourceDir, "apt/$variantDirName")
            File bindingPkgDir = new File(aptDir, 'android/databinding')
            File dataBinderMapperJava = new File(bindingPkgDir, targetJavaName)
            InputStreamReader ir = new InputStreamReader(new FileInputStream(dataBinderMapperJava))
            String code = ''
            String line
            def rules = [
                    [from: 'package android.databinding;', to: "package ${packageName}.databinding;", full: true],
                    [from: 'class DataBinderMapper', to: 'class DataBinderMapper implements small.databinding.DataBinderMappable'],
                    [from: '    android.databinding.ViewDataBinding getDataBinder', to: '    public android.databinding.ViewDataBinding getDataBinder'],
                    [from: '    int getLayoutId', to: '    public int getLayoutId'],
                    [from: '    String convertBrIdToString', to: '    public String convertBrIdToString']
            ]
            while ((line = ir.readLine()) != null) {
                boolean parsed = false
                for (Map rule : rules) {
                    if (!rule.parsed && line.startsWith(rule.from)) {
                        if (rule.full) {
                            code += rule.to + '\n'
                        } else {
                            code += line.replace(rule.from, rule.to) + '\n'
                        }
                        rule.parsed = parsed = true
                        break
                    }
                }
                if (parsed) continue

                code += line + '\n'
            }
            ir.close()

            File smallBindingPkgDir = new File(aptDir, "$packagePath/databinding")
            if (!smallBindingPkgDir.exists()) {
                smallBindingPkgDir.mkdirs()
            }
            dataBinderMapperJava = new File(smallBindingPkgDir, targetJavaName)
            dataBinderMapperJava.write(code)

            def bootClassPath = android.bootClasspath.collect{it.absolutePath}.join(';')

            project.ant.javac(srcdir: smallBindingPkgDir,
                    source: javac.sourceCompatibility,
                    target: javac.targetCompatibility,
                    destdir: javac.destinationDir,
                    includes: targetJavaName,
                    sourcepath: aptDir.path,
                    classpath: javac.classpath.asPath,
                    bootclasspath: bootClassPath,
                    includeantruntime: false)

            // Delete classes in package 'android.databinding'
            File bindingClassesDir = new File(javac.destinationDir, 'android/databinding')
            if (bindingClassesDir.exists()) {
                bindingClassesDir.deleteDir()
            }
            // Delete unused R.class
            File bindingRClassesDir = new File(javac.destinationDir, 'com/android/databinding/library')
            if (bindingRClassesDir.exists()) {
                bindingRClassesDir.deleteDir()
            }
            // Delete classes in library which contains 'BR.class'
            def bindingReferenceDirs = []
            def retainedPackagePath = new File(javac.destinationDir, packagePath)
            javac.destinationDir.eachFileRecurse(FileType.FILES, {
                if (it.name == 'BR.class') {
                    if (it.parentFile != retainedPackagePath) {
                        bindingReferenceDirs.add(it.parentFile)
                    }
                }
            })
            bindingReferenceDirs.each {
                it.deleteDir()
            }

            Log.success "[${project.name}] split databinding classes..."
        }
    }

    private static void addSmallDataBindingJarDependency(Project project, boolean compilesOnly) {
        def repo = project.rootProject
        if (!repo.configurations.collect{it.name}.contains(ARTIFACT_NAME)) {
            createSmallDataBindingJarProvider(repo, ARTIFACT_NAME)
        }

        def aar = "small.support:databinding:${Version.SMALL_BINDING_AAR_VERSION}"
        DependenciesUtils.compile(project, aar)
    }

    private static void createSmallDataBindingJarProvider(Project repo, String artifactName) {
        repo.configurations.maybeCreate(artifactName)

        def jarPath = new File(repo.buildDir, 'small/compat/data-binding')
        def jarName = 'small-databinding-stub.jar'
        def jarFile = new File(jarPath, jarName)
        def jarTask = repo.task(artifactName) { Task jar ->
            jar.outputs.file(jarFile)
        }.doLast {
            InputStream is = getClass().getClassLoader().getResourceAsStream(jarName)
            if (is != null) {
                if (!jarPath.exists()) jarPath.mkdirs()

                if (!jarFile.exists()) {
                    OutputStream out = new FileOutputStream(jarFile)
                    byte[] buffer = new byte[1024]
                    int read
                    while ((read = is.read(buffer)) != -1) {
                        out.write(buffer, 0, read)
                    }
                    out.flush()
                    out.close()
                }

                is.close()
            }
        }

//        repo.artifacts.add(artifactName, [file: jarFile, builtBy: jarTask])
    }
}