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
package org.apache.knox.gateway.services.knoxidf.flowstate;

import jakarta.servlet.ServletContext;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.util.knoxidf.AuthorizeRequestMetadata;
import org.apache.knox.gateway.util.knoxidf.FederatedOpLoginOption;
import org.easymock.EasyMock;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AuthzFlowStateStoreTest {

  @Test
  public void testAuthorizeRequestRoundTrip() {
    final AuthzFlowStateStore store = new AuthzFlowStateStore(new InMemoryAuthzFlowStateService());
    final AuthorizeRequestMetadata metadata = new AuthorizeRequestMetadata("client", "alice", "code",
        "https://app.example.com/cb?x=1&y=2", new LinkedHashSet<>(Arrays.asList("openid", "profile")), "st\"ate",
        "nonce", "challenge", "S256");
    store.putAuthorizeRequest("id", metadata);

    final AuthorizeRequestMetadata restored = store.consumeAuthorizeRequest("id").orElseThrow();
    assertEquals("client", restored.getClientId());
    assertEquals("alice", restored.getSubject());
    assertEquals("code", restored.getResponseType());
    assertEquals("https://app.example.com/cb?x=1&y=2", restored.getRedirectUri());
    assertEquals(metadata.getRequestedScopes(), restored.getRequestedScopes());
    assertEquals("openid profile", restored.getJoinedRequestedScopes());
    assertEquals("st\"ate", restored.getState());
    assertEquals("nonce", restored.getNonce());
    assertEquals("challenge", restored.getCodeChallenge());
    assertEquals("S256", restored.getCodeChallengeMethod());
    assertFalse("the authorize request is single-use", store.consumeAuthorizeRequest("id").isPresent());
  }

  @Test
  public void testAuthorizeRequestNullFieldsRoundTrip() {
    final AuthzFlowStateStore store = new AuthzFlowStateStore(new InMemoryAuthzFlowStateService());
    store.putAuthorizeRequest("id", new AuthorizeRequestMetadata("client", null, "code", "https://cb",
        new LinkedHashSet<>(Collections.singletonList("openid")), "state", null));

    final AuthorizeRequestMetadata restored = store.consumeAuthorizeRequest("id").orElseThrow();
    assertNull(restored.getSubject());
    assertNull(restored.getNonce());
    assertNull(restored.getCodeChallenge());
    assertNull(restored.getCodeChallengeMethod());
  }

  @Test
  public void testFederatedOpOptionsRoundTrip() {
    final AuthzFlowStateStore store = new AuthzFlowStateStore(new InMemoryAuthzFlowStateService());
    final List<FederatedOpLoginOption> options = Arrays.asList(
        new FederatedOpLoginOption("google", "g-client", "https://accounts.google.com/auth", "https://knox/cb"),
        new FederatedOpLoginOption("okta", "o-client", "https://okta.example.com/authorize", "https://knox/cb"));
    store.putFederatedOpOptions("id", options);

    assertEquals(options, store.getFederatedOpOptions("id").orElseThrow());
    // get does not remove: the user may go back to the login page and pick another OP
    assertEquals(options, store.getFederatedOpOptions("id").orElseThrow());
    store.removeFederatedOpOptions("id");
    assertFalse(store.getFederatedOpOptions("id").isPresent());
  }

  @Test
  public void testFederatedOpSelectionRoundTrip() {
    final AuthzFlowStateStore store = new AuthzFlowStateStore(new InMemoryAuthzFlowStateService());
    store.putFederatedOpSelection("id", new FederatedOpSelection("google", "nonce"));
    assertEquals(new FederatedOpSelection("google", "nonce"), store.getFederatedOpSelection("id").orElseThrow());
    assertEquals(new FederatedOpSelection("google", "nonce"), store.getFederatedOpSelection("id").orElseThrow());
    store.removeFederatedOpSelection("id");
    assertFalse(store.getFederatedOpSelection("id").isPresent());
  }

  @Test
  public void testRemoveAuthorizeRequest() {
    final AuthzFlowStateStore store = new AuthzFlowStateStore(new InMemoryAuthzFlowStateService());
    store.putAuthorizeRequest("id", new AuthorizeRequestMetadata("client", "alice", "code", "https://cb",
        new LinkedHashSet<>(Collections.singletonList("openid")), "state", null));
    store.removeAuthorizeRequest("id");
    assertFalse(store.consumeAuthorizeRequest("id").isPresent());
  }

  @Test
  public void testMalformedPayloadsAreAbsent() {
    final InMemoryAuthzFlowStateService service = new InMemoryAuthzFlowStateService();
    final AuthzFlowStateStore store = new AuthzFlowStateStore(service);
    service.put(AuthzFlowStateKind.AUTHORIZE_REQUEST, "id", "not json");
    service.put(AuthzFlowStateKind.FEDERATED_OP_OPTIONS, "id", "{\"not\":\"an array\"}");
    service.put(AuthzFlowStateKind.FEDERATED_OP_SELECTION, "id", "{\"nonce\":\"no op name\"}");
    assertFalse(store.consumeAuthorizeRequest("id").isPresent());
    assertFalse(store.getFederatedOpOptions("id").isPresent());
    assertFalse(store.getFederatedOpSelection("id").isPresent());
    assertEquals(Optional.empty(), AuthzFlowStateStore.authorizeRequestFromJson("[1, 2]"));
  }

  @Test
  public void testUsesGatewayService() {
    final AuthzFlowStateService service = new InMemoryAuthzFlowStateService();
    final GatewayServices gatewayServices = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(gatewayServices.getService(ServiceType.KNOXIDF_AUTHZ_FLOW_STATE_SERVICE)).andReturn(service).anyTimes();
    final ServletContext servletContext = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(gatewayServices).anyTimes();
    EasyMock.replay(gatewayServices, servletContext);

    AuthzFlowStateStore.get(servletContext).putFederatedOpSelection("id", new FederatedOpSelection("google", "nonce"));
    assertTrue(service.get(AuthzFlowStateKind.FEDERATED_OP_SELECTION, "id").isPresent());
  }

  @Test
  public void testFallbackIsSharedAcrossServletContexts() {
    final ServletContext knoxIdfContext = EasyMock.createNiceMock(ServletContext.class);
    final ServletContext knoxSsoContext = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.replay(knoxIdfContext, knoxSsoContext);
    final String id = UUID.randomUUID().toString();

    AuthzFlowStateStore.get(knoxIdfContext).putFederatedOpOptions(id,
        Collections.singletonList(new FederatedOpLoginOption("google", "client", "https://op/auth", "https://knox/cb")));
    assertTrue(AuthzFlowStateStore.get(knoxSsoContext).getFederatedOpOptions(id).isPresent());
    assertTrue(AuthzFlowStateStore.get(null).getFederatedOpOptions(id).isPresent());
    AuthzFlowStateStore.get(null).removeFederatedOpOptions(id);
  }
}
