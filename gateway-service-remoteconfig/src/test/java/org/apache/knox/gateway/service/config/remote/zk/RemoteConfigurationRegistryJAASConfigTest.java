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
package org.apache.knox.gateway.service.config.remote.zk;

import org.apache.knox.gateway.service.config.remote.RemoteConfigurationRegistryConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RemoteConfigurationRegistryJAASConfigTest {

    @Test
    public void testZooKeeperDigestContextEntry() throws Exception {
        List<RemoteConfigurationRegistryConfig> registryConfigs = new ArrayList<>();
        final String ENTRY_NAME       = "my_digest_context";
        final String DIGEST_PRINCIPAL = "myIdentity";
        final String DIGEST_PWD_ALIAS = "myAlias";
        final String DIGEST_PWD       = "mysecret";

        AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
        EasyMock.expect(aliasService.getPasswordFromAliasForGateway(DIGEST_PWD_ALIAS)).andReturn(DIGEST_PWD.toCharArray()).anyTimes();
        EasyMock.replay(aliasService);

        registryConfigs.add(createDigestConfig(ENTRY_NAME, DIGEST_PRINCIPAL, DIGEST_PWD_ALIAS));

        try {
            RemoteConfigurationRegistryJAASConfig jaasConfig =
                                    RemoteConfigurationRegistryJAASConfig.configure(registryConfigs, aliasService);

            // Make sure there are no entries for an invalid context entry name
            assertNull(jaasConfig.getAppConfigurationEntry("invalid"));

            // Validate the intended context entry
            validateDigestContext(jaasConfig,
                                  ENTRY_NAME,
                                  RemoteConfigurationRegistryJAASConfig.digestLoginModules.get("ZOOKEEPER"),
                                  DIGEST_PRINCIPAL,
                                  DIGEST_PWD);
        } finally {
            Configuration.setConfiguration(null);
        }
    }

    @Test
    public void testKerberosContextEntry() throws Exception {
        List<RemoteConfigurationRegistryConfig> registryConfigs = new ArrayList<>();
        final String ENTRY_NAME = "my_kerberos_context";
        final String PRINCIPAL  = "myIdentity";

        File dummyKeyTab = File.createTempFile("my_context", "keytab");
        registryConfigs.add(createKerberosConfig(ENTRY_NAME, PRINCIPAL, dummyKeyTab.getAbsolutePath()));

        try {
            RemoteConfigurationRegistryJAASConfig jaasConfig =
                                            RemoteConfigurationRegistryJAASConfig.configure(registryConfigs, null);

            // Make sure there are no entries for an invalid context entry name
            assertNull(jaasConfig.getAppConfigurationEntry("invalid"));

            // Validate the intended context entry
            validateKerberosContext(jaasConfig,
                                    ENTRY_NAME,
                                    PRINCIPAL,
                                    dummyKeyTab.getAbsolutePath(),
                                    true,
                                    false);

        } finally {
            Configuration.setConfiguration(null);
        }
    }

    @Test
    public void testZooKeeperMultipleContextEntries() throws Exception {
        List<RemoteConfigurationRegistryConfig> registryConfigs = new ArrayList<>();
        final String KERBEROS_ENTRY_NAME = "my_kerberos_context";
        final String KERBEROS_PRINCIPAL  = "myKerberosIdentity";
        final String DIGEST_ENTRY_NAME   = "my_digest_context";
        final String DIGEST_PRINCIPAL    = "myDigestIdentity";
        final String DIGEST_PWD_ALIAS    = "myAlias";
        final String DIGEST_PWD          = "mysecret";

        AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
        EasyMock.expect(aliasService.getPasswordFromAliasForGateway(DIGEST_PWD_ALIAS)).andReturn(DIGEST_PWD.toCharArray()).anyTimes();
        EasyMock.replay(aliasService);

        File dummyKeyTab = File.createTempFile("my_context", "keytab");
        registryConfigs.add(createKerberosConfig(KERBEROS_ENTRY_NAME, KERBEROS_PRINCIPAL, dummyKeyTab.getAbsolutePath()));
        registryConfigs.add(createDigestConfig(DIGEST_ENTRY_NAME, DIGEST_PRINCIPAL, DIGEST_PWD_ALIAS));

        try {
            RemoteConfigurationRegistryJAASConfig jaasConfig =
                                        RemoteConfigurationRegistryJAASConfig.configure(registryConfigs, aliasService);

            // Make sure there are no entries for an invalid context entry name
            assertNull(jaasConfig.getAppConfigurationEntry("invalid"));

            // Validate the kerberos context entry
            validateKerberosContext(jaasConfig,
                                    KERBEROS_ENTRY_NAME,
                                    KERBEROS_PRINCIPAL,
                                    dummyKeyTab.getAbsolutePath(),
                                    true,
                                    false);

            // Validate the digest context entry
            validateDigestContext(jaasConfig,
                                  DIGEST_ENTRY_NAME,
                                  RemoteConfigurationRegistryJAASConfig.digestLoginModules.get("ZOOKEEPER"),
                                  DIGEST_PRINCIPAL,
                                  DIGEST_PWD);

        } finally {
            Configuration.setConfiguration(null);
        }
    }

    @Test
    public void testZooKeeperDigestContextEntryWithoutAliasService() throws Exception {
        List<RemoteConfigurationRegistryConfig> registryConfigs = new ArrayList<>();
        final String ENTRY_NAME       = "my_digest_context";
        final String DIGEST_PRINCIPAL = "myIdentity";
        final String DIGEST_PWD_ALIAS = "myAlias";

        registryConfigs.add(createDigestConfig(ENTRY_NAME, DIGEST_PRINCIPAL, DIGEST_PWD_ALIAS));

        try {
            RemoteConfigurationRegistryJAASConfig jaasConfig =
                                            RemoteConfigurationRegistryJAASConfig.configure(registryConfigs, null);
            fail("Expected IllegalArgumentException because the AliasService is not available.");
        } catch (IllegalArgumentException e) {
            // Expected
            assertTrue(e.getMessage().contains("AliasService"));
        } catch (Throwable e) {
            fail("Wrong exception encountered: " + e.getClass().getName() + ", " + e.getMessage());
        } finally {
            Configuration.setConfiguration(null);
        }
    }

    private static RemoteConfigurationRegistryConfig createDigestConfig(String entryName,
                                                                        String principal,
                                                                        String credentialAlias) {
        return createDigestConfig(entryName, principal, credentialAlias, "ZooKeeper");
    }

    private static RemoteConfigurationRegistryConfig createDigestConfig(String entryName,
                                                                        String principal,
                                                                        String credentialAlias,
                                                                        String registryType) {
        RemoteConfigurationRegistryConfig rc = EasyMock.createNiceMock(RemoteConfigurationRegistryConfig.class);
        EasyMock.expect(rc.getRegistryType()).andReturn(registryType).anyTimes();
        EasyMock.expect(rc.getName()).andReturn(entryName).anyTimes();
        EasyMock.expect(rc.isSecureRegistry()).andReturn(true).anyTimes();
        EasyMock.expect(rc.getAuthType()).andReturn("digest").anyTimes();
        EasyMock.expect(rc.getPrincipal()).andReturn(principal).anyTimes();
        EasyMock.expect(rc.getCredentialAlias()).andReturn(credentialAlias).anyTimes();
        EasyMock.replay(rc);
        return rc;
    }


    private static RemoteConfigurationRegistryConfig createKerberosConfig(String entryName,
                                                                          String principal,
                                                                          String keyTabPath) {
        return createKerberosConfig(entryName, principal, keyTabPath, "ZooKeeper");
    }

    private static RemoteConfigurationRegistryConfig createKerberosConfig(String entryName,
                                                                          String principal,
                                                                          String keyTabPath,
                                                                          String registryType) {
        return createKerberosConfig(entryName, principal, keyTabPath, null, null, registryType);
    }

    private static RemoteConfigurationRegistryConfig createKerberosConfig(String entryName,
                                                                          String principal,
                                                                          String keyTabPath,
                                                                          Boolean useKeyTab,
                                                                          Boolean useTicketCache,
                                                                          String registryType) {
        RemoteConfigurationRegistryConfig rc = EasyMock.createNiceMock(RemoteConfigurationRegistryConfig.class);
        EasyMock.expect(rc.getRegistryType()).andReturn(registryType).anyTimes();
        EasyMock.expect(rc.getName()).andReturn(entryName).anyTimes();
        EasyMock.expect(rc.isSecureRegistry()).andReturn(true).anyTimes();
        EasyMock.expect(rc.getAuthType()).andReturn("kerberos").anyTimes();
        EasyMock.expect(rc.getPrincipal()).andReturn(principal).anyTimes();
        EasyMock.expect(rc.getKeytab()).andReturn(keyTabPath).anyTimes();
        EasyMock.expect(rc.isUseKeyTab()).andReturn(useKeyTab != null ? useKeyTab : true).anyTimes();
        EasyMock.expect(rc.isUseTicketCache()).andReturn(useTicketCache != null ? useTicketCache : false).anyTimes();
        EasyMock.replay(rc);
        return rc;
    }

    private static void validateDigestContext(RemoteConfigurationRegistryJAASConfig config,
                                              String                                entryName,
                                              String                                loginModule,
                                              String                                principal,
                                              String                                password) throws Exception {
        AppConfigurationEntry[] myContextEntries = config.getAppConfigurationEntry(entryName);
        assertNotNull(myContextEntries);
        assertEquals(1, myContextEntries.length);
        AppConfigurationEntry entry = myContextEntries[0];
        assertTrue(entry.getLoginModuleName().equals(loginModule));
        Map<String, ?> entryOpts = entry.getOptions();
        assertEquals(principal, entryOpts.get("username"));
        assertEquals(password, entryOpts.get("password"));
    }

    private static void validateKerberosContext(RemoteConfigurationRegistryJAASConfig config,
                                                String                                entryName,
                                                String                                principal,
                                                String                                keyTab,
                                                boolean                               useKeyTab,
                                                boolean                               useTicketCache) throws Exception {
        AppConfigurationEntry[] myContextEntries = config.getAppConfigurationEntry(entryName);
        assertNotNull(myContextEntries);
        assertEquals(1, myContextEntries.length);
        AppConfigurationEntry entry = myContextEntries[0];
        assertTrue(entry.getLoginModuleName().endsWith(".security.auth.module.Krb5LoginModule"));
        Map<String, ?> entryOpts = entry.getOptions();
        assertEquals(principal, entryOpts.get("principal"));
        assertEquals(keyTab, entryOpts.get("keyTab"));
        assertEquals(useKeyTab, Boolean.valueOf((String)entryOpts.get("isUseKeyTab")));
        assertEquals(useTicketCache, Boolean.valueOf((String)entryOpts.get("isUseTicketCache")));
    }
}
