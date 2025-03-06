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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class GatewayServerClasspathExtenderTest {

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
        Properties properties = this.getProperties("classpath", "org.apache.knox.gateway.GatewayServer");
        GatewayServerClasspathExtender gatewayServerClasspathExtender = new GatewayServerClasspathExtender(confDir.toFile());

        String configContent = this.getConfigContent("/new/classp/*", "/appendage/*");
        Files.write(configFilePath, configContent.getBytes(StandardCharsets.UTF_8));
        gatewayServerClasspathExtender.extendClassPathProperty(properties);

        assertEquals("/new/classp/*;classpath;/appendage/*", properties.getProperty("class.path"));
    }

    @Test
    public void extendClassPathPropertyDifferentMainClassTest() throws IOException {
        Properties properties = this.getProperties("classpath", "org.apache.knox.gateway.KnoxCLI");
        GatewayServerClasspathExtender gatewayServerClasspathExtender = new GatewayServerClasspathExtender(confDir.toFile());

        String configContent = this.getConfigContent("/new/classp/*", "/appendage/*");
        Files.write(configFilePath, configContent.getBytes(StandardCharsets.UTF_8));
        gatewayServerClasspathExtender.extendClassPathProperty(properties);

        assertEquals("classpath", properties.getProperty("class.path"));
    }

    @Test
    public void extendClassPathPropertyWithDelimitersTest() throws IOException {
        Properties properties = this.getProperties("classpath", "org.apache.knox.gateway.GatewayServer");
        GatewayServerClasspathExtender gatewayServerClasspathExtender = new GatewayServerClasspathExtender(confDir.toFile());

        String configContent = this.getConfigContent("/new/classp/*;", ";/appendage/*");
        Files.write(configFilePath, configContent.getBytes(StandardCharsets.UTF_8));
        gatewayServerClasspathExtender.extendClassPathProperty(properties);

        assertEquals("/new/classp/*;classpath;/appendage/*", properties.getProperty("class.path"));
    }

    @Test
    public void extendClassPathPropertyWhitespaceTest() throws IOException {
        Properties properties = this.getProperties("classpath", "org.apache.knox.gateway.GatewayServer");
        GatewayServerClasspathExtender gatewayServerClasspathExtender = new GatewayServerClasspathExtender(confDir.toFile());

        String configContent = this.getConfigContent(" /new/classp/*; ", " ;/appendage/* ");
        Files.write(configFilePath, configContent.getBytes(StandardCharsets.UTF_8));
        gatewayServerClasspathExtender.extendClassPathProperty(properties);

        assertEquals("/new/classp/*;classpath;/appendage/*", properties.getProperty("class.path"));
    }

    @Test
    public void extendClassPathPropertyMultipleTest() throws IOException {
        Properties properties = this.getProperties("classpath", "org.apache.knox.gateway.GatewayServer");
        GatewayServerClasspathExtender gatewayServerClasspathExtender = new GatewayServerClasspathExtender(confDir.toFile());

        String configContent = this.getConfigContent("/new/classp/*,../classp", "/appendage/*,/appendage2/*.jar");
        Files.write(configFilePath, configContent.getBytes(StandardCharsets.UTF_8));
        gatewayServerClasspathExtender.extendClassPathProperty(properties);

        assertEquals("/new/classp/*,../classp;classpath;/appendage/*,/appendage2/*.jar", properties.getProperty("class.path"));
    }

    @Test
    public void extendClassPathPropertyEmptyTest() throws IOException {
        Properties properties = this.getProperties("classpath", "org.apache.knox.gateway.GatewayServer");
        GatewayServerClasspathExtender gatewayServerClasspathExtender = new GatewayServerClasspathExtender(confDir.toFile());

        String configContent = this.getConfigContent("", "");
        Files.write(configFilePath, configContent.getBytes(StandardCharsets.UTF_8));
        gatewayServerClasspathExtender.extendClassPathProperty(properties);

        assertEquals("classpath", properties.getProperty("class.path"));
    }

    @Test
    public void extendClassPathPropertyEmptyWhitespaceTest() throws IOException {
        Properties properties = this.getProperties("classpath", "org.apache.knox.gateway.GatewayServer");
        GatewayServerClasspathExtender gatewayServerClasspathExtender = new GatewayServerClasspathExtender(confDir.toFile());

        String configContent = this.getConfigContent(" ", " ");
        Files.write(configFilePath, configContent.getBytes(StandardCharsets.UTF_8));
        gatewayServerClasspathExtender.extendClassPathProperty(properties);

        assertEquals("classpath", properties.getProperty("class.path"));
    }

    @Test
    public void extendClassPathPropertyOnlyPrepend() throws IOException {
        Properties properties = this.getProperties("classpath", "org.apache.knox.gateway.GatewayServer");
        GatewayServerClasspathExtender gatewayServerClasspathExtender = new GatewayServerClasspathExtender(confDir.toFile());

        String configContent = this.getConfigContent("/new/classp/*,../classp", null);
        Files.write(configFilePath, configContent.getBytes(StandardCharsets.UTF_8));
        gatewayServerClasspathExtender.extendClassPathProperty(properties);

        assertEquals("/new/classp/*,../classp;classpath", properties.getProperty("class.path"));
    }

    @Test
    public void extendClassPathPropertyOnlyAppend() throws IOException {
        Properties properties = this.getProperties("classpath", "org.apache.knox.gateway.GatewayServer");
        GatewayServerClasspathExtender gatewayServerClasspathExtender = new GatewayServerClasspathExtender(confDir.toFile());

        String configContent = this.getConfigContent(null, "/appendage/*,/appendage2/*.jar");
        Files.write(configFilePath, configContent.getBytes(StandardCharsets.UTF_8));
        gatewayServerClasspathExtender.extendClassPathProperty(properties);

        assertEquals("classpath;/appendage/*,/appendage2/*.jar", properties.getProperty("class.path"));
    }

    @Test
    public void extendClassPathPropertyNoExtension() throws IOException {
        Properties properties = this.getProperties("classpath", "org.apache.knox.gateway.GatewayServer");
        GatewayServerClasspathExtender gatewayServerClasspathExtender = new GatewayServerClasspathExtender(confDir.toFile());

        String configContent = this.getConfigContent(null, null);
        Files.write(configFilePath, configContent.getBytes(StandardCharsets.UTF_8));
        gatewayServerClasspathExtender.extendClassPathProperty(properties);

        assertEquals("classpath", properties.getProperty("class.path"));
    }

    @SuppressWarnings("PMD.InsufficientStringBufferDeclaration")
    private String getConfigContent(String prependValue, String appendValue) {
        StringBuilder content = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><configuration>");

        if(prependValue != null) {
            content.append("<configuration><property><name>gateway.server.prepend.classpath</name><value>");
            content.append(prependValue);
            content.append("</value></property></configuration>");
        }

        if(appendValue != null) {
            content.append("<configuration><property><name>gateway.server.append.classpath</name><value>");
            content.append(appendValue);
            content.append("</value></property></configuration>");
        }
        content.append("</configuration>");
        return content.toString();
    }

    private Properties getProperties(String classPath, String mainClass) {
        Properties properties = new Properties();
        properties.setProperty("class.path", classPath);
        properties.setProperty("main.class", mainClass);
        return properties;
    }
}
