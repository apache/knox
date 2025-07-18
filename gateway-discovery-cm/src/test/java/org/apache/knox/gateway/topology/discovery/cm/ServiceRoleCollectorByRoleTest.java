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

import com.cloudera.api.swagger.RolesResourceApi;
import com.cloudera.api.swagger.client.ApiException;
import com.cloudera.api.swagger.model.ApiConfig;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiHostRef;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiRoleConfig;
import com.cloudera.api.swagger.model.ApiRoleConfigList;
import com.cloudera.api.swagger.model.ApiRoleList;
import org.easymock.EasyMock;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.eq;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


public class ServiceRoleCollectorByRoleTest {

    public static final String DATA_VIEW_FULL = "full";
    private final String clusterName = "Cluster 1";
    private final String serviceName = "service1";

    private final ApiRoleList roleListWithNullItems = new ApiRoleList().items(null);
    private final ApiRoleList emptyRoleList = new ApiRoleList().items(new ArrayList<>());

    private final TypeNameFilter allowAllRoleTypesFilter = createAllowAllRoleTypesFilter();

    @Test
    public void testRoleListIsNull() throws ApiException {
        RolesResourceApi api = apiWithReadRolesResult(null);
        ServiceRoleCollector collector = createRoleCollector(api);

        ApiRoleConfigList result = collector.getAllServiceRoleConfigurations(clusterName, serviceName);

        assertEquals("Unexpected role config list size.", 0, result.getItems().size());
        EasyMock.verify(api);
    }

    @Test
    public void testRoleListWithNullItems() throws ApiException {
        RolesResourceApi api = apiWithReadRolesResult(roleListWithNullItems);
        ServiceRoleCollector collector = createRoleCollector(api);

        ApiRoleConfigList result = collector.getAllServiceRoleConfigurations(clusterName, serviceName);

        assertEquals("Unexpected role config list size.", 0, result.getItems().size());
        EasyMock.verify(api);
    }

    @Test
    public void testRoleListWithEmptyItems() throws ApiException {
        RolesResourceApi api = apiWithReadRolesResult(emptyRoleList);
        ServiceRoleCollector collector = createRoleCollector(api);

        ApiRoleConfigList result = collector.getAllServiceRoleConfigurations(clusterName, serviceName);

        assertEquals("Unexpected role config list size.", 0, result.getItems().size());
        EasyMock.verify(api);
    }

    @Test(expected = ApiException.class)
    public void testApiExceptionOnReadRoles() throws ApiException {
        RolesResourceApi api = EasyMock.createNiceMock(RolesResourceApi.class);
        EasyMock.expect(api.readRoles(eq(clusterName), eq(serviceName), anyString(), eq(DATA_VIEW_FULL))).andThrow(new ApiException());
        EasyMock.replay(api);
        ServiceRoleCollector collector = createRoleCollector(api);
        try {
            collector.getAllServiceRoleConfigurations(clusterName, serviceName);
        } finally {
            EasyMock.verify(api);
        }
    }

    @Test(expected = ApiException.class)
    public void testApiExceptionOnReadRoleConfig() throws ApiException {
        RolesResourceApi api = EasyMock.createNiceMock(RolesResourceApi.class);
        String roleName = "HIVE_ON_TEZ-1-HIVESERVER2-f9";
        String roleType = "HIVESERVER2";
        ApiRole role = new ApiRole().name(roleName).type(roleType);
        ApiRoleList roleList = new ApiRoleList().addItemsItem(role);

        EasyMock.expect(api.readRoles(eq(clusterName), eq(serviceName), anyString(), eq(DATA_VIEW_FULL))).andReturn(roleList);
        EasyMock.expect(api.readRoleConfig(eq(clusterName), eq(roleName), eq(serviceName), eq(DATA_VIEW_FULL))).andThrow(new ApiException());
        EasyMock.replay(api);

        ServiceRoleCollector collector = createRoleCollector(api);
        try {
            collector.getAllServiceRoleConfigurations(clusterName, serviceName);
        } finally {
            EasyMock.verify(api);
        }
    }

    @Test
    public void testNullRoleConfigMappings() throws ApiException {
        RolesResourceApi api = EasyMock.createNiceMock(RolesResourceApi.class);

        ApiRoleList serviceRoleList = new ApiRoleList().addItemsItem(hiveServer2Role());
        ApiConfigList hiveServer2ConfigList = null;
        EasyMock.expect(api.readRoles(eq(clusterName), eq(serviceName), anyString(), eq(DATA_VIEW_FULL))).andReturn(serviceRoleList);
        EasyMock.expect(api.readRoleConfig(eq(clusterName), eq(hiveServer2Role().getName()), eq(serviceName), eq(DATA_VIEW_FULL))).andReturn(hiveServer2ConfigList);
        EasyMock.replay(api);

        ServiceRoleCollector collector = createRoleCollector(api);

        ApiRoleConfigList roleConfigList = collector.getAllServiceRoleConfigurations(clusterName, serviceName);
        List<ApiRoleConfig> items = roleConfigList.getItems();
        assertThat("Role configuration items list should be empty.", items, empty());
        EasyMock.verify(api);
    }

    @Test
    public void testRoleConfigWithNullItems() throws ApiException {
        RolesResourceApi api = EasyMock.createNiceMock(RolesResourceApi.class);

        ApiRoleList serviceRoleList = new ApiRoleList().addItemsItem(hiveServer2Role());
        ApiConfigList hiveServer2ConfigList = new ApiConfigList();
        EasyMock.expect(api.readRoles(eq(clusterName), eq(serviceName), anyString(), eq(DATA_VIEW_FULL))).andReturn(serviceRoleList);
        EasyMock.expect(api.readRoleConfig(eq(clusterName), eq(hiveServer2Role().getName()), eq(serviceName), eq(DATA_VIEW_FULL))).andReturn(hiveServer2ConfigList);
        EasyMock.replay(api);

        ServiceRoleCollector collector = createRoleCollector(api);

        ApiRoleConfigList roleConfigList = collector.getAllServiceRoleConfigurations(clusterName, serviceName);
        List<ApiRoleConfig> items = roleConfigList.getItems();
        assertThat("Role configuration items list should be empty.", items, empty());
        EasyMock.verify(api);
    }


    @Test
    public void testMultipleRoleConfigMappings() throws ApiException {
        RolesResourceApi api = EasyMock.createNiceMock(RolesResourceApi.class);

        ApiRoleList serviceRoleList = new ApiRoleList().items(Arrays.asList(hiveServer2Role(), gatewayRole1()));
        ApiConfigList hiveServer2ConfigList = hiveServer2ConfigList();
        ApiConfigList gatewayConfigList = gatewayConfigList();

        EasyMock.expect(api.readRoles(eq(clusterName), eq(serviceName), anyString(), eq(DATA_VIEW_FULL))).andReturn(serviceRoleList);
        EasyMock.expect(api.readRoleConfig(eq(clusterName), eq(hiveServer2Role().getName()), eq(serviceName), eq(DATA_VIEW_FULL))).andReturn(hiveServer2ConfigList);
        EasyMock.expect(api.readRoleConfig(eq(clusterName), eq(gatewayRole1().getName()), eq(serviceName), eq(DATA_VIEW_FULL))).andReturn(gatewayConfigList);
        EasyMock.replay(api);

        ServiceRoleCollector collector = createRoleCollector(api);

        ApiRoleConfigList roleConfigList = collector.getAllServiceRoleConfigurations(clusterName, serviceName);
        List<ApiRoleConfig> items = roleConfigList.getItems();
        List<ApiRoleConfig> expectedItems = new ArrayList<>();
        expectedItems.add(toApiRoleConfig(hiveServer2Role(), hiveServer2ConfigList()));
        expectedItems.add(toApiRoleConfig(gatewayRole1(), gatewayConfigList()));

        assertEquals("Unexpected role config list size.", expectedItems.size(), items.size());
        assertThat("Config items should match", items, containsInAnyOrder(expectedItems.toArray()));
        EasyMock.verify(api);
    }

    @Test
    public void testMultipleRoleConfigMappingsWithRoleTypeFilter() throws ApiException {
        RolesResourceApi api = EasyMock.createNiceMock(RolesResourceApi.class);
        ApiRoleList serviceRoleList = new ApiRoleList().items(Arrays.asList(hiveServer2Role(), gatewayRole1()));
        ApiConfigList hiveServer2ConfigList = hiveServer2ConfigList();
        EasyMock.expect(api.readRoles(eq(clusterName), eq(serviceName), anyString(), eq(DATA_VIEW_FULL))).andReturn(serviceRoleList);
        EasyMock.expect(api.readRoleConfig(eq(clusterName), eq(hiveServer2Role().getName()), eq(serviceName), eq(DATA_VIEW_FULL))).andReturn(hiveServer2ConfigList);
        EasyMock.replay(api);
        Set<String> excludedRoleTypes = Collections.singleton("GATEWAY");
        TypeNameFilter roleTypeFilter = new TypeNameFilter(excludedRoleTypes);
        ServiceRoleCollector collector = createRoleCollectorWithFilter(api, roleTypeFilter);

        ApiRoleConfigList roleConfigList = collector.getAllServiceRoleConfigurations(clusterName, serviceName);

        List<ApiRoleConfig> items = roleConfigList.getItems();
        List<ApiRoleConfig> expectedItems = new ArrayList<>();
        expectedItems.add(toApiRoleConfig(hiveServer2Role(), hiveServer2ConfigList()));
        assertEquals("Unexpected role config list size.", expectedItems.size(), items.size());
        assertThat("Config items should match", items, containsInAnyOrder(expectedItems.toArray()));
        EasyMock.verify(api);
    }

    private static ApiConfigList gatewayConfigList() {
        return new ApiConfigList().addItemsItem(gatewayClientConfigPriorityConfig());
    }

    private static ApiConfigList hiveServer2ConfigList() {
        return new ApiConfigList().addItemsItem(hiveServer2TransportModeConfig()).addItemsItem(hiveServer2ThriftHttpPortConfig());
    }

    private ApiRoleConfig toApiRoleConfig(ApiRole role, ApiConfigList roleConfigList) {
        return new ApiRoleConfig()
                .name(role.getName())
                .roleType(role.getType())
                .hostRef(role.getHostRef())
                .config(roleConfigList);
    }

    @Test
    public void testSingleRoleConfigMapping() throws ApiException {
        RolesResourceApi api = EasyMock.createNiceMock(RolesResourceApi.class);
        ApiRole hiveServer2Role = hiveServer2Role();
        ApiRoleList roleList = new ApiRoleList().addItemsItem(hiveServer2Role);
        ApiConfig transportMode = hiveServer2TransportModeConfig();
        ApiConfig thriftPort = hiveServer2ThriftHttpPortConfig();
        ApiConfigList hiveServer2ConfigList = new ApiConfigList().addItemsItem(transportMode).addItemsItem(thriftPort);
        EasyMock.expect(api.readRoles(eq(clusterName), eq(serviceName), anyString(), eq(DATA_VIEW_FULL))).andReturn(roleList);
        EasyMock.expect(api.readRoleConfig(eq(clusterName), eq(hiveServer2Role.getName()), eq(serviceName), eq(DATA_VIEW_FULL))).andReturn(hiveServer2ConfigList);
        EasyMock.replay(api);
        ServiceRoleCollector collector = createRoleCollector(api);

        ApiRoleConfigList roleConfigList = collector.getAllServiceRoleConfigurations(clusterName, serviceName);
        List<ApiRoleConfig> items = roleConfigList.getItems();
        assertEquals("Unexpected role config list size.", 1, items.size());
        ApiRoleConfig roleConfig = items.get(0);
        assertEquals(hiveServer2Role.getName(), roleConfig.getName());
        assertEquals(hiveServer2Role.getType(), roleConfig.getRoleType());
        assertNotNull(roleConfig.getHostRef());
        assertEquals(hiveServer2Role.getHostRef().getHostname(), roleConfig.getHostRef().getHostname());
        assertEquals(hiveServer2Role.getHostRef().getHostId(), roleConfig.getHostRef().getHostId());
        ApiConfigList configList = roleConfig.getConfig();
        assertNotNull(configList.getItems());
        List<ApiConfig> configItems = configList.getItems();
        assertThat("Config items should match", configItems, containsInAnyOrder(transportMode, thriftPort));
        EasyMock.verify(api);
    }

    private TypeNameFilter createAllowAllRoleTypesFilter() {
        TypeNameFilter roleTypeFilter = EasyMock.createNiceMock(TypeNameFilter.class);
        EasyMock.expect(roleTypeFilter.isExcluded(anyString())).andReturn(false);
        EasyMock.replay(roleTypeFilter);
        return roleTypeFilter;
    }

    private ServiceRoleCollector createRoleCollector(RolesResourceApi rolesResourceApi) {
        return new ServiceRoleCollectorByRole(rolesResourceApi, allowAllRoleTypesFilter);
    }

    private ServiceRoleCollector createRoleCollectorWithFilter(RolesResourceApi rolesResourceApi, TypeNameFilter roleTypeFilter) {
        return new ServiceRoleCollectorByRole(rolesResourceApi, roleTypeFilter);
    }

    private RolesResourceApi apiWithReadRolesResult(ApiRoleList result) throws ApiException {
        RolesResourceApi api = EasyMock.createNiceMock(RolesResourceApi.class);
        EasyMock.expect(api.readRoles(eq(clusterName), eq(serviceName), anyString(), eq(DATA_VIEW_FULL))).andReturn(result);
        EasyMock.replay(api);
        return api;
    }

    private ApiRole hiveServer2Role() {
        String roleName = "HIVE_ON_TEZ-1-HIVESERVER2-f9";
        String roleType = "HIVESERVER2";
        String hostId = "hostId1";
        String hostname = "host1";
        return new ApiRole().name(roleName).type(roleType).hostRef(new ApiHostRef().hostname(hostname).hostId(hostId));
    }

    private static ApiConfig hiveServer2TransportModeConfig() {
        return new ApiConfig().name("hive_server2_transport_mode").relatedName("hive.server2.transport.mode")._default("binary");
    }

    private static ApiConfig hiveServer2ThriftHttpPortConfig() {
        return new ApiConfig().name("hive_server2_thrift_http_port").relatedName("hive.server2.thrift.http.port")._default("10001");
    }

    private ApiRole gatewayRole1() {
        String roleName = "HIVE_ON_TEZ-GATEWAY-1";
        String roleType = "GATEWAY";
        String hostId = "hostId2";
        String hostname = "host2";
        return new ApiRole().name(roleName).type(roleType).hostRef(new ApiHostRef().hostname(hostname).hostId(hostId));
    }

    private static ApiConfig gatewayClientConfigPriorityConfig() {
        return new ApiConfig().name("client_config_priority").relatedName("")._default("92");
    }

}
