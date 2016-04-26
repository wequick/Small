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
 * Class to edit aapt-generated hex xml file
 */
public class AXmlEditor extends AssetEditor {

    private static final ATTR_BEFORE_ID_LENGTH = 16

    AXmlEditor(File file) {
        super(file)
    }

    def setPackageId(int pp, Map idMaps) {
        def xml = readChunkHeader()
        if (xml.type != ResType.RES_XML_TYPE) return
        setPackageIdRecursive(pp, idMaps, xml.size)
        close()
        return isEdited()
    }

    private def setPackageIdRecursive(int pp, Map idMaps, long size) {
        if (tellp() >= size) return
        def chunk = readChunkHeader()
        if (chunk.type == ResType.RES_XML_RESOURCE_MAP_TYPE) {
            def idCount = (chunk.size - chunk.headerSize) / 4
            for (int i = 0; i < idCount; i++) {
                checkToRewritePackageId(pp, idMaps)
            }
        } else if (chunk.type == ResType.RES_XML_START_ELEMENT_TYPE) {
            // Parse element, reset package id
            def node = readNode()
            for (int i = 0; i < node.attributeCount; i++) {
                skip(ATTR_BEFORE_ID_LENGTH)
                checkToRewritePackageId(pp, idMaps)
            }
        } else {
            skip(chunk.size - CHUNK_HEADER_SIZE)
        }
        setPackageIdRecursive(pp, idMaps, size)
    }

    /** Read struct ResXMLTree_node and ResXMLTree_attrExt */
    private def readNode() {
        def node = [:]
        // Skip struct ResXMLTree_node: lineNumber(4), comment(4) and part of struct
        // ResXMLTree_attrExt: ns(4), name(4), attributeStart(2), attributeSize(2)
        skip(20)
        node.attributeCount = readShort()
        // skip tail of struct ResXMLTree_attrExt: idIndex(2), classIndex(2), styleIndex(2)
        skip(6)
        return node
    }

    def createAndroidManefist(Map options) {
        def size = 0
        def xml = [
            header: [type: ResType.RES_XML_TYPE, headerSize: 8, size: size],
            stringPool: []
        ]
    }

    public void dumpXmlTree() {
        def xml = readXmlTree()

    }

    private def readXmlTree() {
        def xml = [:]
        xml.header = readChunkHeader()
        assert (xml.header.type == ResType.RES_XML_TYPE)

        def size = xml.header.size

        println "pos1: ${tellp()}"

        xml.stringPool = readStringPool()
        println xml.stringPool.flags

        dumpStringPool(xml.stringPool)

        readChunks(xml)
//        println xml
//        xml.namespaces.each {
//            println it
//        }
//        println xml.namespaces
    }

    private def readChunks(xml) {
        def size = length()
        def currNamespace = null
        def currNode = null
        def pos
        while ((pos = tellp()) < size) {
            def header = readChunkHeader()
            println header
            switch (header.type) {
                case ResType.RES_XML_RESOURCE_MAP_TYPE:
                    def mapCount = (header.size - header.headerSize) / 4
                    def maps = []
                    for (int i = 0; i < mapCount; i++) {
                        maps.add(readInt())
                    }
                    xml.attrMap = [header: header, ids: maps]
                    break
                case ResType.RES_XML_START_NAMESPACE_TYPE:
                    def ns = [header: header]
                    ns.startLine = readInt()
                    ns.headComment = readInt()
                    ns.prefix = readInt()
                    ns.uri = readInt()
                    ns.nodes = []
                    def nss = xml.namespaces
                    if (nss == null) {
                        xml.namespaces = nss = []
                    }
                    nss.add(ns)
                    ns.currNode = ns
                    currNamespace = ns
                    break
                case ResType.RES_XML_START_ELEMENT_TYPE:
                    def node = [:]
                    node.startLine = readInt()
                    node.headComment = readInt()
                    node.ns = readInt()
                    node.name = readInt()
                    node.attributeStart = readShort()
                    node.attributeSize = readShort()
                    node.attributeCount = readShort()
                    node.idIndex = readShort()
                    node.classIndex = readShort()
                    node.styleIndex = readShort()
                    node.attrs = []
                    for (int i = 0; i < node.attributeCount; i++) {
                        node.attrs.add(readAttribute())
                    }
                    node.nodes = []
                    def parent = currNamespace.currNode
                    println "- parent: ${parent}"
                    parent.nodes.add(node)
                    println "- parent: ${parent}"
                    node.parent = parent
                    println "- 333"
                    currNamespace.currNode = node
                    println "- 444"
                    break
                case ResType.RES_XML_END_NAMESPACE_TYPE:
                    currNamespace.endLine = readInt()
                    currNamespace.tailComment = readInt()
                    skip(8) // Bypass ns and name, ignore tag check
                    currNamespace = null
                    break
                case ResType.RES_XML_END_ELEMENT_TYPE:
                    println "- 555"
                    currNode.endLine = readInt()
                    currNode.tailComment = readInt()
                    skip(8) // Ignore tag check
                    println "- 666 $currNode"
                    currNode = currNode.parent
                    println "- 777 $currNode"
                    break
                default:
//                    pos += header.size - CHUNK_HEADER_SIZE
                    println "-- left: ${header.size + pos - tellp()}"
                    dumpBytes(header.size + pos - tellp())
//                    pos = tellp()
//                    seek(pos)
                    break
            }
        }
    }

    private def readAttribute() {
        def at = [:]
        at.ns = readInt()
        at.name = readInt()
        at.rawValue = readInt()
        at.typedValue = readResValue()
    }
}
