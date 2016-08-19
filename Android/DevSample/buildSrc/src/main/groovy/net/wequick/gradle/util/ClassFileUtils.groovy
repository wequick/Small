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
package net.wequick.gradle.util;

/**
 * The class to parse .class file
 */
public class ClassFileUtils {

    /**
     * Parse string pools to collect R$ filed
     * @see @{link com.sun.tools.classfile.ConstantPool}
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html">Class File Format</a>
     */
    public static void collectResourceReferences(File cls, Set<Map> outRefs) {
        RandomAccessFile raf = new RandomAccessFile(cls, 'r')
        raf.skipBytes(8) // skip magic and version

        int stringCount = raf.readShort()
        if (stringCount == 1) return

        Set<RefInfo> fieldRefs = []
        Set<ClassInfo> classRefs = []
        Set<NameTypeInfo> nameRefs = []
        Object[] pools = new Object[stringCount]

        ClassInfo ci
        NameTypeInfo ni
        for (int i = 1; i < stringCount; i++) {
            int tag = raf.readByte()
            switch(tag) {
                case 1: // CONSTANT_Utf8_info
                    pools[i] = raf.readUTF()
                    break;
                case 2:
                case 13:
                case 14:
                case 17:
                default:
                    break;
                case 3: // CONSTANT_Integer_info
                    raf.skipBytes(4)
                    break;
                case 4: // CONSTANT_Float_info
                    raf.skipBytes(4)
                    break;
                case 5: // CONSTANT_Long_info
                    raf.skipBytes(8)
                    ++i;
                    break;
                case 6: // CONSTANT_Double_info
                    raf.skipBytes(8)
                    ++i;
                    break;
                case 7: // CONSTANT_Class_info
                    pools[i] = ci = new ClassInfo(raf, i)
                    classRefs.add(ci)
                    break;
                case 8: // CONSTANT_String_info
                    raf.skipBytes(2)
                    break;
                case 9: // CONSTANT_Fieldref_info
                    fieldRefs.add(new RefInfo(raf, i))
                    break;
                case 10: // CONSTANT_Methodref_info
                    raf.skipBytes(4)
                    break;
                case 11: // CONSTANT_InterfaceMethodref_info
                    raf.skipBytes(4)
                    break;
                case 12: // CONSTANT_NameAndType_info
                    pools[i] = ni = new NameTypeInfo(raf, i)
                    nameRefs.add(ni)
                    break;
                case 15: // CONSTANT_MethodHandle_info
                    raf.skipBytes(3)
                    break;
                case 16: // CONSTANT_MethodType_info
                    raf.skipBytes(2)
                    break;
                case 18: // CONSTANT_InvokeDynamic_info
                    raf.skipBytes(4)
            }
        }

        fieldRefs.each {
            ClassInfo classInfo = pools[it.class_index]
            String className = pools[classInfo.name_index]
            def pkg, type, name
            int pos = className.indexOf('/R$')
            if (pos < 0) {
                return
            }

            pkg = className.substring(0, pos)
            type = className.substring(pos + 3)

            NameTypeInfo nameTypeInfo = pools[it.name_and_type_index]
            name = pools[nameTypeInfo.name_index]

            outRefs.add(pkg: pkg, type: type, name: name)
        }

        raf.close()
    }

    private static class CpInfo {
        final int tag
        CpInfo(RandomAccessFile raf, int tag) {
            this.tag = tag
        }
    }

    private static final class RefInfo extends CpInfo {
        final int class_index
        final int name_and_type_index
        RefInfo(RandomAccessFile raf, int tag) {
            super(raf, tag)
            class_index = raf.readShort()
            name_and_type_index = raf.readShort()
        }
    }

    private static final class ClassInfo extends CpInfo {
        final int name_index
        ClassInfo(RandomAccessFile raf, int tag) {
            super(raf, tag)
            name_index = raf.readShort()
        }
    }

    private static final class NameTypeInfo extends CpInfo {
        final int name_index
        NameTypeInfo(RandomAccessFile raf, int tag) {
            super(raf, tag)
            name_index = raf.readShort()
            raf.skipBytes(2) // type_index
        }
    }
}
