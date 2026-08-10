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
package org.apache.knox.gateway.service.knoxidf;

import static org.apache.knox.gateway.security.CommonTokenConstants.CLIENT_SECRET;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CLIENT_ID;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CODE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CODE_CHALLENGE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.REDIRECT_URI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;

import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.impl.TokenMAC;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies the single-use enforcement of the authorization_code grant (finding 2.4). The code must
 * be atomically consumed BEFORE any token is issued: exactly one of N concurrent redemptions wins
 * the consume and proceeds to issuance; the losers are rejected with {@code invalid_grant} and no
 * token is minted. This closes the replay window that existed when the code was only revoked in a
 * {@code finally} block after issuance.
 */
public class TokenResourceAuthCodeReplayTest {

  private static final String AUTH_CODE = "auth-code-xyz";
  private static final String CLIENT = "client-abc";
  private static final String REDIRECT = "https://app.example/cb";
  private static final String USER_NAME = "alice";
  private static final long ISSUE_TIME = 1_700_000_000_000L;
  private static final String RAW_PASSCODE = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0";

  private TokenStateService tokenStateService;
  private TestableTokenResource resource;
  private final AtomicInteger issuedCount = new AtomicInteger();

  /**
   * Exposes field injection and stubs out the heavy token-issuance path so the replay guard can be
   * exercised in isolation. {@code getAuthenticationToken} is the step that mints tokens; here it
   * only records that issuance was reached and returns a sentinel.
   */
  final class TestableTokenResource extends TokenResource {
    void inject(final TokenStateService tss, final TokenMAC mac, final HttpServletRequest req) {
      this.tokenStateService = tss;
      this.tokenMAC = mac;
      this.request = req;
    }

    @Override
    public Response getAuthenticationToken() {
      issuedCount.incrementAndGet();
      return Response.ok("issued").build();
    }
  }

  private static String wireSecret(final String tokenId, final String rawPasscode) {
    final String inner = Base64.getEncoder().encodeToString(tokenId.getBytes(StandardCharsets.UTF_8))
        + "::" + Base64.getEncoder().encodeToString(rawPasscode.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(inner.getBytes(StandardCharsets.UTF_8));
  }

  @Before
  public void setUp() throws Exception {
    final TokenMAC tokenMAC = new TokenMAC("HmacSHA256", "0123456789abcdef0123456789abcdef".toCharArray());
    final String storedPasscodeHash = tokenMAC.hash(CLIENT, ISSUE_TIME, USER_NAME, RAW_PASSCODE);

    // Metadata for the authorization code being redeemed (confidential client, no PKCE challenge).
    final TokenMetadata authCodeMetadata = EasyMock.createNiceMock(TokenMetadata.class);
    EasyMock.expect(authCodeMetadata.isAuthCode()).andReturn(true).anyTimes();
    EasyMock.expect(authCodeMetadata.getMetadata(REDIRECT_URI)).andReturn(REDIRECT).anyTimes();
    EasyMock.expect(authCodeMetadata.getMetadata(CLIENT_ID)).andReturn(CLIENT).anyTimes();
    EasyMock.expect(authCodeMetadata.getMetadata(CODE_CHALLENGE)).andReturn(null).anyTimes();
    EasyMock.replay(authCodeMetadata);

    // Metadata for the confidential client, used to authenticate the client_secret.
    final TokenMetadata clientMetadata = EasyMock.createNiceMock(TokenMetadata.class);
    EasyMock.expect(clientMetadata.getUserName()).andReturn(USER_NAME).anyTimes();
    EasyMock.expect(clientMetadata.getPasscode()).andReturn(storedPasscodeHash).anyTimes();
    EasyMock.replay(clientMetadata);

    tokenStateService = EasyMock.createNiceMock(TokenStateService.class);
    EasyMock.expect(tokenStateService.getTokenMetadata(AUTH_CODE)).andReturn(authCodeMetadata).anyTimes();
    EasyMock.expect(tokenStateService.getTokenExpiration(AUTH_CODE))
        .andReturn(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)).anyTimes();
    EasyMock.expect(tokenStateService.getTokenMetadata(CLIENT)).andReturn(clientMetadata).anyTimes();
    EasyMock.expect(tokenStateService.getTokenIssueTime(CLIENT)).andReturn(ISSUE_TIME).anyTimes();
    // consumeToken behaviour is set per-test (win vs. lose) before replay().

    final HttpServletRequest req = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(req.getParameter(CODE)).andReturn(AUTH_CODE).anyTimes();
    EasyMock.expect(req.getParameter(REDIRECT_URI)).andReturn(REDIRECT).anyTimes();
    EasyMock.expect(req.getParameter(CLIENT_ID)).andReturn(CLIENT).anyTimes();
    EasyMock.expect(req.getParameter(CLIENT_SECRET)).andReturn(wireSecret(CLIENT, RAW_PASSCODE)).anyTimes();
    EasyMock.replay(req);

    resource = new TestableTokenResource();
    resource.inject(tokenStateService, tokenMAC, req);
  }

  @Test
  public void testFirstRedemptionConsumesThenIssues() {
    EasyMock.expect(tokenStateService.consumeToken(AUTH_CODE)).andReturn(true).once();
    EasyMock.replay(tokenStateService);

    final Response response = resource.handleAuthorizationCodeFlow();

    assertEquals("A validated, freshly-consumed code should issue a token.",
        Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals("Exactly one issuance for a single winning redemption.", 1, issuedCount.get());
    EasyMock.verify(tokenStateService);
  }

  @Test
  public void testReplayedCodeIsRejectedWithoutIssuing() {
    // Simulate a concurrent redemption having already consumed the code: consumeToken loses.
    EasyMock.expect(tokenStateService.consumeToken(AUTH_CODE)).andReturn(false).once();
    EasyMock.replay(tokenStateService);

    final Response response = resource.handleAuthorizationCodeFlow();

    assertEquals("A code already consumed by a concurrent redemption must be rejected.",
        Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    assertTrue("The error body should identify the invalid_grant condition.",
        String.valueOf(response.getEntity()).contains("invalid_grant"));
    assertEquals("A losing redemption must not mint any token.", 0, issuedCount.get());
    EasyMock.verify(tokenStateService);
  }
}
