package net.wequick.gradle

import com.android.build.api.transform.Format
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.pipeline.IntermediateFolderUtils
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.transforms.ProGuardTransform
import org.apache.commons.io.FileUtils
import org.gradle.api.Project

class LibraryPlugin extends AppPlugin {

    private File mBakBuildFile

    void apply(Project project) {
        super.apply(project)
        mBakBuildFile = new File(project.buildFile.parentFile, "${project.buildFile.name}~")
    }

    @Override
    protected PluginType getPluginType() {
        return PluginType.Library
    }

    @Override
    protected String getSmallCompileType() {
        if (rootSmall.isBuildingApps() || rootSmall.isBuildingLibs()) {
            return 'debugCompile'
        }
        return 'compile'
    }

    @Override
    protected void beforeEvaluate(boolean released) {
        super.beforeEvaluate(released)
        if (!released) return

        // Change android plugin from `lib' to `application' dynamically
        // FIXME: Any better way without edit file?

        if (mBakBuildFile.exists()) {
            // With `tidyUp', should not reach here
            throw new Exception("Conflict buildFile, please delete file $mBakBuildFile or " +
                    "${project.buildFile}")
        }

        def text = project.buildFile.text.replaceAll(
                'com\\.android\\.library', 'com.android.application')
        project.buildFile.renameTo(mBakBuildFile)
        project.buildFile.write(text)
    }

    @Override
    protected void afterEvaluate(boolean released) {
        super.afterEvaluate(released)

        if (released) { //< apply: 'com.android.application'
            // Set application id
            def manifest = new XmlParser().parse(android.sourceSets.main.manifestFile)
            android.defaultConfig.applicationId = manifest.@package
            mDependentLibProjects.each {
                project.preBuild.dependsOn "${it.path}:buildLib"
            }
        } else { //< apply: 'com.android.library'
            // Cause `isBuildingRelease()' return false, at this time, super's
            // `hookJavacTask' will not be triggered. Provided the necessary jars here.
            def smallJar = project.fileTree(
                    dir: rootSmall.preBaseJarDir, include: [SMALL_JAR_PATTERN])
            def libJars = project.fileTree(dir: rootSmall.preLibsJarDir,
                    include: mDependentLibProjects.collect { "$it.name-${it.version}.jar" })
            project.dependencies.add('provided', smallJar)
            project.dependencies.add('provided', libJars)

            // Resolve the transform tasks
            project.preBuild.doLast {
                def ts = project.tasks.withType(TransformTask.class)

                ts.each { t ->
                    if (t.transform.outputTypes.isEmpty()) return
                    if (t.transform.scopes.isEmpty()) return

                    def requiredOutput = IntermediateFolderUtils.getContentLocation(
                            t.streamOutputFolder, 'main',
                            t.transform.outputTypes, t.transform.scopes,
                            Format.DIRECTORY) // folders/2000/1f/main
                    def requiredScope = requiredOutput.parentFile // folders/2000/1f
                    if (requiredScope.exists()) return
                    def typesDir = requiredScope.parentFile // folders/2000
                    if (!typesDir.exists()) return

                    def currentScope = typesDir.listFiles().find { it.isDirectory() }
                    if (currentScope != requiredScope) {
                        // Scope conflict!
                        // This may be caused by:
                        // - 1. After `buildLib', the `lib.*' module was apply to
                        //      'com.android.application' and the transform scopes turn to be `1f'.
                        // - 2. In other way, it was apply to
                        //      'com.android.library' and the scopes are `3'.
                        // What we can do is just rename the folder to make consistent.
                        currentScope.renameTo(requiredScope)
                    }
                }
            }
        }
    }

    @Override
    protected void createTask() {
        super.createTask()

        project.task('cleanLib', dependsOn: 'clean')
        project.task('buildLib', dependsOn: 'assembleRelease')

        project.tasks.remove(project.cleanBundle)
        project.tasks.remove(project.buildBundle)
    }

    @Override
    protected void configureProguard(BaseVariant variant, TransformTask proguard, ProGuardTransform pt) {
        super.configureProguard(variant, proguard, pt)

        // The `lib.*' modules are referenced by any `app.*' modules,
        // so keep all the public methods for them.
        pt.keep("class ${variant.applicationId}.** { public *; }")
    }

    @Override
    protected void configureReleaseVariant(BaseVariant variant) {
        super.configureReleaseVariant(variant)

        small.jar = project.jarReleaseClasses

        variant.assemble.doLast {
            // Generate jar file to root pre-jar directory
            // FIXME: Create a task for this
            def jarName = getJarName(project)
            def jarFile = new File(rootSmall.preLibsJarDir, jarName)
            if (mMinifyJar != null) {
                FileUtils.copyFile(mMinifyJar, jarFile)
            } else {
                project.ant.jar(baseDir: small.javac.destinationDir, destFile: jarFile)
            }

            // Backup R.txt to public.txt
            // FIXME: Create a task for this
            if (!small.symbolFile.exists())  return

            def publicIdsPw = new PrintWriter(small.publicSymbolFile.newWriter(false))
            small.symbolFile.eachLine { s ->
                if (!s.contains("styleable")) {
                    publicIdsPw.println(s)
                }
            }
            publicIdsPw.flush()
            publicIdsPw.close()
        }
    }

    @Override
    protected void tidyUp() {
        super.tidyUp()
        // Restore library module's android plugin to `com.android.library'
        if (mBakBuildFile != null && mBakBuildFile.exists()) {
            project.buildFile.delete()
            mBakBuildFile.renameTo(project.buildFile)
        }
    }
}
