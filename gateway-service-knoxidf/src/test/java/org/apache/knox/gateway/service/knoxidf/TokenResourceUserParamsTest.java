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
import static org.apache.knox.gateway.security.CommonTokenConstants.GRANT_TYPE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.AUTH_CODE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CLIENT_ID;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CODE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CODE_CHALLENGE;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.REDIRECT_URI;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.REFRESH_TOKEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;

import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.service.knoxidf.userparams.UserParamsProvider;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenMetadataType;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.impl.TokenMAC;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies that a failed user params lookup (e.g. LDAP unreachable) does not fail the authorization_code and
 * refresh_token grants: the token is issued without the optional claims and the grant's audit entry records it.
 */
public class TokenResourceUserParamsTest {

  private static final String AUTH_CODE_VALUE = "auth-code-xyz";
  // A UUID so TokenUtils.getTokenId returns it verbatim (no JWT parsing needed).
  private static final String REFRESH_TOKEN_ID = "11111111-2222-3333-[VISA_CARD_NUMBER_REDACTED]";
  private static final String CLIENT = "client-abc";
  private static final String REDIRECT = "https://app.example/cb";
  private static final String USER_NAME = "alice";
  private static final long ISSUE_TIME = 1_700_000_000_000L;
  private static final String RAW_PASSCODE = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0";
  private static final String LDAP_DOWN = "LDAP is down";

  private static final Auditor ORIGINAL_AUDITOR = KnoxIDFAudit.auditor;
  private KnoxIDFAuditTest.CapturingAuditor capturingAuditor;
  private TokenMAC tokenMAC;
  private Map<String, Object> issuedUserParams;

  /**
   * Field injection plus stubs for the token-mint steps. The auth-code stub still builds the user context, which is
   * where the user params lookup happens on that grant.
   */
  final class TestableTokenResource extends TokenResource {
    void inject(final TokenStateService tss, final HttpServletRequest req, final UserParamsProvider provider) {
      this.tokenStateService = tss;
      this.tokenMAC = TokenResourceUserParamsTest.this.tokenMAC;
      this.request = req;
      this.userParamsProvider = provider;
    }

    @Override
    public Response getAuthenticationToken() {
      buildUserContext(request);
      return Response.ok("issued").build();
    }

    @Override
    protected TokenResponseContext getTokenResponse(final UserContext context) {
      return new TokenResponseContext(null, "issued", Response.ok());
    }
  }

  @Before
  public void setUp() throws Exception {
    capturingAuditor = new KnoxIDFAuditTest.CapturingAuditor();
    KnoxIDFAudit.auditor = capturingAuditor;
    tokenMAC = new TokenMAC("HmacSHA256", "0123456789abcdef0123456789abcdef".toCharArray());
    issuedUserParams = null;
  }

  @After
  public void tearDown() {
    KnoxIDFAudit.auditor = ORIGINAL_AUDITOR;
  }

  @Test
  public void testAuthCodeGrantIssuesTokenWhenUserParamsLookupFails() throws Exception {
    final TestableTokenResource resource = new TestableTokenResource();
    resource.inject(authCodeTokenStateService(), request(AUTH_CODE), failingProvider());

    final Response response = resource.handleAuthorizationCodeFlow();

    assertEquals("A failed user params lookup must not fail the grant.", Response.Status.OK.getStatusCode(), response.getStatus());
    assertGrantAudited("grant_type=authorization_code", "reason=tokens_issued user_params=unavailable");
  }

  @Test
  public void testAuthCodeGrantDoesNotAuditUserParamsWhenLookupSucceeds() throws Exception {
    final TestableTokenResource resource = new TestableTokenResource();
    resource.inject(authCodeTokenStateService(), request(AUTH_CODE), recordingProvider());

    final Response response = resource.handleAuthorizationCodeFlow();

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals("The user params must be looked up for the user.", "Alice", issuedUserParams.get("name"));
    final String message = assertGrantAudited("grant_type=authorization_code", "reason=tokens_issued");
    assertFalse(message.contains("user_params="));
  }

  @Test
  public void testRefreshTokenGrantIssuesTokenWhenUserParamsLookupFails() throws Exception {
    final TestableTokenResource resource = new TestableTokenResource();
    resource.inject(refreshTokenStateService(), request(REFRESH_TOKEN), failingProvider());

    final Response response = resource.handleRefreshToken();

    assertEquals("A failed user params lookup must not fail the grant.", Response.Status.OK.getStatusCode(), response.getStatus());
    assertGrantAudited("grant_type=refresh_token", "reason=rotated user_params=unavailable");
  }

  private String assertGrantAudited(String grantType, String detail) {
    assertEquals("Exactly one audit record must be emitted.", 1, capturingAuditor.records.size());
    final String message = capturingAuditor.records.get(0).message;
    assertEquals(ActionOutcome.SUCCESS, capturingAuditor.records.get(0).outcome);
    assertTrue(message, message.contains(grantType));
    assertTrue(message, message.endsWith(detail));
    return message;
  }

  private static UserParamsProvider failingProvider() {
    final UserParamsProvider provider = EasyMock.createMock(UserParamsProvider.class);
    EasyMock.expect(provider.getParamsFor(EasyMock.eq(USER_NAME), EasyMock.anyObject()))
        .andThrow(new RuntimeException("Failed to fetch user parameters for " + USER_NAME, new IllegalStateException(LDAP_DOWN)));
    EasyMock.replay(provider);
    return provider;
  }

  private UserParamsProvider recordingProvider() {
    return (subjectName, scope) -> {
      issuedUserParams = new HashMap<>();
      issuedUserParams.put("name", "Alice");
      return issuedUserParams;
    };
  }

  /** A request for the given grant whose attributes are backed by a map, so per-request flags survive. */
  private static HttpServletRequest request(String grantType) {
    final Map<String, Object> attributes = new HashMap<>();
    final HttpServletRequest req = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(req.getParameter(GRANT_TYPE)).andReturn(grantType).anyTimes();
    EasyMock.expect(req.getParameter(CODE)).andReturn(AUTH_CODE.equals(grantType) ? AUTH_CODE_VALUE : null).anyTimes();
    EasyMock.expect(req.getParameter(REDIRECT_URI)).andReturn(REDIRECT).anyTimes();
    EasyMock.expect(req.getParameter(REFRESH_TOKEN)).andReturn(REFRESH_TOKEN_ID).anyTimes();
    EasyMock.expect(req.getParameter(CLIENT_ID)).andReturn(CLIENT).anyTimes();
    EasyMock.expect(req.getParameter(CLIENT_SECRET)).andReturn(wireSecret(CLIENT, RAW_PASSCODE)).anyTimes();
    req.setAttribute(EasyMock.anyString(), EasyMock.anyObject());
    EasyMock.expectLastCall().andAnswer(() -> {
      attributes.put((String) EasyMock.getCurrentArguments()[0], EasyMock.getCurrentArguments()[1]);
      return null;
    }).anyTimes();
    EasyMock.expect(req.getAttribute(EasyMock.anyString()))
        .andAnswer(() -> attributes.get((String) EasyMock.getCurrentArguments()[0])).anyTimes();
    EasyMock.replay(req);
    return req;
  }

  private TokenStateService authCodeTokenStateService() throws Exception {
    final TokenMetadata authCodeMetadata = EasyMock.createNiceMock(TokenMetadata.class);
    EasyMock.expect(authCodeMetadata.isAuthCode()).andReturn(true).anyTimes();
    EasyMock.expect(authCodeMetadata.getUserName()).andReturn(USER_NAME).anyTimes();
    EasyMock.expect(authCodeMetadata.getMetadata(REDIRECT_URI)).andReturn(REDIRECT).anyTimes();
    EasyMock.expect(authCodeMetadata.getMetadata(CLIENT_ID)).andReturn(CLIENT).anyTimes();
    EasyMock.expect(authCodeMetadata.getMetadata(CODE_CHALLENGE)).andReturn(null).anyTimes();
    EasyMock.replay(authCodeMetadata);

    final TokenStateService tss = clientTokenStateService();
    EasyMock.expect(tss.getTokenMetadata(AUTH_CODE_VALUE)).andReturn(authCodeMetadata).anyTimes();
    EasyMock.expect(tss.getTokenExpiration(AUTH_CODE_VALUE))
        .andReturn(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)).anyTimes();
    EasyMock.expect(tss.consumeToken(AUTH_CODE_VALUE)).andReturn(true).once();
    EasyMock.replay(tss);
    return tss;
  }

  private TokenStateService refreshTokenStateService() throws Exception {
    final TokenMetadata refreshTokenMetadata = EasyMock.createNiceMock(TokenMetadata.class);
    EasyMock.expect(refreshTokenMetadata.getType()).andReturn(TokenMetadataType.REFRESH_TOKEN.name()).anyTimes();
    EasyMock.expect(refreshTokenMetadata.isEnabled()).andReturn(true).anyTimes();
    EasyMock.expect(refreshTokenMetadata.getMetadata(CLIENT_ID)).andReturn(CLIENT).anyTimes();
    EasyMock.expect(refreshTokenMetadata.getUserName()).andReturn(USER_NAME).anyTimes();
    EasyMock.replay(refreshTokenMetadata);

    final TokenStateService tss = clientTokenStateService();
    EasyMock.expect(tss.getTokenMetadata(REFRESH_TOKEN_ID)).andReturn(refreshTokenMetadata).anyTimes();
    EasyMock.expect(tss.getTokenExpiration(REFRESH_TOKEN_ID))
        .andReturn(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30)).anyTimes();
    EasyMock.expect(tss.consumeToken(REFRESH_TOKEN_ID)).andReturn(true).once();
    EasyMock.replay(tss);
    return tss;
  }

  /** A token state service that authenticates the confidential client; callers add the grant's expectations and replay it. */
  private TokenStateService clientTokenStateService() throws Exception {
    final TokenMetadata clientMetadata = EasyMock.createNiceMock(TokenMetadata.class);
    EasyMock.expect(clientMetadata.getUserName()).andReturn(USER_NAME).anyTimes();
    EasyMock.expect(clientMetadata.getPasscode()).andReturn(tokenMAC.hash(CLIENT, ISSUE_TIME, USER_NAME, RAW_PASSCODE)).anyTimes();
    EasyMock.replay(clientMetadata);

    final TokenStateService tss = EasyMock.createNiceMock(TokenStateService.class);
    EasyMock.expect(tss.getTokenMetadata(CLIENT)).andReturn(clientMetadata).anyTimes();
    EasyMock.expect(tss.getTokenIssueTime(CLIENT)).andReturn(ISSUE_TIME).anyTimes();
    return tss;
  }

  private static String wireSecret(final String tokenId, final String rawPasscode) {
    final String inner = Base64.getEncoder().encodeToString(tokenId.getBytes(StandardCharsets.UTF_8))
        + "::" + Base64.getEncoder().encodeToString(rawPasscode.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(inner.getBytes(StandardCharsets.UTF_8));
  }
}
