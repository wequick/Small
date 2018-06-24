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
package net.wequick.gradle.test

import com.android.build.gradle.tasks.ProcessAndroidResources
import com.android.sdklib.BuildToolInfo
import net.wequick.gradle.util.AnsiUtils
import net.wequick.gradle.util.Command
import net.wequick.gradle.util.Log
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.GradleBuild

class UnitTests {
    public static boolean passed
    public static String error
    public static String details
    protected Project project
    private Command command

    UnitTests() { }

    UnitTests(Project project) {
        this.project = project
        this.command = Command.with(project)
    }

    void setUp() {
        Log.action('Executing', this.class.simpleName)
    }

    void tearDown() {

    }

    def cmd(exe, theArgs) {
        return cmd(exe, theArgs, false)
    }

    def cmd(exe, theArgs, logs) {
        return command.execute(exe, theArgs, logs)
    }

    def gradlew(String taskName, boolean quiet, boolean parallel) {
        return command.gradlew(taskName, quiet, parallel)
    }

    def aapt(theArgs) {
        return command.aapt(theArgs)
    }

    static def tAssert(boolean condition, String message) {
        if (!condition) {
            passed = false
            error = message
        }
    }

    static def tAssert(boolean condition, String message, String details) {
        if (!condition) {
            passed = false
            error = message
            this.details = details
        }
    }

    public def runTest(name) {
        passed = true
        error = null
        details = null
        invokeMethod(name, null)
    }

    public static def runAllTests(Project project) {

        Log.setState(Log.LogState.Lint)

        // Collect all the tests
        def tests = []

        // Built-in tests
        tests.add(new BundleManifestTests(project))
        tests.add(new DuplicateClassesTests(project))

        // User tests
        def loader = new GroovyClassLoader(this.classLoader);
        project.projectDir.listFiles().each {
            if (!it.name.endsWith('Tests.gradle')) {
                return
            }

            try {
                def testClass = loader.parseClass(it);
                tests.add(testClass.newInstance(project))
            } catch (Exception e) {
                e.printStackTrace()
            }
        }

        def allTestCount = 0
        def failedTestCount = 0
        def allStartTime = System.nanoTime()

        tests.each { test ->
            test.setUp()

            test.metaClass.methods.each {
                if (it.name.startsWith('test')) {
                    allTestCount++
                    def startTime = System.nanoTime()
                    test.runTest(it.name)
                    def spentTime = (System.nanoTime() - startTime) / 1000000000
                    def status = it.name
                    if (!passed) {
                        failedTestCount++
                        if (error != null) {
                            status += " ($error)"
                        }
                        status += ' failed'
                        if (details != null) {
                            status += "\n$details"
                        }
                        Log.failed(status)
                    } else {
                        status += " (${String.format('%.3f', spentTime)} seconds)"
                        Log.passed(status)
                    }
                }
            }

            test.tearDown()
        }

        println ''
        def allSpentTimeStr = String.format('%.3f', (System.nanoTime() - allStartTime) / 1000000000)
        def resultStr = "    Executed $allTestCount tests, with $failedTestCount failure in $allSpentTimeStr seconds\n"
        if (failedTestCount != 0) {
            println AnsiUtils.red(resultStr)
            throw new RuntimeException("Lint failed!")
        } else {
            println AnsiUtils.green(resultStr)
        }

        Log.setState(Log.LogState.None)
    }
}
