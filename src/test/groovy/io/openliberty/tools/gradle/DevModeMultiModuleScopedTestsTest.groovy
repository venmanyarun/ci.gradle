/*
 * (C) Copyright IBM Corporation 2026.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.openliberty.tools.gradle

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStreamWriter
import java.io.OutputStream

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

/**
 * Regression test for GitHub issue #1098.
 *
 * Three independent Liberty submodules (moduleA, moduleB, moduleC) — none depend on each other.
 * Running :moduleA:libertyDev and pressing Enter must execute only :moduleA:cleanTest and
 * :moduleA:test; :moduleB:test and :moduleC:test must NOT run.
 */
class DevModeMultiModuleScopedTestsTest extends BaseDevTest {

    static File resourceDir = new File("build/resources/test/multi-module-indep-liberty-test")
    static File buildDir    = new File(integTestDir, "/multi-module-indep-liberty-test")
    static String buildFilename = "build.gradle"

    @BeforeClass
    static void setup() throws IOException, InterruptedException, FileNotFoundException {
        createDir(buildDir)
        createTestProject(buildDir, resourceDir, buildFilename)
        new File(buildDir, "build").mkdirs()
        // Use fully-qualified task path; BaseDevTest.runDevMode hardcodes the bare "libertyDev" name.
        runModuleDevMode(":moduleA:libertyDev", "--skipTests", buildDir)
    }

    @Test
    void moduleADevModeStartsSuccessfully() throws Exception {
        assertTrue("libertyDev for :moduleA should reach dev mode",
                verifyLogMessage(5000, "Liberty is running in dev mode."))
    }

    @Test
    void onDemandTestsRunOnlyForModuleA() throws Exception {
        assertTrue(verifyLogMessage(5000, "Liberty is running in dev mode."))

        writer.write("\n")
        writer.flush()

        Thread.sleep(20000)

        assertTrue(":moduleA: must appear in log after pressing Enter",
                verifyLogMessage(30000, ":moduleA:"))
        assertFalse(":moduleB:test must NOT run",
                verifyLogMessage(3000, ":moduleB:test"))
        assertFalse(":moduleC:test must NOT run",
                verifyLogMessage(3000, ":moduleC:test"))
    }

    @AfterClass
    static void cleanUpAfterClass() throws Exception {
        String stdout = getContents(logFile, "Dev mode std output")
        System.out.println(stdout)
        String stderr = getContents(errFile, "Dev mode std error")
        System.out.println(stderr)
        cleanUpAfterClass(true)
    }

    /** Like BaseDevTest.runDevMode but accepts a fully-qualified Gradle task path. */
    protected static void runModuleDevMode(String taskPath, String extraFlags, File buildDirectory)
            throws IOException, InterruptedException, FileNotFoundException {

        buildDir  = buildDirectory
        logFile   = new File(buildDir, "output.log")
        errFile   = new File(buildDir, "stderr.log")

        File gradlew
        String os = System.getProperty("os.name")
        if (os != null && os.toLowerCase().startsWith("windows")) {
            gradlew = new File("gradlew.bat")
        } else {
            gradlew = new File("gradlew")
        }

        String command = gradlew.getAbsolutePath() + " --warning-mode none " + taskPath
        if (extraFlags != null && !extraFlags.isEmpty()) {
            command += " " + extraFlags
        }
        System.out.println("Running command: " + command)

        ProcessBuilder builder = buildProcess(command)
        builder.redirectOutput(logFile)
        builder.redirectError(errFile)
        process = builder.start()
        assertTrue(process.isAlive())

        OutputStream stdin = process.getOutputStream()
        writer = new BufferedWriter(new OutputStreamWriter(stdin))

        assertTrue(verifyLogMessage(120000, "CWWKF0011I", errFile))
        assertTrue(verifyLogMessage(60000, "Liberty is running in dev mode."))

        targetDir = new File(buildDir, "build")
        assertTrue(targetDir.exists())
    }
}
