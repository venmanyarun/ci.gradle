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

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test

/**
 * Integration test for ci.common fix: {@code libertyDevc} must mount
 * {@code configDropins/overrides} into the container so that Liberty reads the
 * plugin-written variable overrides ({@code liberty-plugin-variable-config.xml})
 * and starts on the configured non-default port.
 *
 * <p>This project sets {@code liberty.server.var."default.http.port" = '9090'}
 * and {@code liberty.server.var."default.https.port" = '9453'}.  Those values
 * are written by the Gradle plugin to
 * {@code configDropins/overrides/liberty-plugin-variable-config.xml}.
 *
 * <p>The test verifies that Liberty inside the container starts on port 9090
 * (confirmed via the {@code CWWKT0016I} audit message) and that dev mode
 * reaches the ready banner without error.
 *
 * <p>Note: full end-to-end port-mapping verification (published port == 9090)
 * requires {@code liberty-plugin-config.xml} to be present before
 * {@code libertyDev} calls {@code getContainerCommand()}. In the Gradle plugin
 * that file is written during {@code libertyDev} itself, so the Gradle IT
 * validates the container-side fix only.
 */
class DevcNonDefaultPortTest extends BaseDevTest {

    static final String projectName  = "dev-container-nondefault-port"
    static File resourceDir   = new File("build/resources/test/dev-test/" + projectName)
    static File testBuildDir  = new File(integTestDir, "/test-dev-container-nondefault-port")

    /** Non-default HTTP port configured via {@code liberty.server.var."default.http.port"}. */
    static final String EXPECTED_HTTP_PORT  = "9090"

    @BeforeClass
    static void setup() throws Exception {
        createDir(testBuildDir)
        createTestProject(testBuildDir, resourceDir, "build.gradle", true)

        File buildFile = new File(resourceDir, buildFilename)
        copyBuildFiles(buildFile, testBuildDir, false)

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
     * Liberty inside the container must start on the configured port 9090.
     * This is confirmed by the {@code CWWKT0016I} audit message in stdout,
     * which reports the port Liberty actually bound to.
     * The Gradle plugin prints all dev mode output to stdout (logFile).
     */
    @Test
    void libertyStartsOnConfiguredPort() throws Exception {
        assertTrue("Dev mode must start before checking Liberty port",
                verifyLogMessage(120000, "Liberty is running in dev mode.", logFile))

        assertTrue("Liberty must bind to the configured port " + EXPECTED_HTTP_PORT
                        + " (CWWKT0016I must reference :" + EXPECTED_HTTP_PORT + "): "
                        + getContents(logFile, "stdout"),
                verifyLogMessage(5000, ":" + EXPECTED_HTTP_PORT + "/", logFile))
    }

    /**
     * The configDropins/overrides directory must be mounted into the container
     * so Liberty can read liberty-plugin-variable-config.xml.
     * Confirmed by the CWWKG0093A audit message for that file in stdout.
     */
    @Test
    void configDropinsOverridesMountedInContainer() throws Exception {
        assertTrue("Dev mode must start before checking config dropins",
                verifyLogMessage(120000, "Liberty is running in dev mode.", logFile))

        assertTrue("Liberty must process configDropins/overrides/liberty-plugin-variable-config.xml: "
                        + getContents(logFile, "stdout"),
                verifyLogMessage(5000, "liberty-plugin-variable-config.xml", logFile))
    }
}
