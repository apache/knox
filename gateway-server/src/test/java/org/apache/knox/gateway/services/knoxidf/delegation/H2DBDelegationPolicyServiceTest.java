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
package org.apache.knox.gateway.services.knoxidf.delegation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.gateway.database.AbstractDataSourceFactory;
import org.apache.knox.gateway.database.DatabaseType;
import org.apache.knox.gateway.database.EmbeddedH2Database;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.test.TestUtils;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies that {@link H2DBDelegationPolicyService} self-provisions an embedded H2 database and
 * round-trips a delegation policy through it (KNOX-3401, the embedded backend replacing Derby).
 * <p>
 * This is the decisive POC test: the delegation-policy schema is five inter-dependent
 * {@code CREATE TABLE} statements in a single SQL resource, executed as one script during
 * {@code init()}. It passes only because embedded H2 executes multi-statement DDL natively, which
 * is the capability the backend was chosen for.
 */
public class H2DBDelegationPolicyServiceTest {

  private File securityDir;
  private H2DBDelegationPolicyService service;

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
  public void shouldRoundTripADelegationPolicyOnEmbeddedH2() throws Exception {
    service = newH2Service();

    final Map<String, Set<String>> resourcePolicy = new HashMap<>();
    resourcePolicy.put("/api/v1", new HashSet<>(Arrays.asList("read", "write")));
    final Instant now = Instant.now();
    final DelegationPolicy input = new DelegationPolicy(null, "oidc", "actor-1", "policy-name", "active",
        3600, "desc", "admin", now, now, false,
        new HashSet<>(Arrays.asList("alice", "bob")), Collections.emptySet(), resourcePolicy);

    final DelegationPolicy registered = service.register(input);
    assertNotNull("register must return a policy with a generated id", registered.getRegistrationId());

    final Optional<DelegationPolicy> fetched = service.get(registered.getRegistrationId());
    assertTrue("Expected the policy to be found by its registration id", fetched.isPresent());
    assertEquals("oidc", fetched.get().getActorAuthority());
    assertEquals("actor-1", fetched.get().getActorId());
    assertEquals("policy-name", fetched.get().getName());
    assertEquals(Integer.valueOf(3600), fetched.get().getTokenTtlSec());
    assertEquals(2, fetched.get().getCanActForUsers().size());
    assertTrue(fetched.get().getCanActForUsers().contains("alice"));
    assertEquals(new HashSet<>(Arrays.asList("read", "write")),
        fetched.get().getResourcePolicy().get("/api/v1"));

    // The database file H2 created on first connection.
    final File dbFile = Paths.get(securityDir.getAbsolutePath(), EmbeddedH2Database.DB_FOLDER,
        EmbeddedH2Database.DB_FILE_BASE + ".mv.db").toFile();
    assertTrue("Embedded H2 must have created its database file", dbFile.isFile());
  }

  /**
   * Re-initialising against the same on-disk H2 database (as happens on a Knox restart) must not
   * fail re-running the delegation-policy DDL: the {@code CREATE TABLE IF NOT EXISTS} statements
   * are idempotent, so the previously-persisted policy survives.
   */
  @Test
  public void shouldReinitializeWithoutErrorWhenTablesAlreadyExist() throws Exception {
    service = newH2Service();
    final Instant now = Instant.now();
    final DelegationPolicy input = new DelegationPolicy(null, "oidc", "actor-restart", null, "active",
        null, null, null, now, now, false,
        Collections.singleton("alice"), Collections.emptySet(), Collections.emptyMap());
    final DelegationPolicy registered = service.register(input);
    service.stop();

    // Simulate a restart: a brand-new service instance pointing at the same H2 folder.
    service = newH2Service();
    final Optional<DelegationPolicy> fetched = service.get(registered.getRegistrationId());
    assertTrue("Expected the previously-persisted policy to survive a restart", fetched.isPresent());
    assertEquals("actor-restart", fetched.get().getActorId());
  }

  private H2DBDelegationPolicyService newH2Service() throws Exception {
    final MasterService masterService = EasyMock.createNiceMock(MasterService.class);
    EasyMock.expect(masterService.getMasterSecret()).andReturn("M4st3RSecret!".toCharArray()).anyTimes();
    EasyMock.replay(masterService);

    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_USER_ALIAS_NAME))
        .andReturn("knox".toCharArray()).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForGateway(AbstractDataSourceFactory.DATABASE_PASSWORD_ALIAS_NAME))
        .andReturn("kn0xDbP4ss!".toCharArray()).anyTimes();
    EasyMock.replay(aliasService);

    final GatewayConfigImpl config = EasyMock.createNiceMock(GatewayConfigImpl.class);
    EasyMock.expect(config.getGatewaySecurityDir()).andReturn(securityDir.getAbsolutePath()).anyTimes();
    EasyMock.expect(config.getDatabaseType()).andReturn(DatabaseType.H2.type()).anyTimes();
    EasyMock.expect(config.getDatabaseName())
        .andReturn(Paths.get(securityDir.getAbsolutePath(), EmbeddedH2Database.DB_FOLDER, EmbeddedH2Database.DB_FILE_BASE).toString())
        .anyTimes();
    EasyMock.expect(config.getDelegationServiceTokenTtlSec()).andReturn(7200).anyTimes();
    EasyMock.expect(config.getDelegationServiceListMaxTotal())
        .andReturn(GatewayConfig.DELEGATION_SERVICE_LIST_MAX_TOTAL_DEFAULT).anyTimes();
    EasyMock.expect(config.getDelegationServiceListMaxPerAuthority())
        .andReturn(GatewayConfig.DELEGATION_SERVICE_LIST_MAX_PER_AUTHORITY_DEFAULT).anyTimes();
    EasyMock.replay(config);

    final H2DBDelegationPolicyService svc = new H2DBDelegationPolicyService();
    svc.setAliasService(aliasService);
    svc.setMasterService(masterService);
    svc.init(config, Collections.emptyMap());
    return svc;
  }
}
