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
package org.apache.knox.gateway.services.knoxidf.federation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
 * Verifies that {@link DerbyDBFederatedIdentityService} self-provisions an embedded Derby database
 * and round-trips a federated identity through it.
 */
public class DerbyDBFederatedIdentityServiceTest {

  private File securityDir;
  private DerbyDBFederatedIdentityService service;

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
  public void shouldRoundTripAFederatedIdentityOnEmbeddedDerby() throws Exception {
    final String masterSecret = "M4st3RSecret!";
    final MasterService masterService = EasyMock.createNiceMock(MasterService.class);
    EasyMock.expect(masterService.getMasterSecret()).andReturn(masterSecret.toCharArray()).anyTimes();
    EasyMock.replay(masterService);

    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.replay(aliasService);

    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.expect(config.getGatewaySecurityDir()).andReturn(securityDir.getAbsolutePath()).anyTimes();
    EasyMock.expect(config.getDatabaseType()).andReturn(DatabaseType.DERBY.type()).anyTimes();
    EasyMock.expect(config.getDatabaseName()).andReturn(Paths.get(securityDir.getAbsolutePath(), "tokens").toString()).anyTimes();
    EasyMock.replay(config);

    service = new DerbyDBFederatedIdentityService();
    service.setAliasService(aliasService);
    service.setMasterService(masterService);
    service.init(config, Collections.emptyMap());

    final Map<String, String> attributes = new HashMap<>();
    attributes.put("email", "alice@example.com");
    final FederatedIdentity identity = new FederatedIdentity("knox-user-1", "KEYCLOAK", "external-subject-1",
        "https://issuer.example.com/realms/knox", Instant.now(), attributes);
    service.addFederatedIdentity(identity);

    final Optional<FederatedIdentity> byId = service.findById(identity.getId());
    assertTrue("Expected the identity to be found by id", byId.isPresent());
    assertEquals("KEYCLOAK", byId.get().getProvider());
    assertEquals("alice@example.com", byId.get().getAttribute("email"));

    final Optional<FederatedIdentity> byProviderAndSubject = service.findByProviderAndSubject(
        "KEYCLOAK", "https://issuer.example.com/realms/knox", "external-subject-1");
    assertTrue("Expected the identity to be found by provider/issuer/subject", byProviderAndSubject.isPresent());
    assertEquals(identity.getId(), byProviderAndSubject.get().getId());

    final Optional<FederatedIdentity> missing = service.findByProviderAndSubject(
        "KEYCLOAK", "https://issuer.example.com/realms/knox", "no-such-subject");
    assertFalse("Did not expect an identity for an unknown subject", missing.isPresent());
  }

  /**
   * Regression guard for the "Table/View 'FEDERATED_IDENTITY' already exists" failure on restart:
   * re-initialising against the same on-disk Derby database (as happens on a Knox restart) must not
   * try to re-create the already-present tables. Before the {@code JDBCUtils.tableExists} casing
   * fix, the lowercase {@code federated_identity} table name never matched Derby's uppercased
   * metadata, so init re-ran the CREATE and blew up on the second boot.
   */
  @Test
  public void shouldReinitializeWithoutErrorWhenTablesAlreadyExist() throws Exception {
    service = newDerbyService();
    final FederatedIdentity identity = new FederatedIdentity("knox-user-1", "KEYCLOAK", "external-subject-1",
        "https://issuer.example.com/realms/knox", Instant.now(), new HashMap<>());
    service.addFederatedIdentity(identity);
    service.stop();

    // Simulate a restart: a brand-new service instance pointing at the same Derby folder.
    service = newDerbyService();
    final Optional<FederatedIdentity> byId = service.findById(identity.getId());
    assertTrue("Expected the previously-persisted identity to survive a restart", byId.isPresent());
  }

  private DerbyDBFederatedIdentityService newDerbyService() throws Exception {
    final MasterService masterService = EasyMock.createNiceMock(MasterService.class);
    EasyMock.expect(masterService.getMasterSecret()).andReturn("M4st3RSecret!".toCharArray()).anyTimes();
    EasyMock.replay(masterService);

    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.replay(aliasService);

    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.expect(config.getGatewaySecurityDir()).andReturn(securityDir.getAbsolutePath()).anyTimes();
    EasyMock.expect(config.getDatabaseType()).andReturn(DatabaseType.DERBY.type()).anyTimes();
    EasyMock.expect(config.getDatabaseName()).andReturn(Paths.get(securityDir.getAbsolutePath(), "tokens").toString()).anyTimes();
    EasyMock.replay(config);

    final DerbyDBFederatedIdentityService svc = new DerbyDBFederatedIdentityService();
    svc.setAliasService(aliasService);
    svc.setMasterService(masterService);
    svc.init(config, Collections.emptyMap());
    return svc;
  }
}
