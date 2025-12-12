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

import com.cloudera.api.swagger.model.ApiRoleConfig;
import com.cloudera.api.swagger.model.ApiRoleConfigList;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.AbstractServiceModelGeneratorTest;
import org.easymock.EasyMock;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;

public class OpenSearchApiMasterServiceModelGeneratorTest extends AbstractServiceModelGeneratorTest {

  @Test
  public void testServiceModelMetadata() {
    final Map<String, String> serviceConfig = Collections.emptyMap();
    final Map<String, String> roleConfig = new HashMap<>();
    roleConfig.put("ssl_enabled", "true");
    roleConfig.put("opensearch_http_port", "9200");

    validateServiceModel(createServiceModel(serviceConfig, roleConfig), serviceConfig, roleConfig);
  }

  @Override
  protected String getServiceType() {
    return OpenSearchApiMasterServiceModelGenerator.SERVICE_TYPE;
  }

  @Override
  protected String getRoleType() {
    return OpenSearchApiMasterServiceModelGenerator.OPENSEARCH_MASTER_ROLE_TYPE;
  }

  @Override
  protected ServiceModelGenerator newGenerator() {
    return new OpenSearchApiMasterServiceModelGenerator();
  }

  @Test
  public void testSkipGeneratorWhenOpenSearchMaster() {
    final ApiRoleConfig masterRole = EasyMock.createNiceMock(ApiRoleConfig.class);
    EasyMock.expect(masterRole.getRoleType()).andReturn(OpenSearchApiMasterServiceModelGenerator.OPENSEARCH_MASTER_ROLE_TYPE).anyTimes();
    EasyMock.replay(masterRole);

    final ApiRoleConfig coordinatorRole = EasyMock.createNiceMock(ApiRoleConfig.class);
    EasyMock.expect(coordinatorRole.getRoleType()).andReturn(OpenSearchApiCoordinatorServiceModelGenerator.OPENSEARCH_COORDINATOR_ROLE_TYPE).anyTimes();
    EasyMock.replay(coordinatorRole);

    final ApiRoleConfigList serviceRoleConfigs = EasyMock.createNiceMock(ApiRoleConfigList.class);
    EasyMock.expect(serviceRoleConfigs.getItems()).andReturn(Arrays.asList(masterRole, coordinatorRole)).anyTimes();
    EasyMock.replay(serviceRoleConfigs);

    assertTrue(OpenSearchApiMasterServiceModelGenerator.shouldSkipGeneratorWhenOpenSearchMaster(newGenerator(), serviceRoleConfigs));
  }
}
