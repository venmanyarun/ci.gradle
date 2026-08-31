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
 * Topology: three independent Liberty submodules (moduleA, moduleB, moduleC) with no
 * compile-time dependencies between them.
 *
 * When modules DO depend on each other (e.g. A → D → E), DevUtil already calls
 * runIntegrationTests once per upstream module's build file, so our fix correctly
 * qualifies each call (":moduleD:cleanTest", ":moduleE:cleanTest", etc.) — that
 * scenario is covered by the existing EAR multi-module dev mode tests.
 *
 * This test focuses on the independent case: pressing Enter while running
 * :moduleA:libertyDev must execute only :moduleA:cleanTest and :moduleA:test.
 * :moduleB:test and :moduleC:test must NOT run.
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
        // No --skipTests so that pressing Enter actually triggers runIntegrationTests.
        runModuleDevMode(":moduleA:libertyDev", null, buildDir)
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

        // Wait for the entire test run to finish before checking scope.
        // "Tests finished." is printed by DevUtil after runIntegrationTests returns,
        // so once it appears the test invocation is fully complete — any moduleB or
        // moduleC task output would already be in the log if the bug were present.
        assertTrue(":moduleA:test must run after pressing Enter",
                verifyLogMessage(60000, ":moduleA:test"))
        assertTrue("Tests finished. must appear confirming the run is complete",
                verifyLogMessage(60000, "Tests finished."))

        // Now that the run is done, check scope: only moduleA tasks must have appeared.
        assertFalse(":moduleB:test must NOT be in log",
                verifyLogMessage(1000, ":moduleB:test"))
        assertFalse(":moduleC:test must NOT be in log",
                verifyLogMessage(1000, ":moduleC:test"))
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

        // Build the argument list directly (no shell, no quoting) so that paths with
        // spaces work on all platforms.  --project-dir pins Gradle to the test project
        // root instead of walking up and finding the ci.gradle repo settings.gradle.
        List<String> args = new ArrayList<>()
        args.add(gradlew.getAbsolutePath())
        args.add("--project-dir")
        args.add(buildDir.getAbsolutePath())
        args.add("--warning-mode")
        args.add("none")
        args.add(taskPath)
        if (extraFlags != null && !extraFlags.isEmpty()) {
            args.addAll(extraFlags.trim().split("\\s+").toList())
        }
        System.out.println("Running command: " + args.join(" "))

        ProcessBuilder builder = new ProcessBuilder(args)
        builder.directory(buildDir)
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
