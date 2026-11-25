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

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.EncryptionResult;
import org.apache.knox.gateway.services.security.impl.DefaultCryptoService;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;
import org.pac4j.core.context.Cookie;
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.util.JavaSerializationHelper;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.saml.profile.SAML2Profile;

import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_CUSTOM_ATTRIBUTES;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_GROUPS;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_GROUPS_DEFAULT;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS_DEFAULT;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_ROLES;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_ROLES_DEFAULT;
import static org.apache.knox.gateway.pac4j.session.KnoxSessionStore.PAC4J_PASSWORD;
import static org.apache.knox.gateway.pac4j.session.KnoxSessionStore.PAC4J_SESSION_PREFIX;

public class KnoxSessionStoreTest {
  private static final String CLUSTER_NAME = "knox";
  private static final char[] PASSWORD = "pac4j-test-password".toCharArray();
  private static final String USER_PROFILES_COOKIE = PAC4J_SESSION_PREFIX + Pac4jConstants.USER_PROFILES;

  /**
   * Test exclusion of groups, roles and permissions
   * from pac4j profile object that is saved as a cookie.
   */
  @Test
  public void filterConfigParamsTest() {
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

    final KnoxSessionStore sessionStore = new KnoxSessionStore(PASSWORD, null, CLUSTER_NAME, null, sessionStoreConfigs);

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
  public void testNullCookieValue() {
    final KnoxSessionStore sessionStore = newStore(PASSWORD, null);
    Assert.assertFalse(read(sessionStore, PAC4J_SESSION_PREFIX + "keyWithNullValue", "null").isPresent());
  }

  @Test
  public void testRoundTrip() {
    final KnoxSessionStore sessionStore = newStore(PASSWORD, null);
    final String value = write(sessionStore, Pac4jConstants.USER_PROFILES, profiles("alice"));
    Assert.assertEquals("alice", readProfileId(sessionStore, USER_PROFILES_COOKIE, value));
  }

  @Test
  public void testNonceIsUniquePerCookie() {
    final KnoxSessionStore sessionStore = newStore(PASSWORD, null);
    final String first = write(sessionStore, Pac4jConstants.USER_PROFILES, profiles("alice"));
    final String second = write(sessionStore, Pac4jConstants.USER_PROFILES, profiles("alice"));
    Assert.assertFalse(Arrays.equals(Arrays.copyOf(decode(first), 12), Arrays.copyOf(decode(second), 12)));
    Assert.assertEquals("alice", readProfileId(sessionStore, USER_PROFILES_COOKIE, first));
    Assert.assertEquals("alice", readProfileId(sessionStore, USER_PROFILES_COOKIE, second));
  }

  @Test
  public void testTamperedCookieIsIgnored() {
    final KnoxSessionStore sessionStore = newStore(PASSWORD, null);
    final String value = write(sessionStore, Pac4jConstants.USER_PROFILES, profiles("alice"));
    final byte[] raw = decode(value);
    for (int index : new int[] {0, 11, 12, raw.length / 2, raw.length - 1}) {
      final byte[] tampered = raw.clone();
      tampered[index] ^= 0x01;
      Assert.assertFalse("byte " + index, read(sessionStore, USER_PROFILES_COOKIE, encode(tampered)).isPresent());
    }
  }

  @Test
  public void testLegacyCbcCookieIsIgnored() throws Exception {
    final AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(aliasService.getPasswordFromAliasForCluster(CLUSTER_NAME, PAC4J_PASSWORD))
        .andReturn(PASSWORD).anyTimes();
    EasyMock.replay(aliasService);
    final DefaultCryptoService cryptoService = new DefaultCryptoService();
    cryptoService.setAliasService(aliasService);

    final byte[] serialized = new JavaSerializationHelper().serializeToBytes(new HashMap<>(profiles("admin")));
    final byte[] clear = gzip(serialized);
    final EncryptionResult result = cryptoService.encryptForCluster(CLUSTER_NAME, PAC4J_PASSWORD, clear);
    final String legacy = Base64.getEncoder().encodeToString(result.toByteAray());

    Assert.assertFalse(read(newStore(PASSWORD, null), USER_PROFILES_COOKIE, legacy).isPresent());
  }

  @Test
  public void testCookieMovedToAnotherNameIsIgnored() {
    final KnoxSessionStore sessionStore = newStore(PASSWORD, null);
    final String value = write(sessionStore, Pac4jConstants.CSRF_TOKEN, "csrf-token-value");
    Assert.assertEquals("csrf-token-value",
        read(sessionStore, PAC4J_SESSION_PREFIX + Pac4jConstants.CSRF_TOKEN, value).orElse(null));
    Assert.assertFalse(read(sessionStore, USER_PROFILES_COOKIE, value).isPresent());
  }

  @Test
  public void testGarbageCookiesAreIgnored() {
    final KnoxSessionStore sessionStore = newStore(PASSWORD, null);
    final String[] values = {
        "",
        "!!!not-base64!!!",
        "AAAA",
        StringUtils.repeat('A', 200),
        StringUtils.repeat('A', 64),
        "AAAADAAAABAAAAAQ" + StringUtils.repeat('A', 64)
    };
    for (String value : values) {
      Assert.assertFalse(value, read(sessionStore, USER_PROFILES_COOKIE, value).isPresent());
    }
  }

  @Test
  public void testCookieIsReadableAcrossInstances() {
    final KnoxSessionStore pod1 = new KnoxSessionStore("shared-password".toCharArray(), null, CLUSTER_NAME, null, new HashMap<>());
    final KnoxSessionStore pod2 = new KnoxSessionStore("shared-password".toCharArray(), null, CLUSTER_NAME, null, new HashMap<>());
    Assert.assertEquals("alice", readProfileId(pod2, USER_PROFILES_COOKIE,
        write(pod1, Pac4jConstants.USER_PROFILES, profiles("alice"))));
    Assert.assertEquals("bob", readProfileId(pod1, USER_PROFILES_COOKIE,
        write(pod2, Pac4jConstants.USER_PROFILES, profiles("bob"))));

    final KnoxSessionStore customPod1 = newStore(PASSWORD, gatewayConfig("PBKDF2WithHmacSHA1", "1000", "128"));
    final KnoxSessionStore customPod2 = newStore(PASSWORD, gatewayConfig("PBKDF2WithHmacSHA1", "1000", "128"));
    Assert.assertEquals("carol", readProfileId(customPod2, USER_PROFILES_COOKIE,
        write(customPod1, Pac4jConstants.USER_PROFILES, profiles("carol"))));
  }

  @Test
  public void testDifferentPasswordOrClusterIsIgnored() {
    final String value = write(newStore(PASSWORD, null), Pac4jConstants.USER_PROFILES, profiles("alice"));
    Assert.assertFalse(read(newStore("other-password".toCharArray(), null), USER_PROFILES_COOKIE, value).isPresent());
    final KnoxSessionStore otherCluster = new KnoxSessionStore(PASSWORD, null, "other", null, new HashMap<>());
    Assert.assertFalse(read(otherCluster, USER_PROFILES_COOKIE, value).isPresent());
  }

  @Test
  public void testGatewayCryptoSettingsAreHonored() {
    final KnoxSessionStore custom = newStore(PASSWORD, gatewayConfig("PBKDF2WithHmacSHA1", "1000", "128"));
    final String value = write(custom, Pac4jConstants.USER_PROFILES, profiles("alice"));
    Assert.assertEquals("alice", readProfileId(custom, USER_PROFILES_COOKIE, value));
    Assert.assertFalse(read(newStore(PASSWORD, null), USER_PROFILES_COOKIE, value).isPresent());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidKeyLengthIsRejected() {
    newStore(PASSWORD, gatewayConfig(null, null, "100"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testMissingPasswordIsRejected() {
    newStore(null, null);
  }

  private static KnoxSessionStore newStore(char[] password, GatewayConfig gatewayConfig) {
    return new KnoxSessionStore(password, gatewayConfig, CLUSTER_NAME, null, new HashMap<>());
  }

  private static GatewayConfig gatewayConfig(String pbeAlgorithm, String iterationCount, String keyLength) {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getPBEAlgorithm()).andReturn(pbeAlgorithm).anyTimes();
    EasyMock.expect(gatewayConfig.getIterationCount()).andReturn(iterationCount).anyTimes();
    EasyMock.expect(gatewayConfig.getKeyLength()).andReturn(keyLength).anyTimes();
    EasyMock.replay(gatewayConfig);
    return gatewayConfig;
  }

  private static Map<String, CommonProfile> profiles(String id) {
    final SAML2Profile profile = new SAML2Profile();
    profile.setId(id);
    final Map<String, CommonProfile> profiles = new HashMap<>();
    profiles.put("SAML2Client", profile);
    return profiles;
  }

  private static String write(KnoxSessionStore sessionStore, String key, Object value) {
    final Capture<String> capturedHeader = EasyMock.newCapture();
    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    response.addHeader(EasyMock.eq("Set-Cookie"), EasyMock.capture(capturedHeader));
    EasyMock.replay(response);
    final JEEContext context = EasyMock.createNiceMock(JEEContext.class);
    EasyMock.expect(context.getNativeResponse()).andReturn(response).anyTimes();
    EasyMock.expect(context.getFullRequestURL()).andReturn("https://local.com/gateway/knoxsso/").anyTimes();
    EasyMock.replay(context);

    sessionStore.set(context, key, value);

    final String header = capturedHeader.getValue();
    final String prefix = PAC4J_SESSION_PREFIX + key + "=";
    Assert.assertTrue(header, header.startsWith(prefix));
    final int end = header.indexOf(';');
    return header.substring(prefix.length(), end < 0 ? header.length() : end);
  }

  private static Optional<Object> read(KnoxSessionStore sessionStore, String cookieName, String value) {
    final JEEContext context = EasyMock.createNiceMock(JEEContext.class);
    EasyMock.expect(context.getRequestCookies()).andReturn(Collections.singletonList(new Cookie(cookieName, value))).anyTimes();
    EasyMock.replay(context);
    return sessionStore.get(context, cookieName.substring(PAC4J_SESSION_PREFIX.length()));
  }

  @SuppressWarnings("unchecked")
  private static String readProfileId(KnoxSessionStore sessionStore, String cookieName, String value) {
    final Optional<Object> result = read(sessionStore, cookieName, value);
    Assert.assertTrue(result.isPresent());
    return ((Map<String, CommonProfile>) result.get()).get("SAML2Client").getId();
  }

  private static byte[] decode(String value) {
    return Base64.getUrlDecoder().decode(value);
  }

  private static String encode(byte[] raw) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
  }

  private static byte[] gzip(byte[] data) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
      gzip.write(data);
    }
    return out.toByteArray();
  }
}
