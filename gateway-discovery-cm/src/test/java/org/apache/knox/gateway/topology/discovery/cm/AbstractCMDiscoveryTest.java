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
package org.apache.knox.gateway.topology.discovery.cm;

import com.cloudera.api.swagger.model.ApiConfig;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiHostRef;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import org.easymock.EasyMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AbstractCMDiscoveryTest {
  protected static ApiService createApiServiceMock(final String serviceType) {
    return createApiServiceMock(serviceType + "-1", serviceType);
  }

  protected static ApiService createApiServiceMock(final String serviceName, final String serviceType) {
    ApiService service = EasyMock.createNiceMock(ApiService.class);
    EasyMock.expect(service.getName()).andReturn(serviceName).anyTimes();
    EasyMock.expect(service.getType()).andReturn(serviceType).anyTimes();
    EasyMock.replay(service);
    return service;
  }

  protected static ApiServiceConfig createApiServiceConfigMock(Map<String, String> configProps) {
    ApiServiceConfig serviceConfig = EasyMock.createNiceMock(ApiServiceConfig.class);
    EasyMock.expect(serviceConfig.getItems()).andReturn(createMockApiConfigs(configProps)).anyTimes();
    EasyMock.replay(serviceConfig);
    return serviceConfig;
  }

  protected static ApiRole createApiRoleMock(final String roleType) {
    return createApiRoleMock(roleType + "-12345", roleType);
  }

  protected static ApiRole createApiRoleMock(final String roleName, final String roleType) {
    ApiRole role = EasyMock.createNiceMock(ApiRole.class);
    EasyMock.expect(role.getName()).andReturn(roleName).anyTimes();
    EasyMock.expect(role.getType()).andReturn(roleType).anyTimes();

    ApiHostRef hostRef = EasyMock.createNiceMock(ApiHostRef.class);
    EasyMock.expect(hostRef.getHostname()).andReturn("localhost").anyTimes();
    EasyMock.replay(hostRef);
    EasyMock.expect(role.getHostRef()).andReturn(hostRef).anyTimes();

    EasyMock.replay(role);
    return role;
  }

  protected static ApiConfigList createApiConfigListMock(final Map<String, String> configProps) {
    ApiConfigList configList = EasyMock.createNiceMock(ApiConfigList.class);
    EasyMock.expect(configList.getItems()).andReturn(createMockApiConfigs(configProps)).anyTimes();
    EasyMock.replay(configList);
    return configList;
  }

  protected static List<ApiConfig> createMockApiConfigs(final Map<String, String> configProps) {
    List<ApiConfig> configList = new ArrayList<>();

    for (Map.Entry<String, String> entry : configProps.entrySet()) {
      configList.add(createApiConfigMock(entry.getKey(), entry.getValue()));
    }

    return configList;
  }

  protected static ApiConfig createApiConfigMock(final String name, final String value) {
    ApiConfig apiConfig = EasyMock.createNiceMock(ApiConfig.class);
    EasyMock.expect(apiConfig.getName()).andReturn(name).anyTimes();
    EasyMock.expect(apiConfig.getValue()).andReturn(value).anyTimes();
    EasyMock.replay(apiConfig);
    return apiConfig;
  }
}
