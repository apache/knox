/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.provider.federation.jwt.filter;

import static org.apache.knox.gateway.security.CommonTokenConstants.GRANT_TYPE;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Verifies that {@link JWTFederationFilter#doFilter} routes RFC 8693 token-exchange requests to the
 * {@link TokenExchangeHandler}. The handler itself is replaced by a recording stub so this test
 * only asserts the dispatch decision (the handler's own logic is covered by
 * {@link TokenExchangeHandlerTest}).
 *
 * <p>The grant type lives in the {@code x-www-form-urlencoded} body, which is only visible on the
 * unwrapped request; {@link BodyHidingRequestWrapper} reproduces that (its {@code getParameter}
 * returns {@code null}, mirroring the production wrapper).</p>
 */
public class JWTFederationFilterTokenExchangeRoutingTest {

  private JWTFederationFilter filter;
  private RecordingTokenExchangeHandler recordingHandler;
  private HttpServletResponse response;
  private RecordingFilterChain chain;

  @Before
  public void setUp() {
    filter = new JWTFederationFilter();
    recordingHandler = new RecordingTokenExchangeHandler(filter);
    filter.setTokenExchangeHandler(recordingHandler);
    response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.replay(response);
    chain = new RecordingFilterChain();
  }

  @Test
  public void testTokenExchangeGrantRoutesToHandler() throws Exception {
    // grant_type in the body (unwrapped), no Authorization header
    final HttpServletRequest request = wrapped(bodyRequest(JWTFederationFilter.TOKEN_EXCHANGE, null));

    filter.doFilter(request, response, chain);

    assertTrue("token-exchange grant should be dispatched to the handler", recordingHandler.called);
    assertFalse("the filter chain must not continue when the handler takes over", chain.called);
  }

  @Test
  public void testNonTokenExchangeGrantDoesNotRouteToHandler() throws Exception {
    // no grant_type at all -> not a token exchange
    final HttpServletRequest request = wrapped(bodyRequest(null, null));

    filter.doFilter(request, response, chain);

    assertFalse("non-exchange requests must not reach the handler", recordingHandler.called);
  }

  private static HttpServletRequest wrapped(HttpServletRequest inner) {
    return new BodyHidingRequestWrapper(inner);
  }

  private static HttpServletRequest bodyRequest(String grantType, String authorizationHeader) {
    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getHeader("Authorization")).andReturn(authorizationHeader).anyTimes();
    EasyMock.expect(request.getParameter(GRANT_TYPE)).andReturn(grantType).anyTimes();
    EasyMock.expect(request.getQueryString()).andReturn(null).anyTimes();
    EasyMock.expect(request.getPathInfo()).andReturn(null).anyTimes();
    EasyMock.replay(request);
    return request;
  }

  /** Mirrors the production request wrapper: parameters are hidden, the body is only on getRequest(). */
  private static final class BodyHidingRequestWrapper extends HttpServletRequestWrapper {
    private final HttpServletRequest inner;

    BodyHidingRequestWrapper(HttpServletRequest inner) {
      super(inner);
      this.inner = inner;
    }

    @Override
    public String getParameter(String name) {
      return null; // hide body parameters
    }

    @Override
    public String getHeader(String name) {
      return inner.getHeader(name);
    }

    @Override
    public HttpServletRequest getRequest() {
      return inner;
    }
  }

  private static final class RecordingTokenExchangeHandler extends TokenExchangeHandler {
    private boolean called;

    RecordingTokenExchangeHandler(JWTFederationFilter filter) {
      super(filter);
    }

    @Override
    void handle(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
      this.called = true;
    }
  }

  private static final class RecordingFilterChain implements FilterChain {
    private boolean called;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
      this.called = true;
    }
  }
}
