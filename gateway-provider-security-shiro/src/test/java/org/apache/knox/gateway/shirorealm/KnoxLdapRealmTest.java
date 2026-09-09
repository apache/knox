/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.knox.gateway.shirorealm;

import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.naming.NamingEnumeration;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class KnoxLdapRealmTest {

  private static String captureSearchFilter(KnoxLdapRealm realm, String principal) throws Exception {
    LdapContextFactory factory = EasyMock.createNiceMock(LdapContextFactory.class);
    LdapContext ctx = EasyMock.createNiceMock(LdapContext.class);
    NamingEnumeration<SearchResult> results = EasyMock.createNiceMock(NamingEnumeration.class);

    EasyMock.expect(factory.getSystemLdapContext()).andReturn(ctx).anyTimes();
    Capture<String> filter = EasyMock.newCapture();
    EasyMock.expect(ctx.search(EasyMock.anyString(), EasyMock.capture(filter),
        EasyMock.anyObject(SearchControls.class))).andReturn(results);
    EasyMock.expect(results.hasMore()).andReturn(false).anyTimes();
    EasyMock.replay(factory, ctx, results);

    realm.setContextFactory(factory);
    try {
      realm.getUserDn(principal);
    } catch (IllegalArgumentException expected) {
      // mock returns no entry, so getUserDn throws after the search; we only need the filter
    }
    return filter.getValue();
  }

  private static String captureSearchBase(KnoxLdapRealm realm, String principal) throws Exception {
    LdapContextFactory factory = EasyMock.createNiceMock(LdapContextFactory.class);
    LdapContext ctx = EasyMock.createNiceMock(LdapContext.class);
    NamingEnumeration<SearchResult> results = EasyMock.createNiceMock(NamingEnumeration.class);

    EasyMock.expect(factory.getSystemLdapContext()).andReturn(ctx).anyTimes();
    Capture<String> base = EasyMock.newCapture();
    EasyMock.expect(ctx.search(EasyMock.capture(base), EasyMock.anyString(),
        EasyMock.anyObject(SearchControls.class))).andReturn(results);
    EasyMock.expect(results.hasMore()).andReturn(false).anyTimes();
    EasyMock.replay(factory, ctx, results);

    realm.setContextFactory(factory);
    try {
      realm.getUserDn(principal);
    } catch (IllegalArgumentException expected) {
      // mock returns no entry, so getUserDn throws after the search; we only need the base
    }
    return base.getValue();
  }

  private static KnoxLdapRealm searchModeRealm() {
    KnoxLdapRealm realm = new KnoxLdapRealm();
    realm.setSearchBase("dc=hadoop,dc=apache,dc=org");
    realm.setUserSearchBase("ou=people,dc=hadoop,dc=apache,dc=org");
    realm.setUserSearchAttributeName("uid");
    realm.setUserObjectClass("person");
    return realm;
  }

  @Test
  public void getUserDnEscapesLdapFilterMetacharacters() throws Exception {
    String filter = captureSearchFilter(searchModeRealm(), "*)(uid=admin");
    assertEquals("(&(objectclass=person)(uid=\\2a\\29\\28uid=admin))", filter);
  }

  @Test
  public void getUserDnEscapesWildcard() throws Exception {
    String filter = captureSearchFilter(searchModeRealm(), "*");
    assertEquals("(&(objectclass=person)(uid=\\2a))", filter);
  }

  @Test
  public void getUserDnEscapesBackslash() throws Exception {
    String filter = captureSearchFilter(searchModeRealm(), "a\\b");
    assertEquals("(&(objectclass=person)(uid=a\\5cb))", filter);
  }

  @Test
  public void getUserDnLeavesLegitimateUsernameUnchanged() throws Exception {
    String filter = captureSearchFilter(searchModeRealm(), "sam");
    assertEquals("(&(objectclass=person)(uid=sam))", filter);
  }

  @Test
  public void getUserDnEscapesValueButPreservesOperatorFilterStructure() throws Exception {
    KnoxLdapRealm realm = searchModeRealm();
    realm.setUserSearchFilter("(uid={0})");
    String filter = captureSearchFilter(realm, "a)(b");
    assertEquals("(uid=a\\29\\28b)", filter);
  }

  @Test
  public void getUserDnEscapesSearchBaseTemplateValue() throws Exception {
    KnoxLdapRealm realm = searchModeRealm();
    // A userSearchBase that substitutes the raw principal into the base DN.
    realm.setUserSearchBase("ou={0},dc=hadoop,dc=apache,dc=org");
    // Injection metacharacters in the username must be DN-escaped so they
    // cannot add or rewrite RDNs in the search base.
    String base = captureSearchBase(realm, "people,dc=evil");
    assertEquals("ou=people\\,dc\\=evil,dc=hadoop,dc=apache,dc=org", base);
  }

  @Test
  public void getUserDnWithDefaultTemplateReturnsFullDnUnescaped() {
    // The default userDnTemplate is "{0}", meaning the principal IS the complete bind DN
    // (the system-bind case, e.g. KnoxCLI system-user-auth-test). DN-escaping would turn
    // the ','/'=' separators into '\,'/'\=' and corrupt the DN, so this template must pass
    // the principal through unescaped. Embedded templates ("uid={0},...") still escape.
    KnoxLdapRealm realm = new KnoxLdapRealm();
    String dn = realm.getUserDn("uid=guest,ou=people,dc=hadoop,dc=apache,dc=org");
    assertEquals("uid=guest,ou=people,dc=hadoop,dc=apache,dc=org", dn);
  }

  @Test(timeout = 5000, expected = IllegalArgumentException.class)
  public void getUserDnRejectsTemplatePlaceholderAsUsername() {
    // A username of "{0}" substituted into a template would re-introduce a "{0}" token
    // and loop forever. It must be rejected (auth failure), not expanded. The timeout
    // guards against regression of the infinite loop.
    KnoxLdapRealm realm = new KnoxLdapRealm();
    realm.setUserDnTemplate("uid={0},ou=people,dc=hadoop,dc=apache,dc=org");
    realm.getUserDn("{0}");
  }

  @Test
  public void setGetSearchBase() {
    KnoxLdapRealm realm = new KnoxLdapRealm();
    realm.setSearchBase("dc=hadoop,dc=apache,dc=org");
    assertEquals(realm.getSearchBase(), "dc=hadoop,dc=apache,dc=org");
  }

  @Test
  public void setGetGroupObjectClass() {
    KnoxLdapRealm realm = new KnoxLdapRealm();
    realm.setGroupObjectClass("groupOfMembers");
    assertEquals(realm.getGroupObjectClass(), "groupOfMembers");
  }

  @Test
  public void setGetUniqueMemberAttribute() {
    KnoxLdapRealm realm = new KnoxLdapRealm();
    realm.setMemberAttribute("member");
    assertEquals(realm.getMemberAttribute(), "member");
  }

  @Test
  public void setGetUserSearchAttributeName() {
    KnoxLdapRealm realm = new KnoxLdapRealm();
    realm.setUserSearchAttributeName("uid");
    assertEquals(realm.getUserSearchAttributeName(), "uid");
  }

  @Test
  public void setGetUserObjectClass() {
    KnoxLdapRealm realm = new KnoxLdapRealm();
    realm.setUserObjectClass("inetuser");
    assertEquals(realm.getUserObjectClass(), "inetuser");
  }

  @Test
  public void setGetUserSearchBase() {
    KnoxLdapRealm realm = new KnoxLdapRealm();
    realm.setSearchBase("dc=example,dc=com");
    realm.setUserSearchBase("dc=knox,dc=example,dc=com");
    assertEquals(realm.getUserSearchBase(), "dc=knox,dc=example,dc=com");
  }

  @Test
  public void setGetGroupSearchBase() {
    KnoxLdapRealm realm = new KnoxLdapRealm();
    realm.setSearchBase("dc=example,dc=com");
    realm.setGroupSearchBase("dc=knox,dc=example,dc=com");
    assertEquals(realm.getGroupSearchBase(), "dc=knox,dc=example,dc=com");
  }

  @Test
  public void verifyDefaultUserSearchAttributeName() {
    KnoxLdapRealm realm = new KnoxLdapRealm();
    assertNull(realm.getUserSearchAttributeName());
  }

  @Test
  public void verifyDefaultGetUserObjectClass() {
    KnoxLdapRealm realm = new KnoxLdapRealm();
    assertEquals(realm.getUserObjectClass(), "person");
  }

  @Test
  public void verifyDefaultUserSearchBase() {
    KnoxLdapRealm realm = new KnoxLdapRealm();
    realm.setSearchBase("dc=knox,dc=example,dc=com");
    assertEquals(realm.getUserSearchBase(), "dc=knox,dc=example,dc=com");
  }

  @Test
  public void verifyDefaultGroupSearchBase() {
    KnoxLdapRealm realm = new KnoxLdapRealm();
    realm.setSearchBase("dc=knox,dc=example,dc=com");
    assertEquals(realm.getGroupSearchBase(), "dc=knox,dc=example,dc=com");
  }
}
