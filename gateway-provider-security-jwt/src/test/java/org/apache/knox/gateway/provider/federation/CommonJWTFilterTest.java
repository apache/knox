/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.provider.federation;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.provider.federation.jwt.filter.AbstractJWTFilter;
import org.apache.knox.gateway.provider.federation.jwt.filter.AudienceValidationResult;
import org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter;
import org.apache.knox.gateway.provider.federation.jwt.filter.RequestAudienceValidator;
import org.apache.knox.gateway.provider.federation.jwt.filter.SignatureVerificationCache;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.Subject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CommonJWTFilterTest {

  private AbstractJWTFilter handler;
  private static final String SERVICE_URL = "https://localhost:8888/gateway/sandbox";
  private static final String JWKS_PATH = "/knoxtoken/api/v1/jwks.json";

  @Before
  public void setUp() {
    handler = new TestHandler();
  }

  @After
  public void tearDown() {
    handler = null;
  }

  @Test
  public void testServerManagedTokenStateEnabledByProviderOverride() throws Exception {
    // Provider param should override gateway config
    assertTrue(doTestServerManagedTokenState(false, "true"));
  }

  @Test
  public void testServerManagedTokenStateDisabledByProviderOverride() throws Exception {
    // Provider param should override gateway config
    assertFalse(doTestServerManagedTokenState(true, "false"));
  }

  @Test
  public void testServerManagedTokenStateDisabledWithoutProviderOverride() throws Exception {
    // Missing provider param override should apply gateway config
    assertFalse(doTestServerManagedTokenState(false, null));
  }

  @Test
  public void testServerManagedTokenStateEnabledWithoutProviderOverride() throws Exception {
    // Missing provider param override should apply gateway config
    assertTrue(doTestServerManagedTokenState(true, null));
  }

  @Test
  public void testServerManagedTokenStateDisabledWitEmptyProviderOverride() throws Exception {
    // Empty provider param override should apply gateway config
    assertFalse(doTestServerManagedTokenState(false, ""));
  }

  @Test
  public void testServerManagedTokenStateEnabledWithEmptyProviderOverride() throws Exception {
    // Empty provider param override should apply gateway config
    assertTrue(doTestServerManagedTokenState(true, ""));
  }

  private boolean doTestServerManagedTokenState(final Boolean isEnabledAtGateway, final String providerParamValue)
      throws Exception {

    GatewayConfig gwConf = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gwConf.isServerManagedTokenStateEnabled()).andReturn(isEnabledAtGateway).anyTimes();
    EasyMock.replay(gwConf);

    ServletContext sc = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(sc.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(gwConf).anyTimes();
    EasyMock.replay(sc);

    FilterConfig fc = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(fc.getInitParameter(TokenStateService.CONFIG_SERVER_MANAGED)).andReturn(providerParamValue).anyTimes();
    EasyMock.expect(fc.getServletContext()).andReturn(sc).anyTimes();
    EasyMock.replay(fc);

    return TokenUtils.isServerManagedTokenStateEnabled(fc);
  }

  @Test
  public void testIsStillValid() throws Exception {
    assertTrue("Expected the token to be valid because it has not yet expired.",
               doTestIsStillValid(System.currentTimeMillis() + 300000)); // 5 minutes later
  }

  @Test
  public void testIsStillValidExpired() throws Exception {
    assertFalse("Expected the token to be invalid because it has already expired.",
                doTestIsStillValid(System.currentTimeMillis() - 300000)); // 5 minutes ago
  }

  @Test(expected = UnknownTokenException.class)
  public void testIsStillValidUnknownToken() throws Exception {
    TokenStateService tss = EasyMock.createNiceMock(TokenStateService.class);
    final String tokenId = UUID.randomUUID().toString();
    EasyMock.expect(tss.getTokenExpiration(anyObject(JWT.class)))
            .andThrow(new UnknownTokenException(tokenId))
            .anyTimes();
    EasyMock.expect(tss.getTokenExpiration(anyObject(String.class)))
            .andThrow(new UnknownTokenException(tokenId))
            .anyTimes();
    EasyMock.replay(tss);

    doTestIsStillValid(tss);
  }

  @Test
  public void testUnauthenticatedList() throws Exception {
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    FilterConfig filterConfig = EasyMock.createNiceMock(FilterConfig.class);

    EasyMock.expect(request.getPathInfo()).andReturn(JWKS_PATH).anyTimes();
    EasyMock.expect(request.getQueryString()).andReturn(null);
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.expect(response.encodeRedirectURL(SERVICE_URL)).andReturn(SERVICE_URL);
    EasyMock.expect(response.getOutputStream()).andAnswer(
        AbstractJWTFilterTest.DummyServletOutputStream::new).anyTimes();
    EasyMock.replay(request, response, filterConfig);


    JWTFederationFilter jwtFilter = new JWTFederationFilter();
    DummyFilterChain chain = new DummyFilterChain();

    jwtFilter.init(filterConfig);
    jwtFilter.doFilter(request, response, chain);
    Assert.assertTrue("doFilterCalled should be true.", chain.doFilterCalled );
    /* make sure the principal is anonymous */
    Assert.assertEquals("anonymous", chain.subject.getPrincipals().stream().findFirst().get().getName());
  }

  public static class DummyFilterChain implements FilterChain {
    boolean doFilterCalled;
    Subject subject;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response)
        throws IOException {
      doFilterCalled = true;
      subject = SubjectUtils.getCurrentSubject();
    }
  }

  private boolean doTestIsStillValid(final Long expiration) throws Exception {
    TokenStateService tss = EasyMock.createNiceMock(TokenStateService.class);
    EasyMock.expect(tss.getTokenExpiration(anyObject(JWT.class)))
            .andReturn(expiration)
            .anyTimes();
    EasyMock.expect(tss.getTokenExpiration(anyObject(String.class)))
            .andReturn(expiration)
            .anyTimes();
    EasyMock.replay(tss);
    return doTestIsStillValid(tss);
  }

  private boolean doTestIsStillValid(final TokenStateService tss) throws Exception {
    GatewayConfig gwConf = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gwConf.isServerManagedTokenStateEnabled()).andReturn(true).anyTimes();
    EasyMock.replay(gwConf);

    ServletContext sc = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(sc.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(gwConf).anyTimes();
    EasyMock.replay(sc);

    JWT jwt = EasyMock.createNiceMock(JWT.class);
    EasyMock.expect(jwt.getClaim(JWTToken.KNOX_ID_CLAIM)).andReturn(UUID.randomUUID().toString()).anyTimes();
    EasyMock.replay(jwt);

    Field tokenStateServiceField = AbstractJWTFilter.class.getDeclaredField("tokenStateService");
    tokenStateServiceField.setAccessible(true);
    tokenStateServiceField.set(handler, tss);

    Method m = AbstractJWTFilter.class.getDeclaredMethod("tokenIsStillValid", JWT.class);
    m.setAccessible(true);
    try {
      return (Boolean) m.invoke(handler, jwt);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Exception) {
        throw (Exception) cause;
      } else {
        throw e;
      }
    }
  }

  static final class TestHandler extends AbstractJWTFilter {
    int lastValidationErrorStatus;
    String lastValidationErrorMessage;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

    }

    @Override
    protected void handleValidationError(HttpServletRequest request, HttpServletResponse response, int status, String error) throws IOException {
      lastValidationErrorStatus = status;
      lastValidationErrorMessage = error;
    }

    @Override
    public void destroy() {

    }

    void setAudiences(final List<String> audiences) {
      this.audiences = audiences;
    }

    void setSignatureVerificationCache(final SignatureVerificationCache cache) {
      this.signatureVerificationCache = cache;
    }

    // validateToken() is protected, so this subclass can call it directly as
    // well; these wrappers just expose it to the test class in the same way
    // a real subclass would use it.
    boolean callValidateToken(final HttpServletRequest request, final HttpServletResponse response,
        final FilterChain chain, final JWT token) throws IOException, ServletException {
      return validateToken(request, response, chain, token);
    }

    boolean callValidateToken(final HttpServletRequest request, final HttpServletResponse response,
        final FilterChain chain, final JWT token, final RequestAudienceValidator validator)
        throws IOException, ServletException {
      return validateToken(request, response, chain, token, validator);
    }
  }

  // ---------------------------------------------------------------------
  // RequestAudienceValidator threaded through validateToken()
  // ---------------------------------------------------------------------
  //
  // These tests only go through validateToken(), which is protected on
  // AbstractJWTFilter -- reachable directly from TestHandler (see its
  // callValidateToken() wrappers above) the same way a real subclass would
  // reach it. There is no test here for the private doFullTokenValidation()
  // overloads: both call sites inside validateToken() call the
  // RequestAudienceValidator-accepting overload directly, so every branch of
  // doFullTokenValidation() is already exercised through validateToken(),
  // and nothing else in the class calls it another way.

  @Test
  public void testValidateTokenForwarderMatchesExplicitDefaultLambdaOnReject() throws Exception {
    final String issuer = "https://issuer.example.com";
    final TestHandler testHandler = (TestHandler) handler;
    configureIssuerAndAudiences(testHandler, issuer, Collections.singletonList("expected-audience"));
    JWT token = newAudienceTestToken(issuer, new String[] {"other-audience"});
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    FilterChain chain = EasyMock.createNiceMock(FilterChain.class);

    boolean viaForwarder = testHandler.callValidateToken(request, response, chain, token);
    boolean viaExplicitLambda = testHandler.callValidateToken(request, response, chain, token, DEFAULT_AUDIENCE_VALIDATOR);

    assertFalse(viaForwarder);
    assertEquals(viaExplicitLambda, viaForwarder);
  }

  @Test
  public void testValidateTokenForwarderMatchesExplicitDefaultLambdaOnAccept() throws Exception {
    final String issuer = "https://issuer.example.com";
    final TestHandler testHandler = (TestHandler) handler;
    configureIssuerAndAudiences(testHandler, issuer, Collections.singletonList("expected-audience"));
    allowSignatureVerificationToSucceed(testHandler);
    JWT token = newAudienceTestToken(issuer, new String[] {"expected-audience"});
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    FilterChain chain = EasyMock.createNiceMock(FilterChain.class);

    boolean viaForwarder = testHandler.callValidateToken(request, response, chain, token);
    boolean viaExplicitLambda = testHandler.callValidateToken(request, response, chain, token, DEFAULT_AUDIENCE_VALIDATOR);

    assertTrue(viaForwarder);
    assertEquals(viaExplicitLambda, viaForwarder);
  }

  @Test
  public void testCustomValidatorAlwaysTrueOverridesMismatchedAudienceToAccept() throws Exception {
    final String issuer = "https://issuer.example.com";
    final TestHandler testHandler = (TestHandler) handler;
    configureIssuerAndAudiences(testHandler, issuer, Collections.singletonList("expected-audience"));
    allowSignatureVerificationToSucceed(testHandler);
    // Token audience does NOT match the configured list -- the default check would reject this.
    JWT token = newAudienceTestToken(issuer, new String[] {"other-audience"});
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    FilterChain chain = EasyMock.createNiceMock(FilterChain.class);
    RequestAudienceValidator alwaysTrue = (req, tok, configuredAudiences) -> AudienceValidationResult.of(true);

    boolean result = testHandler.callValidateToken(request, response, chain, token, alwaysTrue);

    assertTrue("Custom validator returning true should accept the request despite the audience mismatch", result);
  }

  @Test
  public void testCustomValidatorAlwaysFalseOverridesMatchedAudienceToReject() throws Exception {
    final String issuer = "https://issuer.example.com";
    final TestHandler testHandler = (TestHandler) handler;
    configureIssuerAndAudiences(testHandler, issuer, Collections.singletonList("expected-audience"));
    // Token audience DOES match the configured list -- the default check would accept this.
    JWT token = newAudienceTestToken(issuer, new String[] {"expected-audience"});
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    FilterChain chain = EasyMock.createNiceMock(FilterChain.class);
    RequestAudienceValidator alwaysFalse = (req, tok, configuredAudiences) -> AudienceValidationResult.of(false);

    boolean result = testHandler.callValidateToken(request, response, chain, token, alwaysFalse);

    assertFalse("Custom validator returning false should reject the request despite the audience match", result);
    assertEquals(HttpServletResponse.SC_BAD_REQUEST, testHandler.lastValidationErrorStatus);
    assertEquals("Bad request: missing required token audience", testHandler.lastValidationErrorMessage);
  }

  // ---------------------------------------------------------------------
  // Shared helpers for the tests above.
  // ---------------------------------------------------------------------

  private static final RequestAudienceValidator DEFAULT_AUDIENCE_VALIDATOR =
      (req, tok, configuredAudiences) -> AudienceValidationResult.of(AbstractJWTFilter.matchesConfiguredAudiences(tok, configuredAudiences));

  /**
   * expectedIssuers is private on AbstractJWTFilter, has no setter, and is
   * otherwise only populated deep inside init(FilterConfig). Reflection is
   * used here for the same reason it already is in doTestIsStillValid()
   * above (for tokenStateService): there is no accessible way to inject it
   * without dragging in unrelated config-parsing setup just to set one
   * field. This is the one field in this section that genuinely needs it --
   * audiences and signatureVerificationCache are protected, so TestHandler
   * sets them directly (see setAudiences()/setSignatureVerificationCache()).
   */
  private static void setExpectedIssuers(final AbstractJWTFilter target, final List<String> issuers) throws Exception {
    Field field = AbstractJWTFilter.class.getDeclaredField("expectedIssuers");
    field.setAccessible(true);
    field.set(target, issuers);
  }

  private JWT newAudienceTestToken(final String issuer, final String[] tokenAudiences) {
    JWT token = EasyMock.createNiceMock(JWT.class);
    EasyMock.expect(token.getIssuer()).andReturn(issuer).anyTimes();
    EasyMock.expect(token.getAudienceClaims()).andReturn(tokenAudiences).anyTimes();
    EasyMock.expect(token.getExpiresDate()).andReturn(null).anyTimes();
    EasyMock.expect(token.getNotBeforeDate()).andReturn(null).anyTimes();
    EasyMock.replay(token);
    return token;
  }

  private void configureIssuerAndAudiences(final TestHandler testHandler, final String issuer,
      final List<String> configuredAudiences) throws Exception {
    setExpectedIssuers(testHandler, Collections.singletonList(issuer));
    testHandler.setAudiences(configuredAudiences);
  }

  /** Allows verifyTokenSignature()'s cache check to short-circuit straight to "already verified". */
  private void allowSignatureVerificationToSucceed(final TestHandler testHandler) {
    SignatureVerificationCache cache = EasyMock.createNiceMock(SignatureVerificationCache.class);
    EasyMock.expect(cache.hasSignatureBeenVerified(anyString())).andReturn(true).anyTimes();
    EasyMock.replay(cache);
    testHandler.setSignatureVerificationCache(cache);
  }

}
