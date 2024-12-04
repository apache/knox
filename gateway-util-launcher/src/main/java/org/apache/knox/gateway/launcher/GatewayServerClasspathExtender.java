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

    private static final String CLASSPATH_EXTENSION_PROPERTY = "gateway.server.classpath.extension";
    private static final String CLASSPATH_PROPERTY_PATTERN = "<property>\\s*<name>" + CLASSPATH_EXTENSION_PROPERTY + "</name>\\s*<value>(.*?)</value>\\s*</property>";
    private static final String CONFIG_FILE = "gateway-site.xml";
    private static final String CONFIG_PATH = "../conf/" + CONFIG_FILE;
    private static final String CLASS_PATH_PROPERTY = "class.path";
    private static final String MAIN_CLASS_PROPERTY = "main.class";
    private static final String GATEWAY_SERVER_MAIN_CLASS = "org.apache.knox.gateway.GatewayServer";
    private static final String[] CLASS_PATH_DELIMITERS = new String[]{",", ";"};

    private final File base;
    private final Pattern pattern = Pattern.compile(CLASSPATH_PROPERTY_PATTERN, Pattern.DOTALL);

    public GatewayServerClasspathExtender(File base) {
        this.base = base;
    }

    public void extendClassPathProperty(Properties properties) throws IOException {
        Path configFilePath = Paths.get(base.getPath(), CONFIG_PATH);
        if (GATEWAY_SERVER_MAIN_CLASS.equals(properties.getProperty(MAIN_CLASS_PROPERTY)) && Files.isReadable(configFilePath)) {
            String configContent = new String(Files.readAllBytes(configFilePath), StandardCharsets.UTF_8);
            extractExtensionPathIntoProperty(configContent, properties);
        }
    }

    protected void extractExtensionPathIntoProperty(String configContent, Properties properties) {
        final Matcher matcher = pattern.matcher(configContent);

        if (matcher.find()) {
            StringBuilder newClassPath = new StringBuilder(matcher.group(1).trim());
            if (newClassPath.length() > 0) {
                if (!endsWithDelimiter(newClassPath.toString())) {
                    newClassPath.append(CLASS_PATH_DELIMITERS[1]);
                }
                newClassPath.append(properties.getProperty(CLASS_PATH_PROPERTY));
                properties.setProperty(CLASS_PATH_PROPERTY, newClassPath.toString());
            }
        }
    }

    private boolean endsWithDelimiter(String path) {
        return Arrays.stream(CLASS_PATH_DELIMITERS).anyMatch(path::endsWith);
    }
}
