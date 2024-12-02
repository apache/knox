/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.launcher;


import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class ExtenderTest {

    private Path tempDir;
    private Path confDir;
    private Path configFilePath;

    @Before
    public void setupDirs() throws IOException {
        tempDir = Files.createTempDirectory("cp_extender_test");
        confDir = Files.createDirectory(tempDir.resolve("conf"));
        configFilePath = confDir.resolve("gateway-site.xml");
    }

    @After
    public void cleanUpDirs() throws IOException {
        Files.deleteIfExists(configFilePath);
        Files.deleteIfExists(confDir);
        Files.deleteIfExists(tempDir);
    }

    @Test
    public void extendClassPathPropertyTest() throws IOException {
        Properties properties = new Properties();
        properties.setProperty("class.path", "classpath");
        properties.setProperty("main.class", "org.apache.knox.gateway.GatewayServer");
        Extender extender = new Extender(confDir.toFile(), properties);

        String configContent = this.getConfigContent("/new/classp/*");
        Files.write(configFilePath, configContent.getBytes(StandardCharsets.UTF_8));
        extender.extendClassPathProperty();

        assertEquals("/new/classp/*;classpath", properties.getProperty("class.path"));
    }

    @Test
    public void extendClassPathPropertyDifferentMainClassTest() throws IOException {
        Properties properties = new Properties();
        properties.setProperty("class.path", "classpath");
        properties.setProperty("main.class", "org.apache.knox.gateway.KnoxCLI");
        Extender extender = new Extender(confDir.toFile(), properties);

        String configContent = this.getConfigContent("/new/classp/*");
        Files.write(configFilePath, configContent.getBytes(StandardCharsets.UTF_8));
        extender.extendClassPathProperty();

        assertEquals("classpath", properties.getProperty("class.path"));
    }

    @Test
    public void extractExtensionPathIntoPropertyNoDelimTest() {
        Properties properties = new Properties();
        properties.setProperty("class.path", "classpath");
        Extender extender = new Extender(null, properties);

        String configContent = this.getConfigContent("/new/classp/*");
        extender.extractExtensionPathIntoProperty(configContent);

        assertEquals("/new/classp/*;classpath", properties.getProperty("class.path"));
    }

    @Test
    public void extractExtensionPathIntoPropertyXMLFormatTest() {
        Properties properties = new Properties();
        properties.setProperty("class.path", "classpath");
        Extender extender = new Extender(null, properties);

        String configContent = this.getConfigContent("/new/classp/*;");
        extender.extractExtensionPathIntoProperty(configContent);

        assertEquals("/new/classp/*;classpath", properties.getProperty("class.path"));
    }

    @Test
    public void extractExtensionPathIntoPropertyWhitespaceTest() {
        Properties properties = new Properties();
        properties.setProperty("class.path", "classpath");
        Extender extender = new Extender(null, properties);

        String configContent = this.getConfigContent(" /new/classp/*; ");
        extender.extractExtensionPathIntoProperty(configContent);

        assertEquals("/new/classp/*;classpath", properties.getProperty("class.path"));
    }

    @Test
    public void extractExtensionPathIntoPropertyMultipleTest() {
        Properties properties = new Properties();
        properties.setProperty("class.path", "classpath");
        Extender extender = new Extender(null, properties);

        String configContent = this.getConfigContent("/new/classp/*,../classp");
        extender.extractExtensionPathIntoProperty(configContent);

        assertEquals("/new/classp/*,../classp;classpath", properties.getProperty("class.path"));
    }

    @Test
    public void extractExtensionPathIntoPropertyEmptyTest() {
        Properties properties = new Properties();
        properties.setProperty("class.path", "classpath");
        Extender extender = new Extender(null, properties);

        String configContent = this.getConfigContent("");
        extender.extractExtensionPathIntoProperty(configContent);

        assertEquals("classpath", properties.getProperty("class.path"));
    }

    @Test
    public void extractExtensionPathIntoPropertyEmptyWhitespaceTest() {
        Properties properties = new Properties();
        properties.setProperty("class.path", "classpath");
        Extender extender = new Extender(null, properties);

        String configContent = this.getConfigContent(" ");
        extender.extractExtensionPathIntoProperty(configContent);

        assertEquals("classpath", properties.getProperty("class.path"));
    }

    @Test
    public void extractExtensionPathIntoPropertyNoConfigTest() throws IOException {
        Properties properties = new Properties();
        properties.setProperty("class.path", "classpath");
        Extender extender = new Extender(null, properties);

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("gateway-site-test.xml").getFile());

        extender.extractExtensionPathIntoProperty(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));

        assertEquals("classpath", properties.getProperty("class.path"));
    }

    private String getConfigContent(String extensionValue) {
        return "<configuration>\n" +
                "    <property>\n" +
                "        <name>gateway.server.classpath.extension</name>\n" +
                "        <value>" + extensionValue + "</value>\n" +
                "    </property>\n" +
                "</configuration>";
    }
}
