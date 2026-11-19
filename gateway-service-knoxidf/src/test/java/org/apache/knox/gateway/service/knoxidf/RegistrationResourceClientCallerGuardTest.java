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

import java.security.PrivilegedAction;

import javax.security.auth.Subject;

import org.apache.knox.gateway.security.ImpersonatedPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenMetadataType;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies that dynamic client registration refuses a caller whose subject is a registered client
 * (a CLIENT_ID token id) unless the deployment explicitly opts out via
 * {@code knoxidf.client.registration.client.callers.denied=false}. Otherwise a client could register
 * clients owned by itself, each with its own per-user token limit.
 */
public class RegistrationResourceClientCallerGuardTest {

  private static final String CLIENT_ID = "MyClient-0001";
  private static final String USER = "alice";

  private TokenStateService tokenStateService;

  /** Exposes injection of the flag and the token state without running the full JAX-RS/servlet lifecycle. */
  static final class TestableRegistrationResource extends RegistrationResource {
    TestableRegistrationResource(final boolean clientCallersDenied, final TokenStateService tokenStateService) {
      this.clientCallersDenied = clientCallersDenied;
      this.tokenStateService = tokenStateService;
    }
  }

  @Before
  public void setUp() throws Exception {
    final TokenMetadata clientMetadata = new TokenMetadata(USER);
    clientMetadata.add(TokenMetadata.TYPE, TokenMetadataType.CLIENT_ID.name());
    final TokenMetadata apiKeyMetadata = new TokenMetadata(USER);
    apiKeyMetadata.add(TokenMetadata.TYPE, TokenMetadataType.API_KEY.name());

    // A strict mock: a lookup of any other name fails the test.
    tokenStateService = EasyMock.createMock(TokenStateService.class);
    EasyMock.expect(tokenStateService.getTokenMetadata(CLIENT_ID)).andReturn(clientMetadata).anyTimes();
    EasyMock.expect(tokenStateService.getTokenMetadata("api-key-0001")).andReturn(apiKeyMetadata).anyTimes();
    EasyMock.expect(tokenStateService.getTokenMetadata(USER)).andThrow(new UnknownTokenException(USER)).anyTimes();
    EasyMock.replay(tokenStateService);
  }

  private boolean deniedAs(final Subject subject, final boolean clientCallersDenied) {
    final TestableRegistrationResource resource = new TestableRegistrationResource(clientCallersDenied, tokenStateService);
    if (subject == null) {
      // No security context at all.
      return resource.clientCallerDenied();
    }
    return Subject.doAs(subject, (PrivilegedAction<Boolean>) resource::clientCallerDenied);
  }

  private static Subject subjectOf(final String primaryPrincipalName) {
    final Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal(primaryPrincipalName));
    return subject;
  }

  @Test
  public void testClientCallerRejectedByDefault() {
    assertTrue(deniedAs(subjectOf(CLIENT_ID), true));
  }

  @Test
  public void testClientCallerAllowedWhenExplicitlyEnabled() {
    assertFalse(deniedAs(subjectOf(CLIENT_ID), false));
  }

  @Test
  public void testUserCallerAllowed() {
    assertFalse(deniedAs(subjectOf(USER), true));
  }

  @Test
  public void testNonClientTokenIdAllowed() {
    // Only CLIENT_ID tokens are clients; a subject that happens to be another token type's id is not.
    assertFalse(deniedAs(subjectOf("api-key-0001"), true));
  }

  @Test
  public void testEffectivePrincipalChecked() {
    // Identity assertion adds the mapped name as the effective principal; that is the name looked up,
    // not the subject as authenticated (the strict mock fails on a lookup of the latter).
    final Subject subject = subjectOf("unmapped-name");
    subject.getPrincipals().add(new ImpersonatedPrincipal(CLIENT_ID));
    assertTrue(deniedAs(subject, true));
  }

  @Test
  public void testNoSecurityContextAllowed() {
    // Without a subject there is no client to match; the anonymous guard handles unauthenticated callers.
    assertFalse(deniedAs(null, true));
  }

  @Test
  public void testNoTokenStateAllowed() {
    final TestableRegistrationResource resource = new TestableRegistrationResource(true, null);
    assertFalse(Subject.doAs(subjectOf(CLIENT_ID), (PrivilegedAction<Boolean>) resource::clientCallerDenied));
  }
}
