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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;



import static org.apache.knox.gateway.util.AuthFilterUtils.PROXYUSER_PREFIX;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

public class KnoxImpersonationProviderTest {

    @Rule
    public Timeout globalTimeout = new Timeout(10000, TimeUnit.MILLISECONDS);
    private KnoxImpersonationProvider provider;
    private Configuration config;

    @Before
    public void setUp() {

        provider = new KnoxImpersonationProvider();
        config = new Configuration();

        // Setup proxy user configuration
        config.set("hadoop.proxyuser.testuser.users", "*");
        config.set("hadoop.proxyuser.testuser.groups", "*");
        config.set("hadoop.proxyuser.testuser.hosts", "*");

        // Setup 3 proxy groups
        config.set("hadoop.proxygroup.virtual_group_1.users", "*");
        config.set("hadoop.proxygroup.virtual_group_1.groups", "*");
        config.set("hadoop.proxygroup.virtual_group_1.hosts", "*");
        config.set("hadoop.proxygroup.virtual.group_2.users", "*");
        config.set("hadoop.proxygroup.virtual.group_2.groups", "*");
        config.set("hadoop.proxygroup.virtual.group_2.hosts", "*");
        config.set("hadoop.proxygroup.virtual group_3.users", "*");
        config.set("hadoop.proxygroup.virtual group_3.groups", "*");
        config.set("hadoop.proxygroup.virtual group_3.hosts", "*");

        provider.setConf(config);
        provider.init(PROXYUSER_PREFIX);
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
        java.lang.reflect.Method method = KnoxImpersonationProvider.class.getDeclaredMethod("getAclKey", String.class);
        method.setAccessible(true);

        assertEquals("hadoop.proxygroup.admin", method.invoke(provider, "hadoop.proxygroup.admin.users"));
        assertEquals("hadoop.proxygroup.admin", method.invoke(provider, "hadoop.proxygroup.admin.groups"));
        assertEquals("key", method.invoke(provider, "key"));
    }

    @Test
    public void testAuthorizationSuccess() throws Exception {
        String proxyUser = "testuser";
        String impersonatedUser = "impersonatedUser";
        String[] proxyGroups = {"virtual_group_1"};

        UserGroupInformation realUserUGI = Mockito.mock(UserGroupInformation.class);
        UserGroupInformation userGroupInformation = Mockito.mock(UserGroupInformation.class);

        when(realUserUGI.getShortUserName()).thenReturn(proxyUser);
        when(realUserUGI.getUserName()).thenReturn(proxyUser);
        when(realUserUGI.getGroupNames()).thenReturn(proxyGroups);

        when(userGroupInformation.getRealUser()).thenReturn(realUserUGI);
        when(userGroupInformation.getUserName()).thenReturn(impersonatedUser);

        // Use reflection to call the checkProxyGroupAuthorization method directly
        java.lang.reflect.Method method = KnoxImpersonationProvider.class.getDeclaredMethod(
                "checkProxyGroupAuthorization",
                UserGroupInformation.class,
                InetAddress.class,
                List.class);
        method.setAccessible(true);
        method.invoke(provider, userGroupInformation, InetAddress.getByName("2.2.2.2"), Collections.emptyList());

        String[] proxyGroups2 = {"virtual.group_2"};
        when(realUserUGI.getShortUserName()).thenReturn(proxyUser);
        when(realUserUGI.getUserName()).thenReturn(proxyUser);
        when(realUserUGI.getGroupNames()).thenReturn(proxyGroups2);
        when(userGroupInformation.getRealUser()).thenReturn(realUserUGI);
        when(userGroupInformation.getUserName()).thenReturn(impersonatedUser);

        // Use reflection to call the checkProxyGroupAuthorization method directly
        method.invoke(provider, userGroupInformation, InetAddress.getByName("2.2.2.2"), Collections.emptyList());
    }

    /**
     * Test the case where proxy user is disabled and only proxy groups is enabled.
     * i.e.
     * hadoop.proxyuser.impersonation.enabled = false
     * hadoop.proxygroup.impersonation.enabled = true
     *
     * @throws Exception
     */
    @Test
    public void testAuthorizationSuccessWithOnlyProxyGroupsConfigured() throws Exception {
        KnoxImpersonationProvider gProvider = new KnoxImpersonationProvider();
        Configuration gConfig = new Configuration();

        String proxyUser = "testuser";
        String impersonatedUser = "impersonatedUser";

        // Setup proxy user configuration
        gConfig.set("hadoop.proxyuser." + proxyUser + ".users", "*");
        gConfig.set("hadoop.proxyuser." + proxyUser + ".hosts", "*");

        // Setup 3 proxy groups
        gConfig.set("hadoop.proxygroup.virtual_group_1.users", "*");
        gConfig.set("hadoop.proxygroup.virtual_group_1.groups", "*");
        gConfig.set("hadoop.proxygroup.virtual_group_1.hosts", "*");
        gConfig.set("hadoop.proxygroup.virtual.group_2.users", "*");
        gConfig.set("hadoop.proxygroup.virtual.group_2.groups", "*");
        gConfig.set("hadoop.proxygroup.virtual.group_2.hosts", "*");
        gConfig.set("hadoop.proxygroup.virtual group_3.users", "*");
        gConfig.set("hadoop.proxygroup.virtual group_3.groups", "*");
        gConfig.set("hadoop.proxygroup.virtual group_3.hosts", "*");

        gProvider.setConf(gConfig);
        // Initialize with both prefixes to enable both proxy user and proxy group authorization
        gProvider.init(PROXYUSER_PREFIX);

        String[] proxyGroups = {"virtual_group_1"};

        UserGroupInformation realUserUGI = Mockito.mock(UserGroupInformation.class);
        UserGroupInformation userGroupInformation = Mockito.mock(UserGroupInformation.class);

        when(realUserUGI.getShortUserName()).thenReturn(proxyUser);
        when(realUserUGI.getUserName()).thenReturn(proxyUser);
        when(realUserUGI.getGroupNames()).thenReturn(proxyGroups);

        when(userGroupInformation.getRealUser()).thenReturn(realUserUGI);
        when(userGroupInformation.getUserName()).thenReturn(impersonatedUser);

        // Use reflection to call the checkProxyGroupAuthorization method directly
        java.lang.reflect.Method method = KnoxImpersonationProvider.class.getDeclaredMethod(
                "checkProxyGroupAuthorization",
                UserGroupInformation.class,
                InetAddress.class,
                List.class);
        method.setAccessible(true);
        method.invoke(gProvider, userGroupInformation, InetAddress.getByName("2.2.2.2"), Collections.emptyList());

        String[] proxyGroups2 = {"virtual.group_2"};
        when(realUserUGI.getShortUserName()).thenReturn(proxyUser);
        when(realUserUGI.getUserName()).thenReturn(proxyUser);
        when(realUserUGI.getGroupNames()).thenReturn(proxyGroups2);
        when(userGroupInformation.getRealUser()).thenReturn(realUserUGI);
        when(userGroupInformation.getUserName()).thenReturn(impersonatedUser);

        // Use reflection to call the checkProxyGroupAuthorization method directly
        method.invoke(gProvider, userGroupInformation, InetAddress.getByName("2.2.2.2"), Collections.emptyList());
    }

    @Test(expected = org.apache.hadoop.security.authorize.AuthorizationException.class)
    public void testAuthorizationFailure() throws Exception {
        String proxyUser = "dummyUser";
        String impersonatedUser = "impersonatedUser";
        String[] proxyGroups = {"virtual group_3"};

        UserGroupInformation realUserUGI = Mockito.mock(UserGroupInformation.class);
        UserGroupInformation userGroupInformation = Mockito.mock(UserGroupInformation.class);

        when(realUserUGI.getUserName()).thenReturn(proxyUser);
        when(realUserUGI.getGroupNames()).thenReturn(proxyGroups);

        when(userGroupInformation.getRealUser()).thenReturn(realUserUGI);
        when(userGroupInformation.getUserName()).thenReturn(impersonatedUser);
        provider.authorize(userGroupInformation, InetAddress.getByName("2.2.2.2"));
    }

    /**
     * Test the case where both proxy user and proxy group are disabled.
     * Authorization should succeed in this case.
     *
     * @throws Exception
     */
    @Test
    public void testAuthorizationSuccessWithBothProxyMethodsDisabled() throws Exception {

        KnoxImpersonationProvider gProvider = new KnoxImpersonationProvider();
        Configuration gConfig = new Configuration();

        String proxyUser = "testuser";
        String impersonatedUser = "impersonatedUser";

        // Setup proxy user configuration
        gConfig.set("hadoop.proxyuser." + proxyUser + ".users", "*");
        gConfig.set("hadoop.proxyuser." + proxyUser + ".hosts", "*");

        // Setup proxy group configuration
        gConfig.set("hadoop.proxygroup.virtual_group_1.users", "*");
        gConfig.set("hadoop.proxygroup.virtual_group_1.groups", "*");
        gConfig.set("hadoop.proxygroup.virtual_group_1.hosts", "*");

        gProvider.setConf(gConfig);
        gProvider.init(PROXYUSER_PREFIX);

        String[] proxyGroups = {"virtual_group_1"};

        UserGroupInformation realUserUGI = Mockito.mock(UserGroupInformation.class);
        UserGroupInformation userGroupInformation = Mockito.mock(UserGroupInformation.class);

        when(realUserUGI.getShortUserName()).thenReturn(proxyUser);
        when(realUserUGI.getUserName()).thenReturn(proxyUser);
        when(realUserUGI.getGroupNames()).thenReturn(proxyGroups);

        when(userGroupInformation.getRealUser()).thenReturn(realUserUGI);
        when(userGroupInformation.getUserName()).thenReturn(impersonatedUser);

        // Use reflection to call the checkProxyGroupAuthorization method directly
        java.lang.reflect.Method method = KnoxImpersonationProvider.class.getDeclaredMethod(
                "checkProxyGroupAuthorization",
                UserGroupInformation.class,
                InetAddress.class,
                List.class);
        method.setAccessible(true);
        method.invoke(gProvider, userGroupInformation, InetAddress.getByName("2.2.2.2"), Collections.emptyList());
    }

    /**
     * Test the case where only proxy user is enabled and proxy group is disabled.
     * i.e.
     * hadoop.proxyuser.impersonation.enabled = true
     * hadoop.proxygroup.impersonation.enabled = false
     *
     * @throws Exception
     */
    @Test
    public void testAuthorizationSuccessWithOnlyProxyUserConfigured() throws Exception {
        KnoxImpersonationProvider gProvider = new KnoxImpersonationProvider();
        Configuration gConfig = new Configuration();

        String proxyUser = "testuser";
        String impersonatedUser = "impersonatedUser";

        // Setup proxy user configuration
        gConfig.set("hadoop.proxyuser." + proxyUser + ".users", "*");
        gConfig.set("hadoop.proxyuser." + proxyUser + ".groups", "*");
        gConfig.set("hadoop.proxyuser." + proxyUser + ".hosts", "*");

        // Setup proxy group configuration for the group the user belongs to
        gConfig.set("hadoop.proxygroup.somegroup.users", "*");
        gConfig.set("hadoop.proxygroup.somegroup.groups", "*");
        gConfig.set("hadoop.proxygroup.somegroup.hosts", "*");

        gProvider.setConf(gConfig);
        gProvider.init(PROXYUSER_PREFIX);

        String[] proxyGroups = {"somegroup"};

        UserGroupInformation realUserUGI = Mockito.mock(UserGroupInformation.class);
        UserGroupInformation userGroupInformation = Mockito.mock(UserGroupInformation.class);

        when(realUserUGI.getShortUserName()).thenReturn(proxyUser);
        when(realUserUGI.getUserName()).thenReturn(proxyUser);
        when(realUserUGI.getGroupNames()).thenReturn(proxyGroups);

        when(userGroupInformation.getRealUser()).thenReturn(realUserUGI);
        when(userGroupInformation.getUserName()).thenReturn(impersonatedUser);

        // Use reflection to call the checkProxyGroupAuthorization method directly
        java.lang.reflect.Method method = KnoxImpersonationProvider.class.getDeclaredMethod(
                "checkProxyGroupAuthorization",
                UserGroupInformation.class,
                InetAddress.class,
                List.class);
        method.setAccessible(true);
        method.invoke(gProvider, userGroupInformation, InetAddress.getByName("2.2.2.2"), Collections.emptyList());
    }

    /**
     * Test the case where both proxy user and proxy group are enabled with AND mode.
     * This ensures that both authorization methods are actually being used.
     * i.e.
     * hadoop.proxyuser.impersonation.enabled = true
     * hadoop.proxygroup.impersonation.enabled = true
     * impersonation.mode = AND
     *
     * @throws Exception
     */
    @Test
    public void testAuthorizationSuccessWithBothProxyMethodsEnabledAndMode() throws Exception {
        KnoxImpersonationProvider gProvider = new KnoxImpersonationProvider();
        Configuration gConfig = new Configuration();

        // Set impersonation mode to AND
        gConfig.set("impersonation.mode", "AND");

        String proxyUser = "testuser";
        String impersonatedUser = "impersonatedUser";

        // Setup proxy user configuration
        gConfig.set("hadoop.proxyuser." + proxyUser + ".users", "*");
        gConfig.set("hadoop.proxyuser." + proxyUser + ".groups", "*");
        gConfig.set("hadoop.proxyuser." + proxyUser + ".hosts", "*");

        // Setup proxy group configuration
        gConfig.set("hadoop.proxygroup.virtual_group_1.users", "*");
        gConfig.set("hadoop.proxygroup.virtual_group_1.groups", "*");
        gConfig.set("hadoop.proxygroup.virtual_group_1.hosts", "*");

        gProvider.setConf(gConfig);
        gProvider.init(PROXYUSER_PREFIX);

        String[] proxyGroups = {"virtual_group_1"};

        UserGroupInformation realUserUGI = Mockito.mock(UserGroupInformation.class);
        UserGroupInformation userGroupInformation = Mockito.mock(UserGroupInformation.class);

        when(realUserUGI.getShortUserName()).thenReturn(proxyUser);
        when(realUserUGI.getUserName()).thenReturn(proxyUser);
        when(realUserUGI.getGroupNames()).thenReturn(proxyGroups);

        when(userGroupInformation.getRealUser()).thenReturn(realUserUGI);
        when(userGroupInformation.getUserName()).thenReturn(impersonatedUser);

        // Use reflection to call the checkProxyGroupAuthorization method directly
        java.lang.reflect.Method method = KnoxImpersonationProvider.class.getDeclaredMethod(
                "checkProxyGroupAuthorization",
                UserGroupInformation.class,
                InetAddress.class,
                List.class);
        method.setAccessible(true);
        method.invoke(gProvider, userGroupInformation, InetAddress.getByName("2.2.2.2"), Collections.emptyList());
    }

    /**
     * Test the case where authorization is successful using the new authorize method
     * that accepts a list of groups as a parameter.
     *
     * @throws org.apache.hadoop.security.authorize.AuthorizationException
     * @throws UnknownHostException
     */
    @Test
    public void testAuthorizationSuccessWithProvidedGroups() throws org.apache.hadoop.security.authorize.AuthorizationException, UnknownHostException {
        KnoxImpersonationProvider gProvider = new KnoxImpersonationProvider();
        Configuration gConfig = new Configuration();

        // Setup proxy group configuration
        gConfig.set("hadoop.proxygroup.virtual_group_1.groups", "*");
        gConfig.set("hadoop.proxygroup.virtual_group_1.hosts", "*");

        gProvider.setConf(gConfig);
        gProvider.init(PROXYUSER_PREFIX);

        String proxyUser = "testuser";
        // User has no groups in their subject
        String[] emptyGroups = {};

        UserGroupInformation realUserUGI = Mockito.mock(UserGroupInformation.class);
        UserGroupInformation userGroupInformation = Mockito.mock(UserGroupInformation.class);

        when(realUserUGI.getShortUserName()).thenReturn(proxyUser);
        when(realUserUGI.getUserName()).thenReturn(proxyUser);
        when(realUserUGI.getGroupNames()).thenReturn(emptyGroups);

        when(userGroupInformation.getRealUser()).thenReturn(realUserUGI);
        when(userGroupInformation.getUserName()).thenReturn("impersonatedUser");

        // Create a list of groups to provide to the authorize method
        List<String> providedGroups = new ArrayList<>();
        providedGroups.add("virtual_group_1");

        // This should not throw an exception since the provided groups include virtual_group_1
        // which is authorized to impersonate
        gProvider.authorize(userGroupInformation, InetAddress.getByName("2.2.2.2"), providedGroups);
    }

    /**
     * Test the case where authorization fails when the provided groups
     * do not have permission to impersonate.
     *
     * @throws org.apache.hadoop.security.authorize.AuthorizationException
     * @throws UnknownHostException
     */
    @Test(expected = org.apache.hadoop.security.authorize.AuthorizationException.class)
    public void testAuthorizationFailureWithProvidedGroups() throws org.apache.hadoop.security.authorize.AuthorizationException, UnknownHostException {
        KnoxImpersonationProvider gProvider = new KnoxImpersonationProvider();
        Configuration gConfig = new Configuration();

        // Setup proxy group configuration
        gConfig.set("hadoop.proxygroup.virtual_group_1.groups", "*");
        gConfig.set("hadoop.proxygroup.virtual_group_1.hosts", "*");

        gProvider.setConf(gConfig);
        gProvider.init(PROXYUSER_PREFIX);

        String proxyUser = "testuser";
        // User has no groups in their subject
        String[] emptyGroups = {};

        UserGroupInformation realUserUGI = Mockito.mock(UserGroupInformation.class);
        UserGroupInformation userGroupInformation = Mockito.mock(UserGroupInformation.class);

        when(realUserUGI.getShortUserName()).thenReturn(proxyUser);
        when(realUserUGI.getUserName()).thenReturn(proxyUser);
        when(realUserUGI.getGroupNames()).thenReturn(emptyGroups);

        when(userGroupInformation.getRealUser()).thenReturn(realUserUGI);
        when(userGroupInformation.getUserName()).thenReturn("impersonatedUser");

        // Create a list of groups to provide to the authorize method
        // These groups do not have permission to impersonate
        List<String> providedGroups = new ArrayList<>();
        providedGroups.add("unauthorized_group");

        // This should throw an exception since the provided groups do not include any
        // that are authorized to impersonate
        gProvider.authorize(userGroupInformation, InetAddress.getByName("2.2.2.2"), providedGroups);
    }

    /**
     * Test the case where authorization succeeds when the user's subject groups
     * combined with the provided groups include one that has permission to impersonate.
     *
     * @throws org.apache.hadoop.security.authorize.AuthorizationException
     * @throws UnknownHostException
     */
    @Test
    public void testAuthorizationSuccessWithCombinedGroups() throws org.apache.hadoop.security.authorize.AuthorizationException, UnknownHostException {
        KnoxImpersonationProvider gProvider = new KnoxImpersonationProvider();
        Configuration gConfig = new Configuration();

        String proxyUser = "testuser";
        String impersonatedUser = "impersonatedUser";

        // Setup proxy user configuration
        gConfig.set("hadoop.proxyuser." + proxyUser + ".users", "*");
        gConfig.set("hadoop.proxyuser." + proxyUser + ".groups", "*");
        gConfig.set("hadoop.proxyuser." + proxyUser + ".hosts", "*");

        // Setup proxy group configuration
        gConfig.set("hadoop.proxygroup.virtual_group_1.users", "*");
        gConfig.set("hadoop.proxygroup.virtual_group_1.groups", "*");
        gConfig.set("hadoop.proxygroup.virtual_group_1.hosts", "*");
        gConfig.set("hadoop.proxygroup.virtual_group_2.users", "*");
        gConfig.set("hadoop.proxygroup.virtual_group_2.groups", "*");
        gConfig.set("hadoop.proxygroup.virtual_group_2.hosts", "*");

        gProvider.setConf(gConfig);
        gProvider.init(PROXYUSER_PREFIX);

        // User has virtual_group_1 in their subject
        String[] subjectGroups = {"virtual_group_1"};

        UserGroupInformation realUserUGI = Mockito.mock(UserGroupInformation.class);
        UserGroupInformation userGroupInformation = Mockito.mock(UserGroupInformation.class);

        when(realUserUGI.getShortUserName()).thenReturn(proxyUser);
        when(realUserUGI.getUserName()).thenReturn(proxyUser);
        when(realUserUGI.getGroupNames()).thenReturn(subjectGroups);

        when(userGroupInformation.getRealUser()).thenReturn(realUserUGI);
        when(userGroupInformation.getUserName()).thenReturn(impersonatedUser);

        // Create a list of groups to provide to the authorize method
        // These include virtual_group_2 which is also authorized
        List<String> providedGroups = new ArrayList<>();
        providedGroups.add("virtual_group_2");

        // This should not throw an exception since the combined groups include
        // both virtual_group_1 (from subject) and virtual_group_2 (provided)
        // which are both authorized to impersonate
        gProvider.authorize(userGroupInformation, InetAddress.getByName("2.2.2.2"), providedGroups);
    }
    /**
     * Test the isProxyGroupFound method directly.
     * This method checks if any of the real user's groups have permission to impersonate the proxy user.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testIsProxyGroupFound() throws Exception {
        // Set up the provider with specific configuration
        KnoxImpersonationProvider testProvider = new KnoxImpersonationProvider();
        Configuration testConfig = new Configuration();

        // Configure a specific proxy group
        testConfig.set("hadoop.proxygroup.authorized_group.users", "*");
        testConfig.set("hadoop.proxygroup.authorized_group.groups", "*");
        testConfig.set("hadoop.proxygroup.authorized_group.hosts", "*");

        testProvider.setConf(testConfig);
        testProvider.init("hadoop.proxygroup");

        // Create mocks for testing
        UserGroupInformation userToImpersonate = Mockito.mock(UserGroupInformation.class);
        when(userToImpersonate.getUserName()).thenReturn("impersonatedUser");

        // Set up the method to test via reflection
        java.lang.reflect.Method method = KnoxImpersonationProvider.class.getDeclaredMethod(
                "isProxyGroupFound",
                UserGroupInformation.class,
                Set.class,
                boolean.class);
        method.setAccessible(true);

        // Test case 1: User belongs to an authorized group
        Set<String> authorizedGroups = new HashSet<>();
        authorizedGroups.add("authorized_group");

        boolean result1 = (boolean) method.invoke(testProvider, userToImpersonate, authorizedGroups, false);
        assertTrue("User with authorized group should be allowed to impersonate", result1);

        // Test case 2: User belongs to an unauthorized group
        Set<String> unauthorizedGroups = new HashSet<>();
        unauthorizedGroups.add("unauthorized_group");

        boolean result2 = (boolean) method.invoke(testProvider, userToImpersonate, unauthorizedGroups, false);
        assertFalse("User with unauthorized group should not be allowed to impersonate", result2);

        // Test case 3: User belongs to multiple groups, including an authorized one
        Set<String> mixedGroups = new HashSet<>();
        mixedGroups.add("unauthorized_group");
        mixedGroups.add("authorized_group");

        boolean result3 = (boolean) method.invoke(testProvider, userToImpersonate, mixedGroups, false);
        assertTrue("User with mixed groups including an authorized one should be allowed to impersonate", result3);

        // Test case 4: Empty group set
        Set<String> emptyGroups = new HashSet<>();

        boolean result4 = (boolean) method.invoke(testProvider, userToImpersonate, emptyGroups, false);
        assertFalse("User with no groups should not be allowed to impersonate", result4);

        // Test case 5: Initial proxyGroupFound is true
        boolean result5 = (boolean) method.invoke(testProvider, userToImpersonate, unauthorizedGroups, true);
        assertTrue("Method should return true if initial proxyGroupFound is true", result5);
    }
    /**
     * Test the authorize method when doesProxyUserConfigExist is false and groups exist.
     * In this scenario, the method should directly call checkProxyGroupAuthorization.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testAuthorizeWhenProxyUserConfigDoesNotExistAndGroupsExist() throws Exception {
        // Set up a provider with only proxy group configuration (no proxy user configuration)
        KnoxImpersonationProvider testProvider = new KnoxImpersonationProvider();
        Configuration testConfig = new Configuration();

        // Configure only proxy groups, not proxy users
        testConfig.set("hadoop.proxygroup.authorized_group.users", "*");
        testConfig.set("hadoop.proxygroup.authorized_group.groups", "*");
        testConfig.set("hadoop.proxygroup.authorized_group.hosts", "*");

        testProvider.setConf(testConfig);
        testProvider.init("hadoop.proxygroup");

        // Create mocks for testing
        String proxyUser = "testuser";
        String impersonatedUser = "impersonatedUser";

        UserGroupInformation realUserUGI = Mockito.mock(UserGroupInformation.class);
        UserGroupInformation userGroupInformation = Mockito.mock(UserGroupInformation.class);

        when(realUserUGI.getUserName()).thenReturn(proxyUser);
        when(realUserUGI.getGroupNames()).thenReturn(new String[0]); // No groups in subject

        when(userGroupInformation.getRealUser()).thenReturn(realUserUGI);
        when(userGroupInformation.getUserName()).thenReturn(impersonatedUser);

        // Create a list of groups to provide to the authorize method
        List<String> providedGroups = new ArrayList<>();
        providedGroups.add("authorized_group");

        // This should not throw an exception since the provided groups include authorized_group
        // which is authorized to impersonate
        testProvider.authorize(userGroupInformation, InetAddress.getByName("2.2.2.2"), providedGroups);
    }

    /**
     * Test the authorize method when doesProxyUserConfigExist is true and valid proxyuser configuration exists.
     * In this scenario, the method should successfully authorize using the parent class's authorize method.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testAuthorizeWhenProxyUserConfigExistsAndIsValid() throws Exception {
        // Set up a provider with valid proxy user configuration
        KnoxImpersonationProvider testProvider = new KnoxImpersonationProvider();
        Configuration testConfig = new Configuration();

        String proxyUser = "testuser";
        String impersonatedUser = "impersonatedUser";

        // Setup valid proxy user configuration
        testConfig.set("hadoop.proxyuser." + proxyUser + ".users", "*");
        testConfig.set("hadoop.proxyuser." + proxyUser + ".groups", "*");
        testConfig.set("hadoop.proxyuser." + proxyUser + ".hosts", "*");

        testProvider.setConf(testConfig);
        testProvider.init("hadoop.proxyuser");

        // Create mocks for testing
        UserGroupInformation realUserUGI = Mockito.mock(UserGroupInformation.class);
        UserGroupInformation userGroupInformation = Mockito.mock(UserGroupInformation.class);

        when(realUserUGI.getShortUserName()).thenReturn(proxyUser);
        when(realUserUGI.getUserName()).thenReturn(proxyUser);

        when(userGroupInformation.getRealUser()).thenReturn(realUserUGI);
        when(userGroupInformation.getUserName()).thenReturn(impersonatedUser);

        // This should not throw an exception since the proxy user configuration is valid
        testProvider.authorize(userGroupInformation, InetAddress.getByName("2.2.2.2"));
    }

    /**
     * Test the authorize method when doesProxyUserConfigExist is true but the proxy user authorization fails,
     * causing it to fall back to checkProxyGroupAuthorization.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    public void testAuthorizeWhenProxyUserConfigExistsButAuthorizationFails() throws Exception {
        // Set up a provider with proxy user configuration that will fail authorization
        // but with valid proxy group configuration
        KnoxImpersonationProvider testProvider = new KnoxImpersonationProvider();
        Configuration testConfig = new Configuration();

        String proxyUser = "testuser";
        String impersonatedUser = "impersonatedUser";
        String unauthorizedUser = "unauthorizedUser"; // Different from the configured proxy user

        // Setup proxy user configuration for a specific user
        testConfig.set("hadoop.proxyuser." + proxyUser + ".users", "specificUser"); // Not wildcard
        testConfig.set("hadoop.proxyuser." + proxyUser + ".groups", "specificGroup"); // Not wildcard
        testConfig.set("hadoop.proxyuser." + proxyUser + ".hosts", "*");

        // Setup proxy group configuration
        testConfig.set("hadoop.proxygroup.authorized_group.users", "*");
        testConfig.set("hadoop.proxygroup.authorized_group.groups", "*");
        testConfig.set("hadoop.proxygroup.authorized_group.hosts", "*");

        testProvider.setConf(testConfig);
        testProvider.init("hadoop.proxyuser");

        // Create mocks for testing
        UserGroupInformation realUserUGI = Mockito.mock(UserGroupInformation.class);
        UserGroupInformation userGroupInformation = Mockito.mock(UserGroupInformation.class);

        when(realUserUGI.getShortUserName()).thenReturn(unauthorizedUser); // Use unauthorized user
        when(realUserUGI.getUserName()).thenReturn(unauthorizedUser);
        when(realUserUGI.getGroupNames()).thenReturn(new String[0]); // No groups in subject

        when(userGroupInformation.getRealUser()).thenReturn(realUserUGI);
        when(userGroupInformation.getUserName()).thenReturn(impersonatedUser);

        // Create a list of groups to provide to the authorize method
        List<String> providedGroups = new ArrayList<>();
        providedGroups.add("authorized_group");

        // This should not throw an exception because even though proxy user authorization fails,
        // it falls back to group-based authorization which succeeds
        testProvider.authorize(userGroupInformation, InetAddress.getByName("2.2.2.2"), providedGroups);
    }
}
