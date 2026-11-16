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

import static org.apache.knox.gateway.security.CommonTokenConstants.GRANT_TYPE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.AUTH_CODE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CLIENT_CREDENTIALS;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.REFRESH_TOKEN;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.TOKEN_EXCHANGE_GRANT_TYPE;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.util.knoxidf.AllowedGrantTypes;
import org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Test;

public class TokenResourceAllowedGrantTypesTest {

  private static final String MINT = "mint";
  private static final String AUTH_CODE_HANDLER = "authorization_code handler";
  private static final String REFRESH_TOKEN_HANDLER = "refresh_token handler";

  private final List<String> reached = new ArrayList<>();
  private TokenStateService tokenStateService;

  // Records which path a request reached. The strict token state mock fails any lookup or consume.
  final class TestableTokenResource extends TokenResource {
    TestableTokenResource(final AllowedGrantTypes allowed, final String grantType) {
      final HttpServletRequest req = EasyMock.createNiceMock(HttpServletRequest.class);
      EasyMock.expect(req.getParameter(GRANT_TYPE)).andReturn(grantType).anyTimes();
      // ALL stands in for "unset".
      EasyMock.expect(req.getAttribute(KnoxIDFConstants.ALLOWED_GRANT_TYPES_ATTRIBUTE))
          .andReturn(allowed == AllowedGrantTypes.ALL ? null : allowed).anyTimes();
      EasyMock.replay(req);
      this.request = req;
      this.tokenStateService = TokenResourceAllowedGrantTypesTest.this.tokenStateService;
    }

    @Override
    public Response getAuthenticationToken() {
      reached.add(MINT);
      return Response.ok("issued").build();
    }

    @Override
    Response handleAuthorizationCodeFlow() {
      reached.add(AUTH_CODE_HANDLER);
      return Response.ok("issued").build();
    }

    @Override
    Response handleRefreshToken() {
      reached.add(REFRESH_TOKEN_HANDLER);
      return Response.ok("issued").build();
    }
  }

  private TestableTokenResource resource(final String allowed, final String grantType) {
    tokenStateService = EasyMock.createMock(TokenStateService.class);
    EasyMock.replay(tokenStateService);
    return new TestableTokenResource(AllowedGrantTypes.parse(allowed), grantType);
  }

  @After
  public void verifyTokenStateUntouched() {
    if (tokenStateService != null) {
      EasyMock.verify(tokenStateService);
    }
  }

  private void assertRejected(final Response response) throws Exception {
    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    final Map<String, String> body = new ObjectMapper().readValue(String.valueOf(response.getEntity()),
        new TypeReference<Map<String, String>>() { });
    assertEquals("unsupported_grant_type", body.get("error"));
    assertEquals("A rejected grant must reach neither a grant handler nor the mint.",
        Collections.emptyList(), reached);
  }

  @Test
  public void testAllowedGrantIsServed() {
    final Response response = resource(TOKEN_EXCHANGE_GRANT_TYPE, TOKEN_EXCHANGE_GRANT_TYPE).doPost();
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(Collections.singletonList(MINT), reached);
  }

  @Test
  public void testAuthorizationCodeIsRejectedWithoutConsumingTheCode() throws Exception {
    assertRejected(resource(TOKEN_EXCHANGE_GRANT_TYPE, AUTH_CODE).doPost());
  }

  @Test
  public void testRefreshTokenIsRejectedWithoutConsumingTheToken() throws Exception {
    assertRejected(resource(TOKEN_EXCHANGE_GRANT_TYPE, REFRESH_TOKEN).doPost());
  }

  @Test
  public void testClientCredentialsIsRejected() throws Exception {
    assertRejected(resource(TOKEN_EXCHANGE_GRANT_TYPE, CLIENT_CREDENTIALS).doPost());
  }

  @Test
  public void testUnknownGrantIsRejected() throws Exception {
    assertRejected(resource(TOKEN_EXCHANGE_GRANT_TYPE, "password").doPost());
  }

  @Test
  public void testPostWithoutGrantTypeIsRejected() throws Exception {
    assertRejected(resource(TOKEN_EXCHANGE_GRANT_TYPE, null).doPost());
  }

  @Test
  public void testGetWithoutGrantTypeIsRejected() throws Exception {
    assertRejected(resource(TOKEN_EXCHANGE_GRANT_TYPE, null).doGet());
  }

  @Test
  public void testGetIsGatedLikePost() throws Exception {
    assertRejected(resource(TOKEN_EXCHANGE_GRANT_TYPE, AUTH_CODE).doGet());
    final Response response = resource(TOKEN_EXCHANGE_GRANT_TYPE, TOKEN_EXCHANGE_GRANT_TYPE).doGet();
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(Collections.singletonList(MINT), reached);
  }

  @Test
  public void testUnrestrictedDispatchIsUnchanged() {
    resource(null, AUTH_CODE).doPost();
    resource(null, REFRESH_TOKEN).doPost();
    resource(null, CLIENT_CREDENTIALS).doPost();
    resource(null, null).doPost();
    resource(null, null).doGet();
    assertEquals(List.of(AUTH_CODE_HANDLER, REFRESH_TOKEN_HANDLER, MINT, MINT, MINT), reached);
  }
}
