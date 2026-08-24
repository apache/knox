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
package org.apache.knox.gateway.identityasserter.common.filter;

import org.apache.knox.gateway.audit.log4j.audit.Log4jAuditService;
import org.apache.knox.gateway.context.ContextAttributes;
import org.apache.knox.gateway.security.ActorChainPrincipal;
import org.apache.knox.gateway.security.ActorChainPrincipalImpl;
import org.apache.knox.gateway.security.ImpersonatedPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.security.TokenExchangePrincipalImpl;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.logging.log4j.ThreadContext;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.Subject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.PrivilegedExceptionAction;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Regression tests for the RFC 8693 token-exchange processing pipeline:
 * {@link AbstractIdentityAssertionFilter#continueChainAsPrincipal} handling of
 * {@code TokenExchangePrincipal} (TEP) and {@code ActorChainPrincipal}.
 *
 * <p>Each test constructs a Subject directly (bypassing the JWT filter) and runs it through
 * a minimal anonymous subclass of {@link CommonIdentityAssertionFilter} with identity
 * {@code mapUserPrincipal} (returns input unchanged) and null {@code mapGroupPrincipals}
 * (no group mapping). A {@link SubjectCapturingChain} captures the Subject visible to
 * downstream filters inside whatever doAs context is active at chain invocation time.
 *
 * <p>Abbreviations used: AIAF for AbstractIdentityAssertionFilter and
 * TEP for TokenExchangePrincipal.
 *
 */
public class AbstractIdentityAssertionFilterTokenExchangeTest {

  private CommonIdentityAssertionFilter filter;
  private FilterConfig filterConfig;

  @Before
  public void setUp() throws Exception {
    filter = new CommonIdentityAssertionFilter() {
      @Override
      public String mapUserPrincipal(String principalName) {
        return principalName;
      }

      @Override
      public String[] mapGroupPrincipals(String name, Subject subject,
          ServletRequest request) {
        return null;
      }
    };

    ServletContext ctx = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(ctx.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE))
        .andReturn("test-topology").anyTimes();
    ctx.setAttribute(
        EasyMock.eq(ContextAttributes.IMPERSONATION_ENABLED_ATTRIBUTE),
        EasyMock.anyObject());
    EasyMock.expectLastCall().anyTimes();
    EasyMock.replay(ctx);

    filterConfig = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(filterConfig.getServletContext()).andReturn(ctx).anyTimes();
    EasyMock.expect(filterConfig.getInitParameter(
        CommonIdentityAssertionFilter.PRINCIPAL_MAPPING)).andReturn(null).anyTimes();
    EasyMock.expect(filterConfig.getInitParameter(
        CommonIdentityAssertionFilter.GROUP_PRINCIPAL_MAPPING)).andReturn(null).anyTimes();
    EasyMock.expect(filterConfig.getInitParameter(
        CommonIdentityAssertionFilter.ADVANCED_PRINCIPAL_MAPPING))
        .andReturn("username").anyTimes();
    EasyMock.expect(filterConfig.getInitParameterNames())
        .andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.replay(filterConfig);

    filter.init(filterConfig);
    ThreadContext.put(Log4jAuditService.MDC_AUDIT_CONTEXT_KEY, "dummy");
  }

  /**
   * When TEP identifies different actor and subject, AIAF creates a new doAs Subject with an
   * ImpersonatedPrincipal set to the subject identity and PrimaryPrincipal preserved as the actor.
   */
  @Test
  public void testTEPWithDifferentActorAndSubjectSetsUpImpersonation() throws Exception {
    Subject subject = buildSubject(
        new PrimaryPrincipal("sa-actor"),
        new TokenExchangePrincipalImpl("end-user", null, "sa-actor", null));

    SubjectCapturingChain chain = runFilterWithSubject(subject);

    Assert.assertTrue("chain should have been called", chain.called);
    Set<ImpersonatedPrincipal> impersonated = chain.subject.getPrincipals(ImpersonatedPrincipal.class);
    Assert.assertEquals("Expected exactly one ImpersonatedPrincipal", 1, impersonated.size());
    Assert.assertEquals("ImpersonatedPrincipal should be end-user", "end-user",
        impersonated.iterator().next().getName());
    Set<PrimaryPrincipal> primary = chain.subject.getPrincipals(PrimaryPrincipal.class);
    Assert.assertEquals("Expected exactly one PrimaryPrincipal", 1, primary.size());
    Assert.assertEquals("PrimaryPrincipal should be sa-actor", "sa-actor",
        primary.iterator().next().getName());
  }

  /**
   * When TEP actor and subject are the same identity, no impersonation is needed and AIAF
   * proceeds without adding an ImpersonatedPrincipal to the downstream Subject.
   */
  @Test
  public void testTEPWithSameActorAndSubjectSkipsImpersonation() throws Exception {
    Subject subject = buildSubject(
        new PrimaryPrincipal("alice"),
        new TokenExchangePrincipalImpl("alice", null, "alice", null));

    SubjectCapturingChain chain = runFilterWithSubject(subject);

    Assert.assertTrue("chain should have been called", chain.called);
    Assert.assertTrue("ImpersonatedPrincipal set should be empty",
        chain.subject.getPrincipals(ImpersonatedPrincipal.class).isEmpty());
  }

  /**
   * When no TEP is present, AIAF proceeds normally without creating an ImpersonatedPrincipal
   * and the downstream Subject contains no TokenExchangePrincipal.
   */
  @Test
  public void testNoTEPProceedsNormally() throws Exception {
    Subject subject = buildSubject(new PrimaryPrincipal("alice"));

    SubjectCapturingChain chain = runFilterWithSubject(subject);

    Assert.assertTrue("chain should have been called", chain.called);
    Assert.assertTrue("ImpersonatedPrincipal set should be empty",
        chain.subject.getPrincipals(ImpersonatedPrincipal.class).isEmpty());
    Assert.assertNull("No TokenExchangePrincipal expected",
        SubjectUtils.getTokenExchangePrincipal(chain.subject));
  }

  /**
   * Principal mapping is applied to the subject identity from TEP (not to the actor identity).
   * AIAF calls {@code mapUserPrincipal} on {@code tep.getSubjectPrincipalName()} and uses the
   * mapped result as the ImpersonatedPrincipal; the actor (PrimaryPrincipal) is unchanged.
   */
  @Test
  public void testTEPAppliesPrincipalMappingToSubjectNotActor() throws Exception {
    CommonIdentityAssertionFilter mappingFilter = new CommonIdentityAssertionFilter() {
      @Override
      public String mapUserPrincipal(String principalName) {
        return "user@external".equals(principalName) ? "localuser" : principalName;
      }

      @Override
      public String[] mapGroupPrincipals(String name, Subject subject,
          ServletRequest request) {
        return null;
      }
    };
    mappingFilter.init(filterConfig);

    Subject subject = buildSubject(
        new PrimaryPrincipal("sa-actor"),
        new TokenExchangePrincipalImpl("user@external", null, "sa-actor", null));

    SubjectCapturingChain chain = runFilterWithSubject(subject, mappingFilter);

    Set<ImpersonatedPrincipal> impersonated = chain.subject.getPrincipals(ImpersonatedPrincipal.class);
    Assert.assertEquals("Expected exactly one ImpersonatedPrincipal", 1, impersonated.size());
    Assert.assertEquals("ImpersonatedPrincipal should be mapped value", "localuser",
        impersonated.iterator().next().getName());
    Set<PrimaryPrincipal> primary = chain.subject.getPrincipals(PrimaryPrincipal.class);
    Assert.assertEquals("Expected exactly one PrimaryPrincipal", 1, primary.size());
    Assert.assertEquals("PrimaryPrincipal should be actor (unmapped)", "sa-actor",
        primary.iterator().next().getName());
  }

  /**
   * The TokenExchangePrincipal is preserved in the new doAs Subject built by AIAF when
   * impersonation is needed, so downstream filters can still read the delegation metadata.
   */
  @Test
  public void testTEPPreservedInDoAsSubject() throws Exception {
    Subject subject = buildSubject(
        new PrimaryPrincipal("sa-actor"),
        new TokenExchangePrincipalImpl("end-user", null, "sa-actor", null));

    SubjectCapturingChain chain = runFilterWithSubject(subject);

    Assert.assertNotNull("TokenExchangePrincipal should be preserved in downstream Subject",
        SubjectUtils.getTokenExchangePrincipal(chain.subject));
  }

  /**
   * The ActorChainPrincipal is preserved in the new doAs Subject built by AIAF when
   * impersonation is needed, so the full delegation chain history is available downstream.
   */
  @Test
  public void testActorChainPrincipalPreservedInDoAsSubject() throws Exception {
    List<Map<String, Object>> chain = List.of(Map.of("sub", "prior-actor"));
    Subject subject = buildSubject(
        new PrimaryPrincipal("sa-actor"),
        new TokenExchangePrincipalImpl("end-user", null, "sa-actor", null),
        new ActorChainPrincipalImpl(chain));

    SubjectCapturingChain capturingChain = runFilterWithSubject(subject);

    Set<ActorChainPrincipal> actorChainPrincipals =
        capturingChain.subject.getPrincipals(ActorChainPrincipal.class);
    Assert.assertFalse("ActorChainPrincipal should be preserved", actorChainPrincipals.isEmpty());
    Assert.assertEquals("getCurrentActor should be prior-actor", "prior-actor",
        actorChainPrincipals.iterator().next().getCurrentActor());
  }

  // ---- Helpers ----

  private static Subject buildSubject(java.security.Principal... principals) {
    Subject s = new Subject();
    for (java.security.Principal p : principals) {
      s.getPrincipals().add(p);
    }
    return s;
  }

  /** Runs the filter inside {@code Subject.doAs(subjectToRun, ...)} using the default filter. */
  private SubjectCapturingChain runFilterWithSubject(Subject subjectToRun) throws Exception {
    return runFilterWithSubject(subjectToRun, filter);
  }

  /** Runs the filter inside {@code Subject.doAs(subjectToRun, ...)} using the given filter. */
  private SubjectCapturingChain runFilterWithSubject(Subject subjectToRun,
      CommonIdentityAssertionFilter f) throws Exception {
    SubjectCapturingChain chain = new SubjectCapturingChain();
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.replay(request, response);
    Subject.doAs(subjectToRun, (PrivilegedExceptionAction<Void>) () -> {
      f.doFilter(request, response, chain);
      return null;
    });
    return chain;
  }

  private static class SubjectCapturingChain implements FilterChain {
    Subject subject;
    boolean called;

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp) {
      called = true;
      subject = SubjectUtils.getCurrentSubject();
    }
  }
}
