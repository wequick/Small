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

class Module {

    String group
    String name
    String version

    static Module fromFullName(String fullName) {
        if (fullName == null) return null

        def components = fullName.split(':')
        def len = components.size()
        if (len == 0) return null

        Module m = new Module()
        if (len > 0) m.group = components[0]
        if (len > 1)  m.name = components[1]
        if (len > 2) m.version = components[2]
        return m
    }

    String getFullName() {
        return "$group:$name:$version"
    }

    String getPath() {
        def ver = version == null ? 'unspecified' : version
        return "$group/$name/$ver"
    }

    @Override
    int hashCode() {
        int hash = 0
        if (group) hash ^= group.hashCode()
        if (name) hash ^= name.hashCode()
        if (version) hash ^= version.hashCode()
        return hash
    }

    @Override
    boolean equals(Object o) {
        if (!o instanceof Module) return false
        Module b = o
        if (group != b.group) return false
        if (name != b.name) return false
        if (version != b.version) return false
        return true
    }
}
