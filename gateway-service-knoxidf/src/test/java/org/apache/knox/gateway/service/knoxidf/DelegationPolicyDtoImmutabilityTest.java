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
package org.apache.knox.gateway.service.knoxidf;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that {@link DelegationPolicyRequest}, {@link DelegationPolicyResponse} and
 * {@link DelegationPolicyListResponse} never expose or retain a mutable view of their collection
 * fields: mutating the collection passed into a setter after the call must not affect the stored
 * value, and mutating the collection returned by a getter must fail.
 */
public class DelegationPolicyDtoImmutabilityTest {

  private static final String USER = "alice";
  private static final String GROUP = "admins";
  private static final String RESOURCE = "hdfs://ns1";
  private static final String SCOPE = "read";

  // ---- DelegationPolicyRequest ----

  @Test
  public void requestCopyOfFiltersNullElements() {
    // JSON null elements (from arrays like ["alice", null]) must be silently filtered rather
    // than letting Set.copyOf throw NullPointerException and surface as a 500.
    final Set<String> withNull = new LinkedHashSet<>(Arrays.asList(USER, null));
    final DelegationPolicyRequest request = new DelegationPolicyRequest();
    request.setCanActForUsers(withNull);
    assertEquals(Collections.singleton(USER), request.getCanActForUsers());
  }

  @Test
  public void requestCopyOfFiltersNullElementsInResourcePolicyScopes() {
    final Set<String> scopesWithNull = new LinkedHashSet<>(Arrays.asList(SCOPE, null));
    final Map<String, Set<String>> source = new LinkedHashMap<>();
    source.put(RESOURCE, scopesWithNull);
    final DelegationPolicyRequest request = new DelegationPolicyRequest();
    request.setResourcePolicy(source);
    assertEquals(Collections.singleton(SCOPE), request.getResourcePolicy().get(RESOURCE));
  }

  @Test
  public void requestCopyOfFiltersNullKeysInResourcePolicy() {
    final Map<String, Set<String>> source = new LinkedHashMap<>();
    source.put(null, Collections.singleton(SCOPE));
    source.put(RESOURCE, Collections.singleton(SCOPE));
    final DelegationPolicyRequest request = new DelegationPolicyRequest();
    request.setResourcePolicy(source);
    assertEquals(1, request.getResourcePolicy().size());
    assertTrue(request.getResourcePolicy().containsKey(RESOURCE));
  }

  @Test
  public void requestCanActForUsersNotAffectedByMutatingSourceAfterSet() {
    final Set<String> source = new LinkedHashSet<>(Collections.singletonList(USER));
    final DelegationPolicyRequest request = new DelegationPolicyRequest();
    request.setCanActForUsers(source);
    source.add("mallory");
    assertEquals(Collections.singleton(USER), request.getCanActForUsers());
  }

  @Test
  public void requestCanActForUsersGetterIsUnmodifiable() {
    final DelegationPolicyRequest request = new DelegationPolicyRequest();
    request.setCanActForUsers(Collections.singleton(USER));
    assertThrows(UnsupportedOperationException.class, () -> request.getCanActForUsers().add("mallory"));
  }

  @Test
  public void requestCanActForGroupsNotAffectedByMutatingSourceAfterSet() {
    final Set<String> source = new LinkedHashSet<>(Collections.singletonList(GROUP));
    final DelegationPolicyRequest request = new DelegationPolicyRequest();
    request.setCanActForGroups(source);
    source.add("mallory-group");
    assertEquals(Collections.singleton(GROUP), request.getCanActForGroups());
  }

  @Test
  public void requestCanActForGroupsGetterIsUnmodifiable() {
    final DelegationPolicyRequest request = new DelegationPolicyRequest();
    request.setCanActForGroups(Collections.singleton(GROUP));
    assertThrows(UnsupportedOperationException.class, () -> request.getCanActForGroups().add("mallory-group"));
  }

  @Test
  public void requestResourcePolicyNotAffectedByMutatingSourceMapOrScopeSetAfterSet() {
    final Set<String> scopes = new LinkedHashSet<>(Collections.singletonList(SCOPE));
    final Map<String, Set<String>> source = new LinkedHashMap<>();
    source.put(RESOURCE, scopes);

    final DelegationPolicyRequest request = new DelegationPolicyRequest();
    request.setResourcePolicy(source);

    source.put("hdfs://ns2", Collections.singleton("write"));
    scopes.add("write");

    assertEquals(Collections.singletonMap(RESOURCE, Collections.singleton(SCOPE)), request.getResourcePolicy());
  }

  @Test
  public void requestResourcePolicyGetterMapIsUnmodifiable() {
    final DelegationPolicyRequest request = new DelegationPolicyRequest();
    request.setResourcePolicy(Collections.singletonMap(RESOURCE, Collections.singleton(SCOPE)));
    assertThrows(UnsupportedOperationException.class,
        () -> request.getResourcePolicy().put("hdfs://ns2", Collections.singleton("write")));
  }

  @Test
  public void requestResourcePolicyGetterScopeSetIsUnmodifiable() {
    final DelegationPolicyRequest request = new DelegationPolicyRequest();
    request.setResourcePolicy(Collections.singletonMap(RESOURCE, Collections.singleton(SCOPE)));
    assertThrows(UnsupportedOperationException.class,
        () -> request.getResourcePolicy().get(RESOURCE).add("write"));
  }

  // ---- DelegationPolicyResponse ----

  @Test
  public void responseCanActForUsersNotAffectedByMutatingSourceAfterSet() {
    final Set<String> source = new LinkedHashSet<>(Collections.singletonList(USER));
    final DelegationPolicyResponse response = new DelegationPolicyResponse();
    response.setCanActForUsers(source);
    source.add("mallory");
    assertEquals(Collections.singleton(USER), response.getCanActForUsers());
  }

  @Test
  public void responseCanActForUsersGetterIsUnmodifiable() {
    final DelegationPolicyResponse response = new DelegationPolicyResponse();
    response.setCanActForUsers(Collections.singleton(USER));
    assertThrows(UnsupportedOperationException.class, () -> response.getCanActForUsers().add("mallory"));
  }

  @Test
  public void responseCanActForGroupsNotAffectedByMutatingSourceAfterSet() {
    final Set<String> source = new LinkedHashSet<>(Collections.singletonList(GROUP));
    final DelegationPolicyResponse response = new DelegationPolicyResponse();
    response.setCanActForGroups(source);
    source.add("mallory-group");
    assertEquals(Collections.singleton(GROUP), response.getCanActForGroups());
  }

  @Test
  public void responseCanActForGroupsGetterIsUnmodifiable() {
    final DelegationPolicyResponse response = new DelegationPolicyResponse();
    response.setCanActForGroups(Collections.singleton(GROUP));
    assertThrows(UnsupportedOperationException.class, () -> response.getCanActForGroups().add("mallory-group"));
  }

  @Test
  public void responseResourcePolicyNotAffectedByMutatingSourceMapOrScopeSetAfterSet() {
    final Set<String> scopes = new LinkedHashSet<>(Collections.singletonList(SCOPE));
    final Map<String, Set<String>> source = new LinkedHashMap<>();
    source.put(RESOURCE, scopes);

    final DelegationPolicyResponse response = new DelegationPolicyResponse();
    response.setResourcePolicy(source);

    source.put("hdfs://ns2", Collections.singleton("write"));
    scopes.add("write");

    assertEquals(Collections.singletonMap(RESOURCE, Collections.singleton(SCOPE)), response.getResourcePolicy());
  }

  @Test
  public void responseResourcePolicyGetterMapIsUnmodifiable() {
    final DelegationPolicyResponse response = new DelegationPolicyResponse();
    response.setResourcePolicy(Collections.singletonMap(RESOURCE, Collections.singleton(SCOPE)));
    assertThrows(UnsupportedOperationException.class,
        () -> response.getResourcePolicy().put("hdfs://ns2", Collections.singleton("write")));
  }

  @Test
  public void responseResourcePolicyGetterScopeSetIsUnmodifiable() {
    final DelegationPolicyResponse response = new DelegationPolicyResponse();
    response.setResourcePolicy(Collections.singletonMap(RESOURCE, Collections.singleton(SCOPE)));
    assertThrows(UnsupportedOperationException.class,
        () -> response.getResourcePolicy().get(RESOURCE).add("write"));
  }

  // ---- DelegationPolicyListResponse ----

  @Test
  public void listResponsePoliciesNotAffectedByMutatingSourceAfterSet() {
    final DelegationPolicyResponse policy = new DelegationPolicyResponse();
    policy.setRegistrationId("reg-1");
    final List<DelegationPolicyResponse> source = new ArrayList<>(Collections.singletonList(policy));

    final DelegationPolicyListResponse listResponse = new DelegationPolicyListResponse();
    listResponse.setPolicies(source);

    source.add(new DelegationPolicyResponse());

    assertEquals(1, listResponse.getPolicies().size());
  }

  @Test
  public void listResponsePoliciesGetterIsUnmodifiable() {
    final DelegationPolicyListResponse listResponse = new DelegationPolicyListResponse();
    listResponse.setPolicies(Collections.singletonList(new DelegationPolicyResponse()));
    assertThrows(UnsupportedOperationException.class,
        () -> listResponse.getPolicies().add(new DelegationPolicyResponse()));
  }
}
