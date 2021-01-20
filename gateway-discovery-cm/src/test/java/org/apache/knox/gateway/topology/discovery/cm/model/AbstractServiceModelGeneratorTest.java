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
package org.apache.knox.gateway.topology.discovery.cm.model;

import com.cloudera.api.swagger.client.ApiException;
import org.apache.knox.gateway.topology.discovery.cm.AbstractCMDiscoveryTest;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModelGenerator;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public abstract class AbstractServiceModelGeneratorTest extends AbstractCMDiscoveryTest {

  protected abstract String getServiceType();

  protected abstract String getRoleType();

  protected abstract ServiceModelGenerator newGenerator();

  @Test
  public void testHandles() {
    assertTrue(doTestHandles(newGenerator(), getServiceType(), getRoleType(), Collections.emptyMap()));
  }

  @Test
  public void testHandlesWrongRoleType() {
    assertFalse(doTestHandles(newGenerator(), getServiceType(), "INCORRECT_ROLE_TYPE", Collections.emptyMap()));
  }

  @Test
  public void testHandlesWrongServiceType() {
    assertFalse(doTestHandles(newGenerator(), "INCORRECT_SERVICE_TYPE", getRoleType(), Collections.emptyMap()));
  }


  protected boolean doTestHandles(final ServiceModelGenerator generator,
                                  final String                serviceType,
                                  final String                roleType,
                                  final Map<String, String>   roleProps) {
    return doTestHandles(generator,
                         serviceType,
                         Collections.emptyMap(),
                         roleType,
                         roleProps);
  }

  protected boolean doTestHandles(final ServiceModelGenerator generator,
                                  final String                serviceType,
                                  final Map<String, String>   serviceConfig,
                                  final String                roleType,
                                  final Map<String, String>   roleProps) {
    return generator.handles(createApiServiceMock(serviceType),
                             createApiServiceConfigMock(serviceConfig),
                             createApiRoleMock(roleType),
                             createApiConfigListMock(roleProps)).handled();
  }


  protected ServiceModel createServiceModel(Map<String, String> serviceConfig, Map<String, String> roleConfig) {
    ServiceModel model = null;
    try {
      model = newGenerator().generateService(createApiServiceMock(getServiceType()),
                                             createApiServiceConfigMock(serviceConfig),
                                             createApiRoleMock(getRoleType()),
                                             createApiConfigListMock(roleConfig));
    } catch (ApiException e) {
      fail(e.getMessage());
    }

    return model;
  }

  protected void validateServiceModel(ServiceModel        candidate,
                                      Map<String, String> serviceConfig,
                                      Map<String, String> roleConfig,
                                      boolean             validateCounts) {

    assertNotNull(candidate);

    // Validate the service configuration
    Map<String, String> modelServiceProps = candidate.getServiceProperties();
    if (validateCounts) {
      assertEquals(serviceConfig.size(), modelServiceProps.size());
    }
    for (Map.Entry<String, String> serviceProp : serviceConfig.entrySet()) {
      assertTrue(modelServiceProps.containsKey(serviceProp.getKey()));
      assertEquals(serviceConfig.get(serviceProp.getKey()), modelServiceProps.get(serviceProp.getKey()));
    }

    // Validate the role configuration
    Map<String, String> modelRoleProperties = candidate.getRoleProperties().get(getRoleType());
    if (validateCounts) {
      assertEquals(roleConfig.size(), modelRoleProperties.size());
    }
    for (Map.Entry<String, String> roleProp : roleConfig.entrySet()) {
      assertTrue(modelRoleProperties.containsKey(roleProp.getKey()));
      assertEquals(roleConfig.get(roleProp.getKey()), modelRoleProperties.get(roleProp.getKey()));
    }
  }

  protected void validateServiceModel(ServiceModel        candidate,
                                      Map<String, String> serviceConfig,
                                      Map<String, String> roleConfig) {
    validateServiceModel(candidate, serviceConfig, roleConfig, true);
  }

}
