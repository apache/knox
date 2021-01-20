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
package org.apache.knox.gateway.topology.discovery.cm.model.hdfs;

import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.AbstractServiceModelGeneratorTest;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class NameNodeServiceModelGeneratorTest extends AbstractServiceModelGeneratorTest {

  @Test
  public void testServiceModelMetadataWithNameService() {
    final Map<String, String> serviceConfig = Collections.emptyMap();
    final Map<String, String> roleConfig = new HashMap<>();
    roleConfig.put(NameNodeServiceModelGenerator.AUTOFAILOVER_ENABLED, "true");
    roleConfig.put(NameNodeServiceModelGenerator.NN_NAMESERVICE, "myService");
    roleConfig.put(NameNodeServiceModelGenerator.NN_PORT, "12345");

    ServiceModel generated = createServiceModel(serviceConfig, roleConfig);
    validateServiceModel(generated, serviceConfig, roleConfig);

    // Validate model metadata properties
    Map<String, String> modelProps = generated.getQualifyingServiceParams();
    assertEquals("Expected one service model properties", 1, modelProps.size());
    assertEquals("Expected " + NameNodeServiceModelGenerator.DISCOVERY_NAMESERVICE + " model property.",
                 roleConfig.get(NameNodeServiceModelGenerator.NN_NAMESERVICE),
                 modelProps.get(NameNodeServiceModelGenerator.DISCOVERY_NAMESERVICE));
  }

  @Test
  public void testServiceModelMetadataWithNNPort() {
    final Map<String, String> serviceConfig = Collections.emptyMap();
    final Map<String, String> roleConfig = new HashMap<>();
    roleConfig.put(NameNodeServiceModelGenerator.AUTOFAILOVER_ENABLED, "false");
    roleConfig.put(NameNodeServiceModelGenerator.NN_PORT, "12345");

    validateServiceModel(createServiceModel(serviceConfig, roleConfig), serviceConfig, roleConfig);
  }


  @Override
  protected String getServiceType() {
    return NameNodeServiceModelGenerator.SERVICE_TYPE;
  }

  @Override
  protected String getRoleType() {
    return NameNodeServiceModelGenerator.ROLE_TYPE;
  }

  @Override
  protected ServiceModelGenerator newGenerator() {
    return new NameNodeServiceModelGenerator();
  }
}
