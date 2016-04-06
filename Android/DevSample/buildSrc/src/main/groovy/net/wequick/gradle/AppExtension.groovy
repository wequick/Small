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
import org.gradle.api.Task

public class AppExtension extends BundleExtension {

    /** Task of java compiler */
    Task javac

    /** Task of dex */
    Task dex

    /** Task of merge manifest */
    Task processManifest

    /** Package path for java classes */
    String packagePath

    /** Directory of all compiled java classes */
    File classesDir

    /** Directory of split compiled java classes */
    File bkClassesDir

    /** Symbol file - R.txt */
    File symbolFile

    /** Directory of all exploded aar */
    File aarDir

    /** Directory of split exploded aar */
    File bkAarDir

    /** File of resources.ap_ */
    File apFile

    /** File of R.java */
    File rJavaFile

    /** File of merger.xml */
    File mergerXml

    /** Public symbol file - public.txt */
    File publicSymbolFile

    LinkedHashMap<Integer, Integer> idMaps
    LinkedHashMap<String, String> idStrMaps
    ArrayList retainedTypes
    ArrayList retainedStyleables

    AppExtension(Project project) {
        super(project)

        File interDir = new File(project.buildDir, FD_INTERMEDIATES)

        aarDir = new File(interDir, 'exploded-aar')
        bkAarDir = new File(interDir, 'exploded-aar~')
        publicSymbolFile = new File(project.projectDir, 'public.txt')
    }
}
