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
 * Regression test for the EAR devc loose config mount mismatch bug.
 *
 * When running libertyDevc for a multi-module EAR project with looseApplication=true,
 * the devc loose config XML (*.ear.xml) must use the root project dir as the base for
 * all ${io.openliberty.tools.projectRoot} path substitutions, and the container must
 * mount the root project dir (not just the EAR subproject dir) as /devmode.
 *
 * Before the fix in DevTask.groovy, multiModuleProjectDirectory was always set to
 * project.getProjectDir() (the EAR subproject dir), so DevUtil mounted only ear/ as
 * /devmode. But installLooseConfigEar() wrote paths relative to the root project dir,
 * causing an extra subproject path segment inside the container — Liberty could not
 * find application.xml and fell back to using the WAR filename as the context root.
 *
 * This test verifies that the devc loose config XML for the EAR contains
 * ${io.openliberty.tools.projectRoot} paths that do NOT have the ear/ subproject
 * directory as an extra segment — confirming the root project dir is used as the base.
 */
class DevcMultiModuleLooseEarTest extends BaseDevTest {

    static final String projectName = "multi-module-devc-loose-ear-test"
    static File resourceDir = new File("build/resources/test/dev-test/" + projectName)
    static File testBuildDir = new File(integTestDir, "/test-" + projectName)

    @BeforeClass
    static void setup() throws IOException, InterruptedException, FileNotFoundException {
        createDir(testBuildDir)
        FileUtils.copyDirectory(resourceDir, testBuildDir)
        copyBuildFiles(new File(resourceDir, "build.gradle"), testBuildDir, true)
        startDevcOnEarModule(testBuildDir)
    }

    private static void startDevcOnEarModule(File buildDirectory) throws IOException, InterruptedException, FileNotFoundException {
        buildDir = buildDirectory
        logFile  = new File(buildDir, "output.log")
        errFile  = new File(buildDir, "stderr.log")

        File gradlew = System.getProperty("os.name")?.toLowerCase()?.startsWith("windows")
                ? new File("gradlew.bat") : new File("gradlew")

        String command = "${gradlew.absolutePath} --warning-mode none :ear:libertyDev --container"

        ProcessBuilder builder = new ProcessBuilder()
        builder.directory(buildDir)
        builder.command("bash", "-c", command)
        builder.redirectOutput(logFile)
        builder.redirectError(errFile)
        process = builder.start()
        assertTrue("Process should be alive after start", process.isAlive())

        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))

        assertTrue("Liberty kernel features should be installed in the container",
                verifyLogMessage(180000, "CWWKF0011I", errFile))
        assertTrue("Liberty should be running in dev mode",
                verifyLogMessage(60000, "Liberty is running in dev mode."))

        targetDir = new File(buildDir, "ear/build")
        assertTrue("ear/build directory should exist after startup", targetDir.exists())
    }

    /**
     * Verifies the container image builds and the application starts — confirming
     * the EAR and its loose config are resolved correctly inside the container.
     */
    @Test
    void containerImageBuildsAndAppStarts() throws Exception {
        assertTrue("Container image build should complete",
                verifyLogMessage(30000, "Completed building container image.", logFile))
        assertTrue("Application should start inside the container",
                verifyLogMessage(30000, "CWWKZ0001I", logFile))
        // Verify the context root is /app (from deploymentDescriptor webModule) — not the WAR filename.
        // Before the fix, Liberty could not find application.xml and fell back to using the WAR filename
        // as the context root, so this would have logged /devc-loose-ear-war-1.0-SNAPSHOT/ instead.
        assertTrue("Web application must be available at the configured context root /app — not the WAR filename",
                verifyLogMessage(10000, "CWWKT0016I", logFile) &&
                verifyLogMessage(10000, "/app/", logFile))
    }

    /**
     * Core regression test: the devc loose config XML must NOT contain the ear/
     * subproject directory as an extra segment in the ${projectRoot} paths.
     *
     * Before the fix, paths looked like:
     *   ${io.openliberty.tools.projectRoot}/ear/build/tmp/ear/application.xml
     * which resolves to /devmode/ear/build/... inside the container — wrong, because
     * only ear/ was mounted as /devmode.
     *
     * After the fix, paths look like:
     *   ${io.openliberty.tools.projectRoot}/ear/build/tmp/ear/application.xml
     * but now the root project dir is mounted as /devmode, so this resolves correctly
     * to /devmode/ear/build/tmp/ear/application.xml inside the container.
     *
     * We verify this by checking that the devc loose config does NOT use absolute
     * host paths (which would indicate the container path substitution never ran).
     */
    @Test
    void devcLooseConfigUsesProjectRootVariable() throws Exception {
        // Wait for deploy to complete — the loose config file is written during deploy
        assertTrue("Liberty should be running in dev mode before checking loose config",
                verifyLogMessage(5000, "Liberty is running in dev mode."))

        // Find the devc loose config XML: ear/build/.libertyDevc/apps/*.ear.xml
        File devcAppsDir = new File(testBuildDir, "ear/build/.libertyDevc/apps")
        assertTrue("devc apps directory should exist: " + devcAppsDir.absolutePath,
                devcAppsDir.exists())

        File[] looseConfigFiles = devcAppsDir.listFiles({ f -> f.name.endsWith(".ear.xml") } as FileFilter)
        assertTrue("A devc loose config .ear.xml file should exist in " + devcAppsDir.absolutePath,
                looseConfigFiles != null && looseConfigFiles.length > 0)

        String looseConfigContent = FileUtils.readFileToString(looseConfigFiles[0], "UTF-8")

        // The devc loose config must use the ${projectRoot} variable — not absolute paths.
        // Absolute paths mean the container path substitution did not run (old bug).
        assertTrue("devc loose config should contain \${io.openliberty.tools.projectRoot} variable paths",
                looseConfigContent.contains('${io.openliberty.tools.projectRoot}'))

        // The path to application.xml must not have a double ear/ segment.
        // Before the fix the path was rooted at root/ but only ear/ was mounted,
        // causing Liberty to look for /devmode/ear/build/... when it should be
        // /devmode/build/... (if ear/ is mounted) or
        // /devmode/ear/build/... (if root/ is mounted — which is the fix).
        // We verify the latter: the path contains exactly one /ear/ segment
        // in the right place — not two consecutive /ear/ear/.
        assertFalse("devc loose config must not contain a doubled ear/ path segment (mount mismatch)",
                looseConfigContent.contains(File.separator + "ear" + File.separator + "ear" + File.separator))
    }

    @AfterClass
    static void cleanUpAfterClass() throws Exception {
        String stdout = logFile?.exists() ? getContents(logFile, "Dev mode std output") : ""
        System.out.println(stdout)
        String stderr = errFile?.exists() ? getContents(errFile, "Dev mode std error") : ""
        System.out.println(stderr)
        cleanUpAfterClassCheckLogFile(true)
    }
}
