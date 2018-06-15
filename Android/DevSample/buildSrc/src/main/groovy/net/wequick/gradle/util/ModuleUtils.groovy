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
package net.wequick.gradle.util

final class ModuleUtils {

    static String buildTaskName(String prefix, String name) {
        return buildTaskName(prefix, name, null)
    }

    static String buildTaskName(String prefix, String name, String suffix) {
        def chars = name.toCharArray()
        def sb = new StringBuilder()
        int len = chars.size()

        if (prefix != null) {
            sb.append(prefix)
            sb.append(chars[0].toUpperCase())
        } else {
            sb.append(chars[0])
        }

        for (int i = 1; i < len; i++) {
            def c = chars[i]
            if (c == '.' || c == ':' || c == '_' || c == '-') {
                if (i < len - 1) {
                    sb.append(chars[++i].toUpperCase())
                    continue
                }
            }

            sb.append(c)
        }

        if (suffix != null) {
            sb.append(suffix)
        }
        return sb.toString()
    }
}
