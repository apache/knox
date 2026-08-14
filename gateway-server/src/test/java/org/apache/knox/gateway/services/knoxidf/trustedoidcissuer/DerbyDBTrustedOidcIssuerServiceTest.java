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
package org.apache.knox.gateway.services.knoxidf.trustedoidcissuer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.gateway.database.DatabaseType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.test.TestUtils;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies that {@link DerbyDBTrustedOidcIssuerService} self-provisions an embedded Derby database
 * and round-trips a trusted OIDC issuer through it.
 */
public class DerbyDBTrustedOidcIssuerServiceTest {

  private File securityDir;
  private DerbyDBTrustedOidcIssuerService service;

  @Before
  public void setUp() throws IOException {
    securityDir = TestUtils.createTempDir(this.getClass().getName());
  }

  @After
  public void tearDown() throws Exception {
    if (service != null) {
      service.stop();
    }
    if (securityDir != null) {
      FileUtils.forceDelete(securityDir);
    }
  }

  @Test
  public void shouldRoundTripATrustedIssuerOnEmbeddedDerby() throws Exception {
    service = newService(newConfig());

    final TrustedOidcIssuer issuer = new TrustedOidcIssuer(
        "https://issuer.example.com/realms/knox", true, "clusterA", Instant.now(), "admin");
    service.register(issuer);

    assertTrue("Expected the registered issuer to be trusted", service.isTrusted(issuer.getIssuerUrl()));
    assertTrue("Expected the registered issuer to be dynamic-jwks", service.isDynamicJwks(issuer.getIssuerUrl()));
    assertFalse("Did not expect an unknown issuer to be trusted", service.isTrusted("https://unknown.example.com"));

    final List<TrustedOidcIssuer> all = service.list();
    assertEquals(1, all.size());
    assertEquals(issuer.getIssuerUrl(), all.get(0).getIssuerUrl());

    service.deregister(issuer.getIssuerUrl());
    assertFalse("Expected the issuer to be gone after deregister", service.isTrusted(issuer.getIssuerUrl()));
  }

  /**
   * Regression guard for the "Table/View 'TRUSTED_OIDC_ISSUERS' already exists" failure on restart:
   * re-initialising against the same on-disk Derby database (as happens on a Knox restart) must not
   * try to re-create the already-present table.
   */
  @Test
  public void shouldReinitializeWithoutErrorWhenTableAlreadyExists() throws Exception {
    service = newService(newConfig());
    final TrustedOidcIssuer issuer = new TrustedOidcIssuer(
        "https://issuer.example.com/realms/knox", false, null, Instant.now(), "admin");
    service.register(issuer);
    service.stop();

    // Simulate a restart: a brand-new service instance pointing at the same Derby folder.
    service = newService(newConfig());
    assertTrue("Expected the previously-registered issuer to survive a restart",
        service.isTrusted(issuer.getIssuerUrl()));
  }

  private DerbyDBTrustedOidcIssuerService newService(GatewayConfigImpl config) throws Exception {
    final MasterService masterService = EasyMock.createNiceMock(MasterService.class);
    EasyMock.expect(masterService.getMasterSecret()).andReturn("M4st3RSecret!".toCharArray()).anyTimes();
    EasyMock.replay(masterService);

    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.replay(aliasService);

    final DerbyDBTrustedOidcIssuerService svc = new DerbyDBTrustedOidcIssuerService();
    svc.setAliasService(aliasService);
    svc.setMasterService(masterService);
    svc.init(config, Collections.emptyMap());
    return svc;
  }

  private GatewayConfigImpl newConfig() {
    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.expect(config.getGatewaySecurityDir()).andReturn(securityDir.getAbsolutePath()).anyTimes();
    EasyMock.expect(config.getDatabaseType()).andReturn(DatabaseType.DERBY.type()).anyTimes();
    EasyMock.expect(config.getDatabaseName()).andReturn(Paths.get(securityDir.getAbsolutePath(), "tokens").toString()).anyTimes();
    EasyMock.expect(config.getTrustedOidcIssuerMaxTrustedIssuers()).andReturn(10).anyTimes();
    EasyMock.expect(config.getTrustedOidcIssuerDiscoveryCacheTtlSecs()).andReturn(300).anyTimes();
    EasyMock.expect(config.getTrustedOidcIssuerDiscoveryConnectTimeoutMs()).andReturn(2000).anyTimes();
    EasyMock.expect(config.getTrustedOidcIssuerDiscoveryReadTimeoutMs()).andReturn(2000).anyTimes();
    EasyMock.replay(config);
    return config;
  }
}
