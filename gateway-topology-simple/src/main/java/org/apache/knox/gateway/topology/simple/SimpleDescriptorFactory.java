/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.simple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;


public class SimpleDescriptorFactory {

    /**
     * Create a SimpleDescriptor from the specified file.
     *
     * @param path The path to the file.
     * @return A SimpleDescriptor based on the contents of the file.
     *
     * @throws IOException exception on parsing the path
     */
    public static SimpleDescriptor parse(String path) throws IOException {
        SimpleDescriptor sd;

        if (path.endsWith(".json")) {
            sd = parseJSON(path);
        } else if (path.endsWith(".yml") || path.endsWith(".yaml")) {
            sd = parseYAML(path);
        } else {
           throw new IllegalArgumentException("Unsupported simple descriptor format: " + path.substring(path.lastIndexOf('.')));
        }

        return sd;
    }


    static SimpleDescriptor parseJSON(String path) throws IOException {
        final ObjectMapper mapper = new ObjectMapper();
        SimpleDescriptorImpl sd = mapper.readValue(new File(path), SimpleDescriptorImpl.class);
        if (sd != null) {
            sd.setName(FilenameUtils.getBaseName(path));
        }
        return sd;
    }


    static SimpleDescriptor parseYAML(String path) throws IOException {
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        SimpleDescriptorImpl sd = mapper.readValue(new File(path), SimpleDescriptorImpl.class);
        if (sd != null) {
            sd.setName(FilenameUtils.getBaseName(path));
        }
        return sd;
    }

}
