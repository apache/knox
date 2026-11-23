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
package org.apache.knox.gateway.filter;

import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.security.auth.Subject;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.http.HttpServletRequest;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PathAclsAuthzCanonicalPathTest {
  private static final String SVC = "https://localhost:8443/gateway/z/svc/";
  private static final String ADMIN_API = "https://localhost:8443/gateway/t/knoxidf/admin/v1/";

  private static final String BROAD_RULE = "testsvc_broad.path.acl";
  private static final String ADMIN_RULE = "testsvc_admin.path.acl";

  private static final String[] ADMIN_PATH_VARIANTS = {
      "admin/op",
      "%61dmin/op",
      "%61%64%6d%69%6e/op",
      "admin;x=1/op",
      "./admin/op",
      "public/../admin/op",
      "public/%2e%2e/admin/op",
      "public/%2E%2E/admin/op",
      "public%2F..%2Fadmin/op",
      "public/..;x/admin/op",
      "public%5C..%5Cadmin/op",
      "/admin/op"
  };

  @Test
  public void testRestrictedPathVariantsAreDeniedInAndModeRegardlessOfRuleOrder() throws Exception {
    for (Map<String, String> rules : bothOrders(BROAD_RULE, "*://*:*/**;*;*;*", ADMIN_RULE, "*://*:*/**/admin/**;sam;*;*")) {
      for (String variant : ADMIN_PATH_VARIANTS) {
        assertFalse("guest should be denied on " + variant + " with rules " + rules.keySet(),
            isGranted("TESTSVC", "AND", rules, SVC + variant, "guest"));
      }
      assertTrue(isGranted("TESTSVC", "AND", rules, SVC + "admin/op", "sam"));
      assertTrue(isGranted("TESTSVC", "AND", rules, SVC + "%61dmin/op", "sam"));
      assertTrue(isGranted("TESTSVC", "AND", rules, SVC + "public/op", "guest"));
      assertTrue(isGranted("TESTSVC", "AND", rules, SVC + "%70ublic/op", "guest"));
    }
  }

  @Test
  public void testRestrictedPathVariantsAreDeniedInOrModeRegardlessOfRuleOrder() throws Exception {
    for (Map<String, String> rules : bothOrders(BROAD_RULE, "*://*:*/**;*;users;*", ADMIN_RULE, "*://*:*/**/admin/**;sam;*;*")) {
      for (String variant : ADMIN_PATH_VARIANTS) {
        assertFalse("guest should be denied on " + variant + " with rules " + rules.keySet(),
            isGranted("TESTSVC", "OR", rules, SVC + variant, "guest", "users"));
      }
      assertTrue(isGranted("TESTSVC", "OR", rules, SVC + "admin/op", "sam", "users"));
      assertTrue(isGranted("TESTSVC", "OR", rules, SVC + "public/op", "guest", "users"));
    }
  }

  @Test
  public void testWildcardRuleGrantsNobodyInOrMode() throws Exception {
    for (Map<String, String> rules : bothOrders(BROAD_RULE, "*://*:*/**;*;*;*", ADMIN_RULE, "*://*:*/**/admin/**;sam;*;*")) {
      assertFalse(isGranted("TESTSVC", "OR", rules, SVC + "public/op", "guest"));
      assertFalse(isGranted("TESTSVC", "OR", rules, SVC + "admin/op", "sam"));
    }
  }

  @Test
  public void testRestrictiveRuleAloneFailsClosed() throws Exception {
    final Map<String, String> rules = new LinkedHashMap<>();
    rules.put(ADMIN_RULE, "*://*:*/**/admin/**;sam;*;*");
    assertTrue(isGranted("TESTSVC", "AND", rules, SVC + "admin/op", "sam"));
    assertFalse(isGranted("TESTSVC", "AND", rules, SVC + "admin/op", "guest"));
    assertFalse(isGranted("TESTSVC", "AND", rules, SVC + "%61dmin/op", "guest"));
    assertFalse(isGranted("TESTSVC", "AND", rules, SVC + "public/op", "guest"));
  }

  @Test
  public void testNonCanonicalizablePathsAreDenied() throws Exception {
    final Map<String, String> rules = new LinkedHashMap<>();
    rules.put(BROAD_RULE, "*://*:*/**;*;*;*");
    assertTrue(isGranted("TESTSVC", "AND", rules, SVC + "public/op", "guest"));
    assertFalse(isGranted("TESTSVC", "AND", rules, SVC + "%zzdmin/op", "guest"));
    assertFalse(isGranted("TESTSVC", "AND", rules, SVC + "admin%2", "guest"));
    assertFalse(isGranted("TESTSVC", "AND", rules, "https://localhost:8443/gateway/../../../admin/op", "guest"));
  }

  @Test
  public void testAdminRolesCannotCrossIntoEachOther() throws Exception {
    final Map<String, String> rules = knoxIdfAdminRules();
    final String collision = ADMIN_API + "delegation-policies/X/knoxidf/admin/v1/trusted-oidc-issuers";
    assertFalse(isGranted("KNOXIDF_ADMIN", "OR", rules, collision, "issuer-admin"));
    assertFalse(isGranted("KNOXIDF_ADMIN", "OR", rules, collision, "delegation-admin"));
    assertFalse(isGranted("KNOXIDF_ADMIN", "OR", rules, ADMIN_API + "trusted-oidc-issuers/%2e%2e/delegation-policies", "issuer-admin"));
    assertFalse(isGranted("KNOXIDF_ADMIN", "OR", rules, ADMIN_API + "trusted-oidc-issuers%2F..%2Fdelegation-policies/p1", "issuer-admin"));
  }

  @Test
  public void testAdminRolesReachTheirOwnEndpoints() throws Exception {
    final Map<String, String> rules = knoxIdfAdminRules();
    for (String path : new String[] {"trusted-oidc-issuers", "trusted-oidc-issuers/", "trusted-oidc-issuers/abc", "trusted-oidc-issuers/abc/def"}) {
      assertTrue(path, isGranted("KNOXIDF_ADMIN", "OR", rules, ADMIN_API + path, "issuer-admin"));
      assertTrue(path, isGranted("KNOXIDF_ADMIN", "OR", rules, ADMIN_API + path, "someone", "issuer-admins"));
      assertFalse(path, isGranted("KNOXIDF_ADMIN", "OR", rules, ADMIN_API + path, "delegation-admin"));
    }
    for (String path : new String[] {"delegation-policies", "delegation-policies/p1"}) {
      assertTrue(path, isGranted("KNOXIDF_ADMIN", "OR", rules, ADMIN_API + path, "delegation-admin"));
      assertFalse(path, isGranted("KNOXIDF_ADMIN", "OR", rules, ADMIN_API + path, "issuer-admin"));
    }
    assertFalse(isGranted("KNOXIDF_ADMIN", "OR", rules, ADMIN_API + "other", "issuer-admin"));
  }

  @Test
  public void testEncodedFormsAreJudgedByTheMatchingAcl() throws Exception {
    final Map<String, String> rules = knoxIdfAdminRules();
    for (String path : new String[] {"%74rusted-oidc-issuers/abc", "trusted-oidc-issuers;x/abc", "%74rusted-oidc-issuers"}) {
      assertFalse(path, isGranted("KNOXIDF_ADMIN", "OR", rules, ADMIN_API + path, "delegation-admin"));
      assertTrue(path, isGranted("KNOXIDF_ADMIN", "OR", rules, ADMIN_API + path, "issuer-admin"));
    }
  }

  private static Map<String, String> knoxIdfAdminRules() {
    final Map<String, String> rules = new LinkedHashMap<>();
    rules.put("KNOXIDF_ADMIN.rule_issuers.path.acl",
        "*://*:*/**/knoxidf/admin/v1/trusted-oidc-issuers**;issuer-admin;issuer-admins;*");
    rules.put("KNOXIDF_ADMIN.rule_delegation.path.acl",
        "*://*:*/**/knoxidf/admin/v1/delegation-policies**;delegation-admin;delegation-admins;*");
    rules.put("KNOXIDF_ADMIN.rule_issuers_subpaths.path.acl",
        "*://*:*/**/knoxidf/admin/v1/trusted-oidc-issuers/**;issuer-admin;issuer-admins;*");
    rules.put("KNOXIDF_ADMIN.rule_delegation_subpaths.path.acl",
        "*://*:*/**/knoxidf/admin/v1/delegation-policies/**;delegation-admin;delegation-admins;*");
    return rules;
  }

  private static List<Map<String, String>> bothOrders(String firstName, String firstRule, String secondName, String secondRule) {
    final Map<String, String> forward = new LinkedHashMap<>();
    forward.put(firstName, firstRule);
    forward.put(secondName, secondRule);
    final Map<String, String> reverse = new LinkedHashMap<>();
    reverse.put(secondName, secondRule);
    reverse.put(firstName, firstRule);
    final List<Map<String, String>> orders = new ArrayList<>();
    orders.add(forward);
    orders.add(reverse);
    return orders;
  }

  private static boolean isGranted(String role, String mode, Map<String, String> rules, String url,
      String user, String... groups) throws Exception {
    final List<String> names = new ArrayList<>(rules.keySet());
    names.add("resource.role");
    final FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(config.getInitParameterNames()).andAnswer(() -> Collections.enumeration(names)).anyTimes();
    EasyMock.expect(config.getInitParameter("resource.role")).andReturn(role).anyTimes();
    EasyMock.expect(config.getInitParameter(role.toLowerCase(Locale.ROOT) + ".path.acl.mode")).andReturn(mode).anyTimes();
    for (Map.Entry<String, String> rule : rules.entrySet()) {
      EasyMock.expect(config.getInitParameter(rule.getKey())).andReturn(rule.getValue()).anyTimes();
    }
    EasyMock.replay(config);

    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL()).andAnswer(() -> new StringBuffer(url)).anyTimes();
    EasyMock.expect(request.getRemoteAddr()).andReturn("127.0.0.1").anyTimes();
    EasyMock.replay(request);

    final PathAclsAuthorizationFilter filter = new PathAclsAuthorizationFilter();
    filter.init(config);

    final Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal(user));
    for (String group : groups) {
      subject.getPrincipals().add(new GroupPrincipal(group));
    }
    try {
      return Subject.doAs(subject, (PrivilegedExceptionAction<Boolean>) () -> filter.enforceAclAuthorizationPolicy(request));
    } catch (PrivilegedActionException e) {
      throw e.getException();
    }
  }
}
