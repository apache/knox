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

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;

import org.apache.knox.gateway.services.knoxidf.flowstate.AuthzFlowStateKind;
import org.apache.knox.gateway.services.knoxidf.flowstate.AuthzFlowStateServiceException;
import org.apache.knox.gateway.services.knoxidf.flowstate.AuthzFlowStateStore;
import org.apache.knox.gateway.services.knoxidf.flowstate.FederatedOpSelection;
import org.apache.knox.gateway.services.knoxidf.flowstate.InMemoryAuthzFlowStateService;
import org.apache.knox.gateway.util.knoxidf.AuthorizeRequestMetadata;
import org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants;
import org.easymock.EasyMock;
import org.junit.Test;

/**
 * Verifies that a flow state read failure ends the flow with {@code server_error}, and that the OP
 * callback deletes nothing before its single consume, so a failed callback can be reloaded.
 */
public class AuthorizeResourceFlowStateFailureTest {

  private static final String STATE = "login-session";

  /** Fails every consume while {@link #failing} is set. */
  private static final class FailingFlowStateService extends InMemoryAuthzFlowStateService {
    private boolean failing = true;

    @Override
    public Optional<String> consume(AuthzFlowStateKind kind, String id) {
      if (failing) {
        throw new AuthzFlowStateServiceException("database down", null);
      }
      return super.consume(kind, id);
    }
  }

  private static AuthorizeRequestMetadata authorizeRequest() {
    return new AuthorizeRequestMetadata("client", null, "code", "https://client/cb",
        new LinkedHashSet<>(Collections.singletonList("openid")), "client-state", null);
  }

  private static ServletContext topologyWithOp(final String opName) {
    final ServletContext servletContext = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(servletContext.getInitParameter(KnoxIDFConstants.FEDERATED_OP_CONFIG_NAMES)).andReturn(opName).anyTimes();
    EasyMock.expect(servletContext.getInitParameter(KnoxIDFConstants.FEDERATED_OP_CONFIG_PREFIX + opName + ".enabled"))
        .andReturn("true").anyTimes();
    EasyMock.replay(servletContext);
    return servletContext;
  }

  private static HttpServletRequest callbackRequest() {
    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter(KnoxIDFConstants.CODE)).andReturn("op-code").anyTimes();
    EasyMock.expect(request.getParameter(KnoxIDFConstants.STATE)).andReturn(STATE).anyTimes();
    EasyMock.replay(request);
    return request;
  }

  private static AuthorizeResource resource(final AuthzFlowStateStore store, final ServletContext servletContext,
      final HttpServletRequest request) throws Exception {
    final AuthorizeResource resource = new AuthorizeResource();
    setField(resource, "authzFlowStateStore", store);
    setField(resource, "servletContext", servletContext);
    setField(resource, "request", request);
    return resource;
  }

  private static void setField(final AuthorizeResource resource, final String name, final Object value) throws Exception {
    final Field field = AuthorizeResource.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(resource, value);
  }

  private static void assertError(final Response response, final int status, final String error) {
    assertEquals(status, response.getStatus());
    assertTrue(String.valueOf(response.getEntity()).contains(error));
  }

  @Test
  public void testConsentReadFailureIsServerError() throws Exception {
    final AuthzFlowStateStore store = new AuthzFlowStateStore(new FailingFlowStateService());
    final Response response = resource(store, null, null).consentAccepted(STATE);
    assertError(response, 500, "server_error");
  }

  @Test
  public void testCallbackReadFailureKeepsTheFlowState() throws Exception {
    final FailingFlowStateService service = new FailingFlowStateService();
    final AuthzFlowStateStore store = new AuthzFlowStateStore(service);
    store.putAuthorizeRequest(STATE, authorizeRequest());
    store.putFederatedOpSelection(STATE, new FederatedOpSelection("google", "nonce"));

    final Response response = resource(store, topologyWithOp("google"), callbackRequest()).authCallback();

    assertError(response, 500, "server_error");
    service.failing = false;
    assertTrue("the callback can be reloaded", store.getFederatedOpSelection(STATE).isPresent());
    assertTrue("the callback can be reloaded", store.consumeAuthorizeRequest(STATE).isPresent());
  }

  @Test
  public void testCallbackForRemovedOpDeletesNothing() throws Exception {
    final FailingFlowStateService service = new FailingFlowStateService();
    service.failing = false;
    final AuthzFlowStateStore store = new AuthzFlowStateStore(service);
    store.putAuthorizeRequest(STATE, authorizeRequest());
    store.putFederatedOpSelection(STATE, new FederatedOpSelection("removed", "nonce"));

    final Response response = resource(store, topologyWithOp("google"), callbackRequest()).authCallback();

    assertError(response, 400, "invalid_request");
    assertTrue(store.getFederatedOpSelection(STATE).isPresent());
    assertTrue(store.consumeAuthorizeRequest(STATE).isPresent());
  }

  @Test
  public void testCallbackWithUnknownStateIsRejected() throws Exception {
    final FailingFlowStateService service = new FailingFlowStateService();
    service.failing = false;
    final Response response = resource(new AuthzFlowStateStore(service), topologyWithOp("google"), callbackRequest()).authCallback();
    assertError(response, 400, "invalid_request");
  }
}
