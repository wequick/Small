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
package net.wequick.gradle.internal

final class Version {
    public static String SMALL_GRADLE_PLUGIN_VERSION
    public static String SMALL_CORE_AAR_VERSION
    public static String SMALL_BINDING_AAR_VERSION

    private Version() {}

    static {
        Properties properties = new Properties()
        try {
            def stream = new BufferedInputStream(
                    Version.class.getClassLoader().getResourceAsStream("version.properties"))
            properties.load(stream)
            SMALL_GRADLE_PLUGIN_VERSION = properties.getProperty("pluginVersion")
            SMALL_CORE_AAR_VERSION = properties.getProperty("smallAarVersion")
            SMALL_BINDING_AAR_VERSION = properties.getProperty("bindingAarVersion")
        } catch (IOException e) {
            throw new UncheckedIOException(e)
        }
    }
}
