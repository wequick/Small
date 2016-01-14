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
 * enum from include/androidfw/ResourceTypes.h
 */
public enum ResType {
    public static int RES_NULL_TYPE = 0x0000;
    public static int RES_STRING_POOL_TYPE = 0x0001;
    public static int RES_TABLE_TYPE = 0x0002;
    public static int RES_XML_TYPE = 0x0003;

    // Chunk types in RES_XML_TYPE
    public static int RES_XML_FIRST_CHUNK_TYPE = 0x0100;
    public static int RES_XML_START_NAMESPACE_TYPE = 0x0100;
    public static int RES_XML_END_NAMESPACE_TYPE = 0x0101;
    public static int RES_XML_START_ELEMENT_TYPE = 0x0102;
    public static int RES_XML_END_ELEMENT_TYPE = 0x0103;
    public static int RES_XML_CDATA_TYPE = 0x0104;
    public static int RES_XML_LAST_CHUNK_TYPE = 0x017;
    // This contains a uint32_t array mapping strings in the string
    // pool back to resource identifiers.  It is optional.
    public static int RES_XML_RESOURCE_MAP_TYPE = 0x0180;

    // Chunk types in RES_TABLE_TYPE
    public static int RES_TABLE_PACKAGE_TYPE = 0x0200;
    public static int RES_TABLE_TYPE_TYPE = 0x0201;
    public static int RES_TABLE_TYPE_SPEC_TYPE = 0x0202;
    public static int RES_TABLE_LIBRARY_TYPE = 0x0203;
}
