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

/**
 * Class to resolve android resource symbol
 *  - http://developer.android.com/intl/zh-cn/guide/topics/resources/available-resources.html
 *  - http://developer.android.com/intl/zh-cn/guide/topics/resources/more-resources.html
 *  Use developer.android.google.cn if the sites cannot visit.
 */
class SymbolTable {

    private static final String UNKNOWN_STYLE_REF = '0'

    static final class Type implements Cloneable {
        String name
        String value
        List<String> values // styleable
        List<Entry> entries

        Type(String name) {
            this.name = name
        }

        void addEntry(String key) {
            addEntry(key, null)
        }

        void addEntry(String key, String id) {
            if (entries == null) {
                entries = []
            } else if (entries.find { it.key == key } != null) {
                return
            }

            entries.add(new Entry(key, id))
        }

        boolean hasEntry(String key) {
            if (entries == null) return false

            return entries.find { it.key == key } != null
        }

        Type merge(Type t) {
            if (t == null) return this

            if (entries == null) {
                entries = t.entries
                return this
            }

            if (t.entries == null) {
                return this
            }

            entries.addAll(t.entries)
            entries = entries.unique()
            return this
        }

        def getValues() {
            if (values != null) return values
            if (value == null) return null

            def idStr = value
            def len = idStr.length()
            if (len < 4) {
                values = []
            } else {
                idStr = idStr.substring(2, len - 2) // bypass '{ ' and ' }'
                values = idStr.split(', ')
            }
            return values
        }

        Type copy() {
            Type copy = new Type()
            copy.name = name
            copy.value = value
            if (entries != null) {
                copy.entries = entries.collect { it.copy() }
            }
            return copy
        }
    }

    static final class Entry {
        String key
        String valueType
        String value
        String tempValue
        int id

        Entry(String key) {
            this(key, null)
        }

        Entry(String key, String value) {
            this('int', key, value)
        }

        Entry(String type, String key, String value) {
            this.valueType = type
            this.key = key
            this.value = value
        }

        @Override
        int hashCode() {
            return key.hashCode()
        }

        @Override
        boolean equals(Object o) {
            if (!o instanceof Entry) return false
            return key == ((Entry) o).key
        }

        int getId() {
            if (id != 0) return id
            if (valueType != 'int') return 0
            if (value == null) return 0
            if (value.length() != 10/*0xPPTTNNNN*/) return 0

            def idStr = value.substring(8)
            id = Integer.parseInt(idStr, 16)
            return id
        }

        void setValue(String aValue) {
            value = aValue
            if (id != 0) id = 0 // Reset the lazy getter
        }

        Entry copy() {
            return new Entry(valueType, key, value)
        }
    }

    List<Type> types
    List<Type> styleables
    List<Type> refStyleables
    Type attrType
    String packageId
    String packageName
    File publicSymbolFile

    SymbolTable() {
        packageId = '7f'
    }

    static SymbolTable fromResDir(File resDir) {
        return fromResDir(resDir, null)
    }

    static SymbolTable fromResDir(File resDir, List<String> filters) {
        if (!resDir.exists()) return null

        SymbolTable table = new SymbolTable()
        List refStyleNames = []
        SymbolTable filterTable = null
        if (filters != null) {
            filterTable = new SymbolTable()
            filters.each {
                def arr = it.split('/')
                if (arr.size() != 2) return

                filterTable.addEntry(arr[0], [arr[1]])
            }
        }

        resDir.listFiles().each { typeDir ->
            if (!typeDir.isDirectory()) return

            String typeName = typeDir.name.split('-')[0]
            if (typeName != 'values') {
                // Collect file entries
                def filterType = filterTable ? filterTable.findType(typeName) : null
                def es = []
                typeDir.listFiles().each { entryFile ->
                    if (!entryFile.isFile()) return

                    def name = entryFile.name
                    if (filterType != null && filterType.hasEntry(name)) {
                        // User filtered
                        return
                    }

                    es.add name.take(name.lastIndexOf('.'))
                }
                if (es.size() > 0) {
                    table.addEntry(typeName, es)
                }
                return
            }

            // Collect value entries
            typeDir.listFiles().each { entryFile ->
                if (!entryFile.isFile()) return
                if (!entryFile.name.endsWith('.xml')) return

                def res = new XmlParser().parse(entryFile)
                if (res.name() != 'resources') return

                res.each {
                    typeName = it.name()
                    String entryName = it.@name
                    if (typeName == 'declare-styleable') {
                        // <declare-styleable name="MyTextView">
                        def attrs = new LinkedList<String>()
                        it.children().each { // <attr format="string" name="label"/>
                            attrs.add(it.@name)
                        }
                        table.addStyle(entryName, attrs)
                        return
                    }

                    if (typeName == 'item') {
                        typeName = 'id'
                    } else if (typeName.endsWith('-array')) {
                        typeName = 'array'
                    } else if (typeName == 'style') {
                        entryName = entryName.replace('.', '_')
                    }

                    table.addEntry(typeName, [entryName])
                }
            }
        }

        refStyleNames.each { name ->
            if (table.styleables.find { it.name == name } == null) {
                table.addRefStyle(name)
            }
        }

        return table
    }

    static SymbolTable fromFile(File file) {
        return fromFile(file, null)
    }

    static SymbolTable fromFile(File file, List<String> retainTypes) {
        if (!file.exists()) return null

        def lastStyleName = ''
        def lastStyleNameLen = 0

        SymbolTable table = new SymbolTable()
        file.eachLine { str ->
            def i = str.indexOf(' ')
            if (i < 0) return

            def vtype = str.substring(0, i) // value type (int or int[])
            str = str.substring(i + 1)
            i = str.indexOf(' ')
            def type = str.substring(0, i) // resource type (attr/string/color etc.)
            if (retainTypes != null && !retainTypes.contains(type)) return

            str = str.substring(i + 1)
            i = str.indexOf(' ')
            String key = str.substring(0, i)
            String idStr = str.substring(i + 1)

            if (type == 'styleable') {
                if (vtype == 'int[]') {
                    def s = table.addStyle(key)
                    s.value = idStr

                    lastStyleName = key
                    lastStyleNameLen = key.length() + 1
                } else {
                    key = key.substring(lastStyleNameLen)
                    def s = table.addStyle(lastStyleName)
                    s.addEntry(key, idStr)
                }
                return
            }

            if (type == 'style') {
                key = key.replace('.', '_')
            }

            table.addEntry(type, [key], [idStr])
        }
        return table
    }

    static SymbolTable fromEntries(List<String> entries) {
        if (entries == null || entries.size() == 0) return null

        SymbolTable table = new SymbolTable()
        entries.each { e ->
            def arr = e.split('/')
            if (arr.length != 2) return

            table.addEntry(arr[0], [arr[1]])
        }

        return table
    }

    Type findType(String name) {
        if (types == null) return null

        return types.find { it.name == name }
    }

    SymbolTable copy() {
        SymbolTable copy = new SymbolTable()
        copy.types = types.collect { it.copy() }
        copy.styleables = styleables.collect { it.copy() }
        return copy
    }

    SymbolTable retainTypes(List<String> typeNames) {
        def strippedTypes = []
        types.each {
            if (!typeNames.contains(it.name)) {
                strippedTypes.add(it)
            }
        }
        types.removeAll(strippedTypes)
        styleables = null
        return this
    }

    SymbolTable merge(SymbolTable st) {
        if (st == null) return this

        if (attrType == null) {
            attrType = st.attrType
        } else {
            attrType = attrType.merge(st.attrType)
        }

        if (types == null) {
            types = st.types
        } else if (st.types != null) {
            def mergedTypes = []
            types.each { t ->
                def mergedType = t.merge(st.types.find { it.name == t.name })
                mergedTypes.add mergedType
            }

            def typeNames = types.collect { it.name }
            def newTypes = st.types.findAll { !typeNames.contains( it.name )}
            mergedTypes.addAll newTypes

            types = mergedTypes
        }

        if (styleables == null) {
            styleables = st.styleables
        } else if (st.styleables != null) {
            def mergedTypes = []
            styleables.each { t ->
                def mergedType = t.merge(st.styleables.find { it.name == t.name })
                mergedTypes.add mergedType
            }

            def typeNames = styleables.collect { it.name }
            def newTypes = st.styleables.findAll { !typeNames.contains( it.name )}
            mergedTypes.addAll newTypes

            styleables = mergedTypes
        }

        if (refStyleables == null) {
            refStyleables = st.refStyleables
        } else if (st.refStyleables != null) {
            def mergedTypes = []
            refStyleables.each { t ->
                def mergedType = t.merge(st.refStyleables.find { it.name == t.name })
                mergedTypes.add mergedType
            }
            refStyleables = mergedTypes
        }
        return this
    }

    void strip(SymbolTable baseSymbols) {
        // Strip types
        def strippedTypes = []
        types.each { t ->
            def baseType = baseSymbols.types.find { it.name == t.name }
            if (baseType == null) {
                // No base type, retain
                return
            }

            def strippedEntries = []
            t.entries.each { e ->
                def baseEntry = baseType.entries.find { it.key == e.key }
                if (baseEntry == null) {
                    // No base entry, retain
                    return
                }

                strippedEntries.add(e)
            }

            if (t.entries.size() == strippedEntries.size()) {
                strippedTypes.add(t)
            } else {
                t.entries.removeAll(strippedEntries)
            }
        }
        types.removeAll(strippedTypes)

        // Strip styleables
        if (styleables == null) return

        def strippedStyleables = []
        styleables.each { s ->
            def relativeStyleable = baseSymbols.styleables.find { it.name == s.name }
            if (relativeStyleable == null) {
                // No base styleable, retain
                return
            }

            strippedStyleables.add(s)
        }
        styleables.removeAll(strippedStyleables)
    }

    void sortEntries() {
        if (types == null) return

        // Sort types
        types.removeAll { it.entries == null }
        types.sort { it.name }
        if (attrType != null) {
            types.remove(attrType)
            types.add(0, attrType)
        }

        types.each { t ->
            t.entries.sort { it.key }
        }

        // Sort styleables
        if (styleables != null) {
            styleables.sort { it.name }
            styleables.each { t ->
                t.entries.sort { it.key }
            }
        }
    }

    void assignEntryIds(String packageId) {
        sortEntries()

        if (types == null) return

        attrType = types.find { it.name == 'attr' }

        def typeId = 1
//        if (attrType == null) {
//            typeId = 2 // skip 'attr' type
//        }
        types.each { t ->
            def entryId = 0
            t.entries.each { e ->
                String id = "0x$packageId${String.format('%02x', typeId)}${String.format('%04x', entryId)}"
                e.value = id
                entryId = entryId + 1
            }
            typeId = typeId + 1
        }

        if (styleables == null || attrType == null) return

        styleables.each { t ->
            def ids = t.entries.collect { e ->
                def entry = attrType.entries.find { it.key == e.key }
                entry != null ? entry.value : UNKNOWN_STYLE_REF
            }
            t.value = "{ ${ids.join(', ')} }"

            def entryId = 0
            t.entries.each { e ->
                e.value = "$entryId"
                entryId = entryId + 1
            }
        }
    }

    void dumpTable() {
        printTextSymbols(null)
    }

    void generateTextSymbolsToFile(File file) {
        if (!file.parentFile.exists()) {
            if (!file.parentFile.mkdirs()) {
                throw new RuntimeException("Failed to create directory for symbol file: '$file'.")
            }
        }

        def pw = new PrintWriter(file)

        printTextSymbols(pw)

        pw.flush()
        pw.close()
    }

    void generateRJavaToFile(File file, String packageName) {
        generateRJavaToFile(file, packageName, null)
    }

    void replaceIds(SymbolTable target) {
        replaceIds(target, false)
    }

    List<Entry> replaceIds(SymbolTable target, boolean temporary) {
        if (target == null) return null

        List<Entry> mixedEntries = []
        types.each { t ->
            def relativeType = target.types.find { it.name == t.name }
            if (relativeType == null) return

            t.entries.each { e ->
                def relativeEntry = relativeType.entries.find { it.key == e.key }
                if (relativeEntry == null) return

                if (temporary) {
                    e.tempValue = e.value
                    e.value = relativeEntry.value
                    mixedEntries.add(e)
                } else {
                    e.value = relativeEntry.value
                }
            }
        }
        return mixedEntries
    }

    void generateRJavaToFile(File file, String packageName, SymbolTable mixedSymbols) {
        if (!file.parentFile.exists()) {
            if (!file.parentFile.mkdirs()) {
                throw new RuntimeException("Failed to create directory for symbol file: '$file'.")
            }
        }

        // Replace the entry ids from `mixedSymbols`
        List<Entry> mixedEntries = replaceIds(mixedSymbols, true)

        // Print
        def pw = new PrintWriter(file)
        printRJava(pw, packageName)
        pw.flush()
        pw.close()

        // Restore entry ids
        if (mixedEntries != null) {
            mixedEntries.each { e ->
                e.value = e.tempValue
                e.tempValue = null
            }
        }
    }

    void mapIds(SymbolTable fullSymbols,
                HashMap<Integer, Integer> outIdMaps,
                List outTypes) {
        def needsCollectTypes = outTypes != null
        types.each { t ->
            def relativeType = fullSymbols.types.find { it.name == t.name }
            if (relativeType == null) return

            Map type
            if (needsCollectTypes) {
                type = [:]
                type.id = fullSymbols.types.indexOf(relativeType) + 1
                type.name = relativeType.name
                type.entries = []
            }

            t.entries.each { e ->
                def relativeEntry = relativeType.entries.find { it.key == e.key }
                if (relativeEntry == null) return

                outIdMaps.put(Integer.decode(relativeEntry.value),
                        Integer.decode(e.value))

                if (needsCollectTypes) {
                    def entry = [:]
                    entry.id = relativeEntry.id
                    entry.name = relativeEntry.key
                    type.entries.add(entry)
                }
            }

            if (needsCollectTypes) outTypes.add(type)
        }

        if (!needsCollectTypes) return

        mapStyleableReferences(fullSymbols)
    }

    void mapStyleableReferences(SymbolTable fullSymbols) {
        // Correct the style references (as `android:xx` so on)
        //
        // e.g.
        //  source:
        //      <declare-styleable name="MyTextView">
        //          <attr name="label" format="string"/>
        //          <attr name="android:cacheColorHint"/>
        //      </declare-styleable>
        //  original symbols:
        //      int[] styleable MyTextView { 0x01010101, 0x7f0400bc }
        //  stripped symbols:
        //      int[] styleable MyTextView { 0,          0x79010000 }
        //  corrected symbols:
        //      int[] styleable MyTextView { 0x01010101, 0x79010000 }
        styleables.each { t ->
            def relativeStyle = fullSymbols.styleables.find { it.name == t.name }
            if (relativeStyle == null) return

            def corrected = false
            t.values.eachWithIndex { String id, int i ->
                if (id == UNKNOWN_STYLE_REF) {
                    t.values[i] = relativeStyle.values[i]
                    corrected = true
                }
            }
            if (corrected) {
                t.value = "{ ${t.values.join(', ')} }"
            }
        }
    }

    private void addEntry(String type, List<String> keys) {
        addEntry(type, keys, null)
    }

    private void addEntry(String type, List<String> keys, List<String> ids) {
        if (types == null) {
            types = new LinkedList<Type>()
        }

        def t = types.find { it.name == type }
        if (t == null) {
            t = new Type(type)
            types.add t
        }

        for (int i = 0; i < keys.size(); i++) {
            String key = keys[i]
            String id = null
            if (ids != null) {
                id = ids[i]
            }
            t.addEntry(key, id)
        }
    }

    private def addStyle(String name) {
        return addStyle(name, null)
    }

    private def addStyle(String name, List<String> attrs) {
        if (styleables == null) {
            styleables = new LinkedList<Type>()
        }
        return addStyleTo(styleables, name, attrs)
    }

    private def addRefStyle(String name) {
        if (refStyleables == null) {
            refStyleables = new LinkedList<Type>()
        }
        return addStyleTo(refStyleables, name, null)
    }

    private Type addStyleTo(List<Type> styleables, String name, List<String> attrs) {
        def t = styleables.find { it.name == name }
        if (t == null) {
            t = new Type(name)
            styleables.add t
        }

        if (attrs != null) {
            attrs.each { key ->
                if (key.startsWith('android:')) {
                    t.addEntry(key.replaceAll(':', '_'))
                } else {
                    t.addEntry(key)
                    addAttrType(key)
                }
            }
        }

        return t
    }

    private void addAttrType(String key) {
        if (attrType == null) {
            attrType = new Type('attr')
        }
        attrType.addEntry(key)
    }

    private void printTextSymbols(PrintWriter pw) {
        if (types == null) return
        types.each { t ->
            t.entries.each { e ->
                printLine("$e.valueType $t.name $e.key $e.value", pw)
            }
        }

        if (styleables == null) return
        styleables.each { t ->
            printLine("int[] styleable $t.name $t.value", pw)
            t.entries.each { e ->
                printLine("$e.valueType styleable ${t.name}_${e.key} $e.value", pw)
            }
        }
    }

    private void printRJava(PrintWriter pw, String packageName) {
        if (types == null) return

        pw.println """/* AUTO-GENERATED FILE.  DO NOT MODIFY.
 *
 * This class was automatically generated by gradle-small.
 * It should not be modified by hand.
 */"""
        pw.println "package ${packageName};"
        pw.println 'public final class R {'

        types.each { t ->
            if (t.entries.size() == 0) return
            pw.println "  public static final class ${t.name} {"
            t.entries.each { e ->
                pw.println "    public static final ${e.valueType} ${e.key}=${e.value};"
            }
            pw.println "  }"
        }
        if (styleables != null && styleables.size() > 0) {
            pw.println "  public static final class styleable {"
            styleables.each { t ->
                pw.println "    public static final int[] $t.name=$t.value;"
                if (t.entries != null && t.entries.size() > 0) {
                    t.entries.each { e ->
                        pw.println "    public static final $e.valueType ${t.name}_${e.key}=$e.value;"
                    }
                }
            }
            pw.println "  }"
        }

        pw.println '}'
    }

    private static void printLine(String s, PrintWriter pw) {
        if (pw != null) {
            pw.println s
        } else {
            println s
        }
    }
}
