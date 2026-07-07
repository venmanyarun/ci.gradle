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

import static org.junit.Assert.assertTrue

/**
 * Integration test for the sourceSets-present path in DevTask.groovy.
 *
 * Complements TestMultiModuleEarNoJavaPluginDevMode (which covers sourceSets == null)
 * by running libertyDev on a project where the WAR and JAR submodules DO have the
 * 'java' plugin applied AND the WAR submodule has an explicit
 * {@code sourceSets { main { java { srcDirs = [...] } } }} block.
 *
 * The EAR module itself has no 'java' plugin (same topology as the no-java-plugin test).
 * What differs is that the sibling projects (WAR, JAR) carry real SourceSetContainers,
 * exercising the non-null branch in DevTask.getProjectModules():
 *   - upstream sourceSets resolved via extensions.findByType(SourceSetContainer)
 *   - classesDirectory resolved from the live mainSourceSet.java.classesDirectory
 */
class TestMultiModuleEarWithSourceSetsDevMode extends BaseDevTest {

    // Deliberately different from the deploy test's buildDir
    // (multi-module-loose-ear-with-sourcesets-deploy-test) to prevent
    // any cross-test interference from shared build artifacts or Liberty state.
    static File resourceDir = new File("build/resources/test/multi-module-loose-ear-with-sourcesets-test")
    static File buildDir    = new File(integTestDir, "/multi-module-loose-ear-with-sourcesets-dev-test")
    static String buildFilename = "build.gradle"

    @BeforeClass
    static void setup() throws IOException, InterruptedException, FileNotFoundException {
        createDir(buildDir)
        createTestProject(buildDir, resourceDir, buildFilename)
        new File(buildDir, "build").mkdirs()

        // Start libertyDev — exercises DevTask with non-null SourceSetContainers on the
        // sibling WAR/JAR modules (sourceSets.findByName('main') returns a real SourceSet).
        runDevMode("--skipTests", buildDir)
    }

    /**
     * Primary test: libertyDev must start successfully when sibling submodules have the
     * 'java' plugin and the WAR module has an explicit sourceSets block.
     */
    @Test
    void earModuleWithSourceSetsOnSiblingsStartsDevMode() throws Exception {
        assertTrue("libertyDev should be running in dev mode when sibling sourceSets are present",
                verifyLogMessage(5000, "Liberty is running in dev mode."))
    }

    /**
     * Verify that modifying a Java source file in the JAR submodule (which has 'java'
     * and a live SourceSet) triggers recompilation via the sourceSets-backed file watcher.
     *
     * This covers DevTask.getProjectModules(): the upstream project's SourceSetContainer
     * is resolved via extensions.findByType(SourceSetContainer) and classesDirectory
     * comes from mainSourceSet.java.classesDirectory.asFile.get().
     */
    @Test
    void modifyJavaFileInJarSubmoduleTriggersRecompilation() throws Exception {
        File srcGreeter = new File(buildDir,
                "jar/src/main/java/io/openliberty/guides/multimodules/lib/Greeter.java")
        File targetGreeter = new File(buildDir,
                "jar/build/classes/java/main/io/openliberty/guides/multimodules/lib/Greeter.class")

        assertTrue("Greeter.java source file must exist", srcGreeter.exists())
        assertTrue("Greeter.class must exist after initial build", targetGreeter.exists())

        long lastModified = targetGreeter.lastModified()
        waitLongEnough()

        BufferedWriter javaWriter = new BufferedWriter(new FileWriter(srcGreeter, true))
        javaWriter.append(" // recompile trigger")
        javaWriter.close()

        assertTrue("Greeter.class should be recompiled after source change",
                waitForCompilation(targetGreeter, lastModified, 12000))
    }

    /**
     * Verify that modifying a Java source file in the WAR submodule — the one with the
     * explicit sourceSets block — triggers recompilation.
     *
     * This covers the path where mainSourceSet is resolved from the explicit sourceSets
     * declaration: srcDirs from mainSourceSet.java.srcDirs, classesDirectory from
     * mainSourceSet.java.classesDirectory.asFile.get().
     */
    @Test
    void modifyJavaFileInWarSubmoduleWithExplicitSourceSetsTriggersRecompilation() throws Exception {
        // Source is in the custom non-default srcDir 'src/war/java' (not 'src/main/java').
        // DevTask reads sourceDirectory from mainSourceSet.java.srcDirs — if it fell back
        // to the default "src/main/java" it would watch the wrong directory and miss changes.
        File srcServlet = new File(buildDir,
                "war/src/war/java/io/openliberty/guides/multimodules/web/HelloServlet.java")
        File targetServlet = new File(buildDir,
                "war/build/classes/java/main/io/openliberty/guides/multimodules/web/HelloServlet.class")

        assertTrue("HelloServlet.java source file must exist", srcServlet.exists())
        assertTrue("HelloServlet.class must exist after initial build", targetServlet.exists())

        long lastModified = targetServlet.lastModified()
        waitLongEnough()

        BufferedWriter javaWriter = new BufferedWriter(new FileWriter(srcServlet, true))
        javaWriter.append(" // recompile trigger")
        javaWriter.close()

        assertTrue("HelloServlet.class should be recompiled — WAR module uses an explicit sourceSets block",
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
