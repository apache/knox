/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.services.token.impl;

import java.io.File;
import java.security.Principal;
import java.security.interfaces.RSAPublicKey;
import java.util.Collections;
import java.util.HashMap;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.services.security.impl.DefaultKeystoreService;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.TokenServiceException;

import org.easymock.EasyMock;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Some unit tests for the DefaultTokenAuthorityService.
 */
public class DefaultTokenAuthorityServiceTest {
  @Test
  public void testTokenCreation() throws Exception {
    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("john.doe@example.com");

    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    EasyMock.expect(config.getGatewaySecurityDir()).andReturn(basedir + "/target/test-classes").anyTimes();
    EasyMock.expect(config.getGatewayKeystoreDir()).andReturn(basedir + "/target/test-classes/keystores").anyTimes();
    EasyMock.expect(config.getSigningKeystoreName()).andReturn("server-keystore.jks").anyTimes();
    EasyMock.expect(config.getSigningKeystorePath()).andReturn(basedir + "/target/test-classes/keystores/server-keystore.jks").anyTimes();
    EasyMock.expect(config.getSigningKeystorePasswordAlias()).andReturn(GatewayConfig.DEFAULT_SIGNING_KEYSTORE_PASSWORD_ALIAS).anyTimes();
    EasyMock.expect(config.getSigningKeyPassphraseAlias()).andReturn(GatewayConfig.DEFAULT_SIGNING_KEY_PASSPHRASE_ALIAS).anyTimes();
    EasyMock.expect(config.getSigningKeystoreType()).andReturn("jks").anyTimes();
    EasyMock.expect(config.getSigningKeyAlias()).andReturn("server").anyTimes();
    EasyMock.expect(config.getCredentialStoreType()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_TYPE).anyTimes();
    EasyMock.expect(config.getCredentialStoreAlgorithm()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_ALG).anyTimes();

    MasterService ms = EasyMock.createNiceMock(MasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("horton".toCharArray());

    AliasService as = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(as.getSigningKeyPassphrase()).andReturn("horton".toCharArray()).anyTimes();

    EasyMock.replay(principal, config, ms, as);

    DefaultKeystoreService ks = new DefaultKeystoreService();
    ks.setMasterService(ms);

    ks.init(config, new HashMap<>());

    DefaultTokenAuthorityService ta = new DefaultTokenAuthorityService();
    ta.setAliasService(as);
    ta.setKeystoreService(ks);

    ta.init(config, new HashMap<>());
    ta.start();

    JWT token = ta.issueToken(principal, "RS256");
    assertEquals("KNOXSSO", token.getIssuer());
    assertEquals("john.doe@example.com", token.getSubject());

    assertTrue(ta.verifyToken(token));
  }

  @Test
  public void testTokenCreationAudience() throws Exception {
    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("john.doe@example.com");

    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    EasyMock.expect(config.getGatewaySecurityDir()).andReturn(basedir + "/target/test-classes").anyTimes();
    EasyMock.expect(config.getGatewayKeystoreDir()).andReturn(basedir + "/target/test-classes/keystores").anyTimes();
    EasyMock.expect(config.getSigningKeystoreName()).andReturn("server-keystore.jks").anyTimes();
    EasyMock.expect(config.getSigningKeystorePath()).andReturn(basedir + "/target/test-classes/keystores/server-keystore.jks").anyTimes();
    EasyMock.expect(config.getSigningKeystorePasswordAlias()).andReturn(GatewayConfig.DEFAULT_SIGNING_KEYSTORE_PASSWORD_ALIAS).anyTimes();
    EasyMock.expect(config.getSigningKeyPassphraseAlias()).andReturn(GatewayConfig.DEFAULT_SIGNING_KEY_PASSPHRASE_ALIAS).anyTimes();
    EasyMock.expect(config.getSigningKeystoreType()).andReturn("jks").anyTimes();
    EasyMock.expect(config.getSigningKeyAlias()).andReturn("server").anyTimes();
    EasyMock.expect(config.getCredentialStoreType()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_TYPE).anyTimes();
    EasyMock.expect(config.getCredentialStoreAlgorithm()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_ALG).anyTimes();

    MasterService ms = EasyMock.createNiceMock(MasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("horton".toCharArray());

    AliasService as = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(as.getSigningKeyPassphrase()).andReturn("horton".toCharArray()).anyTimes();

    EasyMock.replay(principal, config, ms, as);

    DefaultKeystoreService ks = new DefaultKeystoreService();
    ks.setMasterService(ms);

    ks.init(config, new HashMap<>());

    DefaultTokenAuthorityService ta = new DefaultTokenAuthorityService();
    ta.setAliasService(as);
    ta.setKeystoreService(ks);

    ta.init(config, new HashMap<>());
    ta.start();

    JWT token = ta.issueToken(principal, "https://login.example.com", "RS256");
    assertEquals("KNOXSSO", token.getIssuer());
    assertEquals("john.doe@example.com", token.getSubject());
    assertEquals("https://login.example.com", token.getAudience());

    assertTrue(ta.verifyToken(token));
  }

  @Test
  public void testTokenCreationNullAudience() throws Exception {
    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("john.doe@example.com");

    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    EasyMock.expect(config.getGatewaySecurityDir()).andReturn(basedir + "/target/test-classes").anyTimes();
    EasyMock.expect(config.getGatewayKeystoreDir()).andReturn(basedir + "/target/test-classes/keystores").anyTimes();
    EasyMock.expect(config.getSigningKeystoreName()).andReturn("server-keystore.jks").anyTimes();
    EasyMock.expect(config.getSigningKeystorePath()).andReturn(basedir + "/target/test-classes/keystores/server-keystore.jks").anyTimes();
    EasyMock.expect(config.getSigningKeystorePasswordAlias()).andReturn(GatewayConfig.DEFAULT_SIGNING_KEYSTORE_PASSWORD_ALIAS).anyTimes();
    EasyMock.expect(config.getSigningKeyPassphraseAlias()).andReturn(GatewayConfig.DEFAULT_SIGNING_KEY_PASSPHRASE_ALIAS).anyTimes();
    EasyMock.expect(config.getSigningKeystoreType()).andReturn("jks").anyTimes();
    EasyMock.expect(config.getSigningKeyAlias()).andReturn("server").anyTimes();
    EasyMock.expect(config.getCredentialStoreType()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_TYPE).anyTimes();
    EasyMock.expect(config.getCredentialStoreAlgorithm()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_ALG).anyTimes();

    MasterService ms = EasyMock.createNiceMock(MasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("horton".toCharArray());

    AliasService as = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(as.getSigningKeyPassphrase()).andReturn("horton".toCharArray()).anyTimes();

    EasyMock.replay(principal, config, ms, as);

    DefaultKeystoreService ks = new DefaultKeystoreService();
    ks.setMasterService(ms);

    ks.init(config, new HashMap<>());

    DefaultTokenAuthorityService ta = new DefaultTokenAuthorityService();
    ta.setAliasService(as);
    ta.setKeystoreService(ks);

    ta.init(config, new HashMap<>());
    ta.start();

    JWT token = ta.issueToken(principal, null, "RS256");
    assertEquals("KNOXSSO", token.getIssuer());
    assertEquals("john.doe@example.com", token.getSubject());

    assertTrue(ta.verifyToken(token));
  }

  @Test
  public void testTokenCreationSignatureAlgorithm() throws Exception {
    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("john.doe@example.com");

    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    EasyMock.expect(config.getGatewaySecurityDir()).andReturn(basedir + "/target/test-classes").anyTimes();
    EasyMock.expect(config.getGatewayKeystoreDir()).andReturn(basedir + "/target/test-classes/keystores").anyTimes();
    EasyMock.expect(config.getSigningKeystoreName()).andReturn("server-keystore.jks").anyTimes();
    EasyMock.expect(config.getSigningKeystorePath()).andReturn(basedir + "/target/test-classes/keystores/server-keystore.jks").anyTimes();
    EasyMock.expect(config.getSigningKeystorePasswordAlias()).andReturn(GatewayConfig.DEFAULT_SIGNING_KEYSTORE_PASSWORD_ALIAS).anyTimes();
    EasyMock.expect(config.getSigningKeyPassphraseAlias()).andReturn(GatewayConfig.DEFAULT_SIGNING_KEY_PASSPHRASE_ALIAS).anyTimes();
    EasyMock.expect(config.getSigningKeystoreType()).andReturn("jks").anyTimes();
    EasyMock.expect(config.getSigningKeyAlias()).andReturn("server").anyTimes();
    EasyMock.expect(config.getCredentialStoreType()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_TYPE).anyTimes();
    EasyMock.expect(config.getCredentialStoreAlgorithm()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_ALG).anyTimes();

    MasterService ms = EasyMock.createNiceMock(MasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("horton".toCharArray());

    AliasService as = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(as.getSigningKeyPassphrase()).andReturn("horton".toCharArray()).anyTimes();

    EasyMock.replay(principal, config, ms, as);

    DefaultKeystoreService ks = new DefaultKeystoreService();
    ks.setMasterService(ms);

    ks.init(config, new HashMap<>());

    DefaultTokenAuthorityService ta = new DefaultTokenAuthorityService();
    ta.setAliasService(as);
    ta.setKeystoreService(ks);

    ta.init(config, new HashMap<>());
    ta.start();

    JWT token = ta.issueToken(principal, "RS512");
    assertEquals("KNOXSSO", token.getIssuer());
    assertEquals("john.doe@example.com", token.getSubject());
    assertTrue(token.getHeader().contains("RS512"));

    assertTrue(ta.verifyToken(token));
  }

  @Test (expected = TokenServiceException.class)
  public void testTokenCreationBadSignatureAlgorithm() throws Exception {
    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("john.doe@example.com");

    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    EasyMock.expect(config.getGatewaySecurityDir()).andReturn(basedir + "/target/test-classes").anyTimes();
    EasyMock.expect(config.getGatewayKeystoreDir()).andReturn(basedir + "/target/test-classes/keystores").anyTimes();
    EasyMock.expect(config.getSigningKeystoreName()).andReturn("server-keystore.jks").anyTimes();
    EasyMock.expect(config.getSigningKeystorePath()).andReturn(basedir + "/target/test-classes/keystores/server-keystore.jks").anyTimes();
    EasyMock.expect(config.getSigningKeystorePasswordAlias()).andReturn(GatewayConfig.DEFAULT_SIGNING_KEYSTORE_PASSWORD_ALIAS).anyTimes();
    EasyMock.expect(config.getSigningKeyPassphraseAlias()).andReturn(GatewayConfig.DEFAULT_SIGNING_KEY_PASSPHRASE_ALIAS).anyTimes();
    EasyMock.expect(config.getSigningKeystoreType()).andReturn("jks").anyTimes();
    EasyMock.expect(config.getSigningKeyAlias()).andReturn("server").anyTimes();
    EasyMock.expect(config.getCredentialStoreType()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_TYPE).anyTimes();
    EasyMock.expect(config.getCredentialStoreAlgorithm()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_ALG).anyTimes();

    MasterService ms = EasyMock.createNiceMock(MasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("horton".toCharArray());

    AliasService as = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(as.getSigningKeyPassphrase()).andReturn("horton".toCharArray()).anyTimes();

    EasyMock.replay(principal, config, ms, as);

    DefaultKeystoreService ks = new DefaultKeystoreService();
    ks.setMasterService(ms);

    ks.init(config, new HashMap<>());

    DefaultTokenAuthorityService ta = new DefaultTokenAuthorityService();
    ta.setAliasService(as);
    ta.setKeystoreService(ks);

    ta.init(config, new HashMap<>());
    ta.issueToken(principal, "none");
  }

  @Test
  public void testTokenCreationCustomSigningKey() throws Exception {
    /*
     Generated testSigningKeyName.jks with the following commands:
     cd gateway-server/src/test/resources/keystores/
     keytool -genkey -alias testSigningKeyAlias -keyalg RSA -keystore testSigningKeyName.jks \
         -storepass testSigningKeyPassphrase -keypass testSigningKeyPassphrase -keysize 2048 \
         -dname 'CN=testSigningKey,OU=example,O=Apache,L=US,ST=CA,C=US' -noprompt
     */

    String customSigningKeyName = "testSigningKeyName";
    String customSigningKeyAlias = "testSigningKeyAlias";
    String customSigningKeyPassphrase = "testSigningKeyPassphrase";

    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("john.doe@example.com");

    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    EasyMock.expect(config.getGatewaySecurityDir()).andReturn(basedir + "/target/test-classes").anyTimes();
    EasyMock.expect(config.getGatewayKeystoreDir()).andReturn(basedir + "/target/test-classes/keystores").anyTimes();
    EasyMock.expect(config.getSigningKeystoreName()).andReturn("server-keystore.jks").anyTimes();
    EasyMock.expect(config.getSigningKeystorePath()).andReturn(basedir + "/target/test-classes/keystores/server-keystore.jks").anyTimes();
    EasyMock.expect(config.getSigningKeystorePasswordAlias()).andReturn(GatewayConfig.DEFAULT_SIGNING_KEYSTORE_PASSWORD_ALIAS).anyTimes();
    EasyMock.expect(config.getSigningKeyPassphraseAlias()).andReturn(GatewayConfig.DEFAULT_SIGNING_KEY_PASSPHRASE_ALIAS).anyTimes();
    EasyMock.expect(config.getSigningKeystoreType()).andReturn("jks").anyTimes();
    EasyMock.expect(config.getSigningKeyAlias()).andReturn("server").anyTimes();
    EasyMock.expect(config.getCredentialStoreType()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_TYPE).anyTimes();
    EasyMock.expect(config.getCredentialStoreAlgorithm()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_ALG).anyTimes();

    MasterService ms = EasyMock.createNiceMock(MasterService.class);

    AliasService as = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(as.getSigningKeyPassphrase()).andReturn("horton".toCharArray()).anyTimes();

    EasyMock.replay(principal, config, ms, as);

    DefaultKeystoreService ks = new DefaultKeystoreService();
    ks.setMasterService(ms);
    ks.init(config, new HashMap<>());

    DefaultTokenAuthorityService ta = new DefaultTokenAuthorityService();
    ta.setAliasService(as);
    ta.setKeystoreService(ks);
    ta.init(config, new HashMap<>());

    JWT token = ta.issueToken(principal, Collections.emptyList(), "RS256", -1,
        customSigningKeyName, customSigningKeyAlias, customSigningKeyPassphrase.toCharArray());
    assertEquals("KNOXSSO", token.getIssuer());
    assertEquals("john.doe@example.com", token.getSubject());

    RSAPublicKey customPublicKey = (RSAPublicKey)ks.getSigningKeystore(customSigningKeyName)
                                                     .getCertificate(customSigningKeyAlias).getPublicKey();
    assertFalse(ta.verifyToken(token));
    assertTrue(ta.verifyToken(token, customPublicKey));
  }

  @Test
  public void testServiceStart() throws Exception {
    /*
     Generated testSigningKeyName.jks with the following commands:
     cd gateway-server/src/test/resources/keystores/
     keytool -genkey -alias testSigningKeyAlias -keyalg RSA -keystore testSigningKeyName.jks \
         -storepass testSigningKeyPassphrase -keypass testSigningKeyPassphrase -keysize 2048 \
         -dname 'CN=testSigningKey,OU=example,O=Apache,L=US,ST=CA,C=US' -noprompt
     */

    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    GatewayConfig config = EasyMock.createMock(GatewayConfig.class);
    EasyMock.expect(config.getGatewayKeystoreDir()).andReturn(basedir + "/target/test-classes/keystores").atLeastOnce();
    EasyMock.expect(config.getSigningKeystorePath()).andReturn(basedir + "/target/test-classes/keystores/server-keystore.jks").atLeastOnce();
    EasyMock.expect(config.getSigningKeystoreType()).andReturn("jks").atLeastOnce();
    EasyMock.expect(config.getSigningKeystorePasswordAlias()).andReturn(GatewayConfig.DEFAULT_SIGNING_KEYSTORE_PASSWORD_ALIAS).anyTimes();
    EasyMock.expect(config.getSigningKeyAlias()).andReturn("server").anyTimes();
    EasyMock.expect(config.getKeystoreCacheEntryTimeToLiveInMinutes()).andReturn(0L).anyTimes();
    EasyMock.expect(config.getKeystoreCacheSizeLimit()).andReturn(0L).anyTimes();
    EasyMock.expect(config.getCredentialStoreType()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_TYPE).anyTimes();
    EasyMock.expect(config.getCredentialStoreAlgorithm()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_ALG).anyTimes();

    MasterService ms = EasyMock.createMock(MasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("horton".toCharArray()).atLeastOnce();

    AliasService as = EasyMock.createMock(AliasService.class);
    EasyMock.expect(as.getSigningKeyPassphrase()).andReturn("horton".toCharArray()).atLeastOnce();

    EasyMock.replay(config, ms, as);

    DefaultKeystoreService ks = new DefaultKeystoreService();
    ks.setMasterService(ms);
    ks.init(config, new HashMap<>());
    ks.start();

    DefaultTokenAuthorityService ta = new DefaultTokenAuthorityService();
    ta.setAliasService(as);
    ta.setKeystoreService(ks);
    ta.init(config, new HashMap<>());
    ta.start();

    // Stop the started services...
    ta.stop();
    ks.stop();

    EasyMock.verify(config, ms, as);
  }

  @Test(expected = ServiceLifecycleException.class)
  public void testServiceStartMissingKeystore() throws Exception {
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    GatewayConfig config = EasyMock.createMock(GatewayConfig.class);
    EasyMock.expect(config.getGatewayKeystoreDir()).andReturn(basedir + "/target/test-classes/keystores").atLeastOnce();
    EasyMock.expect(config.getSigningKeystorePath()).andReturn(basedir + "/target/test-classes/keystores/missing-server-keystore.jks").atLeastOnce();
    EasyMock.expect(config.getSigningKeystoreType()).andReturn("jks").atLeastOnce();
    EasyMock.expect(config.getSigningKeystorePasswordAlias()).andReturn(GatewayConfig.DEFAULT_SIGNING_KEYSTORE_PASSWORD_ALIAS).anyTimes();
    EasyMock.expect(config.getKeystoreCacheEntryTimeToLiveInMinutes()).andReturn(0L).anyTimes();
    EasyMock.expect(config.getKeystoreCacheSizeLimit()).andReturn(0L).anyTimes();
    EasyMock.expect(config.getCredentialStoreType()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_TYPE).anyTimes();
    EasyMock.expect(config.getCredentialStoreAlgorithm()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_ALG).anyTimes();

    MasterService ms = EasyMock.createMock(MasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("horton".toCharArray()).atLeastOnce();

    AliasService as = EasyMock.createMock(AliasService.class);
    EasyMock.expect(as.getSigningKeyPassphrase()).andReturn("horton".toCharArray()).atLeastOnce();

    EasyMock.replay(config, ms, as);

    DefaultKeystoreService ks = new DefaultKeystoreService();
    ks.setMasterService(ms);
    ks.init(config, new HashMap<>());
    ks.start();

    DefaultTokenAuthorityService ta = new DefaultTokenAuthorityService();
    ta.setAliasService(as);
    ta.setKeystoreService(ks);
    ta.init(config, new HashMap<>());
    ta.start();

    EasyMock.verify(config, ms, as);
  }

  @Test(expected = ServiceLifecycleException.class)
  public void testServiceStartInvalidKeystorePassword() throws Exception {
    /*
     Generated testSigningKeyName.jks with the following commands:
     cd gateway-server/src/test/resources/keystores/
     keytool -genkey -alias testSigningKeyAlias -keyalg RSA -keystore testSigningKeyName.jks \
         -storepass testSigningKeyPassphrase -keypass testSigningKeyPassphrase -keysize 2048 \
         -dname 'CN=testSigningKey,OU=example,O=Apache,L=US,ST=CA,C=US' -noprompt
     */

    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    GatewayConfig config = EasyMock.createMock(GatewayConfig.class);
    EasyMock.expect(config.getGatewayKeystoreDir()).andReturn(basedir + "/target/test-classes/keystores").atLeastOnce();
    EasyMock.expect(config.getSigningKeystorePath()).andReturn(basedir + "/target/test-classes/keystores/server-keystore.jks").atLeastOnce();
    EasyMock.expect(config.getSigningKeystoreType()).andReturn("jks").atLeastOnce();
    EasyMock.expect(config.getSigningKeystorePasswordAlias()).andReturn(GatewayConfig.DEFAULT_SIGNING_KEYSTORE_PASSWORD_ALIAS).anyTimes();
    EasyMock.expect(config.getSigningKeyAlias()).andReturn("server").anyTimes();
    EasyMock.expect(config.getKeystoreCacheEntryTimeToLiveInMinutes()).andReturn(0L).anyTimes();
    EasyMock.expect(config.getKeystoreCacheSizeLimit()).andReturn(0L).anyTimes();
    EasyMock.expect(config.getCredentialStoreType()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_TYPE).anyTimes();
    EasyMock.expect(config.getCredentialStoreAlgorithm()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_ALG).anyTimes();

    MasterService ms = EasyMock.createMock(MasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("invalid_password".toCharArray()).atLeastOnce();

    AliasService as = EasyMock.createMock(AliasService.class);
    EasyMock.expect(as.getSigningKeyPassphrase()).andReturn("horton".toCharArray()).atLeastOnce();

    EasyMock.replay(config, ms, as);

    DefaultKeystoreService ks = new DefaultKeystoreService();
    ks.setMasterService(ms);
    ks.init(config, new HashMap<>());
    ks.start();

    DefaultTokenAuthorityService ta = new DefaultTokenAuthorityService();
    ta.setAliasService(as);
    ta.setKeystoreService(ks);
    ta.init(config, new HashMap<>());
    ta.start();

    EasyMock.verify(config, ms, as);
  }

  @Test(expected = ServiceLifecycleException.class)
  public void testServiceStartMissingKey() throws Exception {
    /*
     Generated testSigningKeyName.jks with the following commands:
     cd gateway-server/src/test/resources/keystores/
     keytool -genkey -alias testSigningKeyAlias -keyalg RSA -keystore testSigningKeyName.jks \
         -storepass testSigningKeyPassphrase -keypass testSigningKeyPassphrase -keysize 2048 \
         -dname 'CN=testSigningKey,OU=example,O=Apache,L=US,ST=CA,C=US' -noprompt
     */

    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    GatewayConfig config = EasyMock.createMock(GatewayConfig.class);
    EasyMock.expect(config.getGatewayKeystoreDir()).andReturn(basedir + "/target/test-classes/keystores").atLeastOnce();
    EasyMock.expect(config.getSigningKeystorePath()).andReturn(basedir + "/target/test-classes/keystores/server-keystore.jks").atLeastOnce();
    EasyMock.expect(config.getSigningKeystoreType()).andReturn("jks").atLeastOnce();
    EasyMock.expect(config.getSigningKeystorePasswordAlias()).andReturn(GatewayConfig.DEFAULT_SIGNING_KEYSTORE_PASSWORD_ALIAS).anyTimes();
    EasyMock.expect(config.getSigningKeyAlias()).andReturn("invalid_key").anyTimes();
    EasyMock.expect(config.getKeystoreCacheEntryTimeToLiveInMinutes()).andReturn(0L).anyTimes();
    EasyMock.expect(config.getKeystoreCacheSizeLimit()).andReturn(0L).anyTimes();
    EasyMock.expect(config.getCredentialStoreType()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_TYPE).anyTimes();
    EasyMock.expect(config.getCredentialStoreAlgorithm()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_ALG).anyTimes();

    MasterService ms = EasyMock.createMock(MasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("horton".toCharArray()).atLeastOnce();

    AliasService as = EasyMock.createMock(AliasService.class);
    EasyMock.expect(as.getSigningKeyPassphrase()).andReturn("horton".toCharArray()).atLeastOnce();

    EasyMock.replay(config, ms, as);

    DefaultKeystoreService ks = new DefaultKeystoreService();
    ks.setMasterService(ms);
    ks.init(config, new HashMap<>());
    ks.start();

    DefaultTokenAuthorityService ta = new DefaultTokenAuthorityService();
    ta.setAliasService(as);
    ta.setKeystoreService(ks);
    ta.init(config, new HashMap<>());
    ta.start();

    EasyMock.verify(config, ms, as);
  }

  @Test(expected = ServiceLifecycleException.class)
  public void testServiceInvalidKeyPassword() throws Exception {
    /*
     Generated testSigningKeyName.jks with the following commands:
     cd gateway-server/src/test/resources/keystores/
     keytool -genkey -alias testSigningKeyAlias -keyalg RSA -keystore testSigningKeyName.jks \
         -storepass testSigningKeyPassphrase -keypass testSigningKeyPassphrase -keysize 2048 \
         -dname 'CN=testSigningKey,OU=example,O=Apache,L=US,ST=CA,C=US' -noprompt
     */

    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    GatewayConfig config = EasyMock.createMock(GatewayConfig.class);
    EasyMock.expect(config.getGatewayKeystoreDir()).andReturn(basedir + "/target/test-classes/keystores").atLeastOnce();
    EasyMock.expect(config.getSigningKeystorePath()).andReturn(basedir + "/target/test-classes/keystores/server-keystore.jks").atLeastOnce();
    EasyMock.expect(config.getSigningKeystoreType()).andReturn("jks").atLeastOnce();
    EasyMock.expect(config.getSigningKeystorePasswordAlias()).andReturn(GatewayConfig.DEFAULT_SIGNING_KEYSTORE_PASSWORD_ALIAS).anyTimes();
    EasyMock.expect(config.getSigningKeyAlias()).andReturn("server").anyTimes();
    EasyMock.expect(config.getKeystoreCacheEntryTimeToLiveInMinutes()).andReturn(0L).anyTimes();
    EasyMock.expect(config.getKeystoreCacheSizeLimit()).andReturn(0L).anyTimes();
    EasyMock.expect(config.getCredentialStoreType()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_TYPE).anyTimes();
    EasyMock.expect(config.getCredentialStoreAlgorithm()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_ALG).anyTimes();

    MasterService ms = EasyMock.createMock(MasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("horton".toCharArray()).atLeastOnce();

    AliasService as = EasyMock.createMock(AliasService.class);
    EasyMock.expect(as.getSigningKeyPassphrase()).andReturn("invalid".toCharArray()).atLeastOnce();

    EasyMock.replay(config, ms, as);

    DefaultKeystoreService ks = new DefaultKeystoreService();
    ks.setMasterService(ms);
    ks.init(config, new HashMap<>());
    ks.start();

    DefaultTokenAuthorityService ta = new DefaultTokenAuthorityService();
    ta.setAliasService(as);
    ta.setKeystoreService(ks);
    ta.init(config, new HashMap<>());
    ta.start();

    EasyMock.verify(config, ms, as);
  }
}
