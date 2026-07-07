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

import org.junit.Assert
import org.junit.BeforeClass
import org.junit.AfterClass
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.NodeList

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

/**
 * Integration test for the deploy task when sibling projects have an explicit
 * {@code sourceSets} block.
 *
 * The {@code multi-module-loose-ear-with-sourcesets-test} resource differs from
 * {@code multi-module-loose-ear-pages-test} in one key way: the WAR submodule
 * declares an explicit {@code sourceSets { main { java { srcDirs = [...] } } }}
 * block. This causes {@code siblingProject.extensions.findByType(SourceSetContainer)}
 * in DeployTask.groovy to return a non-null container, and
 * {@code siblingMainSourceSet.getOutput().getClassesDirs()} to return the actual
 * output directories resolved from the declared source set.
 *
 * Before the fix, dynamic property access ({@code siblingProject.sourceSets.main})
 * on Gradle 9 would throw when the java plugin was absent. After the fix the same
 * findByType/findByName pattern must also work correctly when the source set IS
 * present — this test covers that non-null path.
 */
class TestMultiModuleLooseEarWithSourceSets extends AbstractIntegrationTest {

    static File resourceDir = new File("build/resources/test/multi-module-loose-ear-with-sourcesets-test")
    static File buildDir    = new File(integTestDir, "/multi-module-loose-ear-with-sourcesets-deploy-test")
    static String buildFilename = "build.gradle"

    @BeforeClass
    static void setup() {
        createDir(buildDir)
        createTestProject(buildDir, resourceDir, buildFilename)
        try {
            runTasks(buildDir, 'deploy')
        } catch (Exception e) {
            throw new AssertionError("Fail on task deploy in @BeforeClass.", e)
        }
    }

    @AfterClass
    static void tearDown() throws Exception {
        runTasks(buildDir, 'libertyStop')
    }

    /**
     * Verify the loose EAR XML is generated when sibling modules have sourceSets.
     */
    @Test
    void test_loose_config_file_exists() {
        Assert.assertTrue('looseApplication config file was not created',
                new File("build/testBuilds/multi-module-loose-ear-with-sourcesets-deploy-test/ear/build/wlp/usr/servers/ejbServer/apps/ejb-ear-1.0-SNAPSHOT.ear.xml").exists())
    }

    /**
     * Verify the loose EAR XML contains the compiled classes directory reported by the
     * WAR module's explicit sourceSets.main.output.classesDirs — the non-null path in
     * DeployTask.groovy's siblingMainSourceSet.getOutput().getClassesDirs().
     *
     * Expected structure (abbreviated):
     * <pre>
     * &lt;archive&gt;
     *   &lt;archive targetInArchive="/ejb-war-1.0-SNAPSHOT.war"&gt;
     *     &lt;dir sourceOnDisk=".../war/build/classes/java/main" targetInArchive="/WEB-INF/classes"/&gt;
     *     ...
     *     &lt;archive targetInArchive="/WEB-INF/lib/ejb-jar-1.0-SNAPSHOT.jar"&gt;
     *       &lt;dir sourceOnDisk=".../jar/build/classes/java/main" targetInArchive="/"/&gt;
     *     &lt;/archive&gt;
     *   &lt;/archive&gt;
     * &lt;/archive&gt;
     * </pre>
     */
    @Test
    void test_loose_config_sourceset_classes_dirs_present() {
        File looseXml = new File(
                "build/testBuilds/multi-module-loose-ear-with-sourcesets-deploy-test/ear/build/wlp/usr/servers/ejbServer/apps/ejb-ear-1.0-SNAPSHOT.ear.xml")
        FileInputStream input = new FileInputStream(looseXml)

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance()
        factory.setIgnoringComments(true)
        factory.setCoalescing(true)
        factory.setIgnoringElementContentWhitespace(true)
        factory.setValidating(false)
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false)
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        DocumentBuilder builder = factory.newDocumentBuilder()
        Document doc = builder.parse(input)

        XPath xPath = XPathFactory.newInstance().newXPath()

        // There should be at least one top-level <archive> (the WAR)
        String expression = "/archive/archive"
        NodeList archives = (NodeList) xPath.compile(expression).evaluate(doc, XPathConstants.NODESET)
        Assert.assertTrue("Expected at least one <archive> child for the WAR", archives.getLength() >= 1)

        // Collect all sourceOnDisk values from <dir> elements inside nested archives
        expression = "/archive/archive/dir"
        NodeList dirs = (NodeList) xPath.compile(expression).evaluate(doc, XPathConstants.NODESET)
        Assert.assertTrue("Expected <dir> elements inside the WAR archive", dirs.getLength() > 0)

        // The WAR module declares a NON-DEFAULT source dir: src/war/java (not src/main/java).
         String warClassesDir = "multi-module-loose-ear-with-sourcesets-deploy-test/war/build/classes/java/main"
        boolean foundWarClasses = false
        for (int i = 0; i < dirs.getLength(); i++) {
            String src = dirs.item(i).getAttributes().getNamedItem("sourceOnDisk").getNodeValue()
            if (src.contains(warClassesDir.replace('/', File.separator))) {
                foundWarClasses = true
                break
            }
        }
        Assert.assertTrue(
                "Loose EAR XML must reference WAR classes dir resolved from explicit sourceSets: " + warClassesDir,
                foundWarClasses)

        // The JAR module is a sibling dependency inside the WAR's <archive>.
        // DeployTask resolves its classes via siblingMainSourceSet.getOutput().getClassesDirs().
        String jarClassesDir = "multi-module-loose-ear-with-sourcesets-deploy-test/jar/build/classes/java/main"
        expression = "/archive/archive/archive/dir"
        NodeList jarDirs = (NodeList) xPath.compile(expression).evaluate(doc, XPathConstants.NODESET)
        boolean foundJarClasses = false
        for (int i = 0; i < jarDirs.getLength(); i++) {
            String src = jarDirs.item(i).getAttributes().getNamedItem("sourceOnDisk").getNodeValue()
            if (src.contains(jarClassesDir.replace('/', File.separator))) {
                foundJarClasses = true
                break
            }
        }
        Assert.assertTrue(
                "Loose EAR XML must reference JAR classes dir resolved from explicit sourceSets: " + jarClassesDir,
                foundJarClasses)
    }
}
