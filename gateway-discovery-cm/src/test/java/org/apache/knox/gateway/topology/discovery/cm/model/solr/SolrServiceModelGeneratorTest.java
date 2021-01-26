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
package org.apache.knox.gateway.topology.discovery.cm.model.solr;

import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.AbstractServiceModelGeneratorTest;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.apache.knox.gateway.topology.discovery.cm.ServiceModel.QUALIFYING_SERVICE_PARAM_PREFIX;
import static org.apache.knox.gateway.topology.discovery.cm.model.solr.SolrServiceModelGenerator.DISCOVERY_SERVICE_DISPLAY_NAME;
import static org.apache.knox.gateway.topology.discovery.cm.model.solr.SolrServiceModelGenerator.DISCOVERY_SERVICE_NAME;
import static org.junit.Assert.assertEquals;

public class SolrServiceModelGeneratorTest extends AbstractServiceModelGeneratorTest {

  @Test
  public void testServiceModelMetadata() {
    final Map<String, String> serviceConfig = new HashMap<>();
    serviceConfig.put(SolrServiceModelGenerator.USE_SSL, "false");
    final Map<String, String> roleConfig = new HashMap<>();
    roleConfig.put(SolrServiceModelGenerator.HTTP_PORT, "2468");
    roleConfig.put(SolrServiceModelGenerator.HTTPS_PORT, "1357");

    ServiceModel generated = createServiceModel(serviceConfig, roleConfig);
    validateServiceModel(generated, serviceConfig, roleConfig);

    // Validate model metadata properties
    final String serviceNameQualifier = QUALIFYING_SERVICE_PARAM_PREFIX + DISCOVERY_SERVICE_NAME;
    final String displayNameQualifier = QUALIFYING_SERVICE_PARAM_PREFIX + DISCOVERY_SERVICE_DISPLAY_NAME;
    Map<String, String> modelProps = generated.getQualifyingServiceParams();
    assertEquals("Expected two service model properties", 2, modelProps.size());
    assertEquals("Expected " + serviceNameQualifier + " model property.",
                 getServiceType() + "-1",
                 modelProps.get(serviceNameQualifier));
    assertEquals("Expected " + displayNameQualifier + " model property.",
                 "null",
                 modelProps.get(displayNameQualifier));
  }

  @Override
  protected String getServiceType() {
    return SolrServiceModelGenerator.SERVICE_TYPE;
  }

  @Override
  protected String getRoleType() {
    return SolrServiceModelGenerator.ROLE_TYPE;
  }

  @Override
  protected ServiceModelGenerator newGenerator() {
    return new SolrServiceModelGenerator();
  }

}
