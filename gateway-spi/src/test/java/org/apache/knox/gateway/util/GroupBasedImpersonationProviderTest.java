/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.util;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.Mockito;

import java.util.concurrent.TimeUnit;

import static org.apache.knox.gateway.util.AuthFilterUtils.PROXYGROUP_PREFIX;
import static org.apache.knox.gateway.util.AuthFilterUtils.PROXYUSER_PREFIX;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class GroupBasedImpersonationProviderTest {

    @Rule
    public Timeout globalTimeout = new Timeout(10000, TimeUnit.MILLISECONDS);
    private GroupBasedImpersonationProvider provider;
    private Configuration config;

    @Before
    public void setUp() {
        provider = new GroupBasedImpersonationProvider(true, true);
        config = new Configuration();

        config.set("hadoop.proxyuser.testuser.groups", "*");
        config.set("hadoop.proxyuser.testuser.hosts", "*");

        // Setup 3 proxy users
        config.set("hadoop.proxygroup.virtual_group_1.groups", "*");
        config.set("hadoop.proxygroup.virtual_group_1.hosts", "*");
        config.set("hadoop.proxygroup.virtual.group_2.groups", "*");
        config.set("hadoop.proxygroup.virtual.group_2.hosts", "*");
        config.set("hadoop.proxygroup.virtual group_3.groups", "*");
        config.set("hadoop.proxygroup.virtual group_3.hosts", "*");
        provider.setConf(config);
        provider.init(PROXYUSER_PREFIX, PROXYGROUP_PREFIX);
    }

    @Test
    public void testGetConf() {
        assertEquals(config, provider.getConf());
    }

    @Test
    public void testSetConf() {
        Configuration newConfig = new Configuration(false);
        provider.setConf(newConfig);
        assertEquals(newConfig, provider.getConf());
    }

    @Test
    public void testInitWithEmptyConfig() {
        provider.init("hadoop.proxygroup");
        // No exception should be thrown
    }

    @Test
    public void testInitWithValidConfig() {
        // Set up configuration with valid proxy groups
        config.set("hadoop.proxygroup.admin.users", "user1,user2");
        config.set("hadoop.proxygroup.admin.groups", "group1,group2");
        config.set("hadoop.proxygroup.admin.hosts", "host1,host2");

        provider.init("hadoop.proxygroup");
        // No exception should be thrown
    }

    @Test
    public void testGetAclKey() throws Exception {
        // Test the private getAclKey method using reflection
        java.lang.reflect.Method method = GroupBasedImpersonationProvider.class.getDeclaredMethod("getAclKey", String.class);
        method.setAccessible(true);

        assertEquals("hadoop.proxygroup.admin", method.invoke(provider, "hadoop.proxygroup.admin.users"));
        assertEquals("hadoop.proxygroup.admin", method.invoke(provider, "hadoop.proxygroup.admin.groups"));
        assertEquals("key", method.invoke(provider, "key"));
    }

    @Test
    public void testAuthorizationSuccess() throws AuthorizationException, org.apache.hadoop.security.authorize.AuthorizationException {
        String proxyUser = "testuser";
        String[] proxyGroups = {"virtual_group_1"};

        UserGroupInformation realUserUGI = Mockito.mock(UserGroupInformation.class);
        UserGroupInformation userGroupInformation = Mockito.mock(UserGroupInformation.class);

        when(realUserUGI.getShortUserName()).thenReturn(proxyUser);
        when(realUserUGI.getUserName()).thenReturn(proxyUser);
        when(realUserUGI.getGroupNames()).thenReturn(proxyGroups);

        when(userGroupInformation.getRealUser()).thenReturn(realUserUGI);
        provider.authorize(userGroupInformation, "2.2.2.2");

        String[] proxyGroups2 = {"virtual.group_2"};
        when(realUserUGI.getShortUserName()).thenReturn(proxyUser);
        when(realUserUGI.getUserName()).thenReturn(proxyUser);
        when(realUserUGI.getGroupNames()).thenReturn(proxyGroups2);
        when(userGroupInformation.getRealUser()).thenReturn(realUserUGI);
        provider.authorize(userGroupInformation, "2.2.2.2");
    }

    /**
     * Test the case where proxy user is disabled and only proxy groups is enabled.
     * i.e.
     * hadoop.proxyuser.impersonation.enabled = false
     * hadoop.proxygroup.impersonation.enabled = true
     *
     * @throws AuthorizationException
     * @throws org.apache.hadoop.security.authorize.AuthorizationException
     */
    @Test
    public void testAuthorizationSuccessWithOnlyProxyGroupsConfigured() throws AuthorizationException, org.apache.hadoop.security.authorize.AuthorizationException {

        GroupBasedImpersonationProvider gProvider = new GroupBasedImpersonationProvider(false, true);
        Configuration gConfig = new Configuration();
        // Setup 3 proxy users
        gConfig.set("hadoop.proxygroup.virtual_group_1.groups", "*");
        gConfig.set("hadoop.proxygroup.virtual_group_1.hosts", "*");
        gConfig.set("hadoop.proxygroup.virtual.group_2.groups", "*");
        gConfig.set("hadoop.proxygroup.virtual.group_2.hosts", "*");
        gConfig.set("hadoop.proxygroup.virtual group_3.groups", "*");
        gConfig.set("hadoop.proxygroup.virtual group_3.hosts", "*");
        gProvider.setConf(gConfig);
        gProvider.init(PROXYUSER_PREFIX, PROXYGROUP_PREFIX);

        String proxyUser = "testuser";
        String[] proxyGroups = {"virtual_group_1"};

        UserGroupInformation realUserUGI = Mockito.mock(UserGroupInformation.class);
        UserGroupInformation userGroupInformation = Mockito.mock(UserGroupInformation.class);

        when(realUserUGI.getShortUserName()).thenReturn(proxyUser);
        when(realUserUGI.getUserName()).thenReturn(proxyUser);
        when(realUserUGI.getGroupNames()).thenReturn(proxyGroups);

        when(userGroupInformation.getRealUser()).thenReturn(realUserUGI);
        gProvider.authorize(userGroupInformation, "2.2.2.2");

        String[] proxyGroups2 = {"virtual.group_2"};
        when(realUserUGI.getShortUserName()).thenReturn(proxyUser);
        when(realUserUGI.getUserName()).thenReturn(proxyUser);
        when(realUserUGI.getGroupNames()).thenReturn(proxyGroups2);
        when(userGroupInformation.getRealUser()).thenReturn(realUserUGI);
        gProvider.authorize(userGroupInformation, "2.2.2.2");
    }

    @Test(expected = org.apache.hadoop.security.authorize.AuthorizationException.class)
    public void testAuthorizationFailure() throws Exception {
        String proxyUser = "dummyUser";
        String[] proxyGroups = {"virtual group_3"};

        UserGroupInformation realUserUGI = Mockito.mock(UserGroupInformation.class);
        UserGroupInformation userGroupInformation = Mockito.mock(UserGroupInformation.class);

        when(realUserUGI.getUserName()).thenReturn(proxyUser);
        when(realUserUGI.getGroupNames()).thenReturn(proxyGroups);

        when(userGroupInformation.getRealUser()).thenReturn(realUserUGI);
        provider.authorize(userGroupInformation, "2.2.2.2");
    }
}

