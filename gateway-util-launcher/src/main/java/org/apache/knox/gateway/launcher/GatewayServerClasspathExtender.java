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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GatewayServerClasspathExtender {

    private static final String CLASSPATH_PREPEND_PROPERTY = "gateway.server.prepend.classpath";
    private static final String CLASSPATH_APPEND_PROPERTY = "gateway.server.append.classpath";
    private static final String CLASSPATH_PREPEND_PROPERTY_PATTERN = "<property>\\s*<name>" + CLASSPATH_PREPEND_PROPERTY + "</name>\\s*<value>(.*?)</value>\\s*</property>";
    private static final String CLASSPATH_APPEND_PROPERTY_PATTERN = "<property>\\s*<name>" + CLASSPATH_APPEND_PROPERTY + "</name>\\s*<value>(.*?)</value>\\s*</property>";
    private static final String CONFIG_FILE = "gateway-site.xml";
    private static final String CONFIG_PATH = "../conf/" + CONFIG_FILE;
    private static final String CLASS_PATH_PROPERTY = "class.path";
    private static final String MAIN_CLASS_PROPERTY = "main.class";
    private static final String GATEWAY_SERVER_MAIN_CLASS = "org.apache.knox.gateway.GatewayServer";
    private static final String[] CLASS_PATH_DELIMITERS = new String[]{",", ";"};

    private final File base;
    private final Pattern prependPattern = Pattern.compile(CLASSPATH_PREPEND_PROPERTY_PATTERN, Pattern.DOTALL);
    private final Pattern appendPattern = Pattern.compile(CLASSPATH_APPEND_PROPERTY_PATTERN, Pattern.DOTALL);

    public GatewayServerClasspathExtender(File base) {
        this.base = base;
    }

    public void extendClassPathProperty(Properties properties) throws IOException {
        Path configFilePath = Paths.get(base.getPath(), CONFIG_PATH);
        if (GATEWAY_SERVER_MAIN_CLASS.equals(properties.getProperty(MAIN_CLASS_PROPERTY)) && Files.isReadable(configFilePath)) {
            String configContent = new String(Files.readAllBytes(configFilePath), StandardCharsets.UTF_8);
            prependClassPathProperty(configContent, properties);
            appendClassPathProperty(configContent, properties);
        }
    }

    private void prependClassPathProperty(String configContent, Properties properties) {
        String prepend = getPropertyFromConfigFile(prependPattern, configContent);
        if (prepend != null) {
            StringBuilder newClassPath = new StringBuilder(prepend);
            if (!endsWithDelimiter(newClassPath.toString())) {
                newClassPath.append(CLASS_PATH_DELIMITERS[1]);
            }
            newClassPath.append(properties.getProperty(CLASS_PATH_PROPERTY));
            properties.setProperty(CLASS_PATH_PROPERTY, newClassPath.toString());
        }
    }

    private void appendClassPathProperty(String configContent, Properties properties) {
        String appendage = getPropertyFromConfigFile(appendPattern, configContent);
        if (appendage != null) {
            StringBuilder newClassPath = new StringBuilder(properties.getProperty(CLASS_PATH_PROPERTY));
            if (!startsWithDelimiter(appendage)) {
                newClassPath.append(CLASS_PATH_DELIMITERS[1]);
            }
            newClassPath.append(appendage);
            properties.setProperty(CLASS_PATH_PROPERTY, newClassPath.toString());
        }
    }

    private String getPropertyFromConfigFile(Pattern pattern, String configContent) {
        String property = null;
        final Matcher matcher = pattern.matcher(configContent);
        if (matcher.find() && !matcher.group(1).trim().isEmpty()) {
            property = matcher.group(1).trim();
        }
        return property;
    }

    private boolean endsWithDelimiter(String path) {
        return Arrays.stream(CLASS_PATH_DELIMITERS).anyMatch(path::endsWith);
    }

    private boolean startsWithDelimiter(String path) {
        return Arrays.stream(CLASS_PATH_DELIMITERS).anyMatch(path::startsWith);
    }

}
