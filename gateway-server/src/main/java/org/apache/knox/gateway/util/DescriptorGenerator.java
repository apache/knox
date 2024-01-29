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
package org.apache.knox.gateway.util;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FilenameUtils;
import org.apache.knox.gateway.model.DescriptorConfiguration;
import org.apache.knox.gateway.model.Topology;

public class DescriptorGenerator {
  private static final ObjectMapper mapper = new ObjectMapper();
  private final String descriptorName;
  private final String providerName;
  private final String serviceName;
  private final ServiceUrls serviceUrls;

  static {
    /* skip printing out null fields */
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
  }

  public DescriptorGenerator(String descriptorName, String providerName, String serviceName, ServiceUrls serviceUrls) {
    this.descriptorName = descriptorName;
    this.providerName = providerName;
    this.serviceName = serviceName.toUpperCase(Locale.ROOT);
    this.serviceUrls = serviceUrls;
  }

  public void saveDescriptor(File outputDir, boolean forceOverwrite) {
    File outputFile = new File(outputDir, descriptorName);
    if (outputFile.exists() && !forceOverwrite) {
      throw new IllegalArgumentException(outputFile + "already exists");
    }
    DescriptorConfiguration descriptor = new DescriptorConfiguration();
    descriptor.setName(FilenameUtils.removeExtension(descriptorName));
    descriptor.setProviderConfig(FilenameUtils.removeExtension(providerName));
    Topology.Service service = new Topology.Service();
    service.setRole(serviceName);
    service.setUrls(serviceUrls.toList());
    descriptor.setServices(Arrays.asList(service));
    try {
      mapper.writerWithDefaultPrettyPrinter()
              .writeValue(outputFile, descriptor);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
