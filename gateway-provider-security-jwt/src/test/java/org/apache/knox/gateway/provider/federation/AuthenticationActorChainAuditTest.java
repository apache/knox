/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.provider.federation;

import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.assertEquals;

import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.provider.federation.jwt.filter.AbstractJWTFilter;
import org.apache.knox.gateway.security.ActorChainPrincipalImpl;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.Subject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AuthenticationActorChainAuditTest {

  private static final String SOURCE_URI = "https://localhost:8443/gateway/sandbox/service";

  private Auditor originalAuditor;
  private AuditService originalAuditService;

  @Before
  public void setUp() throws Exception {
    originalAuditor = (Auditor) auditorField().get(null);
    originalAuditService = (AuditService) auditServiceField().get(null);
  }

  @After
  public void tearDown() throws Exception {
    auditorField().set(null, originalAuditor);
    auditServiceField().set(null, originalAuditService);
  }

  @Test
  public void testDelegatedTokenEmitsActChainInAuthenticationMessage() throws Exception {
    final Capture<String> message = EasyMock.newCapture();
    final Auditor auditor = EasyMock.createMock(Auditor.class);
    auditor.audit(eq(Action.AUTHENTICATION), eq(SOURCE_URI), eq(ResourceType.URI),
        eq(ActionOutcome.SUCCESS), EasyMock.capture(message));
    expectLastCall().once();
    EasyMock.replay(auditor);

    invokeContinue(auditor, subjectWithChain(Arrays.asList(
        actor("idp1", "svc-c"), actor("idp1", "svc-b"), actor("idp2", "svc-a"))));

    EasyMock.verify(auditor);
    assertEquals("act_chain=idp1/svc-c<-idp1/svc-b<-idp2/svc-a", message.getValue());
  }

  @Test
  public void testNonDelegatedTokenEmitsNoMessage() throws Exception {
    final Auditor auditor = EasyMock.createMock(Auditor.class);
    auditor.audit(eq(Action.AUTHENTICATION), eq(SOURCE_URI), eq(ResourceType.URI),
        eq(ActionOutcome.SUCCESS));
    expectLastCall().once();
    EasyMock.replay(auditor);

    invokeContinue(auditor, subjectWithChain(null));

    EasyMock.verify(auditor);
  }

  private void invokeContinue(Auditor auditor, Subject subject) throws Exception {
    final AuditContext context = EasyMock.createNiceMock(AuditContext.class);
    final AuditService auditService = EasyMock.createNiceMock(AuditService.class);
    EasyMock.expect(auditService.getContext()).andReturn(context).anyTimes();
    EasyMock.replay(context, auditService);

    auditorField().set(null, auditor);
    auditServiceField().set(null, auditService);

    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getAttribute(AbstractGatewayFilter.SOURCE_REQUEST_CONTEXT_URL_ATTRIBUTE_NAME))
        .andReturn(SOURCE_URI).anyTimes();
    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.replay(request, response);

    new TestHandler().invokeContinueWithEstablishedSecurityContext(subject, request, response,
        new NoOpFilterChain());
  }

  private static Subject subjectWithChain(List<Map<String, Object>> chain) {
    final Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal("alice"));
    if (chain != null) {
      subject.getPrincipals().add(new ActorChainPrincipalImpl(chain));
    }
    return subject;
  }

  private static Map<String, Object> actor(String iss, String sub) {
    final Map<String, Object> actor = new LinkedHashMap<>();
    actor.put("iss", iss);
    actor.put("sub", sub);
    return actor;
  }

  private static Field auditorField() throws Exception {
    final Field field = AbstractJWTFilter.class.getDeclaredField("auditor");
    field.setAccessible(true);
    return field;
  }

  private static Field auditServiceField() throws Exception {
    final Field field = AbstractJWTFilter.class.getDeclaredField("auditService");
    field.setAccessible(true);
    return field;
  }

  static final class TestHandler extends AbstractJWTFilter {
    void invokeContinueWithEstablishedSecurityContext(Subject subject, HttpServletRequest request,
        HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
      continueWithEstablishedSecurityContext(subject, request, response, chain);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
    }

    @Override
    protected void handleValidationError(HttpServletRequest request, HttpServletResponse response,
        int status, String error) {
    }

    @Override
    public void destroy() {
    }
  }

  static final class NoOpFilterChain implements FilterChain {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response) {
    }
  }
}
