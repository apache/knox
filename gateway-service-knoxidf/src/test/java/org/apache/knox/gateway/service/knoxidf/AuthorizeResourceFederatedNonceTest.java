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

import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.NONCE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import javax.ws.rs.core.Response;

import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.easymock.EasyMock;
import org.junit.Test;

/**
 * Verifies the federated OIDC {@code nonce} binding (review finding H2). Knox mints a nonce for each
 * federated login session, sends it to the OP, and — once the returned id_token's signature/issuer/
 * audience have been verified — requires the id_token's {@code nonce} claim to equal that value. This
 * defeats id_token replay/injection: a token minted for a different (or attacker-initiated) request
 * carries a different nonce and is rejected.
 */
public class AuthorizeResourceFederatedNonceTest {

  private static final String NONCE_VALUE = "6d1c7a90-4b2e-4c1a-9f3d-0a1b2c3d4e5f";

  private static JWT idTokenWithNonce(final String nonce) {
    final JWT idToken = EasyMock.createNiceMock(JWT.class);
    EasyMock.expect(idToken.getClaim(NONCE)).andReturn(nonce).anyTimes();
    EasyMock.replay(idToken);
    return idToken;
  }

  @Test
  public void testMatchingNoncePasses() {
    final Response result = new AuthorizeResource().verifyFederatedNonce(NONCE_VALUE, idTokenWithNonce(NONCE_VALUE));
    assertNull("A matching nonce must pass (null == no error).", result);
  }

  @Test
  public void testMismatchedNonceIsRejected() {
    final Response result = new AuthorizeResource().verifyFederatedNonce(NONCE_VALUE, idTokenWithNonce("some-other-nonce"));
    assertNotNull("A mismatched nonce must be rejected.", result);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), result.getStatus());
  }

  @Test
  public void testMissingClaimInTokenIsRejected() {
    final Response result = new AuthorizeResource().verifyFederatedNonce(NONCE_VALUE, idTokenWithNonce(null));
    assertNotNull("An id_token without a nonce claim must be rejected.", result);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), result.getStatus());
  }

  @Test
  public void testMissingExpectedNonceIsRejected() {
    // No stored nonce (expired/replayed state) must fail closed rather than accept any token.
    final Response result = new AuthorizeResource().verifyFederatedNonce(null, idTokenWithNonce(NONCE_VALUE));
    assertNotNull("A missing expected nonce must be rejected.", result);
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), result.getStatus());
  }
}
