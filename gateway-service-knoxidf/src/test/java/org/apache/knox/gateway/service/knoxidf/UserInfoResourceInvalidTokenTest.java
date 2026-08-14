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

import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.SCOPE_ATTRIBUTE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.TOKEN_ID_ATTRIBUTE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;

import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.easymock.EasyMock;
import org.junit.Test;

/**
 * Verifies /userinfo answers a bad bearer token per RFC 6750 (review finding M6): an expired,
 * revoked, or unknown token must yield HTTP 401 with a
 * {@code WWW-Authenticate: Bearer error="invalid_token"} challenge, not a 500 from an unmapped
 * RuntimeException.
 */
public class UserInfoResourceInvalidTokenTest {

  private static final String TOKEN_ID = "11111111-2222-3333-[VISA_CARD_NUMBER_REDACTED]";

  /** Injects the request and a token-state service that throws for an unknown token. */
  static final class TestableUserInfoResource extends UserInfoResource {
    private final TokenStateService tss;

    TestableUserInfoResource(final HttpServletRequest req, final TokenStateService tss) {
      this.request = req;
      this.tss = tss;
    }

    @Override
    TokenStateService getReadonlyTokenStateService() {
      return tss;
    }
  }

  private static HttpServletRequest requestWithToken(final String tokenId) {
    final HttpServletRequest req = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(req.getAttribute(TOKEN_ID_ATTRIBUTE)).andReturn(tokenId).anyTimes();
    EasyMock.expect(req.getAttribute(SCOPE_ATTRIBUTE)).andReturn(null).anyTimes();
    EasyMock.replay(req);
    return req;
  }

  @Test
  public void testUnknownTokenYields401WithBearerChallenge() throws Exception {
    final TokenStateService tss = EasyMock.createNiceMock(TokenStateService.class);
    EasyMock.expect(tss.getTokenMetadata(TOKEN_ID)).andThrow(new UnknownTokenException(TOKEN_ID)).anyTimes();
    EasyMock.replay(tss);

    final Response response = new TestableUserInfoResource(requestWithToken(TOKEN_ID), tss).getUserInfo();

    assertEquals("An unknown/expired token must be 401, not 500.",
        Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    final Object challenge = response.getHeaderString("WWW-Authenticate");
    assertNotNull("RFC 6750 requires a WWW-Authenticate challenge.", challenge);
    assertTrue("The challenge must be a Bearer invalid_token challenge.",
        challenge.toString().contains("Bearer") && challenge.toString().contains("invalid_token"));
    assertTrue("The JSON body should carry the invalid_token error code.",
        String.valueOf(response.getEntity()).contains("invalid_token"));
  }

  @Test
  public void testMissingTokenIdYieldsInvalidRequest() {
    final TokenStateService tss = EasyMock.createNiceMock(TokenStateService.class);
    EasyMock.replay(tss);

    final Response response = new TestableUserInfoResource(requestWithToken(null), tss).getUserInfo();

    assertEquals("A missing token id is a client request error (400).",
        Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
  }
}
