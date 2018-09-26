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
import java.util.HashMap;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.services.security.impl.DefaultKeystoreService;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.TokenServiceException;

import org.easymock.EasyMock;
import org.junit.Test;

/**
 * Some unit tests for the DefaultTokenAuthorityService.
 */
public class DefaultTokenAuthorityServiceTest extends org.junit.Assert {

  @Test
  public void testTokenCreation() throws Exception {

    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("john.doe@example.com");

    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    EasyMock.expect(config.getGatewaySecurityDir()).andReturn(basedir + "/target/test-classes");
    EasyMock.expect(config.getSigningKeystoreName()).andReturn("server-keystore.jks");
    EasyMock.expect(config.getSigningKeyAlias()).andReturn("server").anyTimes();

    MasterService ms = EasyMock.createNiceMock(MasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("horton".toCharArray());

    AliasService as = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(as.getGatewayIdentityPassphrase()).andReturn("horton".toCharArray());

    EasyMock.replay(principal, config, ms, as);

    KeystoreService ks = new DefaultKeystoreService();
    ((DefaultKeystoreService)ks).setMasterService(ms);

    ((DefaultKeystoreService)ks).init(config, new HashMap<String, String>());

    JWTokenAuthority ta = new DefaultTokenAuthorityService();
    ((DefaultTokenAuthorityService)ta).setAliasService(as);
    ((DefaultTokenAuthorityService)ta).setKeystoreService(ks);

    ((DefaultTokenAuthorityService)ta).init(config, new HashMap<String, String>());

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

    EasyMock.expect(config.getGatewaySecurityDir()).andReturn(basedir + "/target/test-classes");
    EasyMock.expect(config.getSigningKeystoreName()).andReturn("server-keystore.jks");
    EasyMock.expect(config.getSigningKeyAlias()).andReturn("server").anyTimes();

    MasterService ms = EasyMock.createNiceMock(MasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("horton".toCharArray());

    AliasService as = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(as.getGatewayIdentityPassphrase()).andReturn("horton".toCharArray());

    EasyMock.replay(principal, config, ms, as);

    KeystoreService ks = new DefaultKeystoreService();
    ((DefaultKeystoreService)ks).setMasterService(ms);

    ((DefaultKeystoreService)ks).init(config, new HashMap<String, String>());

    JWTokenAuthority ta = new DefaultTokenAuthorityService();
    ((DefaultTokenAuthorityService)ta).setAliasService(as);
    ((DefaultTokenAuthorityService)ta).setKeystoreService(ks);

    ((DefaultTokenAuthorityService)ta).init(config, new HashMap<String, String>());

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

    EasyMock.expect(config.getGatewaySecurityDir()).andReturn(basedir + "/target/test-classes");
    EasyMock.expect(config.getSigningKeystoreName()).andReturn("server-keystore.jks");
    EasyMock.expect(config.getSigningKeyAlias()).andReturn("server").anyTimes();

    MasterService ms = EasyMock.createNiceMock(MasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("horton".toCharArray());

    AliasService as = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(as.getGatewayIdentityPassphrase()).andReturn("horton".toCharArray());

    EasyMock.replay(principal, config, ms, as);

    KeystoreService ks = new DefaultKeystoreService();
    ((DefaultKeystoreService)ks).setMasterService(ms);

    ((DefaultKeystoreService)ks).init(config, new HashMap<String, String>());

    JWTokenAuthority ta = new DefaultTokenAuthorityService();
    ((DefaultTokenAuthorityService)ta).setAliasService(as);
    ((DefaultTokenAuthorityService)ta).setKeystoreService(ks);

    ((DefaultTokenAuthorityService)ta).init(config, new HashMap<String, String>());

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

    EasyMock.expect(config.getGatewaySecurityDir()).andReturn(basedir + "/target/test-classes");
    EasyMock.expect(config.getSigningKeystoreName()).andReturn("server-keystore.jks");
    EasyMock.expect(config.getSigningKeyAlias()).andReturn("server").anyTimes();

    MasterService ms = EasyMock.createNiceMock(MasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("horton".toCharArray());

    AliasService as = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(as.getGatewayIdentityPassphrase()).andReturn("horton".toCharArray());

    EasyMock.replay(principal, config, ms, as);

    KeystoreService ks = new DefaultKeystoreService();
    ((DefaultKeystoreService)ks).setMasterService(ms);

    ((DefaultKeystoreService)ks).init(config, new HashMap<String, String>());

    JWTokenAuthority ta = new DefaultTokenAuthorityService();
    ((DefaultTokenAuthorityService)ta).setAliasService(as);
    ((DefaultTokenAuthorityService)ta).setKeystoreService(ks);

    ((DefaultTokenAuthorityService)ta).init(config, new HashMap<String, String>());

    JWT token = ta.issueToken(principal, "RS512");
    assertEquals("KNOXSSO", token.getIssuer());
    assertEquals("john.doe@example.com", token.getSubject());
    assertTrue(token.getHeader().contains("RS512"));

    assertTrue(ta.verifyToken(token));
  }

  @Test
  public void testTokenCreationBadSignatureAlgorithm() throws Exception {

    Principal principal = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(principal.getName()).andReturn("john.doe@example.com");

    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    EasyMock.expect(config.getGatewaySecurityDir()).andReturn(basedir + "/target/test-classes");
    EasyMock.expect(config.getSigningKeystoreName()).andReturn("server-keystore.jks");
    EasyMock.expect(config.getSigningKeyAlias()).andReturn("server").anyTimes();

    MasterService ms = EasyMock.createNiceMock(MasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("horton".toCharArray());

    AliasService as = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(as.getGatewayIdentityPassphrase()).andReturn("horton".toCharArray());

    EasyMock.replay(principal, config, ms, as);

    KeystoreService ks = new DefaultKeystoreService();
    ((DefaultKeystoreService)ks).setMasterService(ms);

    ((DefaultKeystoreService)ks).init(config, new HashMap<String, String>());

    JWTokenAuthority ta = new DefaultTokenAuthorityService();
    ((DefaultTokenAuthorityService)ta).setAliasService(as);
    ((DefaultTokenAuthorityService)ta).setKeystoreService(ks);

    ((DefaultTokenAuthorityService)ta).init(config, new HashMap<String, String>());

    try {
      ta.issueToken(principal, "none");
      fail("Failure expected on a bad signature algorithm");
    } catch (TokenServiceException ex) {
        // expected
    }
  }

}
