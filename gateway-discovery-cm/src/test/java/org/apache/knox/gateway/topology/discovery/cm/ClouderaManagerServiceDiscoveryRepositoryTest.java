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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.cloudera.api.swagger.model.ApiConfig;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiRoleList;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;

public class ClouderaManagerServiceDiscoveryRepositoryTest {

  private static ClouderaManagerServiceDiscoveryRepository repository = ClouderaManagerServiceDiscoveryRepository.getInstance();

  private static ServiceDiscoveryConfig serviceDiscoveryConfig;

  @BeforeClass
  public static void init() {
    serviceDiscoveryConfig = EasyMock.createNiceMock(ServiceDiscoveryConfig.class);
    EasyMock.expect(serviceDiscoveryConfig.getAddress()).andReturn("https://cm_host:7183/cmf").anyTimes();
    EasyMock.expect(serviceDiscoveryConfig.getCluster()).andReturn("Cluster 1").anyTimes();
    EasyMock.replay(serviceDiscoveryConfig);
  }

  @Before
  public void setUp() {
    repository.clear();
    repository.setCacheEntryTTL(GatewayConfig.DEFAULT_CM_SERVICE_DISCOVERY_CACHE_ENTRY_TTL);
    assertTrue(repository.getServices(serviceDiscoveryConfig).isEmpty());
  }

  @Test
  public void testRegisterCluster() throws Exception {
    assertTrue(repository.getServices(serviceDiscoveryConfig).isEmpty());
    final List<ApiService> services = repository.getServices(serviceDiscoveryConfig);
    assertNotNull(services);
    assertTrue(services.isEmpty());
  }

  @Test
  public void testAddService() throws Exception {
    final String serviceName = "HDFS-1";
    assertFalse(containsService(serviceName));
    final ApiService service = EasyMock.createNiceMock(ApiService.class);
    EasyMock.expect(service.getName()).andReturn(serviceName).anyTimes();
    EasyMock.replay(service);
    repository.addService(serviceDiscoveryConfig, service);
    assertTrue(containsService(serviceName));
  }

  @Test
  public void testAddServiceConfig() throws Exception {
    final String serviceName = "HDFS-1";
    final String serviceConfigName = "myServiceConfigName";
    final String serviceConfigValue = "myServiceConfigValue";
    final ApiService service = EasyMock.createNiceMock(ApiService.class);
    EasyMock.expect(service.getName()).andReturn(serviceName).anyTimes();
    final ApiConfig serviceConfig = EasyMock.createNiceMock(ApiConfig.class);
    EasyMock.expect(serviceConfig.getName()).andReturn(serviceConfigName).anyTimes();
    EasyMock.expect(serviceConfig.getValue()).andReturn(serviceConfigValue).anyTimes();
    final ApiServiceConfig serviceConfigs = EasyMock.createNiceMock(ApiServiceConfig.class);
    EasyMock.expect(serviceConfigs.getItems()).andReturn(Collections.singletonList(serviceConfig)).anyTimes();
    EasyMock.replay(service, serviceConfig, serviceConfigs);
    assertFalse(containsServiceConfig(service, serviceConfigName, serviceConfigValue));
    repository.addService(serviceDiscoveryConfig, service);
    repository.addServiceConfig(serviceDiscoveryConfig, service, serviceConfigs);
    assertTrue(containsServiceConfig(service, serviceConfigName, serviceConfigValue));
  }

  @Test
  public void testAddRole() throws Exception {
    final String serviceName = "HDFS-1";
    final String roleName = "NAMENODE-1";
    final ApiService service = EasyMock.createNiceMock(ApiService.class);
    EasyMock.expect(service.getName()).andReturn(serviceName).anyTimes();
    final ApiRole role = EasyMock.createNiceMock(ApiRole.class);
    EasyMock.expect(role.getName()).andReturn(roleName).anyTimes();
    final ApiRoleList roles = EasyMock.createNiceMock(ApiRoleList.class);
    EasyMock.expect(roles.getItems()).andReturn(Collections.singletonList(role)).anyTimes();
    EasyMock.replay(service, role, roles);
    assertFalse(containsRole(service, roleName));
    repository.addService(serviceDiscoveryConfig, service);
    repository.addRoles(serviceDiscoveryConfig, service, roles);
    assertTrue(containsRole(service, roleName));
  }

  @Test
  public void testAddNullRoles() throws Exception {
    final ApiService service = EasyMock.createNiceMock(ApiService.class);
    EasyMock.expect(service.getName()).andReturn(ClouderaManagerServiceDiscovery.CORE_SETTINGS_TYPE).anyTimes();
    EasyMock.replay(service);
    repository.addService(serviceDiscoveryConfig, service);
    repository.addRoles(serviceDiscoveryConfig, service, null);
    assertNull(repository.getRoles(serviceDiscoveryConfig, service).getItems());
  }

  @Test
  public void testAddNullRoleItems() throws Exception {
    final ApiService service = EasyMock.createNiceMock(ApiService.class);
    EasyMock.expect(service.getName()).andReturn(ClouderaManagerServiceDiscovery.CORE_SETTINGS_TYPE).anyTimes();
    final ApiRoleList roles = EasyMock.createNiceMock(ApiRoleList.class);
    EasyMock.expect(roles.getItems()).andReturn(null).anyTimes();
    EasyMock.replay(service, roles);
    repository.addService(serviceDiscoveryConfig, service);
    repository.addRoles(serviceDiscoveryConfig, service, roles);
    assertNull(repository.getRoles(serviceDiscoveryConfig, service).getItems());
  }

  @Test
  public void testAddRoleConfig() throws Exception {
    final String serviceName = "HDFS-1";
    final String roleName = "NAMENODE-1";
    final String roleConfigName = "myRoleConfig";
    final String roleConfigValue = "myRoleConfigValue";

    final ApiService service = EasyMock.createNiceMock(ApiService.class);
    EasyMock.expect(service.getName()).andReturn(serviceName).anyTimes();
    final ApiRole role = EasyMock.createNiceMock(ApiRole.class);
    EasyMock.expect(role.getName()).andReturn(roleName).anyTimes();
    final ApiRoleList roles = EasyMock.createNiceMock(ApiRoleList.class);
    EasyMock.expect(roles.getItems()).andReturn(Collections.singletonList(role)).anyTimes();
    final ApiConfig roleConfig = EasyMock.createNiceMock(ApiConfig.class);
    EasyMock.expect(roleConfig.getName()).andReturn(roleConfigName).anyTimes();
    EasyMock.expect(roleConfig.getValue()).andReturn(roleConfigValue).anyTimes();
    final ApiConfigList roleConfigs = EasyMock.createNiceMock(ApiConfigList.class);
    EasyMock.expect(roleConfigs.getItems()).andReturn(Collections.singletonList(roleConfig)).anyTimes();
    EasyMock.replay(service, role, roles, roleConfig, roleConfigs);
    assertFalse(containsRoleConfig(service, role, roleConfigName, roleConfigValue));
    repository.addService(serviceDiscoveryConfig, service);
    repository.addRoles(serviceDiscoveryConfig, service, roles);
    repository.addRoleConfigs(serviceDiscoveryConfig, service, role, roleConfigs);
    assertTrue(containsRoleConfig(service, role, roleConfigName, roleConfigValue));
  }

  @Test
  public void testCacheAutoEviction() throws Exception {
    final long entryTTL = 1;
    repository.setCacheEntryTTL(entryTTL);
    repository.clear();
    testAddService();
    TimeUnit.SECONDS.sleep(entryTTL + 1);
    assertFalse(containsService("HDFS-1"));
  }

  private boolean containsService(String serviceName) {
    final List<ApiService> services = repository.getServices(serviceDiscoveryConfig);
    if (services != null && !services.isEmpty()) {
      return services.stream().anyMatch(service -> service.getName().equals(serviceName));
    }
    return false;
  }

  private boolean containsServiceConfig(ApiService service, String serviceConfigName, String serviceConfigValue) {
    final ApiServiceConfig serviceConfigs = repository.getServiceConfig(serviceDiscoveryConfig, service);
    if (serviceConfigs != null && serviceConfigs.getItems() != null) {
      return serviceConfigs.getItems().stream()
          .anyMatch(serviceConfig -> serviceConfig.getName().equals(serviceConfigName) && serviceConfig.getValue().equals(serviceConfigValue));
    }
    return false;
  }

  private boolean containsRole(ApiService service, String roleName) {
    final ApiRoleList roles = repository.getRoles(serviceDiscoveryConfig, service);
    if (roles != null && roles.getItems() != null) {
      return roles.getItems().stream().anyMatch(role -> role.getName().equals(roleName));
    }
    return false;
  }

  private boolean containsRoleConfig(ApiService service, ApiRole role, String roleConfigName, String roleConfigValue) {
    final ApiConfigList roleConfigs = repository.getRoleConfigs(serviceDiscoveryConfig, service, role);
    if (roleConfigs != null && roleConfigs.getItems() != null) {
      return roleConfigs.getItems().stream()
          .anyMatch(roleConfig -> roleConfig.getName().equals(roleConfigName) && roleConfig.getValue().equals(roleConfigValue));
    }
    return false;
  }
}
