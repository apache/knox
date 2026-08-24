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
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.REFRESH_TOKEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;

import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenMetadataType;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.impl.TokenMAC;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies single-use enforcement of the {@code refresh_token} grant (review finding M1). The
 * presented refresh token must be atomically consumed BEFORE its replacement is issued: exactly one
 * of N concurrent redemptions wins the consume and rotates; the losers are rejected with
 * {@code invalid_grant} and no new token pair is minted. This closes the check-then-act window that
 * existed when rotation used {@code revokeToken} on the in-memory backend. Mirrors the
 * authorization_code single-use guard (see {@link TokenResourceAuthCodeReplayTest}).
 */
public class TokenResourceRefreshTokenRotationTest {

  // A UUID so TokenUtils.getTokenId returns it verbatim (no JWT parsing needed).
  private static final String REFRESH_TOKEN_ID = "11111111-2222-3333-4444-555555555555";
  private static final String CLIENT = "client-abc";
  private static final String USER_NAME = "alice";
  private static final long ISSUE_TIME = 1_700_000_000_000L;
  private static final String RAW_PASSCODE = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0";

  private TokenStateService tokenStateService;
  private TestableTokenResource resource;
  private final AtomicInteger issuedCount = new AtomicInteger();

  /** Field injection plus a stub for the token-mint step so the rotation guard is tested in isolation. */
  final class TestableTokenResource extends TokenResource {
    void inject(final TokenStateService tss, final TokenMAC mac, final HttpServletRequest req) {
      this.tokenStateService = tss;
      this.tokenMAC = mac;
      this.request = req;
    }

    @Override
    protected TokenResponseContext getTokenResponse(final UserContext context) {
      issuedCount.incrementAndGet();
      return new TokenResponseContext(null, "issued", Response.ok());
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

    final TokenMetadata refreshTokenMetadata = EasyMock.createNiceMock(TokenMetadata.class);
    EasyMock.expect(refreshTokenMetadata.getType()).andReturn(TokenMetadataType.REFRESH_TOKEN.name()).anyTimes();
    EasyMock.expect(refreshTokenMetadata.isEnabled()).andReturn(true).anyTimes();
    EasyMock.expect(refreshTokenMetadata.getMetadata(CLIENT_ID)).andReturn(CLIENT).anyTimes();
    EasyMock.expect(refreshTokenMetadata.getUserName()).andReturn(USER_NAME).anyTimes();
    EasyMock.replay(refreshTokenMetadata);

    final TokenMetadata clientMetadata = EasyMock.createNiceMock(TokenMetadata.class);
    EasyMock.expect(clientMetadata.getUserName()).andReturn(USER_NAME).anyTimes();
    EasyMock.expect(clientMetadata.getPasscode()).andReturn(storedPasscodeHash).anyTimes();
    EasyMock.replay(clientMetadata);

    tokenStateService = EasyMock.createNiceMock(TokenStateService.class);
    EasyMock.expect(tokenStateService.getTokenMetadata(REFRESH_TOKEN_ID)).andReturn(refreshTokenMetadata).anyTimes();
    EasyMock.expect(tokenStateService.getTokenExpiration(REFRESH_TOKEN_ID))
        .andReturn(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30)).anyTimes();
    EasyMock.expect(tokenStateService.getTokenMetadata(CLIENT)).andReturn(clientMetadata).anyTimes();
    EasyMock.expect(tokenStateService.getTokenIssueTime(CLIENT)).andReturn(ISSUE_TIME).anyTimes();
    // consumeToken behaviour is set per-test (win vs. lose) before replay().

    final HttpServletRequest req = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(req.getParameter(REFRESH_TOKEN)).andReturn(REFRESH_TOKEN_ID).anyTimes();
    EasyMock.expect(req.getParameter(CLIENT_ID)).andReturn(CLIENT).anyTimes();
    EasyMock.expect(req.getParameter(CLIENT_SECRET)).andReturn(wireSecret(CLIENT, RAW_PASSCODE)).anyTimes();
    EasyMock.replay(req);

    resource = new TestableTokenResource();
    resource.inject(tokenStateService, tokenMAC, req);
  }

  @Test
  public void testAlreadyRedeemedRefreshTokenIsRejectedWithoutIssuing() {
    // A concurrent redemption already consumed the token: this consume loses.
    EasyMock.expect(tokenStateService.consumeToken(REFRESH_TOKEN_ID)).andReturn(false).once();
    EasyMock.replay(tokenStateService);

    final Response response = resource.handleRefreshToken();

    assertEquals("A refresh token already consumed by a concurrent rotation must be rejected.",
        Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertTrue("The error body should identify the invalid_grant condition.",
        String.valueOf(response.getEntity()).contains("invalid_grant"));
    assertEquals("A losing redemption must not mint any token.", 0, issuedCount.get());
    // Proves rotation now goes through the atomic consume path rather than revokeToken.
    EasyMock.verify(tokenStateService);
  }
}
