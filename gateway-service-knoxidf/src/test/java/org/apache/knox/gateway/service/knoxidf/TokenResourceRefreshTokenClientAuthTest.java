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
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.knox.gateway.service.knoxidf.TokenResource.RefreshTokenValidationError;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenMetadataType;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.impl.TokenMAC;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies that the token endpoint independently authenticates the client on the
 * {@code refresh_token} grant (review finding H1). Matching only {@code client_id} is not enough:
 * the JWTFederationFilter Bearer path forwards a request here without checking {@code client_secret},
 * so a stolen refresh token could otherwise be redeemed (and rotated) by anyone. This mirrors the
 * client-authentication the authorization_code grant already performs.
 */
public class TokenResourceRefreshTokenClientAuthTest {

  private static final String CLIENT = "client-abc";
  private static final String USER_NAME = "alice";
  private static final long ISSUE_TIME = 1_700_000_000_000L;
  private static final String RAW_PASSCODE = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0";
  private static final String REFRESH_TOKEN_ID = "refresh-token-id-123";
  private static final String REFRESH_TOKEN_PARAM = "the-opaque-refresh-token";

  private TokenMAC tokenMAC;
  private TokenStateService tokenStateService;
  private TokenMetadata refreshTokenMetadata;

  /** Exposes injection of the inherited (protected) token-state service, MAC and request. */
  static final class TestableTokenResource extends TokenResource {
    void inject(final TokenStateService tss, final TokenMAC mac, final HttpServletRequest req) {
      this.tokenStateService = tss;
      this.tokenMAC = mac;
      this.request = req;
    }
  }

  private static String wireSecret(final String tokenId, final String rawPasscode) {
    final String inner = Base64.getEncoder().encodeToString(tokenId.getBytes(StandardCharsets.UTF_8))
        + "::" + Base64.getEncoder().encodeToString(rawPasscode.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(inner.getBytes(StandardCharsets.UTF_8));
  }

  @Before
  public void setUp() throws Exception {
    tokenMAC = new TokenMAC("HmacSHA256", "0123456789abcdef0123456789abcdef".toCharArray());
    final String storedPasscodeHash = tokenMAC.hash(CLIENT, ISSUE_TIME, USER_NAME, RAW_PASSCODE);

    // The refresh token's own metadata: a valid, enabled REFRESH_TOKEN bound to CLIENT.
    refreshTokenMetadata = EasyMock.createNiceMock(TokenMetadata.class);
    EasyMock.expect(refreshTokenMetadata.getType()).andReturn(TokenMetadataType.REFRESH_TOKEN.name()).anyTimes();
    EasyMock.expect(refreshTokenMetadata.isEnabled()).andReturn(true).anyTimes();
    EasyMock.expect(refreshTokenMetadata.getMetadata(CLIENT_ID)).andReturn(CLIENT).anyTimes();
    EasyMock.replay(refreshTokenMetadata);

    // The client's registration metadata, used to authenticate the presented client_secret.
    final TokenMetadata clientMetadata = EasyMock.createNiceMock(TokenMetadata.class);
    EasyMock.expect(clientMetadata.getUserName()).andReturn(USER_NAME).anyTimes();
    EasyMock.expect(clientMetadata.getPasscode()).andReturn(storedPasscodeHash).anyTimes();
    EasyMock.replay(clientMetadata);

    tokenStateService = EasyMock.createNiceMock(TokenStateService.class);
    EasyMock.expect(tokenStateService.getTokenExpiration(REFRESH_TOKEN_ID))
        .andReturn(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30)).anyTimes();
    EasyMock.expect(tokenStateService.getTokenMetadata(CLIENT)).andReturn(clientMetadata).anyTimes();
    EasyMock.expect(tokenStateService.getTokenIssueTime(CLIENT)).andReturn(ISSUE_TIME).anyTimes();
    EasyMock.replay(tokenStateService);
  }

  private TestableTokenResource resourceWithClientSecret(final String clientSecret) {
    final HttpServletRequest req = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(req.getParameter(CLIENT_ID)).andReturn(CLIENT).anyTimes();
    EasyMock.expect(req.getParameter(CLIENT_SECRET)).andReturn(clientSecret).anyTimes();
    EasyMock.replay(req);

    final TestableTokenResource resource = new TestableTokenResource();
    resource.inject(tokenStateService, tokenMAC, req);
    return resource;
  }

  @Test
  public void testValidClientSecretPassesRefreshGrant() throws Exception {
    final TestableTokenResource resource = resourceWithClientSecret(wireSecret(CLIENT, RAW_PASSCODE));
    // Should complete without throwing: a correctly authenticated client may refresh.
    resource.validateRefreshTokenGrant(REFRESH_TOKEN_PARAM, REFRESH_TOKEN_ID, refreshTokenMetadata);
  }

  @Test
  public void testMissingClientSecretIsRejected() {
    final TestableTokenResource resource = resourceWithClientSecret(null);
    assertRejected(resource);
  }

  @Test
  public void testWrongClientSecretIsRejected() {
    final TestableTokenResource resource = resourceWithClientSecret(wireSecret(CLIENT, "not-the-real-passcode"));
    assertRejected(resource);
  }

  @Test
  public void testSecretBoundToDifferentClientIsRejected() {
    // A well-formed secret whose embedded tokenId is not the refreshing client must not pass.
    final TestableTokenResource resource = resourceWithClientSecret(wireSecret("some-other-client", RAW_PASSCODE));
    assertRejected(resource);
  }

  private void assertRejected(final TestableTokenResource resource) {
    try {
      resource.validateRefreshTokenGrant(REFRESH_TOKEN_PARAM, REFRESH_TOKEN_ID, refreshTokenMetadata);
      fail("Refresh grant must reject a request that does not authenticate the client.");
    } catch (RefreshTokenValidationError expected) {
      // expected: client authentication failed
    } catch (Exception e) {
      fail("Expected RefreshTokenValidationError but got " + e.getClass().getName());
    }
  }
}
