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
package org.apache.knox.gateway.provider.federation;

import com.nimbusds.jose.proc.JOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.SignedJWT;
import org.apache.knox.gateway.provider.federation.jwt.filter.AbstractJWTFilter;
import org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.TrustedOidcIssuerService;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.security.PublicKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import static org.apache.knox.gateway.security.CommonTokenConstants.GRANT_TYPE;

/**
 * Tests for two JWTFederationFilter extensions added for Knox IDF delegation.
 *
 * <p><b>Change 1 — TOKEN_ISS_ATTRIBUTE</b> ({@link #testIssAttributeSetAfterValidation}):
 * After successful Bearer JWT validation the token's {@code iss} claim is stored as a request
 * attribute for use by admin endpoint handlers (per-cluster scope limiting).
 *
 * <p><b>Change 2 — Dynamic JWKS for token-exchange</b>: If a token's issuer is absent from
 * the static {@code jwt.expected.issuer} list, the filter consults
 * {@code TrustedOidcIssuerService} via {@code resolveRegisteredIssuerJwks}.
 * If the issuer is registered with {@code isDynamicJwks=true}, the dynamically resolved JWKS
 * URI is used exclusively for signature verification. All other validation (expiry, audiences,
 * nbf, token state) runs via the same {@code doFullTokenValidation} helper as the static path.
 *
 * <p> NOTE: If both subject and actor tokens are present on a token-exchange request, both
 * are validated through the same {@code validateToken()} path. It does not matter if only
 * the subject token is present, both are present, or which use dynamic or static issuers
 * for these validateToken tests. Only the paths through the validateToken method are tested.
 * For the delegation use case, when both subject token and actor token are present, we
 * expect the actor token to have the external issuer and the subject token to have the Knox
 * issuer for the typical use case, so a specific test is added for this use case.
 *
 * <p>NOTE: We do not test the specific failure modes {@code isTokenEnabled} or
 * {@code isIdleTimeoutLimitNotExceeded} in the dynamic JWKS path. It would require more complex
 * {@code TokenStateService} setup; without TSS they return true
 * trivially for both paths, same as the static-issuer path covered by the Knox TSS suite.
 *
 * <p><b>Filter configuration:</b> the default {@link TestFilterConfig} sets
 * {@code jwt.expected.issuer} to {@value AbstractJWTFilter#JWT_DEFAULT_ISSUER} only. No static
 * JWKS URLs are configured unless a test explicitly sets {@link JWTFederationFilter#JWKS_URL}.
 */
public class JWTFederationFilterTokenExchangeTest extends AbstractJWTFilterTest {

  static final String EXTERNAL_ISSUER = "https://external.oidc.example.com";
  static final String KNOX_ISSUER = AbstractJWTFilter.JWT_DEFAULT_ISSUER;
  static final String DYNAMIC_JWKS_URI = "https://external.oidc.example.com/.well-known/jwks.json";

  @Before
  public void setUp() {
    handler = new TestJWTFederationFilter();
    ((TestJWTFederationFilter) handler).setTokenService(new TestJWTokenAuthority(publicKey));
  }

  @Override
  protected String getAudienceProperty() {
    return JWTFederationFilter.KNOX_TOKEN_AUDIENCES;
  }

  @Override
  protected String getVerificationPemProperty() {
    return JWTFederationFilter.TOKEN_VERIFICATION_PEM;
  }

  @Override
  protected void setTokenOnRequest(HttpServletRequest request, SignedJWT jwt) {
    EasyMock.expect(request.getHeader("Authorization"))
        .andReturn(JWTFederationFilter.BEARER + " " + jwt.serialize());
  }

  @Override
  protected void setGarbledTokenOnRequest(HttpServletRequest request, SignedJWT jwt) {
    EasyMock.expect(request.getHeader("Authorization"))
        .andReturn(JWTFederationFilter.BEARER + " ljm" + jwt.serialize());
  }

  // ---------------------------------------------------------------------------
  // Dynamic registry path — success
  // ---------------------------------------------------------------------------

  /**
   * Subject token from EXTERNAL_ISSUER (not in static list); actor token from KNOX_ISSUER
   * (static path). The authority mock verifies the dynamic path calls verifyToken with the
   * resolved JWKS URI, configured sig-alg, and type-verifier. The static path calls
   * verifyToken with only the token (instance-key, no PEM or JWKS URLs configured).
   */
  @Test
  public void testDynamicIssuerAllowedSubjectExternal() throws Exception {
    handler.init(new TestFilterConfig(getProperties()));

    final SignedJWT subjectJwt = getJWT(EXTERNAL_ISSUER, "k8s-sa",
        new Date(System.currentTimeMillis() + 60000));
    final SignedJWT actorJwt = getJWT(KNOX_ISSUER, "actor-svc",
        new Date(System.currentTimeMillis() + 60000));

    final Capture<JWT> capturedDynamicJwt = EasyMock.newCapture();
    final Capture<JWT> capturedStaticJwt = EasyMock.newCapture();

    final JWTokenAuthority mockAuth = EasyMock.createMock(JWTokenAuthority.class);
    EasyMock.expect(mockAuth.verifyToken(
        EasyMock.capture(capturedDynamicJwt),
        EasyMock.eq(Set.of(new URI(DYNAMIC_JWKS_URI))),
        EasyMock.eq(AbstractJWTFilter.JWT_DEFAULT_SIGALG), // configured sig-alg
        EasyMock.isA(JOSEObjectTypeVerifier.class)))       // filter-configured type verifier
        .andReturn(true).once();
    EasyMock.expect(mockAuth.verifyToken(EasyMock.capture(capturedStaticJwt))).andReturn(true).once();
    EasyMock.replay(mockAuth);
    ((TestJWTFederationFilter) handler).setTokenService(mockAuth);

    final TrustedOidcIssuerService issuerSvc = EasyMock.createMock(TrustedOidcIssuerService.class);
    EasyMock.expect(issuerSvc.isDynamicJwks(EXTERNAL_ISSUER)).andReturn(true).once();
    EasyMock.expect(issuerSvc.resolveJwksUri(EXTERNAL_ISSUER)).andReturn(Optional.of(DYNAMIC_JWKS_URI)).once();

    final HttpServletRequest request = buildTokenExchangeRequest(
        subjectJwt.serialize(), actorJwt.serialize(), buildContextWithIssuerService(issuerSvc));
    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.replay(request, response, issuerSvc);

    final TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    Assert.assertTrue("Filter chain should proceed", chain.doFilterCalled);
    Assert.assertEquals(EXTERNAL_ISSUER, capturedDynamicJwt.getValue().getIssuer());
    Assert.assertEquals(KNOX_ISSUER, capturedStaticJwt.getValue().getIssuer());
    EasyMock.verify(mockAuth, issuerSvc);
  }

  /**
   * Actor token from EXTERNAL_ISSUER (dynamic path); subject token from KNOX_ISSUER (static
   * path). This is the primary K8s SA delegation scenario: the acting service carries a
   * projected SA token with a dynamically registered issuer; the subject carries a Knox-issued
   * token. The authority mock verifies the same argument contract as the previous test.
   */
  @Test
  public void testDynamicIssuerAllowedActorExternal() throws Exception {
    handler.init(new TestFilterConfig(getProperties()));

    final SignedJWT subjectJwt = getJWT(KNOX_ISSUER, "end-user",
        new Date(System.currentTimeMillis() + 60000));
    final SignedJWT actorJwt = getJWT(EXTERNAL_ISSUER, "k8s-sa",
        new Date(System.currentTimeMillis() + 60000));

    final Capture<JWT> capturedDynamicJwt = EasyMock.newCapture();
    final Capture<JWT> capturedStaticJwt = EasyMock.newCapture();

    final JWTokenAuthority mockAuth = EasyMock.createMock(JWTokenAuthority.class);
    EasyMock.expect(mockAuth.verifyToken(EasyMock.capture(capturedStaticJwt))).andReturn(true).once();
    EasyMock.expect(mockAuth.verifyToken(
        EasyMock.capture(capturedDynamicJwt),
        EasyMock.eq(Set.of(new URI(DYNAMIC_JWKS_URI))),
        EasyMock.eq(AbstractJWTFilter.JWT_DEFAULT_SIGALG), // configured sig-alg
        EasyMock.isA(JOSEObjectTypeVerifier.class)))       // filter-configured type verifier
        .andReturn(true).once();
    EasyMock.replay(mockAuth);
    ((TestJWTFederationFilter) handler).setTokenService(mockAuth);

    final TrustedOidcIssuerService issuerSvc = EasyMock.createMock(TrustedOidcIssuerService.class);
    EasyMock.expect(issuerSvc.isDynamicJwks(EXTERNAL_ISSUER)).andReturn(true).once();
    EasyMock.expect(issuerSvc.resolveJwksUri(EXTERNAL_ISSUER)).andReturn(Optional.of(DYNAMIC_JWKS_URI)).once();

    final HttpServletRequest request = buildTokenExchangeRequest(
        subjectJwt.serialize(), actorJwt.serialize(), buildContextWithIssuerService(issuerSvc));
    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.replay(request, response, issuerSvc);

    final TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    Assert.assertTrue("Filter chain should proceed", chain.doFilterCalled);
    Assert.assertEquals(EXTERNAL_ISSUER, capturedDynamicJwt.getValue().getIssuer());
    Assert.assertEquals(KNOX_ISSUER, capturedStaticJwt.getValue().getIssuer());
    EasyMock.verify(mockAuth, issuerSvc);
  }

  // ---------------------------------------------------------------------------
  // Dynamic registry path — signature, expiry, nbf, audience failures
  // ---------------------------------------------------------------------------

  /**
   * Dynamic JWKS resolved; authority.verifyToken returns false for that URI. The authority
   * mock verifies the exact JWKS URI, configured sig-alg ("RS256"), and JOSEObjectTypeVerifier
   * type were passed to authority.verifyToken. Any other authority call (static JWKS, instance
   * key) would fail the strict mock.
   */
  @Test
  public void testSignatureVerificationFails() throws Exception {
    handler.init(new TestFilterConfig(getProperties()));

    final SignedJWT subjectJwt = getJWT(EXTERNAL_ISSUER, "some-subject",
        new Date(System.currentTimeMillis() + 60000));
    final SignedJWT actorJwt = getJWT(KNOX_ISSUER, "actor-svc",
        new Date(System.currentTimeMillis() + 60000));

    final JWTokenAuthority mockAuth = EasyMock.createMock(JWTokenAuthority.class);
    EasyMock.expect(mockAuth.verifyToken(
        EasyMock.anyObject(JWT.class),
        EasyMock.eq(Set.of(new URI(DYNAMIC_JWKS_URI))),
        EasyMock.eq(AbstractJWTFilter.JWT_DEFAULT_SIGALG), // configured sig-alg
        EasyMock.isA(JOSEObjectTypeVerifier.class)))       // filter-configured type verifier
        .andReturn(false).once();
    EasyMock.replay(mockAuth);
    ((TestJWTFederationFilter) handler).setTokenService(mockAuth);

    final TrustedOidcIssuerService issuerSvc = EasyMock.createMock(TrustedOidcIssuerService.class);
    EasyMock.expect(issuerSvc.isDynamicJwks(EXTERNAL_ISSUER)).andReturn(true).once();
    EasyMock.expect(issuerSvc.resolveJwksUri(EXTERNAL_ISSUER)).andReturn(Optional.of(DYNAMIC_JWKS_URI)).once();

    final HttpServletRequest request = buildTokenExchangeRequest(
        subjectJwt.serialize(), actorJwt.serialize(), buildContextWithIssuerService(issuerSvc));
    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    EasyMock.expectLastCall().once();
    EasyMock.replay(request, response, issuerSvc);

    final TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    Assert.assertFalse(chain.doFilterCalled);
    EasyMock.verify(mockAuth, issuerSvc, response);
  }

  /**
   * Dynamic JWKS resolved, but the token is expired. {@code DynamicJwksPassTokenAuthority}
   * makes JWKS signature verification always succeed, so the "Token has expired" rejection
   * is the guaranteed outcome regardless of the order in which expiry and signature are checked.
   * {@code verify(issuerSvc)} confirms the dynamic path was entered before the expiry check.
   */
  @Test
  public void testExpiredTokenRejectedOnDynamicPath() throws Exception {
    ((TestJWTFederationFilter) handler).setTokenService(new DynamicJwksPassTokenAuthority(publicKey));
    handler.init(new TestFilterConfig(getProperties()));

    final SignedJWT expiredJwt = getJWT(EXTERNAL_ISSUER, "k8s-sa",
        new Date(System.currentTimeMillis() - 60000));
    final SignedJWT actorJwt = getJWT(KNOX_ISSUER, "actor-svc",
        new Date(System.currentTimeMillis() + 60000));

    final TrustedOidcIssuerService issuerSvc = EasyMock.createMock(TrustedOidcIssuerService.class);
    EasyMock.expect(issuerSvc.isDynamicJwks(EXTERNAL_ISSUER)).andReturn(true).once();
    EasyMock.expect(issuerSvc.resolveJwksUri(EXTERNAL_ISSUER)).andReturn(Optional.of(DYNAMIC_JWKS_URI)).once();

    final HttpServletRequest request = buildTokenExchangeRequest(
        expiredJwt.serialize(), actorJwt.serialize(), buildContextWithIssuerService(issuerSvc));
    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token has expired");
    EasyMock.expectLastCall().once();
    EasyMock.replay(request, response, issuerSvc);

    final TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    Assert.assertFalse(chain.doFilterCalled);
    EasyMock.verify(issuerSvc, response);
  }

  /**
   * Dynamic JWKS resolved, but the token's NotBefore is in the future.
   * {@code DynamicJwksPassTokenAuthority} makes JWKS signature verification always succeed,
   * so the "NotBefore check failed" rejection is the guaranteed outcome regardless of the
   * order in which nbf and signature are checked.
   */
  @Test
  public void testFutureNbfRejectedOnDynamicPath() throws Exception {
    ((TestJWTFederationFilter) handler).setTokenService(new DynamicJwksPassTokenAuthority(publicKey));
    handler.init(new TestFilterConfig(getProperties()));

    final Date futureNbf = new Date(System.currentTimeMillis() + 300000);
    final Date futureExpiry = new Date(System.currentTimeMillis() + 600000);
    final SignedJWT nbfJwt = getJWT(EXTERNAL_ISSUER, "k8s-sa", futureExpiry, futureNbf, privateKey, "RS256");
    final SignedJWT actorJwt = getJWT(KNOX_ISSUER, "actor-svc",
        new Date(System.currentTimeMillis() + 60000));

    final TrustedOidcIssuerService issuerSvc = EasyMock.createMock(TrustedOidcIssuerService.class);
    EasyMock.expect(issuerSvc.isDynamicJwks(EXTERNAL_ISSUER)).andReturn(true).once();
    EasyMock.expect(issuerSvc.resolveJwksUri(EXTERNAL_ISSUER)).andReturn(Optional.of(DYNAMIC_JWKS_URI)).once();

    final HttpServletRequest request = buildTokenExchangeRequest(
        nbfJwt.serialize(), actorJwt.serialize(), buildContextWithIssuerService(issuerSvc));
    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request: the NotBefore check failed");
    EasyMock.expectLastCall().once();
    EasyMock.replay(request, response, issuerSvc);

    final TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    Assert.assertFalse(chain.doFilterCalled);
    EasyMock.verify(issuerSvc, response);
  }

  /**
   * Dynamic JWKS resolved, but the token's audience does not match the required audience.
   * {@code DynamicJwksPassTokenAuthority} makes signature verification always succeed, so
   * the audience rejection is the guaranteed outcome regardless of validation order.
   */
  @Test
  public void testAudienceMismatchRejectedOnDynamicPath() throws Exception {
    ((TestJWTFederationFilter) handler).setTokenService(new DynamicJwksPassTokenAuthority(publicKey));
    final Properties props = getProperties();
    props.setProperty(JWTFederationFilter.KNOX_TOKEN_AUDIENCES, "required-audience");
    handler.init(new TestFilterConfig(props));

    final SignedJWT subjectJwt = getJWT(EXTERNAL_ISSUER, "k8s-sa",
        new Date(System.currentTimeMillis() + 60000)); // default aud="bar", not "required-audience"
    final SignedJWT actorJwt = getJWT(KNOX_ISSUER, "actor-svc",
        new Date(System.currentTimeMillis() + 60000));

    final TrustedOidcIssuerService issuerSvc = EasyMock.createMock(TrustedOidcIssuerService.class);
    EasyMock.expect(issuerSvc.isDynamicJwks(EXTERNAL_ISSUER)).andReturn(true).once();
    EasyMock.expect(issuerSvc.resolveJwksUri(EXTERNAL_ISSUER)).andReturn(Optional.of(DYNAMIC_JWKS_URI)).once();

    final HttpServletRequest request = buildTokenExchangeRequest(
        subjectJwt.serialize(), actorJwt.serialize(), buildContextWithIssuerService(issuerSvc));
    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Bad request: missing required token audience");
    EasyMock.expectLastCall().once();
    EasyMock.replay(request, response, issuerSvc);

    final TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    Assert.assertFalse(chain.doFilterCalled);
    EasyMock.verify(issuerSvc, response);
  }

  // ---------------------------------------------------------------------------
  // Token rejected — issuer does not qualify for dynamic JWKS verification
  // ---------------------------------------------------------------------------

  /**
   * The issuer is not registered in the dynamic registry; isDynamicJwks returns false. The
   * filter rejects with 401. resolveJwksUri is not expected on the strict mock — any call to
   * it would fail verify(), proving no HTTP fetch was attempted (SSRF prevention).
   */
  @Test
  public void testUntrustedIssuerRejectedNoHttpCall() throws Exception {
    handler.init(new TestFilterConfig(getProperties()));

    final SignedJWT subjectJwt = getJWT(EXTERNAL_ISSUER, "some-subject",
        new Date(System.currentTimeMillis() + 60000));
    final SignedJWT actorJwt = getJWT(KNOX_ISSUER, "actor-svc",
        new Date(System.currentTimeMillis() + 60000));

    final TrustedOidcIssuerService issuerSvc = EasyMock.createMock(TrustedOidcIssuerService.class);
    EasyMock.expect(issuerSvc.isDynamicJwks(EXTERNAL_ISSUER)).andReturn(false).once();
    // resolveJwksUri not expected — any call fails verify(), proving no HTTP fetch attempted

    final HttpServletRequest request = buildTokenExchangeRequest(
        subjectJwt.serialize(), actorJwt.serialize(), buildContextWithIssuerService(issuerSvc));
    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    EasyMock.expectLastCall().once();
    EasyMock.replay(request, response, issuerSvc);

    final TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    Assert.assertFalse(chain.doFilterCalled);
    EasyMock.verify(issuerSvc, response);
  }

  /**
   * TrustedOidcIssuerService is null. The hook returns without calling any service method.
   * EXTERNAL_ISSUER is not in expectedIssuers, so the filter rejects.
   */
  @Test
  public void testServiceUnavailable() throws Exception {
    handler.init(new TestFilterConfig(getProperties()));

    final SignedJWT subjectJwt = getJWT(EXTERNAL_ISSUER, "some-subject",
        new Date(System.currentTimeMillis() + 60000));
    final SignedJWT actorJwt = getJWT(KNOX_ISSUER, "actor-svc",
        new Date(System.currentTimeMillis() + 60000));

    final GatewayServices gws = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(gws.getService(ServiceType.TRUSTED_OIDC_ISSUER_SERVICE)).andReturn(null).anyTimes();
    EasyMock.replay(gws);

    final HttpServletRequest request = buildTokenExchangeRequest(
        subjectJwt.serialize(), actorJwt.serialize(), buildServletContext(gws));
    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    EasyMock.expectLastCall().once();
    EasyMock.replay(request, response);

    final TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    Assert.assertFalse(chain.doFilterCalled);
    EasyMock.verify(response);
  }

  /**
   * Bearer JWT from EXTERNAL_ISSUER with no grant_type — not a token-exchange request. The
   * hook checks grant_type first and returns without consulting the registry. EXTERNAL_ISSUER
   * is not in expectedIssuers, so the filter rejects. The strict mock proves no service method
   * was called.
   */
  @Test
  public void testNonTokenExchangeRegistryIssuerRejected() throws Exception {
    handler.init(new TestFilterConfig(getProperties()));

    final SignedJWT jwt = getJWT(EXTERNAL_ISSUER, "k8s-sa",
        new Date(System.currentTimeMillis() + 60000));

    final TrustedOidcIssuerService strictIssuerSvc = EasyMock.createMock(TrustedOidcIssuerService.class);
    EasyMock.replay(strictIssuerSvc);

    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
    EasyMock.expect(request.getHeader("Authorization"))
        .andReturn(JWTFederationFilter.BEARER + " " + jwt.serialize()).anyTimes();
    EasyMock.expect(request.getServletContext())
        .andReturn(buildContextWithIssuerService(strictIssuerSvc)).anyTimes();
    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.replay(request, response);

    final TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    Assert.assertFalse(chain.doFilterCalled);
    EasyMock.verify(strictIssuerSvc);
  }

  // ---------------------------------------------------------------------------
  // Static-issuer failures do not fall through to the dynamic path
  // ---------------------------------------------------------------------------

  /**
   * KNOX_ISSUER is in expectedIssuers. Signature verification on the static path fails.
   * The strict issuerSvc mock with no expectations proves isDynamicJwks was never called —
   * the static-issuer failure does not trigger the dynamic registry.
   */
  @Test
  public void testStaticIssuerSignatureFailureDoesNotFallToDynamic() throws Exception {
    handler.init(new TestFilterConfig(getProperties()));

    final SignedJWT jwt = getJWT(KNOX_ISSUER, "some-user",
        new Date(System.currentTimeMillis() + 60000));

    final JWTokenAuthority mockAuth = EasyMock.createMock(JWTokenAuthority.class);
    EasyMock.expect(mockAuth.verifyToken(EasyMock.anyObject(JWT.class))).andReturn(false).once();
    EasyMock.replay(mockAuth);
    ((TestJWTFederationFilter) handler).setTokenService(mockAuth);

    final TrustedOidcIssuerService strictIssuerSvc = EasyMock.createMock(TrustedOidcIssuerService.class);
    EasyMock.replay(strictIssuerSvc);

    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
    EasyMock.expect(request.getHeader("Authorization"))
        .andReturn(JWTFederationFilter.BEARER + " " + jwt.serialize()).anyTimes();
    EasyMock.expect(request.getServletContext())
        .andReturn(buildContextWithIssuerService(strictIssuerSvc)).anyTimes();
    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    EasyMock.expectLastCall().once();
    EasyMock.replay(request, response);

    final TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    Assert.assertFalse(chain.doFilterCalled);
    EasyMock.verify(mockAuth, strictIssuerSvc, response);
  }

  // ---------------------------------------------------------------------------
  // Static JWKS and dynamic registry both configured
  // ---------------------------------------------------------------------------

  /**
   * Static JWKS (knox.token.jwks.url) and dynamic registry are both configured. The authority
   * mock is strict with distinct URI-set expectations per token: the external-issuer token uses
   * the dynamic JWKS URI exclusively (never the static JWKS), and the KNOX_ISSUER token uses
   * the static JWKS. The eq() on sig-alg verifies the configured value ("RS256") is passed to
   * authority.verifyToken on the dynamic path.
   */
  @Test
  public void testDynamicPathUsesRegistryJwksNotStaticJwks() throws Exception {
    final String staticJwksUrl = "https://static.jwks.example.com/jwks";
    final String dynamicJwksUrl = "https://dynamic.jwks.example.com/jwks";
    final Set<URI> staticJwks = Set.of(new URI(staticJwksUrl));
    final Set<URI> dynamicJwks = Set.of(new URI(dynamicJwksUrl));

    final Properties props = getProperties();
    props.setProperty(JWTFederationFilter.JWKS_URL, staticJwksUrl);
    handler.init(new TestFilterConfig(props));

    final JWTokenAuthority mockAuth = EasyMock.createMock(JWTokenAuthority.class);
    EasyMock.expect(mockAuth.verifyToken(
        EasyMock.anyObject(JWT.class), EasyMock.eq(dynamicJwks), // external-issuer token: dynamic JWKS only
        EasyMock.eq(AbstractJWTFilter.JWT_DEFAULT_SIGALG),
        EasyMock.isA(JOSEObjectTypeVerifier.class)))
        .andReturn(true).once();
    EasyMock.expect(mockAuth.verifyToken(
        EasyMock.anyObject(JWT.class), EasyMock.eq(staticJwks),  // Knox-issuer token: static JWKS
        EasyMock.eq(AbstractJWTFilter.JWT_DEFAULT_SIGALG),
        EasyMock.isA(JOSEObjectTypeVerifier.class)))
        .andReturn(true).once();
    EasyMock.replay(mockAuth);
    ((TestJWTFederationFilter) handler).setTokenService(mockAuth);

    final SignedJWT subjectJwt = getJWT(EXTERNAL_ISSUER, "k8s-sa",
        new Date(System.currentTimeMillis() + 60000));
    final SignedJWT actorJwt = getJWT(KNOX_ISSUER, "actor-svc",
        new Date(System.currentTimeMillis() + 60000));

    final TrustedOidcIssuerService issuerSvc = EasyMock.createMock(TrustedOidcIssuerService.class);
    EasyMock.expect(issuerSvc.isDynamicJwks(EXTERNAL_ISSUER)).andReturn(true).once();
    EasyMock.expect(issuerSvc.resolveJwksUri(EXTERNAL_ISSUER)).andReturn(Optional.of(dynamicJwksUrl)).once();

    final HttpServletRequest request = buildTokenExchangeRequest(
        subjectJwt.serialize(), actorJwt.serialize(), buildContextWithIssuerService(issuerSvc));
    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.replay(request, response, issuerSvc);

    final TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    Assert.assertTrue(chain.doFilterCalled);
    EasyMock.verify(mockAuth, issuerSvc);
  }

  // ---------------------------------------------------------------------------
  // Existing behavior unaffected by the new hook
  // ---------------------------------------------------------------------------

  /**
   * Bearer JWT from KNOX_ISSUER (in static expectedIssuers). validateToken() returns from the
   * static-issuer branch before resolveRegisteredIssuerJwks is reached. The strict issuerSvc
   * mock with no expectations proves the hook was not called: any service method call would
   * throw immediately.
   */
  @Test
  public void testNonTokenExchangeGrantUnaffected() throws Exception {
    handler.init(new TestFilterConfig(getProperties()));

    final SignedJWT jwt = getJWT(KNOX_ISSUER, "some-user",
        new Date(System.currentTimeMillis() + 60000));

    final TrustedOidcIssuerService strictIssuerSvc = EasyMock.createMock(TrustedOidcIssuerService.class);
    EasyMock.replay(strictIssuerSvc);

    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
    EasyMock.expect(request.getHeader("Authorization"))
        .andReturn(JWTFederationFilter.BEARER + " " + jwt.serialize()).anyTimes();
    EasyMock.expect(request.getServletContext())
        .andReturn(buildContextWithIssuerService(strictIssuerSvc)).anyTimes();
    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.replay(request, response);

    final TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    Assert.assertTrue(chain.doFilterCalled);
    EasyMock.verify(strictIssuerSvc);
  }

  // ---------------------------------------------------------------------------
  // TOKEN_ISS_ATTRIBUTE — separate concern from JWKS logic
  // ---------------------------------------------------------------------------

  /**
   * After successful Bearer JWT validation, addKnoxIDFAttributes() stores TOKEN_ISS_ATTRIBUTE
   * on the request. Used by admin endpoint handlers for per-cluster scope limiting.
   */
  @Test
  public void testIssAttributeSetAfterValidation() throws Exception {
    handler.init(new TestFilterConfig(getProperties()));

    final SignedJWT jwt = getJWT(KNOX_ISSUER, "some-user",
        new Date(System.currentTimeMillis() + 60000));

    final Map<String, Object> capturedAttrs = new HashMap<>();
    final HttpServletRequest underlying = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(underlying.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
    EasyMock.expect(underlying.getHeader("Authorization"))
        .andReturn(JWTFederationFilter.BEARER + " " + jwt.serialize()).anyTimes();
    EasyMock.replay(underlying);

    final HttpServletRequest request = new HttpServletRequestWrapper(underlying) {
      @Override
      public void setAttribute(String name, Object o) {
        capturedAttrs.put(name, o);
      }

      @Override
      public Object getAttribute(String name) {
        return capturedAttrs.get(name);
      }
    };

    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.replay(response);

    final TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    Assert.assertTrue(chain.doFilterCalled);
    Assert.assertEquals(KNOX_ISSUER, capturedAttrs.get(KnoxIDFConstants.TOKEN_ISS_ATTRIBUTE));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private ServletContext buildContextWithIssuerService(TrustedOidcIssuerService issuerSvc) {
    final GatewayServices gws = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(gws.getService(ServiceType.TRUSTED_OIDC_ISSUER_SERVICE)).andReturn(issuerSvc).anyTimes();
    EasyMock.replay(gws);
    return buildServletContext(gws);
  }

  private ServletContext buildServletContext(GatewayServices gws) {
    final ServletContext ctx = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(ctx.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(gws).anyTimes();
    EasyMock.expect(ctx.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE))
        .andReturn("jwt-test-topology").anyTimes();
    EasyMock.replay(ctx);
    return ctx;
  }

  private HttpServletRequest buildTokenExchangeRequest(String subjectToken, String actorToken,
      ServletContext ctx) {
    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
    EasyMock.expect(request.getParameter(GRANT_TYPE)).andReturn(JWTFederationFilter.TOKEN_EXCHANGE).anyTimes();
    EasyMock.expect(request.getParameter(JWTFederationFilter.SUBJECT_TOKEN)).andReturn(subjectToken).anyTimes();
    EasyMock.expect(request.getParameter(JWTFederationFilter.ACTOR_TOKEN)).andReturn(actorToken).anyTimes();
    EasyMock.expect(request.getServletContext()).andReturn(ctx).anyTimes();
    return request;
  }

  // ---------------------------------------------------------------------------
  // Inner classes
  // ---------------------------------------------------------------------------

  /**
   * Token authority that always passes JWKS-URI-based verification and uses real RSA for the
   * instance-key path. Allows tests to focus on non-signature failures (expiry, nbf, audiences)
   * without binding the test outcome to the current order of validation checks.
   */
  private static class DynamicJwksPassTokenAuthority extends TestJWTokenAuthority {

    DynamicJwksPassTokenAuthority(PublicKey pk) {
      super(pk);
    }

    @Override
    public boolean verifyToken(JWT token, Set<URI> jwksurls, String algorithm,
        JOSEObjectTypeVerifier<SecurityContext> typeVerifier) throws TokenServiceException {
      return true;
    }
  }
}
