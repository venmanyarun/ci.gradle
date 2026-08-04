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
 * Verifies annotation processor path in dev mode for a multi-module EAR project
 */
class DevMultiModuleAnnotationProcessorTest extends BaseDevTest {

    static File resourceDir = new File("build/resources/test/multi-module-loose-ear-annotation-processor-test")
    static File buildDir    = new File(integTestDir, "/multi-module-loose-ear-annotation-processor-test")
    static String buildFilename = "build.gradle"

    @BeforeClass
    static void setup() throws IOException, InterruptedException, FileNotFoundException {
        createDir(buildDir)
        createTestProject(buildDir, resourceDir, buildFilename)
        new File(buildDir, "build").mkdirs()
        runDevMode("--skipTests", buildDir)
    }

    /**
     * Verify dev mode starts when upstream JAR module has Lombok annotationProcessor configured.
     */
    @Test
    void annotationProcessorOnUpstreamJarModuleDoesNotBreakDevMode() throws Exception {
        assertTrue("libertyDev must be running when an upstream JAR module has an annotationProcessor configured",
                verifyLogMessage(5000, "Liberty is running in dev mode."))
    }

    /**
     * Verify no warning is logged for the JAR module when resolving annotation processor path.
     */
    @Test
    void noCompilerOptionsResolutionWarningForJarModule() throws Exception {
        assertTrue("libertyDev must start before checking compiler-options log",
                verifyLogMessage(5000, "Liberty is running in dev mode."))

        assertFalse("getGradleCompilerOptions() must not log a warning for the :jar module",
                verifyLogMessage(3000, "Could not read compiler options for project 'jar'"))

        assertTrue("getGradleCompilerOptions() must log the annotation processor path for the :jar module",
                verifyLogMessage(3000, "Dev mode annotation processor path"))
    }

    /**
     * Verify that modifying a Java file in the JAR module triggers recompilation when Lombok is configured.
     */
    @Test
    void modifyJavaFileInJarModuleWithLombokTriggersRecompilation() throws Exception {
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

        assertTrue("Greeting.class should be recompiled after source change even with Lombok annotationProcessor configured",
                waitForCompilation(targetGreeting, lastModified, 12000))
    }

    /**
     * Verify that modifying GreetingServlet.java in the WAR module triggers recompilation when Lombok is configured on the WAR module itself via compileJava.options.annotationProcessorPath.
     */
    @Test
    void modifyJavaFileInWarModuleWithLombokTriggersRecompilation() throws Exception {
        File srcServlet = new File(buildDir,
                "war/src/main/java/io/openliberty/guides/multimodules/web/GreetingServlet.java")
        File targetServlet = new File(buildDir,
                "war/build/classes/java/main/io/openliberty/guides/multimodules/web/GreetingServlet.class")

        assertTrue("GreetingServlet.java source file must exist", srcServlet.exists())
        assertTrue("GreetingServlet.class must exist after initial build", targetServlet.exists())

        long lastModified = targetServlet.lastModified()
        waitLongEnough()

        BufferedWriter javaWriter = new BufferedWriter(new FileWriter(srcServlet, true))
        javaWriter.append(" // recompile trigger")
        javaWriter.close()

        assertTrue("GreetingServlet.class should be recompiled after source change with Lombok configured on the WAR module",
                waitForCompilation(targetServlet, lastModified, 12000))
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
