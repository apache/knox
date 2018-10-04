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

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.service.config.remote.RemoteConfigurationRegistryConfig;
import org.apache.knox.gateway.service.config.remote.util.RemoteRegistryConfigTestUtils;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class RemoteConfigurationRegistryConfigParserTest {

    @Test
    public void testExternalXMLParsing() throws Exception {
        final String CONN_STR = "http://my.zookeeper.host:2181";

        Map<String, Map<String, String>> testRegistryConfigurations = new HashMap<>();

        Map<String, String> config1 = new HashMap<>();
        config1.put(RemoteRegistryConfigTestUtils.PROPERTY_TYPE, "ZooKeeper");
        config1.put(RemoteRegistryConfigTestUtils.PROPERTY_NAME, "registry1");
        config1.put(RemoteRegistryConfigTestUtils.PROPERTY_ADDRESS, CONN_STR);
        config1.put(RemoteRegistryConfigTestUtils.PROPERTY_SECURE, "true");
        config1.put(RemoteRegistryConfigTestUtils.PROPERTY_AUTH_TYPE, "Digest");
        config1.put(RemoteRegistryConfigTestUtils.PROPERTY_PRINCIPAL, "knox");
        config1.put(RemoteRegistryConfigTestUtils.PROPERTY_CRED_ALIAS, "zkCredential");
        testRegistryConfigurations.put(config1.get("name"), config1);

        Map<String, String> config2 = new HashMap<>();
        config2.put(RemoteRegistryConfigTestUtils.PROPERTY_TYPE, "ZooKeeper");
        config2.put(RemoteRegistryConfigTestUtils.PROPERTY_NAME, "MyKerberos");
        config2.put(RemoteRegistryConfigTestUtils.PROPERTY_ADDRESS, CONN_STR);
        config2.put(RemoteRegistryConfigTestUtils.PROPERTY_SECURE, "true");
        config2.put(RemoteRegistryConfigTestUtils.PROPERTY_AUTH_TYPE, "Kerberos");
        config2.put(RemoteRegistryConfigTestUtils.PROPERTY_PRINCIPAL, "knox");
        File myKeyTab = File.createTempFile("mytest", "keytab");
        config2.put(RemoteRegistryConfigTestUtils.PROPERTY_KEYTAB, myKeyTab.getAbsolutePath());
        config2.put(RemoteRegistryConfigTestUtils.PROPERTY_USE_KEYTAB, "false");
        config2.put(RemoteRegistryConfigTestUtils.PROPERTY_USE_TICKET_CACHE, "true");
        testRegistryConfigurations.put(config2.get("name"), config2);

        Map<String, String> config3 = new HashMap<>();
        config3.put(RemoteRegistryConfigTestUtils.PROPERTY_TYPE, "ZooKeeper");
        config3.put(RemoteRegistryConfigTestUtils.PROPERTY_NAME, "anotherRegistry");
        config3.put(RemoteRegistryConfigTestUtils.PROPERTY_ADDRESS, "whatever:1281");
        testRegistryConfigurations.put(config3.get("name"), config3);

        String configXML =
                    RemoteRegistryConfigTestUtils.createRemoteConfigRegistriesXML(testRegistryConfigurations.values());

        File registryConfigFile = File.createTempFile("remote-registries", "xml");
        try {
            FileUtils.writeStringToFile(registryConfigFile, configXML, StandardCharsets.UTF_8);

            List<RemoteConfigurationRegistryConfig> configs =
                                    RemoteConfigurationRegistriesParser.getConfig(registryConfigFile.getAbsolutePath());
            assertNotNull(configs);
            assertEquals(testRegistryConfigurations.keySet().size(), configs.size());

            for (RemoteConfigurationRegistryConfig registryConfig : configs) {
                Map<String, String> expected = testRegistryConfigurations.get(registryConfig.getName());
                assertNotNull(expected);
                validateParsedRegistryConfiguration(registryConfig, expected);
            }
        } finally {
            registryConfigFile.delete();
        }
    }

    private void validateParsedRegistryConfiguration(RemoteConfigurationRegistryConfig config,
                                                     Map<String, String> expected) throws Exception {
        assertEquals(expected.get(RemoteRegistryConfigTestUtils.PROPERTY_TYPE), config.getRegistryType());
        assertEquals(expected.get(RemoteRegistryConfigTestUtils.PROPERTY_ADDRESS), config.getConnectionString());
        assertEquals(expected.get(RemoteRegistryConfigTestUtils.PROPERTY_NAME), config.getName());
        assertEquals(expected.get(
            RemoteRegistryConfigTestUtils.PROPERTY_NAMESAPCE), config.getNamespace());
        assertEquals(Boolean.valueOf(expected.get(
            RemoteRegistryConfigTestUtils.PROPERTY_SECURE)), config.isSecureRegistry());
        assertEquals(expected.get(
            RemoteRegistryConfigTestUtils.PROPERTY_AUTH_TYPE), config.getAuthType());
        assertEquals(expected.get(
            RemoteRegistryConfigTestUtils.PROPERTY_PRINCIPAL), config.getPrincipal());
        assertEquals(expected.get(
            RemoteRegistryConfigTestUtils.PROPERTY_CRED_ALIAS), config.getCredentialAlias());
        assertEquals(expected.get(RemoteRegistryConfigTestUtils.PROPERTY_KEYTAB), config.getKeytab());
        assertEquals(Boolean.valueOf(expected.get(
            RemoteRegistryConfigTestUtils.PROPERTY_USE_KEYTAB)), config.isUseKeyTab());
        assertEquals(Boolean.valueOf(expected.get(
            RemoteRegistryConfigTestUtils.PROPERTY_USE_TICKET_CACHE)), config.isUseTicketCache());
    }

}
