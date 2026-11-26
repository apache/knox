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
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;

import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.service.knoxidf.userparams.UserParamsProvider;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies /userinfo for a local user when the user params lookup (e.g. LDAP) fails: unlike token issuance, the
 * claims are what /userinfo is for, so it must answer 503 temporarily_unavailable rather than an empty profile or
 * a raw 500.
 */
public class UserInfoResourceUserParamsTest {

  private static final String TOKEN_ID = "11111111-2222-3333-[VISA_CARD_NUMBER_REDACTED]";
  private static final String USER_NAME = "alice";

  private static final Auditor ORIGINAL_AUDITOR = KnoxIDFAudit.auditor;
  private KnoxIDFAuditTest.CapturingAuditor capturingAuditor;

  @Before
  public void setUp() {
    capturingAuditor = new KnoxIDFAuditTest.CapturingAuditor();
    KnoxIDFAudit.auditor = capturingAuditor;
  }

  @After
  public void tearDown() {
    KnoxIDFAudit.auditor = ORIGINAL_AUDITOR;
  }

  @Test
  public void testFailedUserParamsLookupYields503() throws Exception {
    final UserParamsProvider provider = EasyMock.createMock(UserParamsProvider.class);
    EasyMock.expect(provider.getParamsFor(EasyMock.eq(USER_NAME), EasyMock.anyObject()))
        .andThrow(new RuntimeException("Failed to fetch user parameters for " + USER_NAME, new IllegalStateException("LDAP is down")));
    EasyMock.replay(provider);

    final Response response = userInfoResource(provider).getUserInfo();

    assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), response.getStatus());
    assertTrue(String.valueOf(response.getEntity()).contains("temporarily_unavailable"));
    assertEquals(1, capturingAuditor.records.size());
    assertEquals(ActionOutcome.FAILURE, capturingAuditor.records.get(0).outcome);
    assertTrue(capturingAuditor.records.get(0).message.contains("reason=user_params_unavailable"));
  }

  @Test
  public void testUserParamsAreServed() throws Exception {
    final UserParamsProvider provider = (subjectName, scope) -> Collections.singletonMap("email", "alice@example.com");

    final Response response = userInfoResource(provider).getUserInfo();

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertTrue(String.valueOf(response.getEntity()).contains("alice@example.com"));
    assertTrue(capturingAuditor.records.get(0).message.contains("reason=served"));
  }

  private static UserInfoResource userInfoResource(UserParamsProvider provider) throws Exception {
    final HttpServletRequest req = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(req.getAttribute(TOKEN_ID_ATTRIBUTE)).andReturn(TOKEN_ID).anyTimes();
    EasyMock.expect(req.getAttribute(SCOPE_ATTRIBUTE)).andReturn("openid email").anyTimes();
    EasyMock.replay(req);

    // A local (non-federated) user: no federated_identity_id in the token metadata.
    final TokenMetadata tokenMetadata = EasyMock.createNiceMock(TokenMetadata.class);
    EasyMock.expect(tokenMetadata.getUserName()).andReturn(USER_NAME).anyTimes();
    EasyMock.replay(tokenMetadata);

    final TokenStateService tss = EasyMock.createNiceMock(TokenStateService.class);
    EasyMock.expect(tss.getTokenMetadata(TOKEN_ID)).andReturn(tokenMetadata).anyTimes();
    EasyMock.replay(tss);

    final UserInfoResource resource = new UserInfoResourceInvalidTokenTest.TestableUserInfoResource(req, tss);
    resource.userParamsProvider = provider;
    return resource;
  }
}
