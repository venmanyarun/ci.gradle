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

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

/**
 * Verifies compiler args are read correctly in dev mode for a multi-module EAR project
 */
class DevMultiModuleCompilerArgsTest extends BaseDevTest {

    static File resourceDir = new File("build/resources/test/multi-module-loose-ear-compiler-args-test")
    static File buildDir    = new File(integTestDir, "/multi-module-loose-ear-compiler-args-test")
    static String buildFilename = "build.gradle"

    @BeforeClass
    static void setup() throws IOException, InterruptedException, FileNotFoundException {
        createDir(buildDir)
        createTestProject(buildDir, resourceDir, buildFilename)
        new File(buildDir, "build").mkdirs()
        runDevMode("--skipTests", buildDir)
    }

    /**
     * Verify dev mode starts when upstream JAR and WAR modules have compilerArgs configured.
     */
    @Test
    void compilerArgsOnUpstreamModulesDoesNotBreakDevMode() throws Exception {
        assertTrue("libertyDev must be running when upstream modules have compilerArgs configured",
                verifyLogMessage(5000, "Liberty is running in dev mode."))
    }

    /**
     * Verify no warning is logged for either upstream module when resolving compiler args.
     */
    @Test
    void noCompilerOptionsResolutionWarningForUpstreamModules() throws Exception {
        assertTrue("libertyDev must start before checking compiler-options log",
                verifyLogMessage(5000, "Liberty is running in dev mode."))

        assertFalse("getGradleCompilerOptions() must not log a warning for the :jar module",
                verifyLogMessage(3000, "Could not read compiler options for project 'jar'"))
        assertFalse("getGradleCompilerOptions() must not log a warning for the :war module",
                verifyLogMessage(3000, "Could not read compiler options for project 'war'"))
    }

    /**
     * Verify that modifying a Java file in the JAR module triggers recompilation when compilerArgs is set.
     */
    @Test
    void modifyJavaFileInJarModuleWithCompilerArgsTriggersRecompilation() throws Exception {
        File srcGreeting = new File(buildDir,
                "jar/src/main/java/io/openliberty/guides/multimodules/lib/Greeting.java")
        File targetGreeting = new File(buildDir,
                "jar/build/classes/java/main/io/openliberty/guides/multimodules/lib/Greeting.class")

        assertTrue("Greeting.java source file must exist", srcGreeting.exists())
        assertTrue("Greeting.class must exist after initial build", targetGreeting.exists())

        long lastModified = targetGreeting.lastModified()
        waitLongEnough()

        BufferedWriter javaWriter = new BufferedWriter(new FileWriter(srcGreeting, true))
        javaWriter.append(" // recompile trigger")
        javaWriter.close()

        assertTrue("Greeting.class should be recompiled after source change even with compilerArgs configured on the JAR module",
                waitForCompilation(targetGreeting, lastModified, 12000))
    }

    @AfterClass
    static void cleanUpAfterClass() throws Exception {
        String stdout = getContents(logFile, "Dev mode std output")
        System.out.println(stdout)
        String stderr = getContents(errFile, "Dev mode std error")
        System.out.println(stderr)
        cleanUpAfterClass(true)
    }
}
