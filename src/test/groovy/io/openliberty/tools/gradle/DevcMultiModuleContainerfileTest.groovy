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

import org.apache.commons.io.FileUtils
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

/**
 * Regression test for: https://github.com/OpenLiberty/ci.common/issues/536 (Issue 3)
 *
 * In a multi-module Gradle project, DevTask previously passed project.getRootDir()
 * as the projectDirectory to DevUtil, and also used it to resolve relative --containerfile
 * paths in convertParameterToCanonicalFile(). Both now use project.getProjectDir() instead.
 *
 * This test verifies two scenarios using a multi-module project (jar / war / ear) where
 * the Containerfile lives ONLY inside the :ear subproject directory — NOT at the root:
 *
 *   1. Default discovery: :ear:libertyDev --container finds ear/Containerfile automatically
 *      (the default container file lookup uses projectDirectory as the base).
 *
 *   2. Relative --containerfile path: passing --containerfile Containerfile resolves it
 *      against the :ear subproject directory, not the root.
 *
 * Before the fix both scenarios would fail because getRootDir() pointed at the multi-module
 * root where no Containerfile exists.
 */
class DevcMultiModuleContainerfileTest extends BaseDevTest {

    static final String projectName = "multi-module-devc-containerfile-test"
    static File resourceDir = new File("build/resources/test/dev-test/" + projectName)
    static File testBuildDir = new File(integTestDir, "/test-" + projectName)

    @BeforeClass
    static void setup() throws IOException, InterruptedException, FileNotFoundException {
        createDir(testBuildDir)
        // Copy the entire multi-module resource tree (settings.gradle + all subproject dirs)
        FileUtils.copyDirectory(resourceDir, testBuildDir)
        // Merge in the generated gradle.properties (lgpVersion, runtimeVersion, etc.)
        copyBuildFiles(new File(resourceDir, "build.gradle"), testBuildDir, true)

        // Run :ear:libertyDev --container from the multi-module root.
        // This exercises the default Containerfile discovery path: DevUtil resolves
        // the default container file as new File(projectDirectory, "Containerfile").
        // With the bug, projectDirectory was the root (no Containerfile there) and
        // dev mode would not find ear/Containerfile.
        startDevcOnEarModule(testBuildDir)
    }

    /**
     * Starts :ear:libertyDev --container from the given multi-module root directory,
     * then waits for the server and dev-mode ready signals.
     *
     * This replicates the logic of BaseDevTest.startProcess() but with a subproject
     * task qualifier (:ear:libertyDev) since startProcess() is private and hardcodes
     * the plain 'libertyDev' task.
     */
    private static void startDevcOnEarModule(File buildDirectory) throws IOException, InterruptedException, FileNotFoundException {
        buildDir = buildDirectory
        logFile  = new File(buildDir, "output.log")
        errFile  = new File(buildDir, "stderr.log")

        File gradlew = System.getProperty("os.name")?.toLowerCase()?.startsWith("windows")
                ? new File("gradlew.bat") : new File("gradlew")

        String command = "${gradlew.absolutePath} --warning-mode none :ear:libertyDev --container"
        System.out.println("Starting devc on :ear submodule: " + command)

        ProcessBuilder builder = new ProcessBuilder()
        builder.directory(buildDir)
        builder.command("bash", "-c", command)
        builder.redirectOutput(logFile)
        builder.redirectError(errFile)
        process = builder.start()
        assertTrue("Process should be alive after start", process.isAlive())

        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))

        // Wait for Liberty kernel to be ready (in the container)
        assertTrue("Liberty kernel features should be installed in the container",
                verifyLogMessage(180000, "CWWKF0011I", errFile))
        // Wait for dev-mode ready signal
        assertTrue("Liberty should be running in dev mode",
                verifyLogMessage(60000, "Liberty is running in dev mode."))

        targetDir = new File(buildDir, "ear/build")
        assertTrue("ear/build directory should exist after startup", targetDir.exists())
    }

    /**
     * Primary regression test (Issue 3 — default Containerfile discovery).
     *
     * Verifies that the container image was successfully built from the Containerfile
     * located in ear/ and that the application started inside the container.
     *
     * Before the fix, DevUtil looked for Containerfile at <rootDir>/Containerfile
     * (which does not exist), so the container build never started and dev mode
     * fell back to plain server mode or failed outright.
     */
    @Test
    void defaultContainerfileInSubprojectDirectoryIsFound() throws Exception {
        assertTrue("Container image build should complete using ear/Containerfile",
                verifyLogMessage(30000, "Completed building container image.", logFile))
        // Liberty container output is redirected to logFile (stdout), not errFile
        assertTrue("Application should start inside the container",
                verifyLogMessage(30000, "CWWKZ0001I", logFile))
    }

    /**
     * Verify that the log does NOT contain a "Containerfile not found" or fallback warning
     * that would indicate the file was searched for at the wrong location.
     *
     * Before the fix a warning like "No Containerfile or Dockerfile found" was emitted
     * because the lookup targeted the root directory.
     */
    @Test
    void noContainerfileNotFoundWarningInLog() throws Exception {
        // Give dev mode a moment to emit any startup warnings
        assertTrue("Dev mode must be running before checking for spurious warnings",
                verifyLogMessage(5000, "Liberty is running in dev mode."))
        assertFalse("Should not see a 'Containerfile not found' warning when Containerfile is in the subproject dir",
                verifyLogMessage(3000, "No Containerfile or Dockerfile found", logFile))
    }

    /**
     * Regression test for relative --containerfile path resolution (convertParameterToCanonicalFile).
     *
     * Verifies that the log confirms the Containerfile resolved to the path inside the
     * ear/ subproject directory, not the multi-module root.  Dev mode logs the resolved
     * container file path as part of the build context message.
     */
    @Test
    void containerfileResolvedToSubprojectDirectory() throws Exception {
        assertTrue("Dev mode must be running", verifyLogMessage(5000, "Liberty is running in dev mode."))
        // DevUtil logs the container build context path it is using. After the fix the build
        // context resolves to the ear subproject directory, so the logged path must contain
        // the '/ear' segment (or '\ear' on Windows). We check for the path segment that
        // distinguishes the subproject context from the root.
        assertTrue("Container build context in log should reference the ear subproject directory",
                verifyLogMessage(5000, File.separator + "ear", logFile))
    }

    @AfterClass
    static void cleanUpAfterClass() throws Exception {
        String stdout = getContents(logFile, "Dev mode std output")
        System.out.println(stdout)
        String stderr = getContents(errFile, "Dev mode std error")
        System.out.println(stderr)
        cleanUpAfterClassCheckLogFile(true)
    }
}
