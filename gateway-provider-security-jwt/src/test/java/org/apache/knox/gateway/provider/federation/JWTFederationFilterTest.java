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

import static org.apache.knox.gateway.security.CommonTokenConstants.CLIENT_CREDENTIALS;
import static org.apache.knox.gateway.security.CommonTokenConstants.GRANT_TYPE;
import static org.apache.knox.gateway.provider.federation.jwt.filter.AbstractJWTFilter.JWT_DEFAULT_ISSUER;
import static org.apache.knox.gateway.provider.federation.jwt.filter.RequestAudienceValidatorService.REQUEST_AUDIENCE_VALIDATOR_PARAM;
import static org.apache.knox.gateway.provider.federation.jwt.filter.SSOCookieFederationFilter.DEFAULT_SSO_COOKIE_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.security.auth.Subject;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.knox.gateway.provider.federation.jwt.filter.AbstractJWTFilter;
import org.apache.knox.gateway.provider.federation.jwt.filter.AudienceValidationResult;
import org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter;
import org.apache.knox.gateway.provider.federation.jwt.filter.RequestAudienceValidator;
import org.apache.knox.gateway.provider.federation.jwt.filter.SignatureVerificationCache;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.security.TokenIdPrincipal;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;

public class JWTFederationFilterTest extends AbstractJWTFilterTest {

  private static final String TOKEN_QUERY_PARAM = "knoxtoken";
  private static final String COOKIE_TOKEN_ID = "f0b0e2cc-5f1f-4a27-9f05-04b1ac1f6d41";
  private static final String COOKIE_SUBJECT = "bob";

  @Before
  public void setUp() {
    handler = new TestJWTFederationFilter();
    ((TestJWTFederationFilter) handler).setTokenService(new TestJWTokenAuthority(publicKey));
    RecordingRequestAudienceValidator.reset();
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
    // anyTimes: the filter also re-reads this header to decide whether the JWT was presented as
    // the caller's own credential (and is therefore forwardable) or as a grant-flow parameter
    EasyMock.expect(request.getHeader("Authorization")).andReturn(token).anyTimes();
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
  public void testSubjectCreationWithThirdPartyAppReconciliation() throws Exception {
    // Scenario 1: thirdPartyApp = true (default) -> principal should be tokenId
    testSubjectCreation(true);

    // Scenario 2: thirdPartyApp = false -> principal should be userName
    testSubjectCreation(false);
  }

  private void testSubjectCreation(boolean thirdPartyApp) throws Exception {
    final String tokenId = "test-token-id";
    final String userName = "test-user";

    final TokenMetadata metadataTrue = EasyMock.createNiceMock(TokenMetadata.class);
    EasyMock.expect(metadataTrue.isClientId()).andReturn(true).anyTimes();
    EasyMock.expect(metadataTrue.isThirdPartyApp()).andReturn(thirdPartyApp).anyTimes();
    EasyMock.expect(metadataTrue.getUserName()).andReturn(userName).anyTimes();

    final TokenStateService tss = EasyMock.createNiceMock(TokenStateService.class);
    EasyMock.expect(tss.getTokenMetadata(tokenId)).andReturn(metadataTrue).anyTimes();
    EasyMock.replay(metadataTrue, tss);

    final TestJWTFederationFilter filter = new TestJWTFederationFilter();
    Properties props = getProperties();
    props.put(TokenStateService.CONFIG_SERVER_MANAGED, "true");
    filter.init(new TestFilterConfig(props, tss));

    final Subject subject = filter.createSubjectFromTokenIdentifier(tokenId);
    assertEquals(1, subject.getPrincipals(PrimaryPrincipal.class).size());
    assertEquals(thirdPartyApp ? tokenId : userName, subject.getPrincipals(PrimaryPrincipal.class).iterator().next().getName());
    if (!thirdPartyApp) {
      assertEquals(1, subject.getPrincipals(TokenIdPrincipal.class).size());
      assertEquals(tokenId, subject.getPrincipals(TokenIdPrincipal.class).iterator().next().getName());
    }
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
    testVerifyPasscodeTokens(JWTFederationFilter.BASIC, true);
  }

  @Test
  public void testVerifyPasscodeTokensTssDisabled() throws Exception {
    testVerifyPasscodeTokens(JWTFederationFilter.BASIC, false);
  }

  @Test
  public void testVerifyPasscodeBearerTokens() throws Exception {
    testVerifyPasscodeTokens(JWTFederationFilter.BEARER, true);
  }

  @Test
  public void testVerifyPasscodeBearerTokensTssDisabled() throws Exception {
    testVerifyPasscodeTokens(JWTFederationFilter.BEARER, false);
  }

  private void testVerifyPasscodeTokens(String authTokenType, boolean tssEnabled) throws Exception {
    final String topologyName = "jwt-topology";
    final String tokenId = "4e0c548b-6568-4061-a3dc-62908087650a";
    final String passcode = "0138aaed-ca2a-47f1-8ed8-e0c397596f95";
    final String passcodeBearerToken = "TkdVd1l6VTBPR0l0TmpVMk9DMDBNRFl4TFdFelpHTXROakk1TURnd09EYzJOVEJoOjpNREV6T0dGaFpXUXRZMkV5WVMwME4yWXhMVGhsWkRndFpUQmpNemszTlRrMlpqazE=";
    String passcodeToken = "UGFzc2NvZGU6VGtkVmQxbDZWVEJQUjBsMFRtcFZNazlETURCTlJGbDRURmRGZWxwSFRYUk9ha2sxVFVSbmQwOUVZekpPVkVKb09qcE5SRVY2VDBkR2FGcFhVWFJaTWtWNVdWTXdNRTR5V1hoTVZHaHNXa1JuZEZwVVFtcE5lbXN6VGxSck1scHFhekU9";
    if (authTokenType.equals(JWTFederationFilter.BEARER)) {
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

    SignatureVerificationCache.getInstance(topologyName, filterConfig).recordSignatureVerification(passcodeVerificationCacheKey(tokenId, passcode));

    final TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    EasyMock.verify(response);
    if (tssEnabled) {
      Assert.assertTrue(chain.doFilterCalled);
      Assert.assertNotNull(chain.subject);
      // There is no JWT behind a passcode token, so nothing must be captured for forwarding
      Assert.assertNull("A passcode token must not be captured as an auth token credential",
          SubjectUtils.getAuthToken(chain.subject));
    } else {
      Assert.assertFalse(chain.doFilterCalled);
    }
  }

  /*
   * KNOX-3501: a hadoop-jwt cookie whose token has no server-managed state record must still be
   * rejected by default -- "no state" is indistinguishable from "revoked", so tolerating it is an
   * explicit opt-in (AbstractJWTFilter.ALLOW_UNKNOWN_COOKIE_TOKEN_STATE).
   */
  @Test
  public void testCookieAuthUnknownTokenStateRejectedByDefault() throws Exception {
    assertAuthWithServerManagedState(unknownTokenStateService(true), null, true,
        validCookieJwt(), false);
  }

  @Test
  public void testCookieAuthUnknownTokenStateAllowedWhenConfigured() throws Exception {
    assertAuthWithServerManagedState(unknownTokenStateService(false), "true", true,
        validCookieJwt(), true);
  }

  /*
   * The metadata lookup that immediately follows the expiration lookup raises UnknownTokenException
   * for the very same unknown token. Tolerating only the expiration lookup leaves the request
   * failing for the reason the opt-in was supposed to excuse, so both must be tolerated together.
   */
  @Test
  public void testCookieAuthUnknownTokenMetadataAllowedWhenConfigured() throws Exception {
    assertAuthWithServerManagedState(unknownTokenStateService(true), "true", true,
        validCookieJwt(), true);
  }

  /*
   * The opt-in tolerates only the complete absence of state. A token the state service knows about
   * and reports as expired is still rejected.
   */
  @Test
  public void testCookieAuthKnownButExpiredTokenStateStillRejected() throws Exception {
    final TokenStateService tokenStateService = EasyMock.createNiceMock(TokenStateService.class);
    EasyMock.expect(tokenStateService.getTokenExpiration(COOKIE_TOKEN_ID))
        .andReturn(System.currentTimeMillis() - 60000L).anyTimes();
    assertAuthWithServerManagedState(tokenStateService, "true", true, validCookieJwt(), false);
  }

  /*
   * With state absent and tolerated, the cookie's lifecycle falls back to the token's own exp claim
   * -- which must still be honoured.
   */
  @Test
  public void testCookieAuthUnknownTokenStateStillHonoursJwtExpiry() throws Exception {
    final SignedJWT expiredJwt = cookieJwt(new Date(System.currentTimeMillis() - 60000L));
    assertAuthWithServerManagedState(unknownTokenStateService(true), "true", true, expiredJwt, false);
  }

  /*
   * KNOX-3501: tolerating absent state falls back to the token's own exp claim, so there must BE
   * one. A cookie with no exp claim has no expiry from either source and would authenticate
   * forever, which is precisely what the fallback is supposed to bound. It must be rejected.
   */
  @Test
  public void testCookieAuthUnknownTokenStateRejectedWhenJwtHasNoExpiry() throws Exception {
    assertAuthWithServerManagedState(unknownTokenStateService(true), "true", true,
        cookieJwt(null), false);
  }

  /*
   * The expiration and metadata records live in separate tables written by separate calls, so the
   * expiration lookup can succeed while the metadata lookup raises. That token demonstrably HAS
   * server-managed state, so the opt-in -- which excuses only the complete absence of state --
   * must not suppress the enabled/disabled gate for it.
   */
  @Test
  public void testCookieAuthKnownStateButUnknownMetadataStillRejected() throws Exception {
    final TokenStateService tokenStateService = EasyMock.createNiceMock(TokenStateService.class);
    EasyMock.expect(tokenStateService.getTokenExpiration(COOKIE_TOKEN_ID))
        .andReturn(System.currentTimeMillis() + 60000L).anyTimes();
    EasyMock.expect(tokenStateService.getTokenMetadata(COOKIE_TOKEN_ID))
        .andThrow(new UnknownTokenException(COOKIE_TOKEN_ID)).anyTimes();
    assertAuthWithServerManagedState(tokenStateService, "true", true, validCookieJwt(), false);
  }

  /*
   * The opt-in is scoped to cookies. A JWT presented in the Authorization header is expected to be
   * a Knox-managed token, so unknown state there stays a 401 even with the parameter enabled.
   */
  @Test
  public void testBearerAuthUnknownTokenStateRejectedEvenWhenConfigured() throws Exception {
    assertAuthWithServerManagedState(unknownTokenStateService(true), "true", false,
        validCookieJwt(), false);
  }

  private SignedJWT validCookieJwt() throws Exception {
    return cookieJwt(new Date(System.currentTimeMillis() + 60000L));
  }

  private SignedJWT cookieJwt(final Date expires) throws Exception {
    return getJWT(JWT_DEFAULT_ISSUER, COOKIE_SUBJECT, "bar", expires, new Date(), privateKey,
        JWSAlgorithm.RS256.getName(), COOKIE_TOKEN_ID);
  }

  /**
   * A TokenStateService that has never heard of {@link #COOKIE_TOKEN_ID}, exactly as a real one
   * behaves for a cookie minted by an issuer that does not record token state.
   *
   * @param unknownMetadataToo when false, only the expiration lookup raises; used to show that
   *     tolerating that lookup alone is not sufficient.
   */
  private TokenStateService unknownTokenStateService(final boolean unknownMetadataToo) throws Exception {
    final TokenStateService tokenStateService = EasyMock.createNiceMock(TokenStateService.class);
    EasyMock.expect(tokenStateService.getTokenExpiration(COOKIE_TOKEN_ID))
        .andThrow(new UnknownTokenException(COOKIE_TOKEN_ID)).anyTimes();
    if (unknownMetadataToo) {
      EasyMock.expect(tokenStateService.getTokenMetadata(COOKIE_TOKEN_ID))
          .andThrow(new UnknownTokenException(COOKIE_TOKEN_ID)).anyTimes();
    }
    return tokenStateService;
  }

  /**
   * Drives the filter with server-managed token state enabled, presenting the JWT either in a
   * hadoop-jwt cookie or in the Authorization header.
   *
   * @param allowUnknownCookieState value for ALLOW_UNKNOWN_COOKIE_TOKEN_STATE, or null to leave the
   *     parameter absent (the default).
   */
  private void assertAuthWithServerManagedState(final TokenStateService tokenStateService,
                                                final String allowUnknownCookieState,
                                                final boolean useCookie,
                                                final SignedJWT jwt,
                                                final boolean expectAuthenticated) throws Exception {
    final Properties properties = getProperties();
    properties.put(TokenStateService.CONFIG_SERVER_MANAGED, "true");
    if (useCookie) {
      properties.put(JWTFederationFilter.KNOX_TOKEN_USE_COOKIE, "true");
    }
    if (allowUnknownCookieState != null) {
      properties.put(AbstractJWTFilter.ALLOW_UNKNOWN_COOKIE_TOKEN_STATE, allowUnknownCookieState);
    }
    handler.init(new TestFilterConfig(properties, tokenStateService));
    // init() re-reads the token service from the (mocked) GatewayServices that TestFilterConfig
    // supplies whenever a TokenStateService is present, clobbering what setUp() installed.
    ((TestJWTFederationFilter) handler).setTokenService(new TestJWTokenAuthority(publicKey));

    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
    if (useCookie) {
      final Cookie cookie = EasyMock.createNiceMock(Cookie.class);
      EasyMock.expect(cookie.getName()).andReturn(DEFAULT_SSO_COOKIE_NAME).anyTimes();
      EasyMock.expect(cookie.getValue()).andReturn(jwt.serialize()).anyTimes();
      EasyMock.replay(cookie);
      EasyMock.expect(request.getCookies()).andReturn(new Cookie[] { cookie }).anyTimes();
    } else {
      setTokenOnRequest(request, jwt);
    }

    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    if (!expectAuthenticated) {
      response.sendError(EasyMock.eq(HttpServletResponse.SC_UNAUTHORIZED), EasyMock.anyString());
      EasyMock.expectLastCall().atLeastOnce();
    }
    EasyMock.replay(tokenStateService, request, response);

    final TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    EasyMock.verify(response);
    assertEquals(expectAuthenticated, chain.doFilterCalled);
    if (expectAuthenticated) {
      assertEquals(COOKIE_SUBJECT, chain.getSubject().getPrincipals().iterator().next().getName());
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
      // The KnoxSSO cookie is the credential the caller authenticated with, so it is captured
      assertEquals("The cookie's JWT should be carried as a private credential for forwarding",
          jwt.serialize(), SubjectUtils.getAuthToken(chain.getSubject()));
    } else {
      EasyMock.verify(response);
    }
  }

  /*
   * A refresh_token is a grant artifact presented in the request body to mint a new token -- not
   * the credential the caller authenticated this request with -- so it must never be captured for
   * forwarding downstream.
   */
  @Test
  public void testRefreshTokenJWTIsNotCapturedForForwarding() throws Exception {
    assertGrantFlowJWTIsNotCaptured(JWTFederationFilter.REFRESH_TOKEN,
        JWTFederationFilter.REFRESH_TOKEN_PARAM, null, null, null);
  }

  /*
   * The dangerous shape: a credential-shaped Authorization header IS present, but it carries no
   * token this filter recognizes, so parseFromHTTPBasicCredentials declines it and the token is
   * still read from the grant-flow body. The mere presence of the header must not be mistaken for
   * "the caller presented this token".
   */
  @Test
  public void testRefreshTokenIsNotCapturedWhenABasicHeaderIsAlsoPresent() throws Exception {
    assertGrantFlowJWTIsNotCaptured(JWTFederationFilter.REFRESH_TOKEN,
        JWTFederationFilter.REFRESH_TOKEN_PARAM, null, "Basic " + encodeBasic("alice:secret"), null);
  }

  @Test
  public void testClientAssertionIsNotCapturedWhenABasicHeaderIsAlsoPresent() throws Exception {
    assertGrantFlowJWTIsNotCaptured(CLIENT_CREDENTIALS, JWTFederationFilter.CLIENT_ASSERTION,
        JWTFederationFilter.CLIENT_ASSERTION_JWT_BEARER, "Basic " + encodeBasic("clientid:"), null);
  }

  /*
   * getWireToken consults the query parameter LAST, after the grant-flow body, so a request that
   * carries both must be treated as the grant flow it is -- the query parameter's presence proves
   * nothing about where the validated token came from.
   */
  @Test
  public void testRefreshTokenIsNotCapturedWhenTheTokenQueryParamIsAlsoPresent() throws Exception {
    assertGrantFlowJWTIsNotCaptured(JWTFederationFilter.REFRESH_TOKEN,
        JWTFederationFilter.REFRESH_TOKEN_PARAM, null, null, "some-unrelated-value");
  }

  /*
   * createSubjectFromToken(JWT, String) is the one place a validated JWT becomes a Subject, and
   * subclasses hook it to decorate that Subject (TokenExchangeHandlerTest does exactly this). A
   * hook that only the grant-flow path routes through is a trap: the main Bearer path would build
   * Subjects the subclass never sees. Both paths must land on the same overridable method, with the
   * serialized token present only for the caller's own credential.
   */
  @Test
  public void testBothTokenPathsRouteThroughTheSameOverridableHook() throws Exception {
    final HookRecordingFilter filter = new HookRecordingFilter();
    filter.setTokenService(new TestJWTokenAuthority(publicKey));
    filter.init(new TestFilterConfig(getProperties()));

    final SignedJWT jwt = getJWT(JWT_DEFAULT_ISSUER, "alice",
        new Date(System.currentTimeMillis() + 60000), privateKey);

    // the caller's own credential: the hook is handed the serialized token to capture
    final HttpServletRequest bearerRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(bearerRequest.getHeader("Authorization"))
        .andReturn(JWTFederationFilter.BEARER + jwt.serialize()).anyTimes();
    EasyMock.expect(bearerRequest.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
    EasyMock.replay(bearerRequest);
    filter.doFilter(bearerRequest, EasyMock.createNiceMock(HttpServletResponse.class), new TestFilterChain());
    assertEquals("The caller-credential path must route through the hook", 1, filter.hookInvocations);
    assertEquals(jwt.serialize(), filter.lastSerializedToken);

    // a grant artifact: the same hook, told not to capture
    filter.hookInvocations = 0;
    final HttpServletRequest grantRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(grantRequest.getHeader("Authorization")).andReturn(null).anyTimes();
    EasyMock.expect(grantRequest.getParameter(GRANT_TYPE))
        .andReturn(JWTFederationFilter.REFRESH_TOKEN).anyTimes();
    EasyMock.expect(grantRequest.getParameter(JWTFederationFilter.REFRESH_TOKEN_PARAM))
        .andReturn(jwt.serialize()).anyTimes();
    EasyMock.expect(grantRequest.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
    EasyMock.expect(grantRequest.getPathInfo()).andReturn("resource").anyTimes();
    EasyMock.replay(grantRequest);
    filter.doFilter(grantRequest, EasyMock.createNiceMock(HttpServletResponse.class), new TestFilterChain());
    assertEquals("The grant-flow path must route through the same hook", 1, filter.hookInvocations);
    Assert.assertNull("A grant artifact must reach the hook with nothing to capture",
        filter.lastSerializedToken);
  }

  private static final class HookRecordingFilter extends TestJWTFederationFilter {
    private int hookInvocations;
    private String lastSerializedToken;

    @Override
    protected Subject createSubjectFromToken(JWT token, String serializedToken) throws UnknownTokenException {
      hookInvocations++;
      lastSerializedToken = serializedToken;
      return super.createSubjectFromToken(token, serializedToken);
    }
  }

  private static String encodeBasic(String credentials) {
    return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
  }

  /*
   * Likewise a client_assertion JWT (e.g. a Kubernetes service account token) authenticates the
   * client to the token endpoint; it is not the caller's access credential.
   */
  @Test
  public void testClientAssertionJWTIsNotCapturedForForwarding() throws Exception {
    assertGrantFlowJWTIsNotCaptured(CLIENT_CREDENTIALS, JWTFederationFilter.CLIENT_ASSERTION,
        JWTFederationFilter.CLIENT_ASSERTION_JWT_BEARER, null, null);
  }

  /**
   * @param grantType the grant_type body parameter
   * @param tokenParam the body parameter carrying the grant artifact JWT
   * @param clientAssertionType the client_assertion_type body parameter, or null
   * @param authorizationHeader an Authorization header to send alongside the body, or null for none
   * @param queryParamTokenValue a value for the configured token query parameter, or null for none
   */
  private void assertGrantFlowJWTIsNotCaptured(String grantType, String tokenParam, String clientAssertionType,
      String authorizationHeader, String queryParamTokenValue) throws Exception {
      final Properties properties = getProperties();
      if (queryParamTokenValue != null) {
          properties.put(JWTFederationFilter.KNOX_TOKEN_QUERY_PARAM_NAME, TOKEN_QUERY_PARAM);
      }
      handler.init(new TestFilterConfig(properties));

      final SignedJWT jwt = getJWT(JWT_DEFAULT_ISSUER, "alice",
              new Date(System.currentTimeMillis() + 60000), privateKey);

      final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
      EasyMock.expect(request.getHeader("Authorization")).andReturn(authorizationHeader).anyTimes();
      EasyMock.expect(request.getQueryString()).andReturn(null).anyTimes();
      if (queryParamTokenValue != null) {
          EasyMock.expect(request.getParameter(TOKEN_QUERY_PARAM))
                  .andReturn(queryParamTokenValue).anyTimes();
      }
      EasyMock.expect(request.getParameter(GRANT_TYPE)).andReturn(grantType).anyTimes();
      if (clientAssertionType != null) {
          EasyMock.expect(request.getParameter(JWTFederationFilter.CLIENT_ASSERTION_TYPE))
                  .andReturn(clientAssertionType).anyTimes();
      }
      EasyMock.expect(request.getParameter(tokenParam)).andReturn(jwt.serialize()).anyTimes();
      EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
      EasyMock.expect(request.getPathInfo()).andReturn("resource").anyTimes();

      final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
      EasyMock.replay(request, response);

      final TestFilterChain chain = new TestFilterChain();
      handler.doFilter(request, response, chain);

      Assert.assertTrue("The grant flow should have authenticated", chain.doFilterCalled);
      Assert.assertNull("A " + grantType + " JWT is a grant artifact and must not be captured for forwarding",
              SubjectUtils.getAuthToken(chain.subject));
  }

  // ---------------------------------------------------------------------
  // RequestAudienceValidator wired into JWTFederationFilter's
  // direct-bearer JWT path (init()/destroy()/doFilter()).
  // ---------------------------------------------------------------------

  /**
   * ServiceLoader-discoverable test fixture, distinct from
   * RequestAudienceValidatorServiceTest's DummyValidator, that records
   * whether init()/destroy() were called and how many times validate() ran --
   * proof that a configured validator is actually resolved and invoked by
   * JWTFederationFilter, not just registered. Always accepts, so tests using
   * it can also prove a custom validator overrides the default audience
   * check rather than supplementing it. Static state is reset in setUp().
   */
  public static class RecordingRequestAudienceValidator implements RequestAudienceValidator {
    public static final String NAME = "RecordingRequestAudienceValidator";
    static final AtomicBoolean initCalled = new AtomicBoolean();
    static final AtomicBoolean destroyCalled = new AtomicBoolean();
    static final AtomicInteger validateCallCount = new AtomicInteger();
    static final AtomicBoolean throwOnDestroy = new AtomicBoolean();

    public RecordingRequestAudienceValidator() {
    }

    static void reset() {
      initCalled.set(false);
      destroyCalled.set(false);
      validateCallCount.set(0);
      throwOnDestroy.set(false);
    }

    @Override
    public void init(FilterConfig filterConfig) {
      initCalled.set(true);
    }

    @Override
    public void destroy() {
      destroyCalled.set(true);
      if (throwOnDestroy.get()) {
        throw new IllegalStateException("boom");
      }
    }

    @Override
    public AudienceValidationResult validate(HttpServletRequest request, JWT token, List<String> configuredAudiences) {
      validateCallCount.incrementAndGet();
      return AudienceValidationResult.of(true);
    }

    @Override
    public String getName() {
      return NAME;
    }
  }

  /**
   * The single most important regression test in this task: with
   * request.audience.validator left unset, a mismatched-audience direct-bearer
   * token must still be rejected exactly as it was before RequestAudienceValidator
   * existed -- SC_BAD_REQUEST with the unchanged message text.
   */
  @Test
  public void testRejectsMismatchedAudienceWhenNoValidatorConfigured() throws Exception {
    final Properties props = getProperties();
    props.put(getAudienceProperty(), "foo");
    handler.init(new TestFilterConfig(props));

    final SignedJWT jwt = getJWT(JWT_DEFAULT_ISSUER, "alice",
        new Date(new Date().getTime() + 5000), privateKey);
    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    setTokenOnRequest(request, jwt);
    EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
    EasyMock.expect(request.getPathInfo()).andReturn("resource").anyTimes();
    EasyMock.expect(request.getQueryString()).andReturn(null);

    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request: missing required token audience");
    EasyMock.expectLastCall().once();
    EasyMock.replay(request, response);

    final TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    Assert.assertFalse("doFilterCalled should not be true.", chain.doFilterCalled);
    EasyMock.verify(response);
  }

  @Test
  public void testInitWithUnknownRequestAudienceValidatorNameThrowsServletException() throws Exception {
    final Properties props = getProperties();
    props.put(REQUEST_AUDIENCE_VALIDATOR_PARAM, "not-a-registered-validator");

    try {
      handler.init(new TestFilterConfig(props));
      fail("Expected ServletException");
    } catch (ServletException e) {
      assertTrue(e.getMessage().contains("not-a-registered-validator"));
    }
  }

  @Test
  public void testConfiguredRequestAudienceValidatorIsInvokedFromDoFilter() throws Exception {
    final Properties props = getProperties();
    props.put(getAudienceProperty(), "bar");
    props.put(REQUEST_AUDIENCE_VALIDATOR_PARAM, RecordingRequestAudienceValidator.NAME);
    handler.init(new TestFilterConfig(props));
    assertTrue("init() should have been called on the resolved validator", RecordingRequestAudienceValidator.initCalled.get());

    final SignedJWT jwt = getJWT(JWT_DEFAULT_ISSUER, "alice",
        new Date(new Date().getTime() + 5000), privateKey);
    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    setTokenOnRequest(request, jwt);
    EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
    EasyMock.expect(request.getPathInfo()).andReturn("resource").anyTimes();
    EasyMock.expect(request.getQueryString()).andReturn(null);
    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.replay(request, response);

    final TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    assertEquals(1, RecordingRequestAudienceValidator.validateCallCount.get());
    Assert.assertTrue("doFilterCalled should be true.", chain.doFilterCalled);
  }

  @Test
  public void testConfiguredRequestAudienceValidatorAcceptsDespiteMismatchedAudience() throws Exception {
    final Properties props = getProperties();
    props.put(getAudienceProperty(), "foo");
    props.put(REQUEST_AUDIENCE_VALIDATOR_PARAM, RecordingRequestAudienceValidator.NAME);
    handler.init(new TestFilterConfig(props));

    final SignedJWT jwt = getJWT(JWT_DEFAULT_ISSUER, "alice",
        new Date(new Date().getTime() + 5000), privateKey);
    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    setTokenOnRequest(request, jwt);
    EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
    EasyMock.expect(request.getPathInfo()).andReturn("resource").anyTimes();
    EasyMock.expect(request.getQueryString()).andReturn(null);
    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.replay(request, response);

    final TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    Assert.assertTrue("Custom validator returning true should accept the request despite the audience mismatch",
        chain.doFilterCalled);
  }

  @Test
  public void testDestroyPropagatesToConfiguredRequestAudienceValidator() throws Exception {
    final Properties props = getProperties();
    props.put(REQUEST_AUDIENCE_VALIDATOR_PARAM, RecordingRequestAudienceValidator.NAME);
    handler.init(new TestFilterConfig(props));

    handler.destroy();

    assertTrue("destroy() should have been called on the resolved validator", RecordingRequestAudienceValidator.destroyCalled.get());
  }

  /**
   * destroy() is void on both Filter and RequestAudienceValidator -- a misbehaving validator
   * must not be able to abort the filter's own shutdown by throwing out of destroy().
   */
  @Test
  public void testDestroyDoesNotPropagateExceptionFromConfiguredRequestAudienceValidator() throws Exception {
    final Properties props = getProperties();
    props.put(REQUEST_AUDIENCE_VALIDATOR_PARAM, RecordingRequestAudienceValidator.NAME);
    handler.init(new TestFilterConfig(props));
    RecordingRequestAudienceValidator.throwOnDestroy.set(true);

    handler.destroy();

    assertTrue("destroy() should still have been called on the resolved validator", RecordingRequestAudienceValidator.destroyCalled.get());
  }

}
