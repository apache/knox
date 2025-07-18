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
import com.cloudera.api.swagger.client.ApiClient;
import org.apache.knox.gateway.config.GatewayConfig;
import org.easymock.EasyMock;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.isA;

public class ServiceRoleCollectorBuilderTest {

    private static final String API_CLIENT_BASE_PATH = "/api/v57";

    @Test
    public void testBuilderWithByRoleConfig() {
        RolesResourceApi api = rolesResourceApi();
        GatewayConfig config = gatewayConfigWithByRoleFetchType();
        ServiceRoleCollector serviceRoleCollector = ServiceRoleCollectorBuilder.newBuilder()
                .gatewayConfig(config).rolesResourceApi(api).build();

        assertThat("collector type should be by role", serviceRoleCollector, isA(ServiceRoleCollectorByRole.class));
        EasyMock.verify(api);
        EasyMock.verify(config);
    }

    @Test
    public void testBuilderWithByServiceConfig() {
        RolesResourceApi api = rolesResourceApi();
        GatewayConfig config = gatewayConfigWithByServiceFetchType();
        ServiceRoleCollector serviceRoleCollector = ServiceRoleCollectorBuilder.newBuilder()
                .gatewayConfig(config).rolesResourceApi(api).build();

        assertThat("collector type should be by service", serviceRoleCollector, isA(ServiceRoleCollectorByService.class));
        EasyMock.verify(api);
        EasyMock.verify(config);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderWithNullGatewayConfig() {
        RolesResourceApi api = rolesResourceApi();
        ServiceRoleCollector serviceRoleCollector = ServiceRoleCollectorBuilder.newBuilder()
                .gatewayConfig(null).rolesResourceApi(api).build();

        assertThat("collector type should be by role", serviceRoleCollector, isA(ServiceRoleCollectorByRole.class));
        EasyMock.verify(api);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderWithNullRolesResourceApi() {
        ServiceRoleCollectorBuilder.newBuilder()
                .gatewayConfig(gatewayConfigWithByRoleFetchType())
                .rolesResourceApi(null)
                .build();
    }

    @Test
    public void testBuilderWithInvalidFetchTypeConfig() {
        RolesResourceApi api = rolesResourceApi();
        GatewayConfig config = gatewayConfigWithInvalidServiceFetchType();
        ServiceRoleCollector serviceRoleCollector = ServiceRoleCollectorBuilder.newBuilder()
                .gatewayConfig(config).rolesResourceApi(api).build();

        assertThat("collector type should be by role", serviceRoleCollector, isA(ServiceRoleCollectorByRole.class));
        EasyMock.verify(api);
    }

    private RolesResourceApi rolesResourceApi() {
        ApiClient apiClient = EasyMock.createNiceMock(ApiClient.class);
        EasyMock.expect(apiClient.getBasePath()).andReturn(API_CLIENT_BASE_PATH);
        RolesResourceApi api = EasyMock.createNiceMock(RolesResourceApi.class);
        EasyMock.expect(api.getApiClient()).andReturn(apiClient);
        EasyMock.replay(apiClient);
        EasyMock.replay(api);
        return api;
    }

    private GatewayConfig gatewayConfigWithByRoleFetchType() {
        GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(config.getClouderaManagerServiceDiscoveryRoleFetchStrategy()).andReturn(
                GatewayConfig.CLOUDERA_MANAGER_SERVICE_DISCOVERY_ROLE_FETCH_STRATEGY_BY_ROLE);
        EasyMock.replay(config);
        return config;
    }

    private GatewayConfig gatewayConfigWithByServiceFetchType() {
        GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(config.getClouderaManagerServiceDiscoveryRoleFetchStrategy()).andReturn(
                GatewayConfig.CLOUDERA_MANAGER_SERVICE_DISCOVERY_ROLE_FETCH_STRATEGY_BY_SERVICE);
        EasyMock.replay(config);
        return config;
    }

    private GatewayConfig gatewayConfigWithInvalidServiceFetchType() {
        GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(config.getClouderaManagerServiceDiscoveryRoleFetchStrategy()).andReturn(
                "dummy");
        EasyMock.replay(config);
        return config;
    }

}
