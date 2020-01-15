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
package org.apache.knox.gateway.topology.discovery.cm.monitor;

import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiRole;
import org.apache.knox.gateway.topology.discovery.cm.AbstractCMDiscoveryTest;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ServiceConfigurationModelTest extends AbstractCMDiscoveryTest {

  @Test
  public void testEmptyServiceConfigurationModel() {
    ServiceConfigurationModel model = new ServiceConfigurationModel();
    validateServiceConfigurationModel(model, Collections.emptyMap(), Collections.emptyMap());
    assertNull(model.getRoleProps("UNKNOWN_ROLE_TYPE"));
  }

  @Test
  public void testServiceConfigurationModel() {
    Map<String, String> serviceConfig = new HashMap<>();
    serviceConfig.put("prop1", "value1");
    serviceConfig.put("prop2", "value2");

    Map<String, Map<String, String>> roleConfig = new HashMap<>();
    Map<String, String> roleProps = new HashMap<>();
    roleProps.put("prop1", "value1");
    roleProps.put("prop2", "value2");
    roleProps.put("prop3", "value3");
    roleConfig.put("test1", roleProps);

    roleProps = new HashMap<>();
    roleProps.put("prop4", "value4");
    roleProps.put("prop5", "value5");
    roleProps.put("prop6", "value6");
    roleConfig.put("test2", roleProps);

    // Create the model
    ServiceConfigurationModel model = createModel(serviceConfig, roleConfig);
    assertNotNull(model);
    assertFalse(model.getServiceProps().isEmpty());
    assertFalse(model.getRoleProps().isEmpty());
    assertFalse(model.getRoleTypes().isEmpty());

    // Validate model contents
    validateServiceConfigurationModel(model, serviceConfig, roleConfig);
  }

  @Test
  public void testServiceConfigurationModelFromAPI() {
    Map<String, String> serviceConfig = new HashMap<>();
    serviceConfig.put("prop1", "value1");
    serviceConfig.put("prop2", "value2");
    serviceConfig.put("prop3", "value3");

    Map<String, Map<String, String>> roleConfig = new HashMap<>();
    Map<String, String> role1Props = new HashMap<>();
    role1Props.put("prop1", "value1");
    role1Props.put("prop2", "value2");
    role1Props.put("prop3", "value3");
    roleConfig.put("ROLE_1", role1Props);

    Map<String, String> role2Props = new HashMap<>();
    role2Props.put("prop4", "value4");
    role2Props.put("prop5", "value5");
    roleConfig.put("ROLE_2", role2Props);

    Map<String, String> role3Props = new HashMap<>();
    role3Props.put("prop6", "value6");
    role3Props.put("prop7", "value7");
    role3Props.put("prop8", "value8");
    role3Props.put("prop9", "value9");
    roleConfig.put("ROLE_3", role3Props);

    Map<ApiRole, ApiConfigList> apiRoleConfigs = new HashMap<>();
    for (Map.Entry<String, Map<String, String>> entry : roleConfig.entrySet()) {
      apiRoleConfigs.put(createApiRoleMock(entry.getKey()), createApiConfigListMock(entry.getValue()));
    }

    // Create the model
    ServiceConfigurationModel model =
        new ServiceConfigurationModel(createApiServiceConfigMock(serviceConfig), apiRoleConfigs);
    assertNotNull(model);
    assertFalse(model.getServiceProps().isEmpty());
    assertFalse(model.getRoleProps().isEmpty());
    assertFalse(model.getRoleTypes().isEmpty());

    // Validate model contents
    validateServiceConfigurationModel(model, serviceConfig, roleConfig);
  }


  private void validateServiceConfigurationModel(final ServiceConfigurationModel        model,
                                                 final Map<String, String>              expectedServiceConfig,
                                                 final Map<String, Map<String, String>> expectedRoleConfig) {
    assertNotNull(model);

    Map<String, String> modelServiceProps = model.getServiceProps();
    assertEquals(expectedServiceConfig.size(), modelServiceProps.size());
    for (Map.Entry<String, String> entry : expectedServiceConfig.entrySet()) {
      assertEquals(entry.getValue(), modelServiceProps.get(entry.getKey()));
    }

    assertEquals(expectedRoleConfig.size(), model.getRoleProps().size());
    for (Map.Entry<String, Map<String, String>> entry : expectedRoleConfig.entrySet()) {
      String roleType = entry.getKey();
      assertEquals(expectedRoleConfig.get(roleType).size(), entry.getValue().size());
      for (Map.Entry<String, String> prop : entry.getValue().entrySet()) {
        assertEquals(prop.getValue(), expectedRoleConfig.get(roleType).get(prop.getKey()));
      }
    }
  }


  private ServiceConfigurationModel createModel(Map<String, String>              serviceConfig,
                                                Map<String, Map<String, String>> roleConfig) {
    ServiceConfigurationModel model = new ServiceConfigurationModel();

    for (Map.Entry<String, String> entry : serviceConfig.entrySet()) {
      model.addServiceProperty(entry.getKey(), entry.getValue());
    }

    for (Map.Entry<String, Map<String, String>> entry : roleConfig.entrySet()) {
      String roleType = entry.getKey();
      for (Map.Entry<String, String> prop : entry.getValue().entrySet()) {
        model.addRoleProperty(roleType, prop.getKey(), prop.getValue());
      }
    }

    return model;
  }

}
