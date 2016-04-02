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

import java.nio.ByteBuffer

/**
 * Class to edit aapt-generated asset file
 */
public class AssetEditor extends CppHexEditor {

    public static final CHUNK_HEADER_SIZE = 8

    protected def version // com.android.sdklib.repository.FullRevision "major.minor.micro preview"

    public AssetEditor(File file) {
        this(file, null)
    }

    public AssetEditor(File file, def v) {
        super(file)
        this.version = v
    }

    /** Read struct ResChunk_header */
    protected def readChunkHeader() {
        def header = [:]
        header.type = readShort()
        header.headerSize = readShort()
        header.size = readInt()
        return header
    }

    /** Write struct ResChunk_header */
    protected def writeChunkHeader(h) {
        writeShort(h.type)
        writeShort(h.headerSize)
        writeInt(h.size)
    }

    /** Read struct Res_value */
    protected def readResValue() {
        def v = [:]
        v.size = readShort()
        v.res0 = readByte()
        v.dataType = readByte()
        v.data = readInt()
        return v
    }
    /** Write struct Res_value */
    protected def writeResValue(v) {
        writeShort(v.size)
        writeByte(v.res0)
        writeByte(v.dataType)
        writeInt(v.data)
    }

    /**
     * Rewrite package id on incoming uint32_t
     * @param pp high bits of resource id
     */
    protected def checkToRewritePackageId(int pp, Map idMaps) {
        def pos = tellp()
        int id = readInt()
        if (id >> 24 != 0x7f) return
        if (idMaps != null && idMaps.containsKey(id)) {
            id = idMaps.get(id) // use library resource id
        } else {
            id = ((pp << 24) | (id & 0x00ffffff)) // replace pp
        }
        seek(pos)
        writeInt(id)
    }

    /**
     * Rewrite package id on typed value (Res_value: 8 bytes)
     * @param pp
     */
    protected def checkToRewriteTypedValueId(int pp, Map idMaps) {
        skip(4)
        checkToRewritePackageId(pp, idMaps)
    }

    /** Read struct ResStringPool_header and following string data */
    protected def readStringPool() {
        def pos = tellp()
        def s = [:]
        s.header = readChunkHeader() // string pool
        assert (s.header.type == ResType.RES_STRING_POOL_TYPE)

        s.stringCount = readInt()
        s.styleCount = readInt()
        s.flags = readInt()
        s.stringsStart = readInt()
        s.stylesStart = readInt()
        s.stringOffsets = []
        s.styleOffsets = []
        s.strings = [] // byte[][]
        s.styles = [] // byte[][]
        s.stringsSize = 0
        s.stringLens = []
        s.styleLens = []
        s.isUtf8 = (s.flags & ResStringFlag.UTF8_FLAG) != 0
        for (int i = 0; i < s.stringCount; i++) {
            s.stringOffsets.add(readInt())
        }
        for (int i = 0; i < s.styleCount; i++) {
            s.styleOffsets.add(readInt())
        }
        def start = s.stringsStart + pos
        for (int i = 0; i < s.stringCount; i++) {
            seek(start + s.stringOffsets[i])
            def len = decodeLength(s.isUtf8)
            s.stringLens[i] = len.data
            s.strings[i] = readBytes(len.value)
            s.stringsSize += len.value + len.data.length + 1 // 1 for 0x0
            skip(1) // 0x0
        }
        start = s.stylesStart + pos
        for (int i = 0; i < s.styleCount; i++) {
            seek(start + s.styleOffsets[i])
            def len = decodeLength(s.isUtf8)
            s.styleLens[i] = len.data
            s.styles[i] = readBytes(len.value)
            skip(1) // 0x0
        }
        def endPos = pos + s.header.size
        s.paddingSize = endPos - tellp()
        if (s.paddingSize != 0) seek(endPos)

        return s
    }
    /** Write struct ResStringPool_header and following string data */
    protected def writeStringPool(s) {
        writeChunkHeader(s.header)
        writeInt(s.stringCount)
        writeInt(s.styleCount)
        writeInt(s.flags)
        writeInt(s.stringsStart)
        writeInt(s.stylesStart)
        for (int i = 0; i < s.stringCount; i++) {
            writeInt(s.stringOffsets[i])
        }
        for (int i = 0; i < s.styleCount; i++) {
            writeInt(s.styleOffsets[i])
        }
        s.strings.eachWithIndex { it, i ->
            writeBytes(s.stringLens[i])
            writeBytes(it)
            writeByte(0x0)
        }
        s.styles.eachWithIndex { it, i ->
            writeBytes(s.styleLens[i])
            writeBytes(it)
            writeByte(0x0)
        }
        if (s.paddingSize > 0) writeBytes(new byte[s.paddingSize]) // padding
    }
//    /** Make ResStringPool */
//    protected static def makeStringPool(u8strs) {
//        def s = [:]
//
//        def size = 0
//        def strings = []
//        u8strs.each {
//            def str = [:]
//            str
//        }
//        s.header = [type: ResType.RES_STRING_POOL_TYPE, headerSize: 0x1C, size: size]
//
//    }
    /**
     * see https://github.com/android/platform_frameworks_base/blob/d59921149bb5948ffbcb9a9e832e9ac1538e05a0/libs/androidfw/ResourceTypes.cpp
     * @param isUtf8
     * @return
     */
    private Map decodeLength(isUtf8) {
        if (!isUtf8) {
            throw new UnsupportedEncodingException("UTF-16 is unsupported now.")
        }
        // *u16len = decodeLength(&u8str); ResourceTypes.cpp#722, seems to unused here
        def bytes = []
        short hb = readByte()
        bytes.add(hb)
        if (hb & 0x80) {
            bytes.add(readByte())
        }

        // size_t u8len = decodeLength(&u8str); ResourceTypes.cpp#723, the exact length
        hb = readByte()
        bytes.add(hb)
        if (hb & 0x80) {
            short lb = readByte()
            bytes.add(lb)
            hb = ((hb & 0x7F) << 8) | (lb & 0xff)
        }

        def N = bytes.size()
        def data = new byte[N]
        for (int i = 0; i < N; i++) {
            data[i] = (byte)bytes[i]
        }
        return [data: data, value: hb]
    }
    /** Filter ResStringPool with specific string indexes */
    protected static def filterStringPool(sp, ids) {
        if (sp.stringsStart == 0) return sp
        def strings = []
        def offsets = []
        def lens = []
        def offset = 0
        ids.each {
            def s = sp.strings[it]
            strings.add(s)
            offsets.add(offset)
            def lenData = sp.stringLens[it]
            lens.add(lenData)
            def l = s.length
            offset += l + lenData.length + 1 // 1 for 0x0
        }
        def newStringCount = strings.size()
        def d = (sp.stringCount - newStringCount) * 4
        sp.strings = strings
        sp.stringOffsets = offsets
        sp.stringLens = lens
        sp.stringCount = strings.size()
        sp.stringsStart -= d
        if (sp.stylesStart > 0) sp.stylesStart -= d
        def newSize = sp.header.size + offset - sp.stringsSize - d - sp.paddingSize
        // Padding chunk size, !!important
        def flag = newSize & 3
        if (flag == 0) {
            sp.paddingSize = 0
        } else {
            sp.paddingSize = 4 - flag
            newSize += sp.paddingSize
        }
        sp.header.size = newSize
    }
    /** Dump ResStringPool, as `aapt d xmlstrings' command */
    protected static def dumpStringPool(pool) {
        def type = pool.flags == 0 ? 'UTF-16' : 'UTF-8'
        println "String pool of ${pool.stringCount} unique $type non-sorted strings, " +
                "${pool.stringCount} entries and ${pool.styleCount} styles " +
                "using ${pool.header.size} bytes:"
        pool.strings.eachWithIndex { v, i ->
            println "String #$i: ${new String(v)}"
        }
        pool.styles.eachWithIndex { v, i ->
            println "Style #$i: ${new String(v)}"
        }
    }
}
