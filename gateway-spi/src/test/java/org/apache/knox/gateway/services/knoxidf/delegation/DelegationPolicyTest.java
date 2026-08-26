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

import org.junit.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests immutability invariants of {@link DelegationPolicy}.
 */
public class DelegationPolicyTest {

  @Test
  public void defensiveCopyOfCanActForUsers() {
    final Set<String> users = new HashSet<>();
    users.add("alice");
    final DelegationPolicy policy = policy(users, Collections.emptySet(), Collections.emptyMap());

    users.add("eve");

    assertEquals("Stored set must not reflect mutation of original", 1, policy.getCanActForUsers().size());
    assertTrue(policy.getCanActForUsers().contains("alice"));
    assertFalse(policy.getCanActForUsers().contains("eve"));
  }

  @Test
  public void defensiveCopyOfCanActForGroups() {
    final Set<String> groups = new HashSet<>();
    groups.add("admins");
    final DelegationPolicy policy = policy(Collections.emptySet(), groups, Collections.emptyMap());

    groups.add("operators");

    assertEquals("Stored set must not reflect mutation of original", 1, policy.getCanActForGroups().size());
    assertTrue(policy.getCanActForGroups().contains("admins"));
    assertFalse(policy.getCanActForGroups().contains("operators"));
  }

  @Test
  public void defensiveCopyOfResourcePolicyOuterMap() {
    final Map<String, Set<String>> resourcePolicy = new HashMap<>();
    resourcePolicy.put("/api/v1", new HashSet<>(Collections.singleton("read")));
    final DelegationPolicy policy = policy(Collections.emptySet(), Collections.emptySet(), resourcePolicy);

    resourcePolicy.put("/api/v2", new HashSet<>(Collections.singleton("write")));

    assertFalse("Stored map must not reflect mutation of original", policy.getResourcePolicy().containsKey("/api/v2"));
    assertEquals(1, policy.getResourcePolicy().size());
  }

  @Test
  public void defensiveCopyOfResourcePolicyInnerSet() {
    final Set<String> scopes = new HashSet<>();
    scopes.add("read");
    final Map<String, Set<String>> resourcePolicy = new HashMap<>();
    resourcePolicy.put("/api/v1", scopes);
    final DelegationPolicy policy = policy(Collections.emptySet(), Collections.emptySet(), resourcePolicy);

    scopes.add("write");

    final Set<String> storedScopes = policy.getResourcePolicy().get("/api/v1");
    assertEquals("Stored inner set must not reflect mutation of original", 1, storedScopes.size());
    assertTrue(storedScopes.contains("read"));
    assertFalse(storedScopes.contains("write"));
  }

  @Test
  public void returnedSetsAreUnmodifiable() {
    final DelegationPolicy policy = policy(
        new HashSet<>(Collections.singleton("alice")),
        new HashSet<>(Collections.singleton("admins")),
        Collections.emptyMap());

    assertThrows(UnsupportedOperationException.class, () -> policy.getCanActForUsers().add("eve"));
    assertThrows(UnsupportedOperationException.class, () -> policy.getCanActForGroups().add("ops"));
  }

  @Test
  public void returnedMapIsUnmodifiable() {
    final DelegationPolicy policy = policy(
        Collections.emptySet(), Collections.emptySet(),
        new HashMap<>(Collections.singletonMap("/api", new HashSet<>())));

    assertThrows(UnsupportedOperationException.class, () -> policy.getResourcePolicy().put("/other", new HashSet<>()));
  }

  @Test
  public void nullableFieldsArePreservedAsNull() {
    final DelegationPolicy policy = new DelegationPolicy(
        "reg-id", "oidc", "actor@example.com",
        null,   // name
        "active",
        null,   // tokenTtlSec
        null,   // description
        null,   // createdBy
        Instant.now(), Instant.now(), false,
        Collections.emptySet(), Collections.emptySet(), Collections.emptyMap());

    assertNull(policy.getName());
    assertNull(policy.getTokenTtlSec());
    assertNull(policy.getDescription());
    assertNull(policy.getCreatedBy());
  }

  private static DelegationPolicy policy(Set<String> users, Set<String> groups, Map<String, Set<String>> resourcePolicy) {
    return new DelegationPolicy(
        "reg-id", "oidc", "actor@example.com",
        "test-policy", "active", 3600, "desc", "admin",
        Instant.now(), Instant.now(), false,
        users, groups, resourcePolicy);
  }
}
