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
import org.gradle.util.VersionNumber

public class RootExtension extends BaseExtension {

    private static final String FD_BUILD_SMALL = 'build-small'
    private static final String FD_PRE_JAR = 'small-pre-jar'
    private static final String FD_PRE_AP = 'small-pre-ap'
    private static final String FD_PRE_IDS = 'small-pre-ids'
    private static final String FD_PRE_LINK = 'small-pre-link'
    private static final String FD_BASE = 'base'
    private static final String FD_LIBS = 'libs'
    private static final String FD_JAR = 'jar'
    private static final String FD_AAR = 'aar'

    /** The minimum small aar version required */
    private static final String REQUIRED_AAR_VERSION = '1.0.0'
    private static final VersionNumber REQUIRED_AAR_REVISION = VersionNumber.parse(REQUIRED_AAR_VERSION)

    /** 
     * Version of aar net.wequick.small:small
     * default to `gradle-small' plugin version 
     */
    String aarVersion

    /**
     * Host module name
     * default to `app'
     */
    String hostModuleName

    /** The parsed revision of `aarVersion' */
    private VersionNumber aarRevision

    /**
     * Strict mode, <tt>true</tt> if keep only resources in bundle's res directory.
     */
    boolean strictSplitResources = true

    /**
     * The default android version configuration
     * - compileSdkVersion
     * - buildToolsVersion
     * - support library version (AppCompat and etc.)
     */
    protected AndroidConfig android

    /**
     * If <tt>true</tt> build plugins to host assets as *.apk,
     * otherwise build to host smallLibs as *.so
     */
    boolean buildToAssets = false

    /** Count of libraries */
    protected int libCount

    /** Count of bundles */
    protected int bundleCount

    /** Project of Small AAR module */
    protected Project smallProject

    /** Project of host */
    protected Project hostProject

    /** Project of host which are automatically dependent by other bundle modules */
    protected Set<Project> hostStubProjects

    /** Project of lib.* */
    protected Set<Project> libProjects

    /** Project of app.* */
    protected Set<Project> appProjects

    /** Directory to output bundles (*.so) */
    protected File outputBundleDir

    private File preBuildDir

    /** Directory of pre-build host and android support jars */
    private File preBaseJarDir

    /** Directory of pre-build libs jars */
    private File preLibsJarDir

    /** Directory of pre-build resources.ap_ */
    private File preApDir

    /** Directory of pre-build R.txt */
    private File preIdsDir

    /** Directory of prepared dependencies */
    private File preLinkAarDir
    private File preLinkJarDir

    protected String mP // the executing gradle project name
    protected String mT // the executing gradle task name

    RootExtension(Project project) {
        super(project)

        hostModuleName = 'app'

        preBuildDir = new File(project.projectDir, FD_BUILD_SMALL)
        def interDir = new File(preBuildDir, FD_INTERMEDIATES)
        def jarDir = new File(interDir, FD_PRE_JAR)
        preBaseJarDir = new File(jarDir, FD_BASE)
        preLibsJarDir = new File(jarDir, FD_LIBS)
        preApDir = new File(interDir, FD_PRE_AP)
        preIdsDir = new File(interDir, FD_PRE_IDS)
        def preLinkDir = new File(interDir, FD_PRE_LINK)
        preLinkJarDir = new File(preLinkDir, FD_JAR)
        preLinkAarDir = new File(preLinkDir, FD_AAR)

        // Parse gradle task
        def sp = project.gradle.startParameter
        def t = sp.taskNames[0]
        if (t != null) {
            def p = sp.projectDir
            def pn = null
            if (p == null) {
                if (t.startsWith(':')) {
                    // gradlew :app.main:assembleRelease
                    def tArr = t.split(':')
                    if (tArr.length == 3) { // ['', 'app.main', 'assembleRelease']
                        pn = tArr[1]
                        t = tArr[2]
                    }
                }
            } else if (p != project.rootProject.projectDir) {
                // gradlew -p [project.name] assembleRelease
                pn = p.name
            }
            mP = pn
            mT = t
        }
    }

    public File getPreBuildDir() {
        return preBuildDir
    }

    public File getPreBaseJarDir() {
        return preBaseJarDir
    }

    public File getPreLibsJarDir() {
        return preLibsJarDir
    }

    public File getPreApDir() {
        return preApDir
    }

    public File getPreIdsDir() {
        return preIdsDir
    }

    public File getPreLinkJarDir() {
        return preLinkJarDir
    }

    public File getPreLinkAarDir() {
        return preLinkAarDir
    }

    public String getAarVersion() {
        if (aarVersion == null) {
            throw new RuntimeException(
                    'Please specify Small aar version in your root build.gradle:\n' +
                            "small {\n    aarVersion = '[the_version]'\n}")
        }

        if (aarRevision == null) {
            synchronized (this.class) {
                if (aarRevision == null) {
                    aarRevision = VersionNumber.parse(aarVersion)
                }
            }
        }
        if (aarRevision < REQUIRED_AAR_REVISION) {
            throw new RuntimeException(
                    "Small aar version $REQUIRED_AAR_VERSION is required. Current version is $aarVersion"
            )
        }

        return aarVersion
    }

    Map<String, Set<String>> bundleModules = [:]

    public void bundles(String type, String name) {
        def modules = bundleModules.get(type)
        if (modules == null) {
            modules = new HashSet<String>()
            bundleModules.put(type, modules)
        }
        modules.add(name)
    }

    public void bundles(String type, names) {
        def modules = bundleModules.get(type)
        if (modules == null) {
            modules = new HashSet<String>()
            bundleModules.put(type, modules)
        }
        modules.addAll(names)
    }

    /** Check if is building any libs (lib.*) */
    protected boolean isBuildingLibs() {
        if (mT == null) return false // no tasks

        if (mP == null) {
            // ./gradlew buildLib
            return (mT == 'buildLib')
        } else {
            // ./gradlew -p lib.xx aR | ./gradlew :lib.xx:aR
            return (mP.startsWith('lib.') && (mT == 'assembleRelease' || mT == 'aR'))
        }
    }

    /** Check if is building any apps (app.*) */
    protected boolean isBuildingApps() {
        if (mT == null) return false // no tasks

        if (mP == null) {
            // ./gradlew buildBundle
            return (mT == 'buildBundle')
        } else {
            // ./gradlew -p app.xx aR | ./gradlew :app.xx:aR
            return (mP.startsWith('app.') && (mT == 'assembleRelease' || mT == 'aR'))
        }
    }

    protected boolean isLibProject(Project project) {
        boolean found = false;
        if (libProjects != null) {
            found = libProjects.contains(project);
        }
        if (!found && hostStubProjects != null) {
            found = hostStubProjects.contains(project);
        }
        return found;
    }

    protected boolean isLibProject(String name) {
        boolean found = false;
        if (libProjects != null) {
            found = libProjects.find{ it.name == name } != null;
        }
        if (!found && hostStubProjects != null) {
            found = hostStubProjects.find{ it.name == name } != null;
        }
        return found;
    }

    public def android(Closure closure) {
        android = new AndroidConfig()
        project.configure(android, closure)
    }

    class AndroidConfig {
        int compileSdkVersion
        String buildToolsVersion
        String supportVersion
    }
}
