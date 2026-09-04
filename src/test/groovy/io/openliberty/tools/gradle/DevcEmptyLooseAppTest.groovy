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

import static org.junit.Assert.*

import java.io.File
import java.io.FileWriter
import java.nio.file.Files

import org.apache.commons.io.FileUtils
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

/**
 * Integration test for ci.common fix: {@code libertyDevc} must not throw
 * {@code NullPointerException} when the loose application XML file is empty
 * or malformed at startup (simulating the race condition in multi-module
 * container dev mode where the file exists but has not been written yet).
 *
 * <p>Before starting container dev mode the test pre-creates an empty
 * {@code build/.libertyDevc/apps/rest.war.xml} placeholder.  Dev mode must
 * reach "Liberty is running in dev mode." and the log must contain no
 * {@code NullPointerException}.
 */
class DevcEmptyLooseAppTest extends BaseDevTest {

    static final String projectName = "dev-container-empty-looseapp"
    static File resourceDir = new File("build/resources/test/dev-test/" + projectName)
    static File testBuildDir = new File(integTestDir, "/test-dev-container-empty-looseapp")

    @BeforeClass
    static void setup() throws Exception {
        createDir(testBuildDir)
        createTestProject(testBuildDir, resourceDir, "build.gradle", true)

        File buildFile = new File(resourceDir, buildFilename)
        copyBuildFiles(buildFile, testBuildDir, false)

        // Pre-create an empty loose-app XML placeholder in the location that
        // DevUtil reads during file-watcher setup, to simulate the race condition
        // where the file exists but contains no content yet.
        File looseAppDir = new File(testBuildDir, "build/.libertyDevc/apps")
        looseAppDir.mkdirs()
        File emptyLooseApp = new File(looseAppDir, "rest.war.xml")
        emptyLooseApp.createNewFile()   // zero-byte file
        assertTrue("Empty loose-app placeholder must be zero bytes", emptyLooseApp.length() == 0)

        runDevMode("--container", testBuildDir)
    }

    @AfterClass
    static void cleanUpAfterClass() throws Exception {
        String stdout = getContents(logFile, "Dev mode std output")
        System.out.println(stdout)
        String stderr = getContents(errFile, "Dev mode std error")
        System.out.println(stderr)
        cleanUpAfterClassCheckLogFile(true)
    }

    /**
     * Dev mode must start successfully despite the empty loose-app XML.
     * Before the fix a NullPointerException crashed startup immediately.
     * The Gradle plugin prints container build and dev mode messages to stdout (logFile).
     */
    @Test
    void devcStartsWithoutNpe() throws Exception {
        assertTrue("Container build must complete: " + getContents(logFile, "stdout"),
                verifyLogMessage(120000, "Completed building container image.", logFile))
        assertTrue("Liberty must reach dev mode despite the empty loose-app XML: "
                        + getContents(logFile, "stdout"),
                verifyLogMessage(120000, "Liberty is running in dev mode.", logFile))
    }

    /**
     * No NullPointerException must appear in any log stream.
     * This is the primary regression guard for the NPE fix.
     */
    @Test
    void noNullPointerExceptionInLog() throws Exception {
        // Ensure dev mode has started before inspecting logs.
        assertTrue("Dev mode must start before checking for NPE",
                verifyLogMessage(120000, "Liberty is running in dev mode.", logFile))

        assertFalse("NullPointerException must not appear in stdout log",
                verifyLogMessage(500, "NullPointerException", logFile))
        assertFalse("NullPointerException must not appear in stderr log",
                verifyLogMessage(500, "NullPointerException", errFile))
    }

}
