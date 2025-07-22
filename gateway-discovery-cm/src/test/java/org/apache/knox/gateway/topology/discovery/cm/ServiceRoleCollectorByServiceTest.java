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
import com.cloudera.api.swagger.model.ApiRoleConfig;
import com.cloudera.api.swagger.model.ApiRoleConfigList;
import org.easymock.EasyMock;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;
import static java.math.BigDecimal.valueOf;
import static org.easymock.EasyMock.anyString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;


public class ServiceRoleCollectorByServiceTest {

    public static final String DATA_VIEW_FULL = "full";
    private final String clusterName = "Cluster 1";
    private final String serviceName = "service1";
    private final long PAGE_SIZE = 2;

    private final ApiRoleConfigList page1Full = withRoleTypes("role1", "role2");
    private final ApiRoleConfigList page1Partial = withRoleTypes("role1");
    private final ApiRoleConfigList page2Full = withRoleTypes("role3", "role4");
    private final ApiRoleConfigList page3Partial = withRoleTypes("role5");

    private final ApiRoleConfigList emptyConfig = new ApiRoleConfigList().items(new ArrayList<>());
    private final ApiRoleConfigList configWithNullItems = new ApiRoleConfigList();

    private final TypeNameFilter allowAllRoleTypesFilter = createAllowAllRoleTypesFilter();

    @Test
    public void testRolesConfigWithNullItems() throws ApiException {
        RolesResourceApi api = EasyMock.createNiceMock(RolesResourceApi.class);
        EasyMock.expect(
                api.readRolesConfig(clusterName, serviceName, valueOf(PAGE_SIZE), ZERO, DATA_VIEW_FULL))
                .andReturn(configWithNullItems);
        EasyMock.replay(api);

        ServiceRoleCollector collector = createRoleCollector(api);
        ApiRoleConfigList result = collector.getAllServiceRoleConfigurations(clusterName, serviceName);

        assertEquals("Unexpected role config list size.", 0, result.getItems().size());
        EasyMock.verify(api);
    }

    @Test
    public void testRolesConfigWithEmptyItems() throws ApiException {
        RolesResourceApi api = EasyMock.createNiceMock(RolesResourceApi.class);
        EasyMock.expect(
                api.readRolesConfig(clusterName, serviceName, valueOf(PAGE_SIZE), ZERO, DATA_VIEW_FULL))
                .andReturn(emptyConfig);
        EasyMock.replay(api);

        ServiceRoleCollector collector = createRoleCollector(api);
        ApiRoleConfigList result = collector.getAllServiceRoleConfigurations(clusterName, serviceName);

        assertEquals("Unexpected role config list size.", 0, result.getItems().size());
        EasyMock.verify(api);
    }

    @Test(expected = ApiException.class)
    public void testApiException() throws ApiException {
        RolesResourceApi api = EasyMock.createNiceMock(RolesResourceApi.class);
        EasyMock.expect(
                api.readRolesConfig(clusterName, serviceName, valueOf(PAGE_SIZE), ZERO, DATA_VIEW_FULL))
                .andThrow(new ApiException());
        EasyMock.replay(api);
        ServiceRoleCollector collector = createRoleCollector(api);
        try {
            collector.getAllServiceRoleConfigurations(clusterName, serviceName);
        } finally {
            EasyMock.verify(api);
        }
    }

    @Test(expected = ApiException.class)
    public void testApiExceptionOnSecondPage() throws ApiException {
        RolesResourceApi api = EasyMock.createNiceMock(RolesResourceApi.class);
        EasyMock.expect(
                api.readRolesConfig(clusterName, serviceName, valueOf(PAGE_SIZE), ZERO, DATA_VIEW_FULL))
                .andReturn(page1Full);
        EasyMock.expect(
                api.readRolesConfig(clusterName, serviceName, valueOf(PAGE_SIZE), valueOf(PAGE_SIZE), DATA_VIEW_FULL))
                .andThrow(new ApiException());
        EasyMock.replay(api);
        ServiceRoleCollector collector = createRoleCollector(api);
        try {
            collector.getAllServiceRoleConfigurations(clusterName, serviceName);
        } finally {
            EasyMock.verify(api);
        }
    }


    @Test
    public void testPageSizeSetting() throws ApiException {
        long pageSize = 1;
        ApiRoleConfigList page1 = new ApiRoleConfigList()
                .addItemsItem(new ApiRoleConfig().roleType("role1"));
        ApiRoleConfigList page2 = new ApiRoleConfigList()
                .addItemsItem(new ApiRoleConfig().roleType("role2"));

        RolesResourceApi api = EasyMock.createNiceMock(RolesResourceApi.class);
        EasyMock.expect(api.readRolesConfig(clusterName, serviceName, ONE, ZERO, DATA_VIEW_FULL))
                .andReturn(page1);
        EasyMock.expect(api.readRolesConfig(clusterName, serviceName, ONE, ONE, DATA_VIEW_FULL))
                .andReturn(page2);

        EasyMock.replay(api);
        ServiceRoleCollector collector = new ServiceRoleCollectorByService(api, pageSize, allowAllRoleTypesFilter);
        ApiRoleConfigList result = collector.getAllServiceRoleConfigurations(clusterName, serviceName);

        assertEquals("Unexpected role config list size.", 2, result.getItems().size());
        assertThat("role names should match", toRoleTypes(result), containsInAnyOrder("role1", "role2"));

        EasyMock.verify(api);
    }

    @Test
    public void testPartialResultFromFirstPage() throws ApiException {
        long pageSize = 2;
        RolesResourceApi api = EasyMock.createNiceMock(RolesResourceApi.class);
        EasyMock.expect(
                api.readRolesConfig(clusterName, serviceName, valueOf(pageSize), ZERO, DATA_VIEW_FULL))
                .andReturn(page1Partial);
        EasyMock.replay(api);
        ServiceRoleCollector collector = new ServiceRoleCollectorByService(api, pageSize, allowAllRoleTypesFilter);
        ApiRoleConfigList result = collector.getAllServiceRoleConfigurations(clusterName, serviceName);

        assertEquals("Unexpected role config list size.", 1, result.getItems().size());
        assertThat("role names should match", toRoleTypes(result), containsInAnyOrder("role1"));

        EasyMock.verify(api);
    }

    @Test
    public void testPageSizeResultFromFirstPageThenEmptyResult() throws ApiException {
        RolesResourceApi api = EasyMock.createNiceMock(RolesResourceApi.class);
        EasyMock.expect(
                api.readRolesConfig(clusterName, serviceName, valueOf(PAGE_SIZE), ZERO, DATA_VIEW_FULL))
                .andReturn(page1Full);
        EasyMock.expect(
                api.readRolesConfig(clusterName, serviceName, valueOf(PAGE_SIZE), valueOf(PAGE_SIZE), DATA_VIEW_FULL))
                .andReturn(emptyConfig);
        EasyMock.replay(api);

        ServiceRoleCollector serviceRoleCollector = createRoleCollector(api);
        ApiRoleConfigList result = serviceRoleCollector.getAllServiceRoleConfigurations(clusterName, serviceName);

        assertEquals("Unexpected role config list size.", PAGE_SIZE, result.getItems().size());
        assertThat("role names should match", toRoleTypes(result), containsInAnyOrder("role1", "role2"));
        EasyMock.verify(api);
    }

    @Test
    public void testTwoFullPages() throws ApiException {
        RolesResourceApi api = createApiWithTwoFullPages();
        ServiceRoleCollector serviceRoleCollector = createRoleCollector(api);

        ApiRoleConfigList result = serviceRoleCollector.getAllServiceRoleConfigurations(clusterName, serviceName);

        assertEquals("Unexpected role config list size.", 4, result.getItems().size());
        assertThat("role names should match", toRoleTypes(result), containsInAnyOrder(
                "role1", "role2", "role3", "role4"));
        EasyMock.verify(api);
    }

    @Test
    public void testTwoFullPagesAndFilter() throws ApiException {
        RolesResourceApi api = createApiWithTwoFullPages();

        Set<String> excludedRoleTypes = Collections.singleton("RolE1");
        TypeNameFilter filter = new TypeNameFilter(excludedRoleTypes);
        ServiceRoleCollector serviceRoleCollector = createRoleCollectorWithFilter(api, filter);

        ApiRoleConfigList result = serviceRoleCollector.getAllServiceRoleConfigurations(clusterName, serviceName);

        assertEquals("Unexpected role config list size.", 3, result.getItems().size());
        assertThat("role names should match", toRoleTypes(result), containsInAnyOrder(
                "role2", "role3", "role4"));
        EasyMock.verify(api);
    }

    @Test
    public void testTwoFullPagesAndPartialPage() throws ApiException {
        BigDecimal limit = valueOf(PAGE_SIZE);
        BigDecimal offset2 = valueOf(PAGE_SIZE);
        BigDecimal offset3 = valueOf(PAGE_SIZE * 2);

        RolesResourceApi api = EasyMock.createNiceMock(RolesResourceApi.class);
        EasyMock.expect(
                api.readRolesConfig(clusterName, serviceName, limit, ZERO, DATA_VIEW_FULL))
                .andReturn(page1Full);
        EasyMock.expect(
                api.readRolesConfig(clusterName, serviceName, limit, offset2, DATA_VIEW_FULL))
                .andReturn(page2Full);
        EasyMock.expect(
                api.readRolesConfig(clusterName, serviceName, limit, offset3, DATA_VIEW_FULL))
                .andReturn(page3Partial);
        EasyMock.replay(api);
        ServiceRoleCollector serviceRoleCollector = createRoleCollector(api);
        ApiRoleConfigList result = serviceRoleCollector.getAllServiceRoleConfigurations(clusterName, serviceName);

        assertEquals("Unexpected role config list size.", 5, result.getItems().size());
        assertThat("role names should match", toRoleTypes(result), containsInAnyOrder(
                "role1", "role2", "role3", "role4", "role5"));
        EasyMock.verify(api);
    }

    private TypeNameFilter createAllowAllRoleTypesFilter() {
        TypeNameFilter roleTypeFilter = EasyMock.createNiceMock(TypeNameFilter.class);
        EasyMock.expect(roleTypeFilter.isExcluded(anyString())).andReturn(false);
        EasyMock.replay(roleTypeFilter);
        return roleTypeFilter;
    }

    private ServiceRoleCollector createRoleCollector(RolesResourceApi rolesResourceApi) {
        return new ServiceRoleCollectorByService(rolesResourceApi, PAGE_SIZE, allowAllRoleTypesFilter);
    }

    private ServiceRoleCollector createRoleCollectorWithFilter(RolesResourceApi rolesResourceApi, TypeNameFilter roleTypeFilter) {
        return new ServiceRoleCollectorByService(rolesResourceApi, PAGE_SIZE, roleTypeFilter);
    }

    private List<String> toRoleTypes(ApiRoleConfigList apiRoleConfigList) {
        return apiRoleConfigList
                .getItems()
                .stream()
                .map(ApiRoleConfig::getRoleType)
                .collect(Collectors.toList());
    }

    private static ApiRoleConfigList withRoleTypes(String... roleTypes) {
        ApiRoleConfigList roleConfigList = new ApiRoleConfigList();
        for (String roleType : roleTypes) {
            roleConfigList.addItemsItem(new ApiRoleConfig().roleType(roleType));
        }
        return roleConfigList;
    }

    private RolesResourceApi createApiWithTwoFullPages() throws ApiException {
        RolesResourceApi api = EasyMock.createNiceMock(RolesResourceApi.class);
        BigDecimal limit = valueOf(PAGE_SIZE);
        BigDecimal offset2 = valueOf(PAGE_SIZE);
        BigDecimal offset3 = valueOf(PAGE_SIZE * 2);
        EasyMock.expect(api.readRolesConfig(clusterName, serviceName, limit, ZERO, DATA_VIEW_FULL)).andReturn(page1Full);
        EasyMock.expect(api.readRolesConfig(clusterName, serviceName, limit, offset2, DATA_VIEW_FULL)).andReturn(page2Full);
        EasyMock.expect(api.readRolesConfig(clusterName, serviceName, limit, offset3, DATA_VIEW_FULL)).andReturn(emptyConfig);
        EasyMock.replay(api);
        return api;
    }

}