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
package net.wequick.gradle.aapt

import groovy.io.FileType
import org.gradle.api.Project

/**
 * Class to expand aapt function
 */
public class Aapt {

    public static final int ID_DELETED = -1

    private File mAssetDir
    private File mJavaFile
    private File mSymbolFile
    private def mToolsRevision

    Aapt(File assetDir, File javaFile, File symbolFile, def toolsRevision) {
        this.mAssetDir = assetDir
        this.mJavaFile = javaFile
        this.mSymbolFile = symbolFile
        this.mToolsRevision = toolsRevision
    }

    /**
     * Filter package assets by specific types
     * @param retainedTypes the resource types to retain
     * @param pp new package id
     * @param idMaps
     */
    void filterPackage(List retainedTypes, int pp, Map idMaps, List retainedStyleables) {
        File arscFile = new File(mAssetDir, 'resources.arsc')
        if (retainedTypes.size() == 0) {
            // Remove everything
            mJavaFile.write('')
            if (mSymbolFile != null) mSymbolFile.write('')
            arscFile.delete()
            return
        }

        def arscEditor = new ArscEditor(arscFile, mToolsRevision)

        // Filter R.java
        filterRjava(mJavaFile, retainedTypes, retainedStyleables, null)
        // Filter R.txt
        if (mSymbolFile != null) filterRtext(mSymbolFile, retainedTypes, retainedStyleables)
        // Filter resources.arsc
        arscEditor.slice(pp, idMaps, retainedTypes)

        resetAllXmlPackageId(mAssetDir, pp, idMaps)
        // TODO: slice AndroidManifest.xml, if you finish `AXmlEditor.slice', uncomment following.
//        // Filter AndroidManifest.xml
//        File manifest = new File(mAssetDir, 'AndroidManifest.xml')
//        AXmlEditor xmlEditor = new AXmlEditor(manifest)
//        xmlEditor.slice(pp, idMaps, retainedTypes)
//        // Reset xml package id
//        File resDir = new File(mAssetDir, 'res')
//        resetAllXmlPackageId(resDir, pp, idMaps)
    }

    /**
     * Reset resource package id for assets
     * @param pp new package id
     * @param ppStr the hex string of package id
     * @param idMaps
     */
    void resetPackage(int pp, String ppStr, Map idMaps) {
        File arscFile = new File(mAssetDir, 'resources.arsc')
        def arscEditor = new ArscEditor(arscFile, null)

        // Modify R.java
        resetRjava(mJavaFile, ppStr)
        // Modify resources.arsc
        arscEditor.reset(pp, idMaps)

        resetAllXmlPackageId(mAssetDir, pp, idMaps)
    }

    /**
     * Generate AndroidManifest.xml
     * @param options [package, versionName, versionCode, aaptExe, baseAsset]
     */
    void manifest(Map options) {
        // TODO: generate hex file without aapt
//        File file = new File(mAssetDir, 'AndroidManifest.xml')
//        AXmlEditor editor = new AXmlEditor(file)
//        editor.createAndroidManifest(options)
    }

    void manifest(Project project, Map options) {
        // Create source file
        File tempManifest = new File(mAssetDir, 'AndroidManifest.xml')
        tempManifest.write("""<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="${options.packageName}"
    android:versionName="${options.versionName}"
    android:versionCode="${options.versionCode}"/>""")
        // Compile to hex with aapt
        File tempZip = new File(mAssetDir, 'manifest.zip')
        project.exec {
            executable options.aaptExe
            args 'package', '-f', '-M', tempManifest.path,
                    '-I', options.baseAsset, '-F', tempZip.path
        }
        // Unzip the compiled apk
        tempManifest.delete()
        project.copy {
            from project.zipTree(tempZip)
            into mAssetDir
        }
        tempZip.delete()
    }

    /**
     * Filter resources with specific types
     * @param retainedTypes
     */
    void filterResources(List retainedTypes) {
        def resDir = new File(mAssetDir, 'res')
        resDir.listFiles().each { typeDir ->
            def type = retainedTypes.find { typeDir.name.startsWith(it.name) }
            if (type == null) {
                // Split whole type
                typeDir.deleteDir()
                return
            }
            def entryFiles = typeDir.listFiles()
            def retainedEntryCount = entryFiles.size()
            entryFiles.each { entryFile ->
                def entry = type.entries.find { entryFile.name.startsWith("${it.name}.") }
                if (entry == null) {
                    // Split specify entry
                    entryFile.delete()
                    retainedEntryCount--
                }
            }
            if (retainedEntryCount == 0) {
                // Delete empty dir
                typeDir.deleteDir()
            }
        }
    }

    /** Reset package id for *.xml */
    private void resetAllXmlPackageId(File dir, int pp, Map idMaps) {
        dir.eachFileRecurse(FileType.FILES) { file ->
            if (file.name.endsWith('.xml')) {
                def editor = new AXmlEditor(file)
                editor.setPackageId(pp, idMaps)
            }
        }
    }

    /**
     * Filter specify types for R.java
     * @param rJavaFile
     * @param retainedTypes types that to keep
     * @param retainedStyleables styleables that to keep
     * @param dynamicIds ids that needs to map at runtime
     * @return
     */
    private static def filterRjava(File rJavaFile, List retainedTypes, List retainedStyleables,
                                   List dynamicIds) {
        def final clazzStart = '    public static final class '
        def final clazzEnd = '    }'
        def final varStart = '        public static final '
        def final varEnd = ';'
        def pkgRPath = rJavaFile.parentFile
        def tempRFile = new File(pkgRPath, "${rJavaFile.name}~")
        def pw = tempRFile.newPrintWriter()
        def types = []
        def currType
        def currValue
        def currIdMap
        def skip = false
        def entriesMaps = [:]
        retainedTypes.each {
            entriesMaps.put(it.name, it.entries)
        }
        rJavaFile.eachLine { str, no ->
            if (skip) {
                // ignored
                if (str == clazzEnd) skip = false
            } else if (str.startsWith(clazzStart)) {
                def name = str.substring(clazzStart.length())
                def idx = name.indexOf(' ')
                name = name.substring(0, idx)
                def entries = entriesMaps.get(name)
                if (entries == null) {
                    skip = true
                    return
                }
                currType = [declare:str, name:name, values:[]]
                currIdMap = [:]
                entries.each {
                    currIdMap.put(it.name, it._vs)
                }
                types.add(currType)
            } else if (str.startsWith(varStart)) {
                str = str.substring(varStart.length())
                def vtIdx = str.indexOf(' ')
                def vtype = str.substring(0, vtIdx)
                str = str.substring(vtIdx + 1)
                def eqIdx = str.indexOf('=')
                String var = str.substring(0, eqIdx)
                def id = currIdMap.get(var)
                if (id == null) return
                str = "$varStart$vtype $var=$id${str.substring(eqIdx + 11)}" // 0x$packageId}
                if (!str.endsWith(varEnd)) {
                    currValue = str
                    return
                }
                currType.values.add(str)
            } else if (currValue != null) {
                if (!str.endsWith(varEnd)) {
                    currValue += '\n' + str
                    return
                }
                currType.values.add(str)
            } else if (currType == null) {
                // Copy text before any types
                pw.println(str)
            }
        }
        def addedDynamicIds = (dynamicIds == null || dynamicIds.size() == 0)
        def dynamicIdsStr = addedDynamicIds ? null :
                "        public static final int[] SmallDynamicIds = {${dynamicIds.join(',')}};"
        types.each {
            if (it.values.size() == 0) return
            pw.println(it.declare)
            it.values.each {
                pw.println(it)
            }
            if (!addedDynamicIds && it.name == 'styleable') {
                pw.println dynamicIdsStr
                addedDynamicIds = true
            }
            pw.println(clazzEnd)
        }
        if (!addedDynamicIds) {
            pw.println '    public static final class styleable {'
            pw.println dynamicIdsStr
            pw.println clazzEnd
        }

        if (retainedStyleables.size() > 0) {
            pw.println '    public static final class styleable {'
            retainedStyleables.each {
                pw.println "        public static final ${it.vtype} ${it.key} = ${it.idStr};"
            }
            pw.println clazzEnd
        }

        pw.println('}')

        pw.flush()
        pw.close()

        rJavaFile.delete()
        tempRFile.renameTo(rJavaFile)
    }

    /**
     * Filter specify types for R.txt
     * @param rText
     * @param retainedTypes
     */
    private static def filterRtext(File rtext, List retainedTypes, List retainedStyleables) {
        rtext.write('')
        def pw = rtext.newPrintWriter()
        retainedTypes.each { t ->
            t.entries.each { e ->
                pw.write("${t.type} ${t.name} ${e.name} ${e._vs}\n")
            }
        }
        retainedStyleables.each {
            pw.write("${it.vtype} ${it.type} ${it.key} ${it.idStr}\n")
        }
        pw.flush()
        pw.close()
    }

    /**
     * Reset package id for R.java
     * @param rJavaFile
     * @param packageId
     * @return
     */
    private static def resetRjava(File rJavaFile, String packageId) {
        def pkgRPath = rJavaFile.parentFile
        def tempRFile = new File(pkgRPath, 'tempR.java')
        def tempRWriter = tempRFile.newPrintWriter()
        rJavaFile.eachLine { str, no ->
            def str2 = str.replaceAll('0x7f([0-9a-f]{6})', '0x' + packageId + '$1');
            tempRWriter.write(str2 + '\n')
        }
        tempRWriter.flush()
        tempRWriter.close()

        rJavaFile.delete()
        tempRFile.renameTo(rJavaFile)
    }
}
