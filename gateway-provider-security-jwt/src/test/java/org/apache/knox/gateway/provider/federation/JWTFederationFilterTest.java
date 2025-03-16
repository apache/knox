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
package org.apache.knox.gateway.provider.federation;

import static org.apache.knox.gateway.provider.federation.jwt.filter.AbstractJWTFilter.JWT_DEFAULT_ISSUER;
import static org.apache.knox.gateway.provider.federation.jwt.filter.SSOCookieFederationFilter.DEFAULT_SSO_COOKIE_NAME;
import static org.junit.Assert.assertEquals;

import java.util.Date;
import java.util.Properties;

import javax.servlet.FilterConfig;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.knox.gateway.provider.federation.jwt.filter.AbstractJWTFilter;
import org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter;
import org.apache.knox.gateway.provider.federation.jwt.filter.SignatureVerificationCache;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.nimbusds.jwt.SignedJWT;

@SuppressWarnings("PMD.TestClassWithoutTestCases")
public class JWTFederationFilterTest extends AbstractJWTFilterTest {

  private static final String BASIC_ = "Basic ";
  private static final String BEARER_ = "Bearer ";

  @Before
  public void setUp() {
    handler = new TestJWTFederationFilter();
    ((TestJWTFederationFilter) handler).setTokenService(new TestJWTokenAuthority(publicKey));
  }

  @Override
  protected String getAudienceProperty() {
    return TestJWTFederationFilter.KNOX_TOKEN_AUDIENCES;
  }

  @Override
  protected String getVerificationPemProperty() {
    return TestJWTFederationFilter.TOKEN_VERIFICATION_PEM;
  }

  @Override
  protected void setTokenOnRequest(HttpServletRequest request, SignedJWT jwt) {
    String token = TestJWTFederationFilter.BEARER + " " + jwt.serialize();
    EasyMock.expect(request.getHeader("Authorization")).andReturn(token);
  }

  @Override
  protected void setGarbledTokenOnRequest(HttpServletRequest request, SignedJWT jwt) {
    String token = TestJWTFederationFilter.BEARER + " ljm" + jwt.serialize();
    EasyMock.expect(request.getHeader("Authorization")).andReturn(token);
  }

  @Test
  public void testMissingTokenValue() throws Exception {
    handler.init(new TestFilterConfig(getProperties()));

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
    EasyMock.expect(request.getHeader("Authorization")).andReturn("Basic VG9rZW46");
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    EasyMock.expectLastCall().once();
    EasyMock.replay(request, response);

    TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    EasyMock.verify(response);
  }

  @Test
  public void testCookieAuthSupportValidCookie() throws Exception {
    testCookieAuthSupport(true);
  }

  @Test
  public void testCookieAuthSupportInvalidCookie() throws Exception {
    testCookieAuthSupport(false);
  }

  @Test
  public void testCookieAuthSupportCustomCookieName() throws Exception {
    testCookieAuthSupport(true, "customCookie");
  }

  @Test
  public void testVerifyPasscodeTokens() throws Exception {
    testVerifyPasscodeTokens(BASIC_, true);
  }

  @Test
  public void testVerifyPasscodeTokensTssDisabled() throws Exception {
    testVerifyPasscodeTokens(BASIC_, false);
  }

  @Test
  public void testVerifyPasscodeBearerTokens() throws Exception {
    testVerifyPasscodeTokens(BEARER_, true);
  }

  @Test
  public void testVerifyPasscodeBearerTokensTssDisabled() throws Exception {
    testVerifyPasscodeTokens(BEARER_, false);
  }

  private void testVerifyPasscodeTokens(String authTokenType, boolean tssEnabled) throws Exception {
    final String topologyName = "jwt-topology";
    final String tokenId = "4e0c548b-6568-4061-a3dc-62908087650a";
    final String passcode = "0138aaed-ca2a-47f1-8ed8-e0c397596f95";
    final String passcodeBearerToken = "TkdVd1l6VTBPR0l0TmpVMk9DMDBNRFl4TFdFelpHTXROakk1TURnd09EYzJOVEJoOjpNREV6T0dGaFpXUXRZMkV5WVMwME4yWXhMVGhsWkRndFpUQmpNemszTlRrMlpqazE=";
    String passcodeToken = "UGFzc2NvZGU6VGtkVmQxbDZWVEJQUjBsMFRtcFZNazlETURCTlJGbDRURmRGZWxwSFRYUk9ha2sxVFVSbmQwOUVZekpPVkVKb09qcE5SRVY2VDBkR2FGcFhVWFJaTWtWNVdWTXdNRTR5V1hoTVZHaHNXa1JuZEZwVVFtcE5lbXN6VGxSck1scHFhekU9";
    if (authTokenType.equals(BEARER_)) {
      passcodeToken = passcodeBearerToken;
    }

    final TokenStateService tokenStateService = EasyMock.createNiceMock(TokenStateService.class);
    EasyMock.expect(tokenStateService.getTokenExpiration(tokenId)).andReturn(Long.MAX_VALUE).anyTimes();

    final TokenMetadata tokenMetadata = EasyMock.createNiceMock(TokenMetadata.class);
    EasyMock.expect(tokenMetadata.isEnabled()).andReturn(true).anyTimes();
    EasyMock.expect(tokenMetadata.getPasscode()).andReturn(passcodeToken).anyTimes();
    EasyMock.expect(tokenStateService.getTokenMetadata(EasyMock.anyString())).andReturn(tokenMetadata).anyTimes();

    final Properties filterConfigProps = getProperties();
    filterConfigProps.put(TokenStateService.CONFIG_SERVER_MANAGED, Boolean.toString(tssEnabled));
    filterConfigProps.put(TestFilterConfig.TOPOLOGY_NAME_PROP, topologyName);
    final FilterConfig filterConfig = new TestFilterConfig(filterConfigProps, tokenStateService);
    handler.init(filterConfig);

    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
    EasyMock.expect(request.getHeader("Authorization")).andReturn(authTokenType + passcodeToken);

    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    if (!tssEnabled) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, AbstractJWTFilter.TOKEN_STATE_SERVICE_DISABLED_ERROR);
      EasyMock.expectLastCall().once();
    }
    EasyMock.replay(tokenStateService, tokenMetadata, request, response);

    SignatureVerificationCache.getInstance(topologyName, filterConfig).recordSignatureVerification(passcode);

    final TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    EasyMock.verify(response);
    if (tssEnabled) {
      Assert.assertTrue(chain.doFilterCalled);
      Assert.assertNotNull(chain.subject);
    } else {
      Assert.assertFalse(chain.doFilterCalled);
    }
  }

  private void testCookieAuthSupport(boolean validCookie) throws Exception {
    testCookieAuthSupport(validCookie, null);
  }

  private void testCookieAuthSupport(boolean validCookie, String customCookieName) throws Exception {
    final Properties properties = getProperties();
    properties.put(JWTFederationFilter.KNOX_TOKEN_USE_COOKIE, "true");
    if (customCookieName != null) {
      properties.put(JWTFederationFilter.KNOX_TOKEN_COOKIE_NAME, customCookieName);
    }
    handler.init(new TestFilterConfig(properties));

    final String subject = "bob";
    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    final SignedJWT jwt = getJWT(JWT_DEFAULT_ISSUER, subject, new Date(System.currentTimeMillis() + 60000));
    final Cookie[] cookies = new Cookie[1];
    final Cookie cookie = EasyMock.createNiceMock(Cookie.class);
    EasyMock.expect(cookie.getValue()).andReturn(jwt.serialize());
    final String cookieName = validCookie ? (customCookieName == null ? DEFAULT_SSO_COOKIE_NAME : customCookieName) : "dummyCookie";
    EasyMock.expect(cookie.getName()).andReturn(cookieName).anyTimes();
    cookies[0] = cookie;
    EasyMock.expect(request.getCookies()).andReturn(cookies).anyTimes();

    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    if (!validCookie) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      EasyMock.expectLastCall().once();
    }
    EasyMock.replay(request, response, cookie);

    final TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    if (validCookie) {
      assertEquals(1, chain.getSubject().getPrincipals().size());
      assertEquals(subject, chain.getSubject().getPrincipals().iterator().next().getName());
    } else {
      EasyMock.verify(response);
    }
  }

}
