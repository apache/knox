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
import java.util.List;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.valueOf;
import static java.math.BigDecimal.ZERO;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;


public class ClouderaManagerServiceRoleCollectorTest {

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

    @Test
    public void testRolesConfigWithNullItems() throws ApiException {
        RolesResourceApi rolesResourceApi = EasyMock.createNiceMock(RolesResourceApi.class);
        EasyMock.expect(
                rolesResourceApi.readRolesConfig(clusterName, serviceName, valueOf(PAGE_SIZE), ZERO, DATA_VIEW_FULL))
                .andReturn(configWithNullItems);
        EasyMock.replay(rolesResourceApi);

        ServiceRoleCollector serviceRoleCollector = new ClouderaManagerServiceRoleCollector(rolesResourceApi, clusterName, PAGE_SIZE);
        ApiRoleConfigList result = serviceRoleCollector.getAllServiceRoleConfiguration(serviceName);

        assertEquals("Unexpected role config list size.", 0, result.getItems().size());
        EasyMock.verify(rolesResourceApi);
    }

    @Test
    public void testRolesConfigWithEmptyItems() throws ApiException {

        RolesResourceApi rolesResourceApi = EasyMock.createNiceMock(RolesResourceApi.class);
        EasyMock.expect(
                rolesResourceApi.readRolesConfig(clusterName, serviceName, valueOf(PAGE_SIZE), ZERO, DATA_VIEW_FULL))
                .andReturn(emptyConfig);
        EasyMock.replay(rolesResourceApi);

        ServiceRoleCollector serviceRoleCollector = new ClouderaManagerServiceRoleCollector(rolesResourceApi, clusterName, PAGE_SIZE);
        ApiRoleConfigList result = serviceRoleCollector.getAllServiceRoleConfiguration(serviceName);

        assertEquals("Unexpected role config list size.", 0, result.getItems().size());
        EasyMock.verify(rolesResourceApi);
    }


    @Test(expected = ApiException.class)
    public void testApiException() throws ApiException {
        RolesResourceApi rolesResourceApi = EasyMock.createNiceMock(RolesResourceApi.class);
        EasyMock.expect(
                rolesResourceApi.readRolesConfig(clusterName, serviceName, valueOf(PAGE_SIZE), ZERO, DATA_VIEW_FULL))
                .andThrow(new ApiException());
        EasyMock.replay(rolesResourceApi);
        ServiceRoleCollector serviceRoleCollector = new ClouderaManagerServiceRoleCollector(rolesResourceApi, clusterName, PAGE_SIZE);
        try {
            serviceRoleCollector.getAllServiceRoleConfiguration(serviceName);
        } finally {
            EasyMock.verify(rolesResourceApi);
        }
    }

    @Test(expected = ApiException.class)
    public void testApiExceptionOnSecondPage() throws ApiException {
        RolesResourceApi rolesResourceApi = EasyMock.createNiceMock(RolesResourceApi.class);
        EasyMock.expect(
                rolesResourceApi.readRolesConfig(clusterName, serviceName, valueOf(PAGE_SIZE), ZERO, DATA_VIEW_FULL))
                .andReturn(page1Full);
        EasyMock.expect(
                rolesResourceApi.readRolesConfig(clusterName, serviceName, valueOf(PAGE_SIZE), valueOf(PAGE_SIZE), DATA_VIEW_FULL))
                .andThrow(new ApiException());
        EasyMock.replay(rolesResourceApi);
        ServiceRoleCollector serviceRoleCollector = new ClouderaManagerServiceRoleCollector(rolesResourceApi, clusterName, PAGE_SIZE);
        try {
            serviceRoleCollector.getAllServiceRoleConfiguration(serviceName);
        } finally {
            EasyMock.verify(rolesResourceApi);
        }
    }


    @Test
    public void testPageSizeSetting() throws ApiException {
        long pageSize = 1;
        ApiRoleConfigList page1 = new ApiRoleConfigList()
                .addItemsItem(new ApiRoleConfig().roleType("role1"));
        ApiRoleConfigList page2 = new ApiRoleConfigList()
                .addItemsItem(new ApiRoleConfig().roleType("role2"));

        RolesResourceApi rolesResourceApi = EasyMock.createNiceMock(RolesResourceApi.class);
        EasyMock.expect(
                        rolesResourceApi.readRolesConfig(clusterName, serviceName, ONE, ZERO, DATA_VIEW_FULL))
                .andReturn(page1);
        EasyMock.expect(
                        rolesResourceApi.readRolesConfig(clusterName, serviceName, ONE, ONE, DATA_VIEW_FULL))
                .andReturn(page2);

        EasyMock.replay(rolesResourceApi);
        ServiceRoleCollector serviceRoleCollector = new ClouderaManagerServiceRoleCollector(rolesResourceApi, clusterName, pageSize);
        ApiRoleConfigList result = serviceRoleCollector.getAllServiceRoleConfiguration(serviceName);

        assertEquals("Unexpected role config list size.", 2, result.getItems().size());
        assertThat("role names should match", toRoleTypes(result), containsInAnyOrder("role1", "role2"));

        EasyMock.verify(rolesResourceApi);
    }

    @Test
    public void testPartialResultFromFirstPage() throws ApiException {
        long pageSize = 2;
        RolesResourceApi rolesResourceApi = EasyMock.createNiceMock(RolesResourceApi.class);
        EasyMock.expect(
                rolesResourceApi.readRolesConfig(clusterName, serviceName, valueOf(pageSize), ZERO, DATA_VIEW_FULL))
                .andReturn(page1Partial);
        EasyMock.replay(rolesResourceApi);
        ServiceRoleCollector serviceRoleCollector = new ClouderaManagerServiceRoleCollector(rolesResourceApi, clusterName, pageSize);
        ApiRoleConfigList result = serviceRoleCollector.getAllServiceRoleConfiguration(serviceName);

        assertEquals("Unexpected role config list size.", 1, result.getItems().size());
        assertThat("role names should match", toRoleTypes(result), containsInAnyOrder("role1"));

        EasyMock.verify(rolesResourceApi);
    }

    private List<String> toRoleTypes(ApiRoleConfigList apiRoleConfigList) {
        return apiRoleConfigList
                .getItems()
                .stream()
                .map(ApiRoleConfig::getRoleType)
                .collect(Collectors.toList());
    }

    @Test
    public void testPageSizeResultFromFirstPageThenEmptyResult() throws ApiException {
        RolesResourceApi rolesResourceApi = EasyMock.createNiceMock(RolesResourceApi.class);
        EasyMock.expect(
                rolesResourceApi.readRolesConfig(clusterName, serviceName, valueOf(PAGE_SIZE), ZERO, DATA_VIEW_FULL))
                .andReturn(page1Full);
        EasyMock.expect(
                rolesResourceApi.readRolesConfig(clusterName, serviceName, valueOf(PAGE_SIZE), valueOf(PAGE_SIZE), DATA_VIEW_FULL))
                .andReturn(emptyConfig);
        EasyMock.replay(rolesResourceApi);

        ServiceRoleCollector serviceRoleCollector = new ClouderaManagerServiceRoleCollector(rolesResourceApi, clusterName, PAGE_SIZE);
        ApiRoleConfigList result = serviceRoleCollector.getAllServiceRoleConfiguration(serviceName);

        assertEquals("Unexpected role config list size.", PAGE_SIZE, result.getItems().size());
        assertThat("role names should match", toRoleTypes(result), containsInAnyOrder("role1", "role2"));
        EasyMock.verify(rolesResourceApi);
    }

    @Test
    public void testTwoFullPages() throws ApiException {
        RolesResourceApi rolesResourceApi = EasyMock.createNiceMock(RolesResourceApi.class);

        BigDecimal limit = valueOf(PAGE_SIZE);
        BigDecimal offset2 = valueOf(PAGE_SIZE);
        BigDecimal offset3 = valueOf(PAGE_SIZE * 2);
        EasyMock.expect(rolesResourceApi.readRolesConfig(clusterName, serviceName, limit, ZERO, DATA_VIEW_FULL)).andReturn(page1Full);
        EasyMock.expect(rolesResourceApi.readRolesConfig(clusterName, serviceName, limit, offset2, DATA_VIEW_FULL)).andReturn(page2Full);
        EasyMock.expect(rolesResourceApi.readRolesConfig(clusterName, serviceName, limit, offset3, DATA_VIEW_FULL)).andReturn(emptyConfig);
        EasyMock.replay(rolesResourceApi);
        ServiceRoleCollector serviceRoleCollector = new ClouderaManagerServiceRoleCollector(rolesResourceApi, clusterName, PAGE_SIZE);
        ApiRoleConfigList result = serviceRoleCollector.getAllServiceRoleConfiguration(serviceName);

        assertEquals("Unexpected role config list size.", 4, result.getItems().size());
        assertThat("role names should match", toRoleTypes(result), containsInAnyOrder(
                "role1", "role2", "role3", "role4"));
        EasyMock.verify(rolesResourceApi);
    }

    @Test
    public void testTwoFullPagesAndPartialPage() throws ApiException {
        BigDecimal limit = valueOf(PAGE_SIZE);
        BigDecimal offset2 = valueOf(PAGE_SIZE);
        BigDecimal offset3 = valueOf(PAGE_SIZE * 2);

        RolesResourceApi rolesResourceApi = EasyMock.createNiceMock(RolesResourceApi.class);
        EasyMock.expect(
                rolesResourceApi.readRolesConfig(clusterName, serviceName, limit, ZERO, DATA_VIEW_FULL))
                .andReturn(page1Full);
        EasyMock.expect(
                rolesResourceApi.readRolesConfig(clusterName, serviceName, limit, offset2, DATA_VIEW_FULL))
                .andReturn(page2Full);
        EasyMock.expect(
                rolesResourceApi.readRolesConfig(clusterName, serviceName, limit, offset3, DATA_VIEW_FULL))
                .andReturn(page3Partial);
        EasyMock.replay(rolesResourceApi);
        ServiceRoleCollector serviceRoleCollector = new ClouderaManagerServiceRoleCollector(rolesResourceApi, clusterName, PAGE_SIZE);
        ApiRoleConfigList result = serviceRoleCollector.getAllServiceRoleConfiguration(serviceName);

        assertEquals("Unexpected role config list size.", 5, result.getItems().size());
        assertThat("role names should match", toRoleTypes(result), containsInAnyOrder(
                "role1", "role2", "role3", "role4", "role5"));
        EasyMock.verify(rolesResourceApi);
    }

    private static ApiRoleConfigList withRoleTypes(String... roleTypes) {
        ApiRoleConfigList roleConfigList = new ApiRoleConfigList();
        for (String roleType : roleTypes) {
            roleConfigList.addItemsItem(new ApiRoleConfig().roleType(roleType));
        }
        return roleConfigList;
    }

}