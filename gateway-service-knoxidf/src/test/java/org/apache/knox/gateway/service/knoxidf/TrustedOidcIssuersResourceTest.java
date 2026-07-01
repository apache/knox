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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.TrustedOidcIssuer;
import org.apache.knox.gateway.services.knoxidf.trustedoidcissuer.TrustedOidcIssuerService;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TrustedOidcIssuersResourceTest {

  private static final String ISSUER_A = "https://issuer-a.example.com";
  private static final String ISSUER_B = "https://issuer-b.example.com";
  private static final String OPERATOR = "admin";

  // Capture the real static Auditor so @After can restore it.
  private static final Auditor ORIGINAL_AUDITOR = TrustedOidcIssuersResource.auditor;

  private TrustedOidcIssuersResource resource;
  private TrustedOidcIssuerService mockService;
  private Auditor mockAuditor;

  @Before
  public void setUp() throws Exception {
    mockService = EasyMock.createMock(TrustedOidcIssuerService.class);
    mockAuditor = EasyMock.createMock(Auditor.class);
    TrustedOidcIssuersResource.auditor = mockAuditor;
    resource = buildResource(buildPrincipal(OPERATOR));
  }

  @After
  public void tearDown() {
    TrustedOidcIssuersResource.auditor = ORIGINAL_AUDITOR;
  }

  // ---------------------------------------------------------------------------
  // POST /register
  // ---------------------------------------------------------------------------

  @Test
  public void testRegisterIssuer() {
    EasyMock.expect(mockService.isTrusted(ISSUER_A)).andReturn(false).once();
    final Capture<TrustedOidcIssuer> capturedIssuer = EasyMock.newCapture();
    mockService.register(EasyMock.capture(capturedIssuer));
    EasyMock.expectLastCall().once();
    final Capture<String> auditMsg = EasyMock.newCapture();
    mockAuditor.audit(
        EasyMock.eq(Action.DELEGATION_LIFECYCLE), EasyMock.eq(ISSUER_A),
        EasyMock.eq(ResourceType.TRUSTED_ISSUER), EasyMock.eq(ActionOutcome.SUCCESS),
        EasyMock.capture(auditMsg));
    EasyMock.expectLastCall().once();
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.registerIssuer(buildRegisterBody(ISSUER_A, false, null));

    assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    assertEquals(ISSUER_A, capturedIssuer.getValue().getIssuerUrl());
    assertFalse(capturedIssuer.getValue().isDynamicJwks());
    assertNull(capturedIssuer.getValue().getClusterName());
    assertEquals(OPERATOR, capturedIssuer.getValue().getRegisteredBy());
    assertNotNull(capturedIssuer.getValue().getRegisteredAt());
    assertTrue(auditMsg.getValue().contains("event_type=issuer_registered"));
    assertTrue(auditMsg.getValue().contains("performed_by=" + OPERATOR));
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testRegisterWithClusterNameAndDynamicJwks() {
    EasyMock.expect(mockService.isTrusted(ISSUER_A)).andReturn(false).once();
    final Capture<TrustedOidcIssuer> capturedIssuer = EasyMock.newCapture();
    mockService.register(EasyMock.capture(capturedIssuer));
    EasyMock.expectLastCall().once();
    expectAudit(ISSUER_A, ActionOutcome.SUCCESS, "issuer_registered");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.registerIssuer(
        buildRegisterBody(ISSUER_A, true, "production-cluster"));

    assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    assertTrue(capturedIssuer.getValue().isDynamicJwks());
    assertEquals("production-cluster", capturedIssuer.getValue().getClusterName());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testRegisterNonHttpsUrl() {
    final String httpUrl = "http://insecure.example.com";
    // No service calls expected; audit fires with the non-HTTPS URL as resource name.
    expectAudit(httpUrl, ActionOutcome.FAILURE, "issuer_registered");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.registerIssuer(buildRegisterBody(httpUrl, false, null));

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertErrorField(response, "invalid_request");
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testRegisterMissingIssuerUrl() {
    // issuerUrl field absent from JSON → sentinel "(missing)" used as audit resource name.
    expectAudit("(missing)", ActionOutcome.FAILURE, "issuer_registered");
    EasyMock.replay(mockService, mockAuditor);

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(),
        resource.registerIssuer("{\"dynamicJwks\":false}").getStatus());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testRegisterEmptyIssuerUrl() {
    // Empty string issuerUrl → same sentinel "(missing)" as null case.
    expectAudit("(missing)", ActionOutcome.FAILURE, "issuer_registered");
    EasyMock.replay(mockService, mockAuditor);

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(),
        resource.registerIssuer("{\"issuerUrl\":\"\",\"dynamicJwks\":false}").getStatus());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testRegisterInvalidJson() {
    // JSON parse failure before URL is known → sentinel "(invalid)" as audit resource name.
    expectAudit("(invalid)", ActionOutcome.FAILURE, "issuer_registered");
    EasyMock.replay(mockService, mockAuditor);

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(),
        resource.registerIssuer("{ not valid json }").getStatus());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testDuplicateIssuer() {
    EasyMock.expect(mockService.isTrusted(ISSUER_A)).andReturn(true).once();
    expectAudit(ISSUER_A, ActionOutcome.FAILURE, "issuer_registered");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.registerIssuer(buildRegisterBody(ISSUER_A, false, null));

    assertEquals(Response.Status.CONFLICT.getStatusCode(), response.getStatus());
    assertErrorField(response, "issuer_exists");
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testRegisterNullPrincipal() throws Exception {
    final TrustedOidcIssuersResource res = buildResource(null);
    EasyMock.expect(mockService.isTrusted(ISSUER_A)).andReturn(false).once();
    final Capture<TrustedOidcIssuer> capturedIssuer = EasyMock.newCapture();
    mockService.register(EasyMock.capture(capturedIssuer));
    EasyMock.expectLastCall().once();
    mockAuditor.audit(
        EasyMock.eq(Action.DELEGATION_LIFECYCLE), EasyMock.eq(ISSUER_A),
        EasyMock.eq(ResourceType.TRUSTED_ISSUER), EasyMock.eq(ActionOutcome.SUCCESS),
        EasyMock.contains("performed_by=unknown"));
    EasyMock.expectLastCall().once();
    EasyMock.replay(mockService, mockAuditor);

    assertEquals(Response.Status.CREATED.getStatusCode(),
        res.registerIssuer(buildRegisterBody(ISSUER_A, false, null)).getStatus());
    assertEquals("unknown", capturedIssuer.getValue().getRegisteredBy());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testAuditRegisterStorageFailure() {
    EasyMock.expect(mockService.isTrusted(ISSUER_A)).andReturn(false).once();
    mockService.register(EasyMock.anyObject(TrustedOidcIssuer.class));
    EasyMock.expectLastCall().andThrow(new RuntimeException("DB error")).once();
    expectAudit(ISSUER_A, ActionOutcome.FAILURE, "issuer_registered");
    EasyMock.replay(mockService, mockAuditor);

    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
        resource.registerIssuer(buildRegisterBody(ISSUER_A, false, null)).getStatus());
    EasyMock.verify(mockService, mockAuditor);
  }

  // ---------------------------------------------------------------------------
  // DELETE /{issuerUrl}
  // ---------------------------------------------------------------------------

  @Test
  public void testRemoveRegisteredIssuer() {
    mockService.deregister(ISSUER_A);
    EasyMock.expectLastCall().once();
    final Capture<String> auditMsg = EasyMock.newCapture();
    mockAuditor.audit(
        EasyMock.eq(Action.DELEGATION_LIFECYCLE), EasyMock.eq(ISSUER_A),
        EasyMock.eq(ResourceType.TRUSTED_ISSUER), EasyMock.eq(ActionOutcome.SUCCESS),
        EasyMock.capture(auditMsg));
    EasyMock.expectLastCall().once();
    EasyMock.replay(mockService, mockAuditor);

    assertEquals(Response.Status.NO_CONTENT.getStatusCode(),
        resource.removeIssuer(ISSUER_A).getStatus());
    assertTrue(auditMsg.getValue().contains("event_type=issuer_removed"));
    assertTrue(auditMsg.getValue().contains("performed_by=" + OPERATOR));
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testUrlDecodingOnDelete() {
    // Jersey does not decode %2F; resource must decode explicitly.
    // Encoding ISSUER_A gives https%3A%2F%2Fissuer-a.example.com.
    // EasyMock.verify() confirms deregister was called with the decoded value.
    final String encoded = URLEncoder.encode(ISSUER_A, StandardCharsets.UTF_8);
    mockService.deregister(ISSUER_A);
    EasyMock.expectLastCall().once();
    expectAudit(ISSUER_A, ActionOutcome.SUCCESS, "issuer_removed");
    EasyMock.replay(mockService, mockAuditor);

    assertEquals(Response.Status.NO_CONTENT.getStatusCode(),
        resource.removeIssuer(encoded).getStatus());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testAuditRemoveStorageFailure() {
    mockService.deregister(ISSUER_A);
    EasyMock.expectLastCall().andThrow(new RuntimeException("DB error")).once();
    expectAudit(ISSUER_A, ActionOutcome.FAILURE, "issuer_removed");
    EasyMock.replay(mockService, mockAuditor);

    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
        resource.removeIssuer(ISSUER_A).getStatus());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testRemoveInvalidEncoding() {
    // %GG is not a valid percent-encoding sequence; URLDecoder throws IllegalArgumentException.
    // The audit resource name falls back to "(invalid-encoding)".
    expectAudit("(invalid-encoding)", ActionOutcome.FAILURE, "issuer_removed");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.removeIssuer("https%GGinvalid.example.com");

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertErrorField(response, "invalid_request");
    EasyMock.verify(mockService, mockAuditor);
  }

  // ---------------------------------------------------------------------------
  // POST /{issuerUrl}/refresh-jwks
  // ---------------------------------------------------------------------------

  @Test
  public void testRefreshJwksUri() {
    mockService.refreshJwksUri(ISSUER_A);
    EasyMock.expectLastCall().once();
    final Capture<String> auditMsg = EasyMock.newCapture();
    mockAuditor.audit(
        EasyMock.eq(Action.DELEGATION_LIFECYCLE), EasyMock.eq(ISSUER_A),
        EasyMock.eq(ResourceType.TRUSTED_ISSUER), EasyMock.eq(ActionOutcome.SUCCESS),
        EasyMock.capture(auditMsg));
    EasyMock.expectLastCall().once();
    EasyMock.replay(mockService, mockAuditor);

    assertEquals(Response.Status.NO_CONTENT.getStatusCode(),
        resource.refreshJwksUri(ISSUER_A).getStatus());
    assertTrue(auditMsg.getValue().contains("event_type=issuer_jwks_refreshed"));
    assertTrue(auditMsg.getValue().contains("performed_by=" + OPERATOR));
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testUrlDecodingOnRefresh() {
    final String encoded = URLEncoder.encode(ISSUER_A, StandardCharsets.UTF_8);
    mockService.refreshJwksUri(ISSUER_A);
    EasyMock.expectLastCall().once();
    expectAudit(ISSUER_A, ActionOutcome.SUCCESS, "issuer_jwks_refreshed");
    EasyMock.replay(mockService, mockAuditor);

    assertEquals(Response.Status.NO_CONTENT.getStatusCode(),
        resource.refreshJwksUri(encoded).getStatus());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testRefreshJwksUriAuditsStorageFailure() {
    mockService.refreshJwksUri(ISSUER_A);
    EasyMock.expectLastCall().andThrow(new RuntimeException("Cache error")).once();
    expectAudit(ISSUER_A, ActionOutcome.FAILURE, "issuer_jwks_refreshed");
    EasyMock.replay(mockService, mockAuditor);

    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
        resource.refreshJwksUri(ISSUER_A).getStatus());
    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testRefreshInvalidEncoding() {
    expectAudit("(invalid-encoding)", ActionOutcome.FAILURE, "issuer_jwks_refreshed");
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.refreshJwksUri("https%GGinvalid.example.com");

    assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    assertErrorField(response, "invalid_request");
    EasyMock.verify(mockService, mockAuditor);
  }

  // ---------------------------------------------------------------------------
  // GET /
  // ---------------------------------------------------------------------------

  @Test
  public void testListIssuers() throws Exception {
    final Instant now = Instant.now();
    final TrustedOidcIssuer issuerA = new TrustedOidcIssuer(ISSUER_A, true, "cluster-a",
        now, OPERATOR);
    final TrustedOidcIssuer issuerB = new TrustedOidcIssuer(ISSUER_B, false, null,
        now, null);
    EasyMock.expect(mockService.list()).andReturn(Arrays.asList(issuerA, issuerB)).once();
    // listIssuers does not audit; no expectations set on mockAuditor.
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.listIssuers();

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    final List<Map<String, Object>> body = parseJsonList(response.getEntity().toString());
    assertEquals(2, body.size());

    final Map<String, Object> a = findByIssuerUrl(body, ISSUER_A);
    assertEquals(true, a.get("dynamicJwks"));
    assertEquals("cluster-a", a.get("clusterName"));
    assertNotNull(a.get("registeredAt"));
    assertEquals(OPERATOR, a.get("registeredBy"));

    final Map<String, Object> b = findByIssuerUrl(body, ISSUER_B);
    assertEquals(false, b.get("dynamicJwks"));
    assertNull(b.get("clusterName"));
    assertNull(b.get("registeredBy"));
    assertNotNull(b.get("registeredAt"));

    EasyMock.verify(mockService, mockAuditor);
  }

  @Test
  public void testListReturnsEmptyArray() throws Exception {
    EasyMock.expect(mockService.list()).andReturn(Collections.emptyList()).once();
    EasyMock.replay(mockService, mockAuditor);

    final Response response = resource.listIssuers();

    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertTrue(parseJsonList(response.getEntity().toString()).isEmpty());
    EasyMock.verify(mockService, mockAuditor);
  }

  // ---------------------------------------------------------------------------
  // @PostConstruct wiring
  // ---------------------------------------------------------------------------

  @Test
  public void testInitWiresServiceFromGatewayServices() throws Exception {
    final TrustedOidcIssuerService svc = EasyMock.createNiceMock(TrustedOidcIssuerService.class);
    EasyMock.replay(svc);

    final GatewayServices gws = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(gws.getService(ServiceType.TRUSTED_OIDC_ISSUER_SERVICE)).andReturn(svc).once();
    EasyMock.replay(gws);

    final ServletContext ctx = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(ctx.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(gws).once();
    EasyMock.replay(ctx);

    final TrustedOidcIssuersResource res = new TrustedOidcIssuersResource();
    injectField(res, "servletContext", ctx);
    injectField(res, "request", buildRequest(buildPrincipal(OPERATOR)));
    res.init();

    EasyMock.verify(gws, ctx);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private TrustedOidcIssuersResource buildResource(Principal principal) throws Exception {
    final TrustedOidcIssuersResource res = new TrustedOidcIssuersResource();
    injectField(res, "request", buildRequest(principal));
    injectField(res, "trustedIssuers", mockService);
    return res;
  }

  private HttpServletRequest buildRequest(Principal principal) {
    final HttpServletRequest req = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(req.getUserPrincipal()).andReturn(principal).anyTimes();
    EasyMock.replay(req);
    return req;
  }

  private Principal buildPrincipal(String name) {
    if (name == null) {
      return null;
    }
    final Principal p = EasyMock.createNiceMock(Principal.class);
    EasyMock.expect(p.getName()).andReturn(name).anyTimes();
    EasyMock.replay(p);
    return p;
  }

  private void expectAudit(String issuerUrl, String outcome, String eventType) {
    mockAuditor.audit(
        EasyMock.eq(Action.DELEGATION_LIFECYCLE),
        EasyMock.eq(issuerUrl),
        EasyMock.eq(ResourceType.TRUSTED_ISSUER),
        EasyMock.eq(outcome),
        EasyMock.contains(eventType));
    EasyMock.expectLastCall().once();
  }

  private static String buildRegisterBody(String issuerUrl, boolean dynamicJwks,
      String clusterName) {
    final StringBuilder sb = new StringBuilder("{");
    if (issuerUrl != null) {
      sb.append("\"issuerUrl\":\"").append(issuerUrl).append("\",");
    }
    sb.append("\"dynamicJwks\":").append(dynamicJwks);
    if (clusterName != null) {
      sb.append(",\"clusterName\":\"").append(clusterName).append("\"");
    }
    sb.append("}");
    return sb.toString();
  }

  private static void assertErrorField(Response response, String expectedError) {
    assertNotNull(response.getEntity());
    final String body = response.getEntity().toString();
    assertFalse("Error body must not be empty", body.isEmpty());
    assertTrue("Expected error field '" + expectedError + "' in: " + body,
        body.contains(expectedError));
  }

  private static List<Map<String, Object>> parseJsonList(String json) throws Exception {
    return new ObjectMapper().readValue(json, new TypeReference<List<Map<String, Object>>>() {});
  }

  private static Map<String, Object> findByIssuerUrl(List<Map<String, Object>> list,
      String issuerUrl) {
    return list.stream()
        .filter(m -> issuerUrl.equals(m.get("issuerUrl")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Issuer not found: " + issuerUrl));
  }

  private static void injectField(Object target, String fieldName, Object value) throws Exception {
    final Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
