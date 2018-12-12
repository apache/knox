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
package org.apache.knox.gateway.service.config.remote.config;

import org.apache.knox.gateway.config.GatewayConfig;
import org.easymock.EasyMock;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DefaultRemoteConfigurationRegistriesTest {

    /*
     * Test a single registry configuration with digest auth configuration.
     */
    @Test
    public void testPropertiesRemoteConfigurationRegistriesSingleDigest() {
        Map<String, Properties> testProperties = new HashMap<>();
        Properties p = new Properties();
        p.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_TYPE, "ZooKeeper");
        p.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_ADDRESS, "hostx:2181");
        p.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_PRINCIPAL, "zkDigestUser");
        p.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_AUTH_TYPE, "digest");
        p.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_CREDENTIAL_ALIAS, "zkDigestAlias");
        testProperties.put("testDigest", p);

        doTestPropertiesRemoteConfigurationRegistries(testProperties);
    }

    /*
     * Test a single registry configuration with kerberos auth configuration.
     */
    @Test
    public void testPropertiesRemoteConfigurationRegistriesSingleKerberos() {
        Map<String, Properties> testProperties = new HashMap<>();
        Properties p = new Properties();
        p.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_TYPE, "ZooKeeper");
        p.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_ADDRESS, "hostx:2181");
        p.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_PRINCIPAL, "zkUser");
        p.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_AUTH_TYPE, "kerberos");
        p.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_KEYTAB, "/home/user/remoteregistry.keytab");
        p.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_USE_KEYTAB, "true");
        p.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_USE_TICKET_CACHE, "false");
        testProperties.put("testKerb", p);

        doTestPropertiesRemoteConfigurationRegistries(testProperties);
    }

    /*
     * Test multiple registry configuration with varying auth configurations.
     */
    @Test
    public void testPropertiesRemoteConfigurationRegistriesMultipleMixed() {
        Map<String, Properties> testProperties = new HashMap<>();

        Properties kerb = new Properties();
        kerb.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_TYPE, "ZooKeeper");
        kerb.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_ADDRESS, "host1:2181");
        kerb.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_NAMESPACE, "/knox/config");
        kerb.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_PRINCIPAL, "kerbPrincipal");
        kerb.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_AUTH_TYPE, "kerberos");
        kerb.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_KEYTAB, "/home/user/mykrb.keytab");
        kerb.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_USE_KEYTAB, "true");
        kerb.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_USE_TICKET_CACHE, "false");
        testProperties.put("testKerb1", kerb);

        Properties digest = new Properties();
        digest.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_TYPE, "ZooKeeper");
        digest.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_ADDRESS, "host2:2181");
        digest.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_PRINCIPAL, "digestPrincipal");
        digest.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_AUTH_TYPE, "digest");
        digest.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_CREDENTIAL_ALIAS, "digestPwdAlias");
        testProperties.put("testDigest1", digest);

        Properties unsecured = new Properties();
        unsecured.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_TYPE, "ZooKeeper");
        unsecured.setProperty(GatewayConfig.REMOTE_CONFIG_REGISTRY_ADDRESS, "host2:2181");
        testProperties.put("testUnsecured", unsecured);

        doTestPropertiesRemoteConfigurationRegistries(testProperties);
    }

    /**
     * Perform the actual test.
     *
     * @param testProperties The test properties
     */
    private void doTestPropertiesRemoteConfigurationRegistries(Map<String, Properties> testProperties) {
        // Mock gateway config
        GatewayConfig gc = mockGatewayConfig(testProperties);

        // Create the RemoteConfigurationRegistries object to be tested from the GatewayConfig
        RemoteConfigurationRegistries registries = new DefaultRemoteConfigurationRegistries(gc);

        // Basic validation
        assertNotNull(registries);
        List<RemoteConfigurationRegistry> registryConfigs = registries.getRegistryConfigurations();
        assertNotNull(registryConfigs);
        assertEquals(testProperties.size(), registryConfigs.size());

        // Validate the contents of the created object
        for (RemoteConfigurationRegistry regConfig : registryConfigs) {
            validateRemoteRegistryConfig(regConfig.getName(), testProperties.get(regConfig.getName()), regConfig);
        }
    }

    /**
     * Create a mock GatewayConfig based on the specified test properties.
     *
     * @param testProperties The test properties to set on the config
     */
    private GatewayConfig mockGatewayConfig(Map<String, Properties> testProperties) {
        // Mock gateway config
        GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
        List<String> configNames = new ArrayList<>();
        for (String registryName : testProperties.keySet()) {
            configNames.add(registryName);

            StringBuilder propertyValueString = new StringBuilder();
            Properties props = testProperties.get(registryName);
            Enumeration names = props.propertyNames();
            while (names.hasMoreElements()) {
                String propertyName = (String) names.nextElement();
                propertyValueString.append(propertyName).append('=').append(props.get(propertyName));
                if (names.hasMoreElements()) {
                    propertyValueString.append(';');
                }
            }
            EasyMock.expect(gc.getRemoteRegistryConfiguration(registryName))
                    .andReturn(propertyValueString.toString())
                    .anyTimes();
        }
        EasyMock.expect(gc.getRemoteRegistryConfigurationNames()).andReturn(configNames).anyTimes();
        EasyMock.replay(gc);

        return gc;
    }

    /**
     * Validate the specified RemoteConfigurationRegistry based on the expected test properties.
     *
     * @param configName config name to validate
     * @param expected expected properties
     * @param registryConfig registryConfig to validate
     */
    private void validateRemoteRegistryConfig(String                      configName,
                                              Properties                  expected,
                                              RemoteConfigurationRegistry registryConfig) {
        assertEquals(configName, registryConfig.getName());
        assertEquals(expected.get(GatewayConfig.REMOTE_CONFIG_REGISTRY_TYPE), registryConfig.getRegistryType());
        assertEquals(expected.get(GatewayConfig.REMOTE_CONFIG_REGISTRY_ADDRESS), registryConfig.getConnectionString());
        assertEquals(expected.get(GatewayConfig.REMOTE_CONFIG_REGISTRY_NAMESPACE), registryConfig.getNamespace());
        assertEquals(registryConfig.isSecureRegistry(), expected.get(GatewayConfig.REMOTE_CONFIG_REGISTRY_AUTH_TYPE) != null);
        assertEquals(expected.get(GatewayConfig.REMOTE_CONFIG_REGISTRY_AUTH_TYPE), registryConfig.getAuthType());
        assertEquals(expected.get(GatewayConfig.REMOTE_CONFIG_REGISTRY_PRINCIPAL), registryConfig.getPrincipal());
        assertEquals(expected.get(GatewayConfig.REMOTE_CONFIG_REGISTRY_CREDENTIAL_ALIAS), registryConfig.getCredentialAlias());
        assertEquals(expected.get(GatewayConfig.REMOTE_CONFIG_REGISTRY_KEYTAB), registryConfig.getKeytab());
        assertEquals(Boolean.valueOf((String)expected.get(GatewayConfig.REMOTE_CONFIG_REGISTRY_USE_KEYTAB)), registryConfig.isUseKeyTab());
        assertEquals(Boolean.valueOf((String)expected.get(GatewayConfig.REMOTE_CONFIG_REGISTRY_USE_TICKET_CACHE)), registryConfig.isUseTicketCache());
    }
}
