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
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.CLIENT_ID;
import static org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants.REFRESH_TOKEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;

import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.CorrelationContext;
import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.ResourceType;
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
 * Representative coverage for the KnoxIDF audit instrumentation (structured audit-log completeness).
 * A capturing {@link Auditor} is injected into {@link KnoxIDFAudit#auditor} so the emitted
 * action/outcome/resource/message can be asserted for a representative SUCCESS path (a rotated
 * refresh-token grant) and a representative FAILURE path (a rejected refresh-token grant). It also
 * pins the security-critical invariant that {@link KnoxIDFAudit#mask(String)} never echoes a raw
 * secret into the record.
 */
public class KnoxIDFAuditTest {

  // A UUID so TokenUtils.getTokenId returns it verbatim (no JWT parsing needed).
  private static final String REFRESH_TOKEN_ID = "11111111-2222-3333-4444-555555555555";
  private static final String CLIENT = "client-abc";
  private static final String USER_NAME = "alice";
  private static final long ISSUE_TIME = 1_700_000_000_000L;
  private static final String RAW_PASSCODE = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0";

  /** Records every thread-local 5-arg audit call so a test can assert what was emitted. */
  static final class CapturingAuditor implements Auditor {
    static final class Record {
      final String action;
      final String resource;
      final String resourceType;
      final String outcome;
      final String message;

      Record(String action, String resource, String resourceType, String outcome, String message) {
        this.action = action;
        this.resource = resource;
        this.resourceType = resourceType;
        this.outcome = outcome;
        this.message = message;
      }
    }

    final List<Record> records = new ArrayList<>();

    @Override
    public void audit(String action, String resourceName, String resourceType, String outcome, String message) {
      records.add(new Record(action, resourceName, resourceType, outcome, message));
    }

    @Override
    public void audit(String action, String resourceName, String resourceType, String outcome) {
      audit(action, resourceName, resourceType, outcome, null);
    }

    @Override
    public void audit(CorrelationContext correlationContext, AuditContext auditContext, String action,
        String resourceName, String resourceType, String outcome, String message) {
      audit(action, resourceName, resourceType, outcome, message);
    }

    @Override
    public String getServiceName() {
      return "knox";
    }

    @Override
    public String getComponentName() {
      return "knox";
    }

    @Override
    public String getAuditorName() {
      return "audit";
    }
  }

  private static final Auditor ORIGINAL_AUDITOR = KnoxIDFAudit.auditor;
  private CapturingAuditor capturingAuditor;

  @Before
  public void setUp() {
    capturingAuditor = new CapturingAuditor();
    KnoxIDFAudit.auditor = capturingAuditor;
  }

  @After
  public void tearDown() {
    KnoxIDFAudit.auditor = ORIGINAL_AUDITOR;
  }

  // ---------------------------------------------------------------------------
  // mask(): never echoes a raw secret; blank/unmaskable -> UNKNOWN
  // ---------------------------------------------------------------------------

  @Test
  public void testMaskNeverLeaksRawValue() {
    final String secret = "supersecret-client-secret-value-1234567890";
    final String masked = KnoxIDFAudit.mask(secret);
    assertNotNull(masked);
    assertFalse("mask() must never return the raw secret", secret.equals(masked));
    assertFalse("mask() output must not contain the full raw secret", masked.contains(secret));
    assertTrue("mask() output must be shorter than the raw secret", masked.length() < secret.length());
  }

  @Test
  public void testMaskBlankOrTooShortIsUnknown() {
    assertEquals(KnoxIDFAudit.UNKNOWN, KnoxIDFAudit.mask(null));
    assertEquals(KnoxIDFAudit.UNKNOWN, KnoxIDFAudit.mask(""));
    assertEquals(KnoxIDFAudit.UNKNOWN, KnoxIDFAudit.mask("   "));
    // Too short for Tokens display text -> normalized to UNKNOWN, never the raw value.
    assertEquals(KnoxIDFAudit.UNKNOWN, KnoxIDFAudit.mask("abc"));
  }

  // ---------------------------------------------------------------------------
  // Representative FAILURE: a rejected refresh-token grant on the token endpoint.
  // (Unknown grant types are no longer rejected here -- doPost() delegates them to
  // the knoxtoken base, which only issues a token for an already-authenticated caller.)
  // ---------------------------------------------------------------------------

  @Test
  public void testRejectedRefreshTokenGrantEmitsFailureAudit() throws Exception {
    // Missing client_id makes validateRefreshTokenGrant reject the request with invalid_grant,
    // which must emit exactly one FAILURE audit record for the refresh_token grant.
    final HttpServletRequest req = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(req.getParameter(GRANT_TYPE)).andReturn(REFRESH_TOKEN).anyTimes();
    EasyMock.expect(req.getParameter(REFRESH_TOKEN)).andReturn(REFRESH_TOKEN_ID).anyTimes();
    EasyMock.expect(req.getParameter(CLIENT_ID)).andReturn(null).anyTimes();
    EasyMock.replay(req);

    final TokenStateService tokenStateService = EasyMock.createNiceMock(TokenStateService.class);
    EasyMock.replay(tokenStateService);

    final TestableTokenResource resource = new TestableTokenResource();
    resource.inject(tokenStateService, null, req, null);

    final Response response = resource.handleRefreshToken();

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertEquals("Exactly one audit record must be emitted.", 1, capturingAuditor.records.size());
    final CapturingAuditor.Record record = capturingAuditor.records.get(0);
    assertEquals(Action.AUTHENTICATION, record.action);
    assertEquals(ResourceType.PRINCIPAL, record.resourceType);
    assertEquals(ActionOutcome.FAILURE, record.outcome);
    assertTrue(record.message.contains("grant_type=refresh_token"));
    assertTrue(record.message.contains("reason=validation_failed"));
  }

  // ---------------------------------------------------------------------------
  // Representative SUCCESS: a rotated refresh-token grant
  // ---------------------------------------------------------------------------

  /** Field injection plus a stub for the token-mint step so only the audit emission is under test. */
  private static final class TestableTokenResource extends TokenResource {
    void inject(final TokenStateService tss, final TokenMAC mac, final HttpServletRequest req,
        final UserParamsProvider userParamsProvider) throws Exception {
      this.tokenStateService = tss;
      this.tokenMAC = mac;
      this.request = req;
      // userParamsProvider is private on TokenResource and normally wired in init(); inject it
      // directly so the rotation success path can build its UserContext without a servlet context.
      final Field field = TokenResource.class.getDeclaredField("userParamsProvider");
      field.setAccessible(true);
      field.set(this, userParamsProvider);
    }

    @Override
    protected TokenResponseContext getTokenResponse(final UserContext context) {
      return new TokenResponseContext(null, "issued", Response.ok());
    }
  }

  private static String wireSecret(final String tokenId, final String rawPasscode) {
    final String inner = Base64.getEncoder().encodeToString(tokenId.getBytes(StandardCharsets.UTF_8))
        + "::" + Base64.getEncoder().encodeToString(rawPasscode.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(inner.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void testRefreshTokenRotationEmitsSuccessAudit() throws Exception {
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

    final TokenStateService tokenStateService = EasyMock.createNiceMock(TokenStateService.class);
    EasyMock.expect(tokenStateService.getTokenMetadata(REFRESH_TOKEN_ID)).andReturn(refreshTokenMetadata).anyTimes();
    EasyMock.expect(tokenStateService.getTokenExpiration(REFRESH_TOKEN_ID))
        .andReturn(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30)).anyTimes();
    EasyMock.expect(tokenStateService.getTokenMetadata(CLIENT)).andReturn(clientMetadata).anyTimes();
    EasyMock.expect(tokenStateService.getTokenIssueTime(CLIENT)).andReturn(ISSUE_TIME).anyTimes();
    // This redemption wins the atomic consume and rotates.
    EasyMock.expect(tokenStateService.consumeToken(REFRESH_TOKEN_ID)).andReturn(true).once();
    EasyMock.replay(tokenStateService);

    final HttpServletRequest req = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(req.getParameter(REFRESH_TOKEN)).andReturn(REFRESH_TOKEN_ID).anyTimes();
    EasyMock.expect(req.getParameter(CLIENT_ID)).andReturn(CLIENT).anyTimes();
    EasyMock.expect(req.getParameter(CLIENT_SECRET)).andReturn(wireSecret(CLIENT, RAW_PASSCODE)).anyTimes();
    EasyMock.replay(req);

    final UserParamsProvider userParamsProvider = EasyMock.createNiceMock(UserParamsProvider.class);
    EasyMock.expect(userParamsProvider.getParamsFor(EasyMock.anyString(), EasyMock.anyObject()))
        .andReturn(new HashMap<>()).anyTimes();
    EasyMock.replay(userParamsProvider);

    final TestableTokenResource resource = new TestableTokenResource();
    resource.inject(tokenStateService, tokenMAC, req, userParamsProvider);

    final Response response = resource.handleRefreshToken();

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals("Exactly one audit record must be emitted.", 1, capturingAuditor.records.size());
    final CapturingAuditor.Record record = capturingAuditor.records.get(0);
    assertEquals(Action.AUTHENTICATION, record.action);
    assertEquals(ResourceType.PRINCIPAL, record.resourceType);
    assertEquals(ActionOutcome.SUCCESS, record.outcome);
    assertTrue(record.message.contains("grant_type=refresh_token"));
    assertTrue(record.message.contains("reason=rotated"));
    // Neither the raw refresh token id nor the client_id appear verbatim.
    assertFalse(record.message.contains(REFRESH_TOKEN_ID));
    assertFalse("client_id must be masked in the audit record", CLIENT.equals(record.resource));
  }
}
