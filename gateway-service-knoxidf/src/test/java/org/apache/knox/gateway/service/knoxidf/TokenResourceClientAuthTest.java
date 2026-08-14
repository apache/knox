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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.impl.TokenMAC;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies that the token endpoint independently authenticates a confidential client on the
 * authorization_code grant (finding 1.1). This closes the gap where the JWTFederationFilter Bearer
 * path forwards a request without checking client_secret: a stolen auth code must not be redeemable
 * without proving client identity.
 */
public class TokenResourceClientAuthTest {

  private static final String CLIENT_ID = "client-abc";
  private static final String USER_NAME = "alice";
  private static final long ISSUE_TIME = 1_700_000_000_000L;
  private static final String RAW_PASSCODE = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0";

  private TokenMAC tokenMAC;

  private TestableTokenResource resource;

  /**
   * Exposes injection of the inherited (protected) token-state service and the package-private MAC
   * so the client-authentication logic can be exercised without the full JAX-RS/servlet lifecycle.
   */
  static final class TestableTokenResource extends TokenResource {
    void inject(final TokenStateService tokenStateService, final TokenMAC mac) {
      this.tokenStateService = tokenStateService;
      this.tokenMAC = mac;
    }
  }

  @Before
  public void setUp() throws Exception {
    // A deterministic MAC shared by "the server" (stored hash) and the resource under test.
    tokenMAC = new TokenMAC("HmacSHA256", "0123456789abcdef0123456789abcdef".toCharArray());
    final String storedPasscodeHash = tokenMAC.hash(CLIENT_ID, ISSUE_TIME, USER_NAME, RAW_PASSCODE);

    final TokenMetadata clientMetadata = EasyMock.createNiceMock(TokenMetadata.class);
    EasyMock.expect(clientMetadata.getUserName()).andReturn(USER_NAME).anyTimes();
    EasyMock.expect(clientMetadata.getPasscode()).andReturn(storedPasscodeHash).anyTimes();
    EasyMock.replay(clientMetadata);

    final TokenStateService tokenStateService = EasyMock.createNiceMock(TokenStateService.class);
    EasyMock.expect(tokenStateService.getTokenMetadata(CLIENT_ID)).andReturn(clientMetadata).anyTimes();
    EasyMock.expect(tokenStateService.getTokenIssueTime(CLIENT_ID)).andReturn(ISSUE_TIME).anyTimes();
    EasyMock.replay(tokenStateService);

    resource = new TestableTokenResource();
    resource.inject(tokenStateService, tokenMAC);
  }

  private static String wireSecret(final String tokenId, final String rawPasscode) {
    final String inner = Base64.getEncoder().encodeToString(tokenId.getBytes(StandardCharsets.UTF_8))
        + "::" + Base64.getEncoder().encodeToString(rawPasscode.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(inner.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void testValidClientSecretIsAccepted() {
    assertTrue(resource.isValidClientSecret(CLIENT_ID, wireSecret(CLIENT_ID, RAW_PASSCODE)));
  }

  @Test
  public void testWrongPasscodeIsRejected() {
    assertFalse(resource.isValidClientSecret(CLIENT_ID, wireSecret(CLIENT_ID, "not-the-real-passcode")));
  }

  @Test
  public void testSecretBoundToDifferentClientIsRejected() {
    // A secret whose embedded tokenId does not match the client_id redeeming the code must fail,
    // even if the secret itself is otherwise well-formed.
    assertFalse(resource.isValidClientSecret("some-other-client", wireSecret(CLIENT_ID, RAW_PASSCODE)));
  }

  @Test
  public void testBlankSecretIsRejected() {
    assertFalse(resource.isValidClientSecret(CLIENT_ID, null));
    assertFalse(resource.isValidClientSecret(CLIENT_ID, ""));
  }

  @Test
  public void testMalformedSecretIsRejected() {
    // Not base64 / no "tokenId::passcode" structure.
    assertFalse(resource.isValidClientSecret(CLIENT_ID, "!!!not-base64!!!"));
    assertFalse(resource.isValidClientSecret(CLIENT_ID,
        Base64.getEncoder().encodeToString("no-separator".getBytes(StandardCharsets.UTF_8))));
  }
}
