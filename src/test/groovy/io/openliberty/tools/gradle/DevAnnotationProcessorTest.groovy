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
package io.openliberty.tools.gradle;

import static org.junit.Assert.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Test;

/**
 * Verifies annotation processor for dev mode hot-reload
 */
class DevAnnotationProcessorTest extends BaseDevTest {
    static final String projectName = "dev-mode-annotation-processor-test";

    static File resourceDir = new File("build/resources/test/dev-test/" + projectName);
    static File buildDir = new File(integTestDir, "dev-test/" + projectName + System.currentTimeMillis());

    @BeforeClass
    public static void setup() throws IOException, InterruptedException, FileNotFoundException {
        createDir(buildDir);
        createTestProject(buildDir, resourceDir, buildFilename);
        runDevMode(null, buildDir);
    }

    /**
     * Verify the server started with the Lombok-processed sources compiled successfully.
     */
    @Test
    public void testAnnotationProcessorServerStartup() throws Exception {
        assertTrue("Web application did not become available — " +
                   "Lombok annotation processor may not have been applied at startup",
                verifyLogMessage(30000, WEB_APP_AVAILABLE, errFile));
    }

    /**
     * Trigger a hot-reload and verify that compilation succeeds.
     */
    @Test
    public void testAnnotationProcessorHotReloadCompilation() throws Exception {
        assertTrue("Server did not start before hot-reload test",
                verifyLogMessage(60000, "Liberty is running in dev mode."));
        assertTrue("Web application did not become available before hot-reload test",
                verifyLogMessage(30000, WEB_APP_AVAILABLE, errFile));

        File greetingServlet = new File(buildDir, "src/main/java/com/demo/GreetingServlet.java");
        assertTrue("GreetingServlet.java source file not found", greetingServlet.exists());

        File greetingClass = new File(targetDir, "classes/java/main/com/demo/GreetingServlet.class");
        assertTrue("GreetingServlet.class not found after initial compile", greetingClass.exists());

        int compilationCount = countOccurrences(COMPILATION_SUCCESSFUL, logFile);

        waitLongEnough();
        long lastModified = greetingClass.lastModified();

        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(greetingServlet, true));
            writer.append("\n// trigger hot reload");
        } finally {
            if (writer != null) writer.close();
        }

        assertTrue("GreetingServlet.class was not recompiled after source change",
                waitForCompilation(greetingClass, lastModified, 30000));
        assertTrue("Compilation did not succeed after hot reload",
                verifyLogMessage(30000, COMPILATION_SUCCESSFUL, ++compilationCount));
        assertFalse("Unexpected compilation error during hot reload",
                verifyLogMessage(5000, COMPILATION_ERRORS));
    }

    @AfterClass
    public static void cleanUpAfterClass() throws Exception {
        String stdout = getContents(logFile, "Dev mode std output");
        System.out.println(stdout);
        String stderr = getContents(errFile, "Dev mode std error");
        System.out.println(stderr);
        cleanUpAfterClass(true);
    }
}
