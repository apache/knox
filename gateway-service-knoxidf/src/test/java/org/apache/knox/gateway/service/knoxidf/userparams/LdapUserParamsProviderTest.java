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
package org.apache.knox.gateway.service.knoxidf.userparams;

import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;
import jakarta.servlet.ServletContext;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import static org.easymock.EasyMock.aryEq;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Verifies how {@link LdapUserParamsProvider} finds the user (subtree search under {@code userSearchBase} or a
 * direct read of the {@code userDnTemplate} DN), resolves roles (from {@code memberOf}, falling back to the
 * group search) and that the system password is only read from the configured alias.
 */
public class LdapUserParamsProviderTest {

  private static final String ALL_SCOPES = "openid profile email roles";

  @Test
  public void testSystemPasswordAliasNameIsReadFromConfig() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(LdapUserParamsProvider.LDAP_SYSTEM_PASSWORD_ALIAS, "gateway_ldap_bind_password");
    final AliasService aliasService = EasyMock.createMock(AliasService.class);
    expect(aliasService.getPasswordFromAliasForGateway("gateway_ldap_bind_password")).andReturn("secret".toCharArray());
    EasyMock.replay(aliasService);

    new LdapUserParamsProvider(servletContext(params, aliasService));

    EasyMock.verify(aliasService);
  }

  @Test
  public void testMissingSystemPasswordAliasFailsLookup() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(LdapUserParamsProvider.LDAP_URL, "ldap://localhost:10389");
    // No expectations: without a configured alias no alias (in particular no default one) may be read.
    final AliasService aliasService = EasyMock.createStrictMock(AliasService.class);
    EasyMock.replay(aliasService);

    final LdapUserParamsProvider provider = new LdapUserParamsProvider(servletContext(params, aliasService));

    final IllegalStateException e = assertLookupFails(provider);
    assertTrue(e.getMessage().contains(LdapUserParamsProvider.LDAP_SYSTEM_PASSWORD_ALIAS));
    EasyMock.verify(aliasService);
  }

  @Test
  public void testUnresolvableSystemPasswordAliasFailsLookup() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(LdapUserParamsProvider.LDAP_URL, "ldap://localhost:10389");
    params.put(LdapUserParamsProvider.LDAP_SYSTEM_PASSWORD_ALIAS, "no_such_alias");
    final AliasService aliasService = EasyMock.createMock(AliasService.class);
    expect(aliasService.getPasswordFromAliasForGateway("no_such_alias")).andReturn(null);
    EasyMock.replay(aliasService);

    final LdapUserParamsProvider provider = new LdapUserParamsProvider(servletContext(params, aliasService));

    final IllegalStateException e = assertLookupFails(provider);
    assertTrue(e.getMessage().contains("no_such_alias"));
    EasyMock.verify(aliasService);
  }

  @Test
  public void testUserSearchBaseSearchesSubtreeAndDerivesRolesFromMemberOf() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put("user.params.provider.ldap.baseDn", "dc=proxy,dc=com");
    params.put(LdapUserParamsProvider.LDAP_USER_SEARCH_BASE, "ou=people,dc=proxy,dc=com");

    final BasicAttributes attrs = userAttributes();
    final Attribute memberOf = new BasicAttribute("memberOf");
    memberOf.add("cn=project1:admin,ou=groups,dc=proxy,dc=com");
    memberOf.add("CN=global:admin,OU=Groups,DC=proxy,DC=com");
    // not directly under ou=groups,<baseDn>: ignored, like the group search would
    memberOf.add("cn=team,ou=teams,dc=proxy,dc=com");
    memberOf.add("cn=nested,ou=sub,ou=groups,dc=proxy,dc=com");
    memberOf.add("not a dn");
    attrs.put(memberOf);

    // A strict mock: any other search (e.g. the group scan) fails the test.
    final LdapContext ctx = EasyMock.createStrictMock(LdapContext.class);
    final Capture<SearchControls> controls = Capture.newInstance();
    expect(ctx.search(eq("ou=people,dc=proxy,dc=com"), eq(LdapUserParamsProvider.DEFAULT_USER_SEARCH_FILTER),
        aryEq(new Object[]{"alice"}), capture(controls)))
        .andReturn(results(searchResult("uid=alice,ou=people,dc=proxy,dc=com", attrs)));
    ctx.close();
    EasyMock.replay(ctx);

    final Map<String, Object> userParams = provider(params, ctx).getParamsFor("alice", ALL_SCOPES);

    EasyMock.verify(ctx);
    assertEquals(SearchControls.SUBTREE_SCOPE, controls.getValue().getSearchScope());
    assertTrue(Arrays.asList(controls.getValue().getReturningAttributes()).contains("memberOf"));
    assertEquals("alice", userParams.get("sub"));
    assertEquals("Alice Admin", userParams.get("name"));
    assertEquals("Admin", userParams.get("family_name"));
    assertEquals("Alice", userParams.get("given_name"));
    assertEquals("alice@example.com", userParams.get("email"));
    assertEquals(Arrays.asList("project1:admin", "global:admin"), userParams.get("roles"));
  }

  @Test
  public void testUserDnTemplateReadsEntryAndFallsBackToGroupSearch() throws Exception {
    final String userDn = "uid=alice,ou=people,dc=hadoop,dc=apache,dc=org";

    final BasicAttributes group = new BasicAttributes(true);
    group.put("cn", "analysts");
    final Attribute member = new BasicAttribute("member");
    member.add("uid=bob,ou=people,dc=hadoop,dc=apache,dc=org");
    member.add(userDn.toUpperCase(Locale.ROOT));
    group.put(member);
    final BasicAttributes otherGroup = new BasicAttributes(true);
    otherGroup.put("cn", "admins");
    otherGroup.put("member", "uid=bob,ou=people,dc=hadoop,dc=apache,dc=org");

    final LdapContext ctx = EasyMock.createStrictMock(LdapContext.class);
    final Capture<SearchControls> controls = Capture.newInstance();
    expect(ctx.search(eq(userDn), eq("(objectClass=*)"), capture(controls)))
        .andReturn(results(searchResult(userDn, userAttributes())));
    expect(ctx.search(eq("ou=groups,dc=hadoop,dc=apache,dc=org"), eq("(objectClass=groupOfNames)"),
        EasyMock.anyObject(SearchControls.class)))
        .andReturn(results(searchResult("cn=analysts,ou=groups,dc=hadoop,dc=apache,dc=org", group),
            searchResult("cn=admins,ou=groups,dc=hadoop,dc=apache,dc=org", otherGroup)));
    ctx.close();
    EasyMock.replay(ctx);

    final Map<String, Object> userParams = provider(new HashMap<>(), ctx).getParamsFor("alice", ALL_SCOPES);

    EasyMock.verify(ctx);
    assertEquals(SearchControls.OBJECT_SCOPE, controls.getValue().getSearchScope());
    assertEquals("alice@example.com", userParams.get("email"));
    assertEquals(Collections.singletonList("analysts"), userParams.get("roles"));
  }

  @Test
  public void testUserNotFoundYieldsNoParams() throws Exception {
    final Map<String, String> params = new HashMap<>();
    params.put(LdapUserParamsProvider.LDAP_USER_SEARCH_BASE, "ou=people,dc=proxy,dc=com");
    params.put(LdapUserParamsProvider.LDAP_USER_SEARCH_FILTER, "(&(objectClass=person)(uid={0}))");

    final LdapContext ctx = EasyMock.createStrictMock(LdapContext.class);
    expect(ctx.search(eq("ou=people,dc=proxy,dc=com"), eq("(&(objectClass=person)(uid={0}))"),
        aryEq(new Object[]{"nobody"}), EasyMock.anyObject(SearchControls.class)))
        .andReturn(results());
    ctx.close();
    EasyMock.replay(ctx);

    final Map<String, Object> userParams = provider(params, ctx).getParamsFor("nobody", ALL_SCOPES);

    EasyMock.verify(ctx);
    assertFalse(userParams.containsKey("sub"));
    assertTrue(userParams.isEmpty());
  }

  // The provider fails closed: the bind is never attempted and the lookup reports the configuration problem.
  private static IllegalStateException assertLookupFails(LdapUserParamsProvider provider) {
    try {
      provider.getParamsFor("alice", ALL_SCOPES);
      fail("Expected the user parameter lookup to fail");
      return null;
    } catch (RuntimeException e) {
      assertTrue(e.getCause() instanceof IllegalStateException);
      return (IllegalStateException) e.getCause();
    }
  }

  private static BasicAttributes userAttributes() {
    final BasicAttributes attrs = new BasicAttributes(true);
    attrs.put("cn", "Alice Admin");
    attrs.put("sn", "Admin");
    attrs.put("givenName", "Alice");
    attrs.put("mail", "alice@example.com");
    return attrs;
  }

  private static SearchResult searchResult(String dn, BasicAttributes attrs) {
    final SearchResult result = new SearchResult(dn, null, attrs);
    result.setNameInNamespace(dn);
    return result;
  }

  private static LdapUserParamsProvider provider(Map<String, String> params, LdapContext ctx) {
    params.putIfAbsent(LdapUserParamsProvider.LDAP_URL, "ldap://localhost:10389");
    params.putIfAbsent(LdapUserParamsProvider.LDAP_SYSTEM_PASSWORD_ALIAS, "ldap_system_password");
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    try {
      expect(aliasService.getPasswordFromAliasForGateway(EasyMock.anyString())).andReturn("secret".toCharArray()).anyTimes();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    EasyMock.replay(aliasService);
    return new LdapUserParamsProvider(servletContext(params, aliasService)) {
      @Override
      LdapContext createSystemContext() {
        return ctx;
      }
    };
  }

  private static ServletContext servletContext(Map<String, String> params, AliasService aliasService) {
    final GatewayServices services = EasyMock.createNiceMock(GatewayServices.class);
    expect(services.getService(ServiceType.ALIAS_SERVICE)).andReturn(aliasService).anyTimes();
    final ServletContext servletContext = EasyMock.createNiceMock(ServletContext.class);
    expect(servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(services).anyTimes();
    expect(servletContext.getInitParameter(EasyMock.anyString()))
        .andAnswer(() -> params.get((String) EasyMock.getCurrentArguments()[0])).anyTimes();
    EasyMock.replay(services, servletContext);
    return servletContext;
  }

  private static NamingEnumeration<SearchResult> results(SearchResult... results) {
    final Iterator<SearchResult> it = Arrays.asList(results).iterator();
    return new NamingEnumeration<SearchResult>() {
      @Override
      public SearchResult next() {
        return it.next();
      }

      @Override
      public boolean hasMore() {
        return it.hasNext();
      }

      @Override
      public void close() {
      }

      @Override
      public boolean hasMoreElements() {
        return it.hasNext();
      }

      @Override
      public SearchResult nextElement() {
        return it.next();
      }
    };
  }
}
