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
package org.apache.knox.gateway.topology.discovery.cm.model.opensearch;

import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.AbstractServiceModelGeneratorTest;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class OpenSearchDashboardsServiceModelGeneratorTest extends AbstractServiceModelGeneratorTest {

  @Test
  @Override
  public void testHandles() {
    final Map<String, String> roleConfig = new HashMap<>();
    roleConfig.put(OpenSearchDashboardsServiceModelGenerator.ENABLE_SSO_CONFIG, "true");
    assertTrue(doTestHandles(newGenerator(), getServiceType(), Collections.emptyMap(), getRoleType(), roleConfig));
  }

  @Test
  public void testHandlesSsoDisabled() {
    final Map<String, String> roleConfig = new HashMap<>();
    roleConfig.put(OpenSearchDashboardsServiceModelGenerator.ENABLE_SSO_CONFIG, "false");
    assertFalse(doTestHandles(newGenerator(), getServiceType(), Collections.emptyMap(), getRoleType(), roleConfig));
  }

  @Test
  public void testHandlesSsoConfigMissing() {
    assertFalse(doTestHandles(newGenerator(), getServiceType(), Collections.emptyMap(), getRoleType(), Collections.emptyMap()));
  }

  @Test
  public void testServiceModelMetadata() {
    final Map<String, String> serviceConfig = Collections.emptyMap();
    final Map<String, String> roleConfig = new HashMap<>();
    roleConfig.put("ssl_enabled", "false");
    roleConfig.put(OpenSearchDashboardsServiceModelGenerator.DASHBOARDS_PORT_CONFIG, "5601");

    validateServiceModel(createServiceModel(serviceConfig, roleConfig), serviceConfig, roleConfig);
  }

  @Test
  public void testServiceModelMetadataSslEnabled() {
    final Map<String, String> serviceConfig = Collections.emptyMap();
    final Map<String, String> roleConfig = new HashMap<>();
    roleConfig.put("ssl_enabled", "true");
    roleConfig.put(OpenSearchDashboardsServiceModelGenerator.DASHBOARDS_PORT_CONFIG, "5601");

    final ServiceModel model = createServiceModel(serviceConfig, roleConfig);
    assertNotNull(model);
    // When SSL is enabled the URL scheme must be https
    assertTrue("Expected https URL when ssl_enabled=true, got: " + model.getServiceUrl(),
               model.getServiceUrl().startsWith("https://"));
  }

  @Test
  public void testServiceNameIsOpenSearchDashboards() {
    assertEquals(OpenSearchDashboardsServiceModelGenerator.OPENSEARCH_DASHBOARDS_SERVICE_NAME,
                 newGenerator().getService());
  }

  @Test
  public void testModelTypeIsUi() {
    assertEquals(ServiceModel.Type.UI, newGenerator().getModelType());
  }

  @Override
  protected String getServiceType() {
    return OpenSearchDashboardsServiceModelGenerator.SERVICE_TYPE;
  }

  @Override
  protected String getRoleType() {
    return OpenSearchDashboardsServiceModelGenerator.OPENSEARCH_DASHBOARD_ROLE_TYPE;
  }

  @Override
  protected ServiceModelGenerator newGenerator() {
    return new OpenSearchDashboardsServiceModelGenerator();
  }
}
