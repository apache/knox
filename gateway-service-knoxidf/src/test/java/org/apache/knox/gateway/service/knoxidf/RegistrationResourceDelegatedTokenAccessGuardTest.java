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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import javax.security.auth.Subject;

import jakarta.servlet.ServletContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import org.apache.knox.gateway.security.ActorChainPrincipalImpl;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.easymock.EasyMock;
import org.junit.Test;

/**
 * Verifies that the token endpoints under {@code /client} (getUserTokens, revoke, enable, disable and the
 * batch forms) refuse a caller whose token carries an RFC 8693 actor chain unless the deployment explicitly opts
 * out via {@code knoxidf.client.registration.delegated.callers.denied=false}. They authorize by the
 * effective principal, so otherwise a delegated token could act on the tokens of the user it acts for.
 */
public class RegistrationResourceDelegatedTokenAccessGuardTest {

  private static final String USER = "alice";
  private static final String TOKEN_ID = UUID.randomUUID().toString();
  private static final String TOKEN_IDS = "[\"" + TOKEN_ID + "\"]";

  /** Injects the flag, token state and servlet context without running the full JAX-RS/servlet lifecycle. */
  static final class TestableRegistrationResource extends RegistrationResource {
    TestableRegistrationResource(final boolean delegatedCallersDenied, final TokenStateService tokenStateService)
        throws Exception {
      this.delegatedCallersDenied = delegatedCallersDenied;
      this.tokenStateService = tokenStateService;
      // Read by the parent when it logs the outcome.
      final Field field = org.apache.knox.gateway.service.knoxtoken.TokenResource.class.getDeclaredField("context");
      field.setAccessible(true);
      field.set(this, EasyMock.createNiceMock(ServletContext.class));
    }
  }

  private static Response callAs(final Subject subject, final RegistrationResource resource,
                                 final Function<RegistrationResource, Response> call) {
    return Subject.doAs(subject, (PrivilegedAction<Response>) () -> call.apply(resource));
  }

  private static Subject userSubject() {
    final Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal(USER));
    return subject;
  }

  private static Subject delegatedSubject() {
    final Subject subject = userSubject();
    final List<Map<String, Object>> actorChain = Collections.singletonList(Collections.singletonMap("sub", "engine-sa"));
    subject.getPrincipals().add(new ActorChainPrincipalImpl(actorChain));
    return subject;
  }

  private static void assertDelegatedCallerDenied(final Function<RegistrationResource, Response> call) throws Exception {
    // Strict and without expectations: the parent must not be reached.
    final TokenStateService tokenStateService = EasyMock.createStrictMock(TokenStateService.class);
    EasyMock.replay(tokenStateService);

    final Response response = callAs(delegatedSubject(), new TestableRegistrationResource(true, tokenStateService), call);

    assertEquals(Response.Status.FORBIDDEN.getStatusCode(), response.getStatus());
    assertTrue(String.valueOf(response.getEntity()).contains("access_denied"));
    EasyMock.verify(tokenStateService);
  }

  private static TokenStateService ownTokenState(final boolean enabled) throws Exception {
    final TokenMetadata metadata = new TokenMetadata(USER);
    metadata.setEnabled(enabled);
    final TokenStateService tokenStateService = EasyMock.createNiceMock(TokenStateService.class);
    EasyMock.expect(tokenStateService.getTokenMetadata(TOKEN_ID)).andReturn(metadata).anyTimes();
    return tokenStateService;
  }

  private static void assertRevokesOwnToken(final Subject subject, final boolean delegatedCallersDenied) throws Exception {
    final TokenStateService tokenStateService = ownTokenState(true);
    tokenStateService.revokeToken(TOKEN_ID);
    EasyMock.expectLastCall().once();
    EasyMock.replay(tokenStateService);

    final Response response = callAs(subject, new TestableRegistrationResource(delegatedCallersDenied, tokenStateService),
        resource -> resource.revoke(TOKEN_ID));

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    EasyMock.verify(tokenStateService);
  }

  private static void assertDisablesOwnToken(final Subject subject, final boolean delegatedCallersDenied) throws Exception {
    final TokenStateService tokenStateService = ownTokenState(true);
    tokenStateService.addMetadata(EasyMock.eq(TOKEN_ID), EasyMock.anyObject(TokenMetadata.class));
    EasyMock.expectLastCall().once();
    EasyMock.replay(tokenStateService);

    final Response response = callAs(subject, new TestableRegistrationResource(delegatedCallersDenied, tokenStateService),
        resource -> resource.disable(TOKEN_ID));

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    EasyMock.verify(tokenStateService);
  }

  private static UriInfo ownTokensQuery() {
    final MultivaluedMap<String, String> query = new MultivaluedHashMap<>();
    query.putSingle("userName", USER);
    final UriInfo uriInfo = EasyMock.createNiceMock(UriInfo.class);
    EasyMock.expect(uriInfo.getQueryParameters()).andReturn(query).anyTimes();
    EasyMock.replay(uriInfo);
    return uriInfo;
  }

  private static void assertListsOwnTokens(final Subject subject, final boolean delegatedCallersDenied) throws Exception {
    final TokenStateService tokenStateService = EasyMock.createNiceMock(TokenStateService.class);
    EasyMock.expect(tokenStateService.getTokens(USER)).andReturn(Collections.emptyList()).once();
    EasyMock.replay(tokenStateService);

    final Response response = callAs(subject, new TestableRegistrationResource(delegatedCallersDenied, tokenStateService),
        resource -> resource.getUserTokens(ownTokensQuery()));

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    EasyMock.verify(tokenStateService);
  }

  @Test
  public void testDelegatedCallerCannotGetUserTokens() throws Exception {
    assertDelegatedCallerDenied(resource -> resource.getUserTokens(ownTokensQuery()));
  }

  @Test
  public void testDelegatedCallerCannotRevoke() throws Exception {
    assertDelegatedCallerDenied(resource -> resource.revoke(TOKEN_ID));
  }

  @Test
  public void testDelegatedCallerCannotRevokeTokens() throws Exception {
    assertDelegatedCallerDenied(resource -> resource.revokeTokens(TOKEN_IDS));
  }

  @Test
  public void testDelegatedCallerCannotEnable() throws Exception {
    assertDelegatedCallerDenied(resource -> resource.enable(TOKEN_ID));
  }

  @Test
  public void testDelegatedCallerCannotEnableTokens() throws Exception {
    assertDelegatedCallerDenied(resource -> resource.enableTokens(TOKEN_IDS));
  }

  @Test
  public void testDelegatedCallerCannotDisable() throws Exception {
    assertDelegatedCallerDenied(resource -> resource.disable(TOKEN_ID));
  }

  @Test
  public void testDelegatedCallerCannotDisableTokens() throws Exception {
    assertDelegatedCallerDenied(resource -> resource.disableTokens(TOKEN_IDS));
  }

  @Test
  public void testDelegatedCallerRevokesWhenExplicitlyAllowed() throws Exception {
    assertRevokesOwnToken(delegatedSubject(), false);
  }

  @Test
  public void testDelegatedCallerDisablesWhenExplicitlyAllowed() throws Exception {
    assertDisablesOwnToken(delegatedSubject(), false);
  }

  @Test
  public void testDelegatedCallerListsTokensWhenExplicitlyAllowed() throws Exception {
    assertListsOwnTokens(delegatedSubject(), false);
  }

  @Test
  public void testNonDelegatedCallerListsOwnTokens() throws Exception {
    assertListsOwnTokens(userSubject(), true);
  }

  @Test
  public void testNonDelegatedCallerRevokesOwnToken() throws Exception {
    assertRevokesOwnToken(userSubject(), true);
  }

  @Test
  public void testNonDelegatedCallerDisablesOwnToken() throws Exception {
    assertDisablesOwnToken(userSubject(), true);
  }
}
