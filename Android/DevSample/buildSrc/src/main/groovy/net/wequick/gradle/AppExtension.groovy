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
import org.gradle.api.tasks.compile.JavaCompile

public class AppExtension extends BundleExtension {

    /** Task of java compiler */
    JavaCompile javac

    /** Task of merge manifest */
    Task processManifest

    /** Variant application id */
    String packageName

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

    /** File of resources.ap_ */
    File apFile

    /** File of R.java */
    File rJavaFile

    /** File of merger.xml */
    File mergerXml

    /** Public symbol file - public.txt */
    File publicSymbolFile

    /** Paths of aar to split */
    Set<Map> splitAars

    /** Paths of aar to retain */
    Set<Map> retainedAars

    /** File of split R.java */
    File splitRJavaFile

    LinkedHashMap<Integer, Integer> idMaps
    LinkedHashMap<String, String> idStrMaps
    ArrayList retainedTypes
    ArrayList retainedStyleables
    Map<String, List> vendorTypes
    Map<String, List> vendorStyleables

    /** List of all resource types */
    ArrayList allTypes

    /** List of all resource styleables */
    ArrayList allStyleables

    AppExtension(Project project) {
        super(project)

        File interDir = new File(project.buildDir, FD_INTERMEDIATES)

        aarDir = new File(interDir, 'exploded-aar')
        publicSymbolFile = new File(project.projectDir, 'public.txt')
    }
}
