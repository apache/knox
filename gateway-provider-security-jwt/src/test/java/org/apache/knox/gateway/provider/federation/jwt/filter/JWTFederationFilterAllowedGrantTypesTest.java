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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.knox.gateway.security.CommonTokenConstants.AUTH_CODE;
import static org.apache.knox.gateway.security.CommonTokenConstants.CLIENT_CREDENTIALS;
import static org.apache.knox.gateway.security.CommonTokenConstants.CLIENT_ID;
import static org.apache.knox.gateway.security.CommonTokenConstants.CLIENT_SECRET;
import static org.apache.knox.gateway.security.CommonTokenConstants.GRANT_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.provider.federation.TestFilterConfig;
import org.apache.knox.gateway.util.knoxidf.AllowedGrantTypes;
import org.apache.knox.gateway.util.knoxidf.KnoxIDFConstants;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class JWTFederationFilterAllowedGrantTypesTest {

  private static final String DISCOVERY_PATH = "/knoxidf/api/v1/.well-known/openid-configuration";
  private static final String MALFORMED_SECRET = "not-a-passcode";

  private Auditor originalAuditor;
  private RecordingAuditor auditor;
  private RecordingTokenExchangeHandler handler;
  private RecordingFilterChain chain;
  private RecordingResponse response;

  @Before
  public void setUp() {
    originalAuditor = GrantTypeAuditing.auditor;
    auditor = new RecordingAuditor();
    GrantTypeAuditing.auditor = auditor;
    chain = new RecordingFilterChain();
    response = new RecordingResponse();
  }

  @After
  public void tearDown() {
    GrantTypeAuditing.auditor = originalAuditor;
  }

  @Test
  public void testUnsetLeavesTheFilterUnrestricted() throws Exception {
    final JWTFederationFilter filter = filter(null);
    assertSame(AllowedGrantTypes.ALL, filter.getAllowedGrantTypes());

    final RequestStub request = new RequestStub(JWTFederationFilter.TOKEN_EXCHANGE, null);
    filter.doFilter(request, response, chain);

    assertTrue(handler.called);
    assertNull("No attribute when unset.", request.getAttribute(KnoxIDFConstants.ALLOWED_GRANT_TYPES_ATTRIBUTE));
  }

  @Test
  public void testUnsetStillValidatesClientCredentials() throws Exception {
    // Contrast for testClientCredentialsRejectedBeforeTheSecretIsParsed.
    final RequestStub request = new RequestStub(CLIENT_CREDENTIALS, null)
        .param(CLIENT_ID, "client").param(CLIENT_SECRET, MALFORMED_SECRET);
    assertThrows(SecurityException.class, () -> filter(null).doFilter(request, response, chain));
    assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.status);
  }

  @Test
  public void testAllowedGrantRoutesToTheHandler() throws Exception {
    final RequestStub request = new RequestStub(JWTFederationFilter.TOKEN_EXCHANGE, null);
    tokenExchangeOnly().doFilter(request, response, chain);

    assertTrue(handler.called);
    assertAllowedGrantTypesAttached(request);
    assertTrue(auditor.entries.isEmpty());
  }

  @Test
  public void testRefreshTokenRejected() throws Exception {
    assertRejected(new RequestStub(JWTFederationFilter.REFRESH_TOKEN, null)
        .param(JWTFederationFilter.REFRESH_TOKEN_PARAM, "refresh"), JWTFederationFilter.REFRESH_TOKEN);
  }

  @Test
  public void testAuthorizationCodeRejected() throws Exception {
    assertRejected(new RequestStub(AUTH_CODE, null), AUTH_CODE);
  }

  @Test
  public void testClientCredentialsRejectedBeforeTheSecretIsParsed() throws Exception {
    assertRejected(new RequestStub(CLIENT_CREDENTIALS, null)
        .param(CLIENT_ID, "client").param(CLIENT_SECRET, MALFORMED_SECRET), CLIENT_CREDENTIALS);
  }

  @Test
  public void testClientCredentialsOverBasicRejected() throws Exception {
    final String basic = "Basic " + Base64.getEncoder().encodeToString(("client:" + MALFORMED_SECRET).getBytes(UTF_8));
    assertRejected(new RequestStub(CLIENT_CREDENTIALS, basic), CLIENT_CREDENTIALS);
  }

  @Test
  public void testClientAssertionRejected() throws Exception {
    assertRejected(new RequestStub(CLIENT_CREDENTIALS, null)
        .param(JWTFederationFilter.CLIENT_ASSERTION_TYPE, JWTFederationFilter.CLIENT_ASSERTION_JWT_BEARER)
        .param(JWTFederationFilter.CLIENT_ASSERTION, "header.payload.signature"), CLIENT_CREDENTIALS);
  }

  @Test
  public void testDisallowedGrantRejectedEvenWithABearerHeader() throws Exception {
    assertRejected(new RequestStub(JWTFederationFilter.REFRESH_TOKEN, "Bearer not-a-jwt"), JWTFederationFilter.REFRESH_TOKEN);
  }

  @Test
  public void testUnknownGrantIsAuditedAsOther() throws Exception {
    assertRejected(new RequestStub("password\nevent=forged", null), "other");
  }

  @Test
  public void testMissingGrantTypePassesThrough() throws Exception {
    // KnoxIDF rejects it instead.
    final RequestStub request = new RequestStub(" ", null);
    tokenExchangeOnly().doFilter(request, response, chain);

    assertEquals("No credentials, so the usual 401.", HttpServletResponse.SC_UNAUTHORIZED, response.status);
    assertEquals("", response.body.toString());
    assertAllowedGrantTypesAttached(request);
    assertTrue(auditor.entries.isEmpty());
  }

  @Test
  public void testUnauthenticatedPathGetsTheListWithoutReadingTheBody() throws Exception {
    final RequestStub request = new RequestStub(AUTH_CODE, null).path(DISCOVERY_PATH);
    tokenExchangeOnly().doFilter(request, response, chain);

    assertTrue(chain.called);
    assertFalse("The body of an unauthenticated path must not be read.", request.parametersRead);
    assertAllowedGrantTypesAttached(request);
  }

  private void assertRejected(RequestStub request, String auditedGrantType) throws Exception {
    tokenExchangeOnly().doFilter(request, response, chain);

    assertEquals(HttpServletResponse.SC_BAD_REQUEST, response.status);
    assertEquals("{\"error\":\"unsupported_grant_type\",\"error_description\":\"grant_type is not allowed on this endpoint\"}",
        response.body.toString());
    assertEquals("no-store", response.headers.get("Cache-Control"));
    assertFalse("The handler must not run.", handler.called);
    assertFalse("The chain must not continue.", chain.called);
    assertEquals(1, auditor.entries.size());
    final RecordingAuditor.Entry entry = auditor.entries.get(0);
    assertEquals("authentication", entry.action());
    assertEquals("failure", entry.outcome());
    assertEquals("/gateway/knoxidf-tokenexchange/knoxidf/api/v1/token", entry.resourceName());
    assertEquals("event=token_grant grant_type=" + auditedGrantType + " reason=grant_type_not_allowed", entry.message());
  }

  private static void assertAllowedGrantTypesAttached(ServletRequest request) {
    final AllowedGrantTypes attached = AllowedGrantTypes.from(request);
    assertTrue(attached.isRestricted());
    assertTrue(attached.isAllowed(JWTFederationFilter.TOKEN_EXCHANGE));
    assertFalse(attached.isAllowed(AUTH_CODE));
  }

  private JWTFederationFilter tokenExchangeOnly() throws ServletException {
    return filter(JWTFederationFilter.TOKEN_EXCHANGE);
  }

  private JWTFederationFilter filter(String allowedGrantTypes) throws ServletException {
    final Properties props = new Properties();
    if (allowedGrantTypes != null) {
      props.setProperty(JWTFederationFilter.ALLOWED_GRANT_TYPES, allowedGrantTypes);
    }
    props.setProperty(JWTFederationFilter.JWT_UNAUTHENTICATED_PATHS_PARAM, DISCOVERY_PATH);
    final JWTFederationFilter filter = new JWTFederationFilter();
    filter.init(new TestFilterConfig(props));
    handler = new RecordingTokenExchangeHandler(filter);
    filter.setTokenExchangeHandler(handler);
    return filter;
  }

  // Like the production wrapper, hides the form body: parameters are visible only when unwrapped.
  private static final class RequestStub extends HttpServletRequestWrapper {
    private final Map<String, String> parameters = new HashMap<>();
    private final Map<String, Object> attributes = new HashMap<>();
    private final String authorizationHeader;
    private String pathInfo = "/knoxidf/api/v1/token";
    private boolean parametersRead;

    RequestStub(String grantType, String authorizationHeader) {
      super(EasyMock.createNiceMock(HttpServletRequest.class));
      this.authorizationHeader = authorizationHeader;
      final HttpServletRequest body = EasyMock.createNiceMock(HttpServletRequest.class);
      EasyMock.expect(body.getParameter(EasyMock.anyString())).andAnswer(() -> {
        parametersRead = true;
        return parameters.get((String) EasyMock.getCurrentArguments()[0]);
      }).anyTimes();
      EasyMock.replay(body);
      setRequest(body);
      if (grantType != null) {
        parameters.put(GRANT_TYPE, grantType);
      }
    }

    RequestStub param(String name, String value) {
      parameters.put(name, value);
      return this;
    }

    RequestStub path(String path) {
      this.pathInfo = path;
      return this;
    }

    @Override
    public String getParameter(String name) {
      return null;
    }

    @Override
    public String getHeader(String name) {
      return "Authorization".equals(name) ? authorizationHeader : null;
    }

    @Override
    public String getPathInfo() {
      return pathInfo;
    }

    @Override
    public String getRequestURI() {
      return "/gateway/knoxidf-tokenexchange" + pathInfo;
    }

    @Override
    public String getQueryString() {
      return null;
    }

    @Override
    public Object getAttribute(String name) {
      return attributes.get(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
      attributes.put(name, value);
    }
  }

  private static final class RecordingResponse extends HttpServletResponseWrapper {
    private final StringWriter body = new StringWriter();
    private final Map<String, String> headers = new HashMap<>();
    private int status;

    RecordingResponse() {
      super(EasyMock.createNiceMock(HttpServletResponse.class));
    }

    @Override
    public void setStatus(int status) {
      this.status = status;
    }

    @Override
    public void sendError(int status) {
      this.status = status;
    }

    @Override
    public void sendError(int status, String message) {
      this.status = status;
    }

    @Override
    public void setHeader(String name, String value) {
      headers.put(name, value);
    }

    @Override
    public PrintWriter getWriter() {
      return new PrintWriter(body, true);
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
