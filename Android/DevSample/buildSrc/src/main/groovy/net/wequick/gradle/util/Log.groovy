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

public final class Log {

    private static final boolean isWindows = System.properties['os.name'].toLowerCase().contains('windows')
    private static final String IC_ACTION = isWindows ? '>' : '▸'
    private static final String IC_FAILED = isWindows ? 'x' : '✗'
    private static final String IC_PASSED = isWindows ? 'o' : '✓'
    private static final String IC_ARROW = '->'

    public static enum LogState {
        None,
        Lint
    }

    private static LogState state

    public static void setState(LogState state) {
        this.state = state
    }

    public static void header(String text) {
        def logo = (state == LogState.None) ? "[Small] " : "  Small "
        print AnsiUtils.yellow(logo)
        println AnsiUtils.white(text)
    }

    public static void success(String text) {
        print String.format('        %-64s', text)
        println AnsiUtils.green('[  OK  ]')
    }

    public static void warn(String text) {
        println AnsiUtils.red(String.format('        %s', text))
    }

    public static void footer(String text) {
        println AnsiUtils.white(String.format('        %s', text))
    }

    public static void result(String text) {
        println "        ${AnsiUtils.yellow(IC_ARROW)} $text"
    }

    public static void action(String action, String text) {
        println "${AnsiUtils.yellow(IC_ACTION)} ${AnsiUtils.bold(action)} $text"
    }

    public static void failed(String text) {
        println "    ${AnsiUtils.red(IC_FAILED)} $text"
    }

    public static void passed(String text) {
        println "    ${AnsiUtils.green(IC_PASSED)} $text"
    }
}