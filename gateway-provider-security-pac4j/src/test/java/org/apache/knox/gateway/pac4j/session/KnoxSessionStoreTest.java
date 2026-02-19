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
package org.apache.knox.gateway.pac4j.session;

import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.CryptoService;
import org.apache.knox.gateway.services.security.impl.DefaultCryptoService;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;
import org.pac4j.core.context.Cookie;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.saml.profile.SAML2Profile;

import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_CUSTOM_ATTRIBUTES;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_GROUPS;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_GROUPS_DEFAULT;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS_DEFAULT;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_ROLES;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_ROLES_DEFAULT;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_SECURE_COOKIE;
import static org.apache.knox.gateway.pac4j.session.KnoxSessionStore.PAC4J_PASSWORD;
import static org.apache.knox.gateway.pac4j.session.KnoxSessionStore.PAC4J_SESSION_PREFIX;

public class KnoxSessionStoreTest {
  private static final String CLUSTER_NAME = "knox";

  /**
   * Test exclusion of groups, roles and permissions
   * from pac4j profile object that is saved as a cookie.
   * @throws AliasServiceException
   */
  @Test
  public void filterConfigParamsTest()
      throws AliasServiceException {
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForCluster(CLUSTER_NAME, PAC4J_PASSWORD, true))
        .andReturn(PAC4J_PASSWORD.toCharArray()).anyTimes();
    EasyMock.expect(aliasService.getPasswordFromAliasForCluster(CLUSTER_NAME, PAC4J_PASSWORD))
        .andReturn(PAC4J_PASSWORD.toCharArray()).anyTimes();
    EasyMock.replay(aliasService);

    final DefaultCryptoService cryptoService = new DefaultCryptoService();
    cryptoService.setAliasService(aliasService);

    final Map<String, String> sessionStoreConfigs = new HashMap();

    final Capture<org.pac4j.core.context.Cookie> captureCookieValue = EasyMock.newCapture();
    final JEEContext mockContext = EasyMock.createNiceMock(JEEContext.class);
    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.expect(mockContext.getFullRequestURL()).andReturn("https://local.com/gateway/knoxsso/").anyTimes();
    mockContext.addResponseCookie(EasyMock.capture(captureCookieValue));
    EasyMock.expect(mockContext.getNativeResponse()).andReturn(response).anyTimes();

    EasyMock.replay(mockContext);


    final SAML2Profile samlProfile = new SAML2Profile();
    Set<String> groups = new HashSet<>(Arrays.asList("admin_2", "admin_1", "admin"));
    Set<String> roles = new HashSet<>(Arrays.asList("roles_2", "roles_1", "roles"));
    Set<String> permissions = new HashSet<>(Arrays.asList("permissions_2", "permissions_1", "permissions"));
    Map<String, Object> attributes = new HashMap<>();
    attributes.put("groups", groups);
    attributes.put("permissions", permissions);
    attributes.put("roles", roles);
    attributes.put("https://knox.apache.org/SAML/Attributes/groups", groups);
    attributes.put("https://knox.apache.org/SAML/Attributes/groups2", groups);
    samlProfile.addAttributes(attributes);

    /*
     * Test the default behavior where groups, roles and permissions are
     * excluded from the cookie.
     */

    /* Make sure groups are present */
    Assert.assertNotNull(samlProfile.getAttribute("groups"));
    Assert.assertNotNull(samlProfile.getAttribute("roles"));
    Assert.assertNotNull(samlProfile.getAttribute("permissions"));
    Assert.assertNotNull(samlProfile.getAttribute("https://knox.apache.org/SAML/Attributes/groups"));
    Assert.assertNotNull(samlProfile.getAttribute("https://knox.apache.org/SAML/Attributes/groups2"));


    sessionStoreConfigs.put(PAC4J_SESSION_STORE_EXCLUDE_GROUPS, PAC4J_SESSION_STORE_EXCLUDE_GROUPS_DEFAULT);
    sessionStoreConfigs.put(PAC4J_SESSION_STORE_EXCLUDE_ROLES, PAC4J_SESSION_STORE_EXCLUDE_ROLES_DEFAULT);
    sessionStoreConfigs.put(PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS, PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS_DEFAULT);
    sessionStoreConfigs.put(PAC4J_SESSION_STORE_EXCLUDE_CUSTOM_ATTRIBUTES, "https://knox.apache.org/SAML/Attributes/groups, https://knox.apache.org/SAML/Attributes/groups2");

    final Map<String, CommonProfile> profile = new HashMap<>();
    profile.put("SAML2Client", samlProfile);

    final KnoxSessionStore sessionStore = new KnoxSessionStore(cryptoService, CLUSTER_NAME, null, sessionStoreConfigs);

    sessionStore.set(mockContext, Pac4jConstants.USER_PROFILES, profile);

    /* Make sure groups are removed */
    Assert.assertNull(samlProfile.getAttribute("groups"));
    Assert.assertNull(samlProfile.getAttribute("roles"));
    Assert.assertNull(samlProfile.getAttribute("permissions"));
    Assert.assertNull(samlProfile.getAttribute("https://knox.apache.org/SAML/Attributes/groups"));
    Assert.assertNull(samlProfile.getAttribute("https://knox.apache.org/SAML/Attributes/groups2"));


    /*
     * Test the override behavior where groups, roles and permissions are
     * not-excluded from the cookie.
     */
    attributes.put("groups", groups);
    attributes.put("permissions", permissions);
    attributes.put("roles", roles);
    attributes.put("https://knox.apache.org/SAML/Attributes/groups", groups);
    attributes.put("https://knox.apache.org/SAML/Attributes/groups2", groups);
    samlProfile.addAttributes(attributes);

    /* Make sure groups are present */
    Assert.assertNotNull(samlProfile.getAttribute("groups"));
    Assert.assertNotNull(samlProfile.getAttribute("roles"));
    Assert.assertNotNull(samlProfile.getAttribute("permissions"));
    Assert.assertNotNull(samlProfile.getAttribute("https://knox.apache.org/SAML/Attributes/groups"));
    Assert.assertNotNull(samlProfile.getAttribute("https://knox.apache.org/SAML/Attributes/groups2"));


    sessionStoreConfigs.put(PAC4J_SESSION_STORE_EXCLUDE_GROUPS, "false");
    sessionStoreConfigs.put(PAC4J_SESSION_STORE_EXCLUDE_ROLES, "false");
    sessionStoreConfigs.put(PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS, "false");
    sessionStoreConfigs.put(PAC4J_SESSION_STORE_EXCLUDE_CUSTOM_ATTRIBUTES, "");

    profile.put("SAML2Client", samlProfile);

    sessionStore.set(mockContext, Pac4jConstants.USER_PROFILES, profile);

    /* Make sure attributes are not removed */
    Assert.assertNotNull(samlProfile.getAttribute("groups"));
    Assert.assertNotNull(samlProfile.getAttribute("roles"));
    Assert.assertNotNull(samlProfile.getAttribute("permissions"));
    Assert.assertNotNull(samlProfile.getAttribute("https://knox.apache.org/SAML/Attributes/groups"));
    Assert.assertNotNull(samlProfile.getAttribute("https://knox.apache.org/SAML/Attributes/groups2"));
  }

  @Test
  public void testSecureCookieSettings() throws Exception {
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForCluster(CLUSTER_NAME, PAC4J_PASSWORD)).andReturn(PAC4J_PASSWORD.toCharArray()).anyTimes();
    EasyMock.replay(aliasService);

    final DefaultCryptoService cryptoService = new DefaultCryptoService();
    cryptoService.setAliasService(aliasService);

    final Map<String, CommonProfile> profile = new HashMap<>();
    profile.put("SAML2Client", new SAML2Profile());

    // 1. Test when pac4j.session.store.secure.cookie is explicitly set to true
    runSecureCookieTest(cryptoService, profile, "true", "http://local.com/gateway/knoxsso/", true, "Secure flag should be true when explicitly set");

    // 2. Test when pac4j.session.store.secure.cookie is explicitly set to false
    runSecureCookieTest(cryptoService, profile, "false", "https://local.com/gateway/knoxsso/", false, "Secure flag should be false when explicitly set");

    // 3. Test default behavior for HTTPS request
    runSecureCookieTest(cryptoService, profile, null, "https://local.com/gateway/knoxsso/", true, "Secure flag should be true for HTTPS request by default");

    // 4. Test default behavior for HTTP request
    runSecureCookieTest(cryptoService, profile, null, "http://local.com/gateway/knoxsso/", false, "Secure flag should be false for HTTP request by default");
  }

  private void runSecureCookieTest(CryptoService cryptoService, Map<String, CommonProfile> profile,
      String secureCookieValue, String requestUrl, boolean expectSecureFlag, String assertMessage) {
    final Map<String, String> sessionStoreConfigs = new HashMap<>();
    if (secureCookieValue != null) {
      sessionStoreConfigs.put(PAC4J_SESSION_STORE_SECURE_COOKIE, secureCookieValue);
    }

    final Capture<String> capturedHeader = EasyMock.newCapture();
    final HttpServletResponse mockResponse = EasyMock.createNiceMock(HttpServletResponse.class);
    mockResponse.addHeader(EasyMock.eq("Set-Cookie"), EasyMock.capture(capturedHeader));
    EasyMock.replay(mockResponse);

    final JEEContext mockContext = EasyMock.createNiceMock(JEEContext.class);
    EasyMock.expect(mockContext.getNativeResponse()).andReturn(mockResponse).anyTimes();
    EasyMock.expect(mockContext.getFullRequestURL()).andReturn(requestUrl).anyTimes();
    if (requestUrl.startsWith("https")) {
      EasyMock.expect(mockContext.getScheme()).andReturn("https").anyTimes();
      EasyMock.expect(mockContext.isSecure()).andReturn(true).anyTimes();
    } else {
      EasyMock.expect(mockContext.getScheme()).andReturn("http").anyTimes();
      EasyMock.expect(mockContext.isSecure()).andReturn(false).anyTimes();
    }
    EasyMock.replay(mockContext);

    final KnoxSessionStore sessionStore = new KnoxSessionStore(cryptoService, CLUSTER_NAME, null, sessionStoreConfigs);
    sessionStore.set(mockContext, Pac4jConstants.USER_PROFILES, profile);

    if (expectSecureFlag) {
      Assert.assertTrue(assertMessage, capturedHeader.getValue().contains("Secure"));
    } else {
      Assert.assertFalse(assertMessage, capturedHeader.getValue().contains("Secure"));
    }
  }

  @Test
  public void testNullCookieValue() throws AliasServiceException {
    final CryptoService cryptoService = EasyMock.createNiceMock(CryptoService.class);
    final Map<String, String> sessionStoreConfigs = new HashMap<>();

    final JEEContext mockContext = EasyMock.createNiceMock(JEEContext.class);
    final String keyWithNullValue = "keyWithNullValue";
    Cookie cookie = new Cookie(PAC4J_SESSION_PREFIX + keyWithNullValue, "null");
    EasyMock.expect(mockContext.getRequestCookies()).andReturn(Collections.singletonList(cookie));
    EasyMock.replay(mockContext);

    final KnoxSessionStore sessionStore = new KnoxSessionStore(cryptoService, CLUSTER_NAME, null, sessionStoreConfigs);
    Assert.assertTrue(sessionStore.get(mockContext, keyWithNullValue).isEmpty());
  }

}
