package net.wequick.gradle

import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.transforms.ProGuardTransform
import net.wequick.gradle.tasks.CleanBundleTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.compile.JavaCompile

class HostPlugin extends AndroidPlugin {

    void apply(Project project) {
        super.apply(project)
    }

    @Override
    protected void configureProject() {
        super.configureProject()
        
        project.afterEvaluate {
            // Configure libs dir
            def sourceSet = project.android.sourceSets.main
            def source = rootSmall.buildToAssets ? sourceSet.assets : sourceSet.jniLibs
            if (source.srcDirs == null) {
                source.srcDirs = [SMALL_LIBS]
            } else {
                source.srcDirs += SMALL_LIBS
            }
            // If contains release signing config, all bundles will be signed with it,
            // copy the config to debug type to ensure the signature-validating works
            // while launching application from IDE.
            def releaseSigningConfig = android.buildTypes.release.signingConfig
            if (releaseSigningConfig != null) {
                android.buildTypes.debug.signingConfig = releaseSigningConfig
            }

            // Add a build config to specify whether load-from-assets or not.
            android.defaultConfig.buildConfigField(
                    "boolean", "LOAD_FROM_ASSETS", rootSmall.buildToAssets ? "true" : "false")

            // Support data binding
            if (android.dataBinding.enabled) {
                if (rootSmall.smallProject != null) {
                    project.dependencies.add('compile', rootSmall.smallBindingProject)
                } else {
                    project.dependencies.add('compile', "${SMALL_BINDING_AAR_PREFIX}$rootSmall.bindingAarVersion")
                }
            }
        }
    }

    @Override
    protected PluginType getPluginType() {
        return PluginType.Host
    }

    @Override
    protected String getSmallCompileType() {
        return 'compile'
    }

    @Override
    protected void createTask() {
        super.createTask()

        project.task('cleanLib', type: CleanBundleTask)
        project.task('buildLib')
    }

    @Override
    protected void configureDebugVariant(BaseVariant variant) {
        super.configureDebugVariant(variant)

        hookDataBinding(variant.javaCompile, variant.dirName)
    }

    @Override
    protected void configureReleaseVariant(BaseVariant variant) {
        super.configureReleaseVariant(variant)

        hookDataBinding(variant.javaCompile, variant.dirName)

        if (small.jar != null) return // Handle once for multi flavors

        def flavor = variant.flavorName
        if (flavor != null) {
            flavor = flavor.capitalize()
            small.jar = project.tasks["jar${flavor}ReleaseClasses"]
            small.aapt = project.tasks["process${flavor}ReleaseResources"]
        } else {
            small.jar = project.jarReleaseClasses
            small.aapt = project.processReleaseResources
        }
        project.buildLib.dependsOn small.jar
    }

    @Override
    protected void configureProguard(BaseVariant variant, TransformTask proguard, ProGuardTransform pt) {
        super.configureProguard(variant, proguard, pt)
        pt.keep('class android.databinding.** { *; }')
        pt.dontwarn('small.databinding.**')
        pt.keep('class small.databinding.** { *; }')
        pt.keep('interface small.databinding.DataBinderMappable')
    }

    def hookDataBinding(JavaCompile javac, String variantDirName) {
        if (!android.dataBinding.enabled) return

        javac.doLast {
            // Recompile android.databinding.DataBinderMapper
            File aptDir = new File(project.buildDir, "generated/source/apt/$variantDirName")
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

            project.ant.javac(srcdir: bindingPkgDir,
                    source: javac.sourceCompatibility,
                    target: javac.targetCompatibility,
                    destdir: javac.destinationDir,
                    includes: 'DataBinderMapper.java',
                    classpath: javac.classpath.asPath,
                    bootclasspath: android.bootClasspath.join(';'),
                    includeantruntime: false)

            bak.renameTo(dataBinderMapperJava)
        }
    }
}
