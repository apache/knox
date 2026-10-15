/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import org.apache.knox.gateway.provider.federation.TestFilterConfig;
import org.apache.knox.gateway.provider.federation.jwt.JWTMessages;
import org.apache.knox.gateway.util.Tokens;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.Properties;

/**
 * KNOX-3516: a JWT whose issuer is neither in {@code jwt.expected.issuer} nor a runtime-registered
 * issuer used to be rejected with a bare 401 and no log message. These tests pin the server-side
 * log record and the error handed to the client, and that the (still unverified, hence
 * attacker-controlled) issuer claim echoed by both is sanitized. The sanitizing itself is covered
 * by {@code TokenUtilsTest}.
 */
public class UnexpectedIssuerRejectionTest {

  private static final String UNTRUSTED_ISSUER = "https://untrusted.oidc.example.com";

  private static RSAPrivateKey privateKey;

  private JWTMessages originalLog;
  private JWTMessages log;
  private JWTFederationFilter filter;

  @BeforeClass
  public static void generateKey() throws Exception {
    final KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    final KeyPair keyPair = kpg.generateKeyPair();
    privateKey = (RSAPrivateKey) keyPair.getPrivate();
  }

  @Before
  public void setUp() throws Exception {
    originalLog = AbstractJWTFilter.log;
    log = EasyMock.createNiceMock(JWTMessages.class);
    AbstractJWTFilter.log = log;

    filter = new JWTFederationFilter();
    filter.init(new TestFilterConfig(new Properties()));
  }

  @After
  public void tearDown() {
    AbstractJWTFilter.log = originalLog;
  }

  /**
   * The reported scenario: a foreign token (no {@code knox.id} claim, so no Knox token id) from an
   * issuer Knox does not trust. The rejection must be logged with the issuer and the redacted token.
   */
  @Test
  public void testUnexpectedIssuerIsLogged() throws Exception {
    final SignedJWT jwt = getForeignJWT(UNTRUSTED_ISSUER);

    log.rejectedTokenWithUnexpectedIssuer(
        EasyMock.eq(Tokens.getTokenDisplayText(jwt.serialize())), EasyMock.isNull(), EasyMock.eq(UNTRUSTED_ISSUER));
    EasyMock.expectLastCall().once();
    EasyMock.replay(log);

    final String error = rejectAndCaptureError(jwt);

    EasyMock.verify(log);
    assertEquals(AbstractJWTFilter.UNTRUSTED_ISSUER_PREFIX + UNTRUSTED_ISSUER + AbstractJWTFilter.UNTRUSTED_ISSUER_POSTFIX,
        error);
  }

  @Test
  public void testUnexpectedIssuerControlCharactersAreNeutralized() throws Exception {
    final SignedJWT jwt = getForeignJWT("https://evil.example.com\r\n2026-10-15 ERROR forged log line");
    final String sanitizedIssuer = "https://evil.example.com??2026-10-15 ERROR forged log line";

    log.rejectedTokenWithUnexpectedIssuer(EasyMock.anyString(), EasyMock.isNull(), EasyMock.eq(sanitizedIssuer));
    EasyMock.expectLastCall().once();
    EasyMock.replay(log);

    final String error = rejectAndCaptureError(jwt);

    EasyMock.verify(log);
    assertTrue(error, error.contains(sanitizedIssuer));
  }

  /**
   * SSOCookieFederationFilter treats an error that starts with {@code "Token "} and ends with
   * {@code " is disabled"} or {@code " exceeded idle timeout"} as an invalid SSO cookie (cookie
   * removal, session invalidation, possibly a global logout). An issuer crafted with such a suffix
   * must not be able to steer the rejection into that branch.
   */
  @Test
  public void testUnexpectedIssuerCannotForgeInvalidSsoCookieSuffix() throws Exception {
    EasyMock.replay(log);
    for (String postfix : new String[] {AbstractJWTFilter.DISABLED_POSTFIX, AbstractJWTFilter.IDLE_TIMEOUT_POSTFIX}) {
      final String error = rejectAndCaptureError(getForeignJWT("forged" + postfix));

      assertTrue(error, error.startsWith(AbstractJWTFilter.TOKEN_PREFIX));
      assertFalse(error, error.endsWith(postfix));
      assertTrue(error, error.endsWith(AbstractJWTFilter.UNTRUSTED_ISSUER_POSTFIX));
    }
  }

  /**
   * Presents {@code jwt} as a Bearer token on a plain (non token-exchange) request, asserts it is
   * rejected with 401, and returns the error message passed to {@code sendError}.
   */
  private String rejectAndCaptureError(final SignedJWT jwt) throws Exception {
    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer("https://localhost:8443/resource")).anyTimes();
    EasyMock.expect(request.getHeader("Authorization"))
        .andReturn(JWTFederationFilter.BEARER + " " + jwt.serialize()).anyTimes();

    final Capture<String> error = EasyMock.newCapture();
    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    response.sendError(EasyMock.eq(HttpServletResponse.SC_UNAUTHORIZED), EasyMock.capture(error));
    EasyMock.expectLastCall().once();

    final FilterChain chain = EasyMock.createStrictMock(FilterChain.class);
    EasyMock.replay(request, response, chain);

    filter.doFilter(request, response, chain);

    EasyMock.verify(response, chain);
    return error.getValue();
  }

  /** A token as issued by a foreign IdP: no {@code knox.id} claim. */
  private static SignedJWT getForeignJWT(final String issuer) throws Exception {
    return JWTTestUtils.getJWT(issuer, "alice", "bar", new Date(System.currentTimeMillis() + 60000), new Date(),
        privateKey, JWSAlgorithm.RS256.getName(), null);
  }
}
