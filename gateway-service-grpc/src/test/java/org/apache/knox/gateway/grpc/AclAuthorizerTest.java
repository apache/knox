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
package org.apache.knox.gateway.grpc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.knox.gateway.filter.InvalidACLException;

import org.junit.Test;

public class AclAuthorizerTest {

  private static final String ROLE = "SPARKCONNECT";
  private static final String IP = "10.0.0.5";

  @Test
  public void allowsEverythingWhenTheTopologyDeclaresNoAcls() throws Exception {
    // Matching the servlet provider: a topology that never mentions the role does
    // not silently deny it.
    final AclAuthorizer authorizer = authorizer(Collections.emptyMap());
    assertTrue(authorizer.isPermitted("alice", groups(), IP));
  }

  @Test
  public void andModeRequiresUserGroupAndAddressToMatch() throws Exception {
    final AclAuthorizer authorizer = authorizer(acls("alice;analysts;10.0.0.*", "AND"));

    assertTrue(authorizer.isPermitted("alice", groups("analysts"), IP));
    assertFalse("wrong user", authorizer.isPermitted("mallory", groups("analysts"), IP));
    assertFalse("wrong group", authorizer.isPermitted("alice", groups("interns"), IP));
    assertFalse("wrong address", authorizer.isPermitted("alice", groups("analysts"), "192.168.1.1"));
  }

  @Test
  public void andModeAcceptsAGrouplessSubjectAgainstAGroupWildcard() throws Exception {
    // A token minted without a groups claim must still satisfy '*;*;10.0.0.*'.
    final AclAuthorizer authorizer = authorizer(acls("*;*;10.0.0.*", "AND"));
    assertTrue(authorizer.isPermitted("alice", groups(), IP));
  }

  @Test
  public void andModeRejectsAGrouplessSubjectAgainstANamedGroup() throws Exception {
    final AclAuthorizer authorizer = authorizer(acls("*;analysts;*", "AND"));
    assertFalse(authorizer.isPermitted("alice", groups(), IP));
  }

  @Test
  public void orModeAcceptsAnySingleMatch() throws Exception {
    final AclAuthorizer authorizer = authorizer(acls("alice;analysts;10.0.0.*", "OR"));

    assertTrue("user match alone", authorizer.isPermitted("alice", groups("interns"), "192.168.1.1"));
    assertTrue("group match alone", authorizer.isPermitted("mallory", groups("analysts"), "192.168.1.1"));
    assertTrue("address match alone", authorizer.isPermitted("mallory", groups("interns"), IP));
    assertFalse(authorizer.isPermitted("mallory", groups("interns"), "192.168.1.1"));
  }

  @Test
  public void orModeTreatsWildcardsAsNoReasonToGrant() throws Exception {
    // Otherwise a single '*' in any position would admit everyone, which inverts
    // the intent of an OR policy.
    final AclAuthorizer authorizer = authorizer(acls("*;analysts;*", "OR"));

    assertTrue(authorizer.isPermitted("alice", groups("analysts"), IP));
    assertFalse(authorizer.isPermitted("alice", groups("interns"), IP));
  }

  @Test
  public void resolvesTheKnoxAdminPlaceholders() throws Exception {
    final AclAuthorizer users = new AclAuthorizer(ROLE,
        acls("KNOX_ADMIN_USERS;*;*", "AND"), "admin,root", "wheel");
    assertTrue(users.isPermitted("admin", groups(), IP));
    assertFalse(users.isPermitted("alice", groups(), IP));

    final AclAuthorizer adminGroups = new AclAuthorizer(ROLE,
        acls("*;KNOX_ADMIN_GROUPS;*", "AND"), "admin", "wheel");
    assertTrue(adminGroups.isPermitted("alice", groups("wheel"), IP));
    assertFalse(adminGroups.isPermitted("alice", groups("interns"), IP));
  }

  @Test
  public void defaultsToAndWhenNoModeIsConfigured() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(ROLE + ".acl", "alice;*;*");
    final AclAuthorizer authorizer = authorizer(params);

    assertTrue(authorizer.isPermitted("alice", groups(), IP));
    assertFalse(authorizer.isPermitted("mallory", groups(), IP));
  }

  @Test
  public void readsParametersCaseInsensitively() throws Exception {
    // Provider params are lowercased when they become filter params on the
    // servlet path, so the same topology XML has to work here either way.
    final Map<String, String> params = new HashMap<>();
    params.put("sparkconnect.acl", "alice;*;*");
    final AclAuthorizer authorizer = authorizer(params);

    assertTrue(authorizer.isPermitted("alice", groups(), IP));
    assertFalse(authorizer.isPermitted("mallory", groups(), IP));
  }

  @Test
  public void fallsBackToTheSharedAclMode() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(ROLE + ".acl", "alice;analysts;10.0.0.*");
    params.put("acl.mode", "OR");
    final AclAuthorizer authorizer = authorizer(params);

    assertTrue(authorizer.isPermitted("mallory", groups("analysts"), "192.168.1.1"));
  }

  @Test(expected = InvalidACLException.class)
  public void rejectsAMalformedAcl() throws Exception {
    authorizer(acls("alice;analysts", "AND"));
  }

  private static AclAuthorizer authorizer(Map<String, String> params) throws InvalidACLException {
    return new AclAuthorizer(ROLE, params, "", "");
  }

  private static Map<String, String> acls(String acl, String mode) {
    final Map<String, String> params = new HashMap<>();
    params.put(ROLE + ".acl", acl);
    params.put(ROLE + ".acl.mode", mode);
    return params;
  }

  private static Set<String> groups(String... names) {
    return new HashSet<>(java.util.Arrays.asList(names));
  }
}
