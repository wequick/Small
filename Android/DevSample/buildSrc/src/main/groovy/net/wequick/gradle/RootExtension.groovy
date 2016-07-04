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

    /** Count of libraries */
    protected int libCount

    /** Count of bundles */
    protected int bundleCount

    /** Project of Small AAR module */
    protected Project smallProject

    /** Project of host */
    protected Project hostProject

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
}
