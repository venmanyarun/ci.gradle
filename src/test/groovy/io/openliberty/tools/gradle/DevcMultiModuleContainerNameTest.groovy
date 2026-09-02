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

import java.io.BufferedWriter
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStreamWriter
import java.io.OutputStream
import java.util.concurrent.TimeUnit

import org.apache.commons.io.FileUtils
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

/**
 * Integration test for ci.common Issue 1 (container name collision) when multiple
 * independently Liberty-configured Gradle submodules start with container dev mode.
 *
 * <p>This test starts container dev mode on two independent WAR submodules
 * (modulea and moduleb) sequentially, and verifies that the container name written
 * to each module's devc metadata file uses the Gradle project name as a suffix:
 * <ul>
 *   <li>{@code :modulea:libertyDev --container} → container name {@code liberty-dev-modulea}</li>
 *   <li>{@code :moduleb:libertyDev --container} → container name {@code liberty-dev-moduleb}</li>
 * </ul>
 *
 * <p>Distinct names are the stable outcome of the Issue 1 fix in ci.common's
 * {@code DevUtil.generateNewContainerName()}. If the bug were still present both
 * modules would receive the same name ({@code liberty-dev} or {@code liberty-dev-1}),
 * causing a container engine collision when started concurrently.
 *
 */
class DevcMultiModuleContainerNameTest extends BaseDevTest {

    static final String projectName = "devc-multimodule"
    static File resourceDir  = new File("build/resources/test/dev-test/" + projectName)
    static File testBuildDir = new File(integTestDir, "/test-devc-multimodule")

    @BeforeClass
    static void setup() throws IOException, InterruptedException, FileNotFoundException {
        createDir(testBuildDir)
        createTestProject(testBuildDir, resourceDir, "build.gradle", true)
        copySettingsFile(resourceDir, testBuildDir)
    }

    @AfterClass
    static void cleanUpAfterClass() throws Exception {
        String stdout = logFile?.exists() ? getContents(logFile, "Dev mode std output") : ""
        System.out.println(stdout)
        String stderr = errFile?.exists() ? getContents(errFile, "Dev mode std error") : ""
        System.out.println(stderr)
        cleanUpAfterClassCheckLogFile(true)
    }

    /**
     * Starts :modulea:libertyDev with the container flag, waits for the Liberty
     * server to reach dev mode inside the container, then reads the devc metadata
     * XML and asserts the container name is {@code liberty-dev-modulea}.
     *
     * <p>We do not assert {@code CWWKZ0001I} (app started) here because the
     * loose-app WAR is not deployed as a static file; only the container name —
     * the actual regression target for Issue 1 — is verified.
     */
    @Test
    void moduleaContainerNameIncludesProjectName() throws Exception {
        runModuleDevMode(":modulea:libertyDev", "--container --skipTests", testBuildDir)

        assertTrue("modulea: container build did not complete.",
            verifyLogMessage(120000, "Completed building container image.", logFile))
        assertTrue("modulea: Liberty did not reach dev mode.",
            verifyLogMessage(60000, "Liberty is running in dev mode."))

        // Metadata is written to buildDir (modulea/build) as
        // ${serverName}-liberty-devc-metadata.xml.
        File metaFile = new File(testBuildDir, "modulea/build/defaultServer-liberty-devc-metadata.xml")
        assertTrue("modulea: devc metadata file does not exist: " + metaFile.absolutePath,
            verifyFileExists(metaFile, 30000))

        String content = FileUtils.readFileToString(metaFile, "UTF-8")
        assertTrue("modulea: container name must be 'liberty-dev-modulea', got: " + content,
            content.contains("<containerName>liberty-dev-modulea</containerName>"))

        // Stop modulea cleanly before the moduleb test runs.
        // Do NOT call cleanUpAfterClass — that would delete the build directory.
        writer.write("exit")
        writer.flush()
        try { writer.close() } catch (IOException ignored) {}
        process.waitFor(120, TimeUnit.SECONDS)
        writer = null
        process = null
    }

    /**
     * Starts :moduleb:libertyDev with the container flag and verifies that its
     * container name is {@code liberty-dev-moduleb} — distinct from modulea's.
     */
    @Test
    void modulebContainerNameIncludesProjectName() throws Exception {
        runModuleDevMode(":moduleb:libertyDev", "--container --skipTests", testBuildDir)

        assertTrue("moduleb: container build did not complete.",
            verifyLogMessage(120000, "Completed building container image.", logFile))
        assertTrue("moduleb: Liberty did not reach dev mode.",
            verifyLogMessage(60000, "Liberty is running in dev mode."))

        File metaFile = new File(testBuildDir, "moduleb/build/defaultServer-liberty-devc-metadata.xml")
        assertTrue("moduleb: devc metadata file does not exist: " + metaFile.absolutePath,
            verifyFileExists(metaFile, 30000))

        String content = FileUtils.readFileToString(metaFile, "UTF-8")
        assertTrue("moduleb: container name must be 'liberty-dev-moduleb', got: " + content,
            content.contains("<containerName>liberty-dev-moduleb</containerName>"))
        // moduleb cleanup is handled by @AfterClass
    }

    /**
     * Starts a single Gradle submodule with {@code libertyDev} using a fully-qualified
     * task path (e.g. {@code :modulea:libertyDev}) and waits for Liberty to be ready
     * inside the container.
     */
    protected static void runModuleDevMode(String taskPath, String extraFlags, File buildDirectory)
            throws IOException, InterruptedException, FileNotFoundException {

        buildDir = buildDirectory
        logFile  = new File(buildDir, "output.log")
        errFile  = new File(buildDir, "stderr.log")

        File gradlew = System.getProperty("os.name")?.toLowerCase()?.startsWith("windows")
                ? new File("gradlew.bat") : new File("gradlew")

        List<String> args = new ArrayList<>()
        args.add(gradlew.absolutePath)
        args.add("--project-dir")
        args.add(buildDir.absolutePath)
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

        // Wait for Liberty kernel features installed, then dev mode ready banner.
        assertTrue(verifyLogMessage(120000, "CWWKF0011I", errFile))
        assertTrue(verifyLogMessage(60000, "Liberty is running in dev mode."))

        targetDir = new File(buildDir, "build")
    }
}
