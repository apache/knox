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
package org.apache.knox.gateway.provider.federation.jwt.filter;

import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.actorChain;
import static org.apache.knox.gateway.provider.federation.jwt.filter.K8sAudienceTestSupport.withAttributes;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.provider.federation.TestFilterConfig;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The audit trail {@link JWTFederationFilter}, configured with
 * {@link K8sDestinationAudienceValidator}, writes for a request presenting a delegated token
 * (KNOX-3568): the audience-check record, followed by the authentication success record with the
 * same destination, or by an authentication failure record stating why the token was rejected.
 */
public class DelegatedRequestAuditTest {

  private static final String SERVER_NAME_HEADER = "x-dest-server-name";
  private static final String DEST_SERVICE = "secret-service.platform.svc.cluster.local";
  private static final String REQUEST_URI = "/gateway/console/auth/api/v1/extauthz/api/v1/secrets";
  private static final String SOURCE_URI = "https://localhost:8443" + REQUEST_URI;
  private static final String ACTOR = "spiffe://td/ns/engines/sa/engine";
  private static final String DESTINATION_FIELDS = " dest_service=" + DEST_SERVICE + " path=" + REQUEST_URI;

  private static RSAPrivateKey privateKey;

  private Auditor originalAuditor;
  private Auditor originalFilterAuditor;
  private AuditService originalAuditService;
  private RecordingAuditor auditor;
  private SignatureStubbedFilter filter;

  @BeforeClass
  public static void generateKey() throws Exception {
    final KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    privateKey = (RSAPrivateKey) kpg.generateKeyPair().getPrivate();
  }

  @Before
  public void setUp() throws Exception {
    auditor = new RecordingAuditor();
    originalAuditor = DelegatedRequestAuditing.auditor;
    DelegatedRequestAuditing.auditor = auditor;
    originalFilterAuditor = (Auditor) filterField("auditor").get(null);
    filterField("auditor").set(null, auditor);
    // the success record is only written with an audit context in place
    originalAuditService = (AuditService) filterField("auditService").get(null);
    final AuditService auditService = EasyMock.createNiceMock(AuditService.class);
    EasyMock.expect(auditService.getContext()).andReturn(EasyMock.createNiceMock(AuditContext.class)).anyTimes();
    EasyMock.replay(auditService);
    filterField("auditService").set(null, auditService);

    final Properties props = new Properties();
    props.put(RequestAudienceValidatorService.REQUEST_AUDIENCE_VALIDATOR_PARAM,
        K8sDestinationAudienceValidator.VALIDATION_METHOD_VALUE);
    props.put(K8sDestinationAudienceValidator.SERVER_NAME_HEADER_PARAM, SERVER_NAME_HEADER);
    filter = new SignatureStubbedFilter();
    filter.init(new TestFilterConfig(props));
  }

  @After
  public void tearDown() throws Exception {
    DelegatedRequestAuditing.auditor = originalAuditor;
    filterField("auditor").set(null, originalFilterAuditor);
    filterField("auditService").set(null, originalAuditService);
  }

  @Test
  public void testAcceptedDelegatedRequest() throws Exception {
    assertTrue(present(delegatedToken("https://" + DEST_SERVICE, expiresIn(60_000)), true));

    assertEquals(2, auditor.entries.size());
    final RecordingAuditor.Entry check = auditor.entries.get(0);
    assertEquals(Action.AUDIENCE_VALIDATION, check.action());
    assertEquals(ActionOutcome.SUCCESS, check.outcome());
    assertEquals("event_type=delegated_audience_check claims_verified=false token_id= sub=alice act_chain="
        + ACTOR + DESTINATION_FIELDS, check.message());

    final RecordingAuditor.Entry authentication = auditor.entries.get(1);
    assertEquals(Action.AUTHENTICATION, authentication.action());
    assertEquals(SOURCE_URI + "?page=2", authentication.resourceName());
    assertEquals(ResourceType.URI, authentication.resourceType());
    assertEquals(ActionOutcome.SUCCESS, authentication.outcome());
    assertEquals("act_chain=" + ACTOR + DESTINATION_FIELDS, authentication.message());
  }

  @Test
  public void testAudienceMismatch() throws Exception {
    assertFalse(present(delegatedToken("https://other.platform.svc.cluster.local", expiresIn(60_000)), true));

    assertEquals(2, auditor.entries.size());
    assertEquals(Action.AUDIENCE_VALIDATION, auditor.entries.get(0).action());
    assertEquals(ActionOutcome.FAILURE, auditor.entries.get(0).outcome());
    assertRejected(auditor.entries.get(1), DESTINATION_FIELDS + " reason=audience_invalid detail=");
  }

  @Test
  public void testAudienceAcceptedButSignatureInvalid() throws Exception {
    assertFalse(present(delegatedToken("https://" + DEST_SERVICE, expiresIn(60_000)), false));

    assertEquals(2, auditor.entries.size());
    assertEquals(Action.AUDIENCE_VALIDATION, auditor.entries.get(0).action());
    assertEquals(ActionOutcome.SUCCESS, auditor.entries.get(0).outcome());
    assertRejected(auditor.entries.get(1), DESTINATION_FIELDS + " reason=signature_invalid");
    assertTrue(auditor.entries.get(1).message().endsWith(" reason=signature_invalid"));
  }

  @Test
  public void testExpiredTokenIsRejectedBeforeTheAudienceCheck() throws Exception {
    assertFalse(present(delegatedToken("https://" + DEST_SERVICE, expiresIn(-60_000)), true));

    assertEquals(1, auditor.entries.size());
    // no audience check ran, so only the request path is known
    assertRejected(auditor.entries.get(0), " path=" + REQUEST_URI + " reason=token_expired");
  }

  @Test
  public void testTokenWithoutActClaimProducesNoDelegationRecords() throws Exception {
    assertTrue(present(token("https://" + DEST_SERVICE, expiresIn(60_000), null), true));
    assertFalse(present(token("https://other.platform.svc.cluster.local", expiresIn(60_000), null), true));
    assertFalse(present(token("https://" + DEST_SERVICE, expiresIn(-60_000), null), true));

    // only the plain authentication success record that predates KNOX-3568
    assertEquals(auditor.entries.toString(), 1, auditor.entries.size());
    assertEquals(Action.AUTHENTICATION, auditor.entries.get(0).action());
    assertEquals(ActionOutcome.SUCCESS, auditor.entries.get(0).outcome());
    assertEquals(null, auditor.entries.get(0).message());
  }

  @Test
  public void testRecordsCarryNoTokenMaterial() throws Exception {
    final SignedJWT jwt = delegatedToken("https://" + DEST_SERVICE, expiresIn(60_000));
    present(jwt, true);
    present(delegatedToken("https://other.platform.svc.cluster.local", expiresIn(60_000)), true);

    final String serialized = jwt.serialize();
    final List<String> messages = auditor.entries.stream().map(RecordingAuditor.Entry::message)
        .collect(Collectors.toList());
    for (final String message : messages) {
      assertFalse(message, message.contains(serialized.substring(0, serialized.indexOf('.'))));
      assertFalse(message, message.contains(serialized.substring(serialized.lastIndexOf('.') + 1)));
    }
  }

  private static void assertRejected(final RecordingAuditor.Entry entry, final String expectedSuffixStart) {
    assertEquals(Action.AUTHENTICATION, entry.action());
    assertEquals(SOURCE_URI, entry.resourceName());
    assertEquals(ResourceType.URI, entry.resourceType());
    assertEquals(ActionOutcome.FAILURE, entry.outcome());
    assertTrue(entry.message(), entry.message().startsWith(
        "event_type=delegated_request_rejected claims_verified=false token_id= sub=alice act_chain=" + ACTOR
            + expectedSuffixStart));
  }

  /** Present {@code jwt} as a Bearer token, and return whether the filter let the request through. */
  private boolean present(final SignedJWT jwt, final boolean signatureValid) throws Exception {
    filter.signatureValid = signatureValid;
    final HttpServletRequest mock = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(mock.getHeader("Authorization"))
        .andReturn(JWTFederationFilter.BEARER + " " + jwt.serialize()).anyTimes();
    EasyMock.expect(mock.getHeader(SERVER_NAME_HEADER)).andReturn(DEST_SERVICE).anyTimes();
    EasyMock.expect(mock.getRequestURI()).andReturn(REQUEST_URI).anyTimes();
    EasyMock.expect(mock.getRequestURL()).andReturn(new StringBuffer(SOURCE_URI)).anyTimes();
    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    final FilterChain chain = EasyMock.createNiceMock(FilterChain.class);
    final boolean[] passed = new boolean[1];
    chain.doFilter(EasyMock.anyObject(), EasyMock.anyObject());
    EasyMock.expectLastCall().andAnswer(() -> {
      passed[0] = true;
      return null;
    }).anyTimes();
    EasyMock.replay(mock, response, chain);

    final HttpServletRequest request = withAttributes(mock);
    request.setAttribute(AbstractGatewayFilter.SOURCE_REQUEST_CONTEXT_URL_ATTRIBUTE_NAME, SOURCE_URI + "?page=2");
    filter.doFilter(request, response, chain);
    return passed[0];
  }

  private static Date expiresIn(final long millis) {
    return new Date(System.currentTimeMillis() + millis);
  }

  private static SignedJWT delegatedToken(final String audience, final Date expires) throws Exception {
    return token(audience, expires, actorChain(ACTOR));
  }

  private static SignedJWT token(final String audience, final Date expires, final Object act) throws Exception {
    final JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
        .issuer(AbstractJWTFilter.JWT_DEFAULT_ISSUER)
        .subject("alice")
        .audience(audience)
        .expirationTime(expires)
        .notBeforeTime(new Date(System.currentTimeMillis() - 60_000));
    if (act != null) {
      claims.claim(JWTToken.ACT_CLAIM, act);
    }
    final SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims.build());
    jwt.sign(new RSASSASigner(privateKey));
    return jwt;
  }

  private static Field filterField(final String name) throws Exception {
    final Field field = AbstractJWTFilter.class.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  /** Signature verification is not under test; let each test decide its outcome. */
  private static final class SignatureStubbedFilter extends JWTFederationFilter {
    boolean signatureValid;

    @Override
    protected boolean verifyTokenSignature(final JWT token) {
      return signatureValid;
    }
  }
}
