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
import org.apache.knox.gateway.security.AuthTokenCredential;
import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.logging.log4j.ThreadContext;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.security.auth.Subject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.security.PrivilegedExceptionAction;
import java.util.Collections;

/**
 * Covers the propagation of {@link AuthTokenCredential} through identity assertion.
 *
 * <p>{@code AbstractIdentityAssertionFilter.continueChainAsPrincipal} builds a brand new Subject
 * and hand-copies selected principals into it whenever impersonation or group mapping applies --
 * which is the common case in real topologies, not an edge case. Anything it does not copy
 * explicitly is dropped, so the caller's captured token needs its own copy. These tests pin that:
 * lose them and KNOX-AUTH-SERVICE silently stops emitting the token for any topology that maps
 * groups or impersonates, while every other test keeps passing.
 */
public class AbstractIdentityAssertionFilterAuthTokenTest {

  private static final String TOKEN = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhbGljZSJ9.c2lnbmF0dXJl";

  private ServletContext ctx;

  @Before
  public void setUp() {
    ctx = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(ctx.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE))
        .andReturn("test-topology").anyTimes();
    ctx.setAttribute(
        EasyMock.eq(ContextAttributes.IMPERSONATION_ENABLED_ATTRIBUTE),
        EasyMock.anyObject());
    EasyMock.expectLastCall().anyTimes();
    EasyMock.replay(ctx);
    ThreadContext.put(Log4jAuditService.MDC_AUDIT_CONTEXT_KEY, "dummy");
  }

  @After
  public void tearDown() {
    // The audit MDC is thread local and surefire reuses the thread, so leaving it set would leak
    // this class's dummy audit context into every other test class in the module.
    ThreadContext.remove(Log4jAuditService.MDC_AUDIT_CONTEXT_KEY);
  }

  /**
   * Impersonation re-wraps the Subject: the token must come along.
   */
  @Test
  public void testAuthTokenSurvivesImpersonation() throws Exception {
    final CommonIdentityAssertionFilter filter = newFilter("mapped-alice", null);

    final SubjectCapturingChain chain = runFilterWithSubject(filter, buildSubject());

    Assert.assertTrue("chain should have been called", chain.called);
    Assert.assertEquals("The auth token should survive the impersonation re-wrap",
        TOKEN, SubjectUtils.getAuthToken(chain.subject));
  }

  /**
   * Mapped groups re-wrap the Subject too -- the far more common trigger of the two.
   */
  @Test
  public void testAuthTokenSurvivesGroupMapping() throws Exception {
    final CommonIdentityAssertionFilter filter = newFilter(null, new String[] { "analysts" });

    final SubjectCapturingChain chain = runFilterWithSubject(filter, buildSubject());

    Assert.assertTrue("chain should have been called", chain.called);
    Assert.assertFalse("groups should have been mapped",
        chain.subject.getPrincipals(GroupPrincipal.class).isEmpty());
    Assert.assertEquals("The auth token should survive the group-mapping re-wrap",
        TOKEN, SubjectUtils.getAuthToken(chain.subject));
  }

  /**
   * Existing group principals on the incoming Subject also trigger the re-wrap.
   */
  @Test
  public void testAuthTokenSurvivesWhenSubjectAlreadyCarriesGroups() throws Exception {
    final CommonIdentityAssertionFilter filter = newFilter(null, null);

    final SubjectCapturingChain chain =
        runFilterWithSubject(filter, buildSubject(new GroupPrincipal("analysts")));

    Assert.assertTrue("chain should have been called", chain.called);
    Assert.assertEquals("The auth token should survive the re-wrap",
        TOKEN, SubjectUtils.getAuthToken(chain.subject));
  }

  /**
   * With neither impersonation nor groups the filter reuses the ambient Subject rather than
   * building a new one, so the token is still there and no copying is needed. Reaching that branch
   * takes a bare {@link AbstractIdentityAssertionFilter}: {@link CommonIdentityAssertionFilter}
   * always hands {@code continueChainAsPrincipal} a non-null groups array, so for it the re-wrap
   * is unconditional.
   */
  @Test
  public void testAuthTokenPresentWhenSubjectIsNotReWrapped() throws Exception {
    final SubjectCapturingChain chain = new SubjectCapturingChain();
    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.replay(request, response);

    final PassThroughAssertionFilter filter = new PassThroughAssertionFilter();
    final Subject ambientSubject = buildSubject();
    Subject.doAs(ambientSubject, (PrivilegedExceptionAction<Void>) () -> {
      filter.assertIdentity(new HttpServletRequestWrapper(request), response, chain);
      return null;
    });

    Assert.assertTrue("chain should have been called", chain.called);
    // Identity, not equality: the point of this test is that this branch does NOT build a new
    // Subject. Asserting only that the token is present would pass on the re-wrap path too, now
    // that the re-wrap copies the credential, and so would prove nothing about which branch ran.
    Assert.assertSame("The ambient Subject should be reused rather than re-wrapped",
        ambientSubject, chain.subject);
    Assert.assertEquals("The auth token should be visible on the un-wrapped subject",
        TOKEN, SubjectUtils.getAuthToken(chain.subject));
  }

  // ---- Helpers ----

  /**
   * @param mappedPrincipalName the name to map the caller to, or null for no mapping
   * @param mappedGroups        the groups to map, or null for none
   * @return an initialized filter with the given mapping behaviour
   */
  private CommonIdentityAssertionFilter newFilter(final String mappedPrincipalName,
                                                  final String[] mappedGroups) throws Exception {
    final CommonIdentityAssertionFilter filter = new CommonIdentityAssertionFilter() {
      @Override
      public String mapUserPrincipal(String principalName) {
        return mappedPrincipalName == null ? principalName : mappedPrincipalName;
      }

      @Override
      public String[] mapGroupPrincipals(String name, Subject subject, ServletRequest request) {
        return mappedGroups == null ? null : mappedGroups.clone();
      }
    };

    final FilterConfig filterConfig = EasyMock.createNiceMock(FilterConfig.class);
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
    return filter;
  }

  /** A Subject for "alice" carrying the auth token as a private credential. */
  private static Subject buildSubject(final Principal... extraPrincipals) {
    final Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal("alice"));
    for (Principal p : extraPrincipals) {
      subject.getPrincipals().add(p);
    }
    subject.getPrivateCredentials().add(new AuthTokenCredential(TOKEN));
    return subject;
  }

  private static SubjectCapturingChain runFilterWithSubject(final CommonIdentityAssertionFilter filter,
                                                            final Subject subjectToRun) throws Exception {
    final SubjectCapturingChain chain = new SubjectCapturingChain();
    final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.replay(request, response);
    Subject.doAs(subjectToRun, (PrivilegedExceptionAction<Void>) () -> {
      filter.doFilter(request, response, chain);
      return null;
    });
    return chain;
  }

  /**
   * The minimal identity asserter: no mapping, no groups, so
   * {@code continueChainAsPrincipal} takes its no-re-wrap branch.
   */
  private static class PassThroughAssertionFilter extends AbstractIdentityAssertionFilter {
    @Override
    public String[] mapGroupPrincipals(String mappedPrincipalName, Subject subject, ServletRequest request) {
      return null;
    }

    @Override
    public String mapUserPrincipal(String principalName) {
      return principalName;
    }

    void assertIdentity(HttpServletRequestWrapper request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
      continueChainAsPrincipal(request, response, chain, "alice", null);
    }
  }

  private static class SubjectCapturingChain implements FilterChain {
    private Subject subject;
    private boolean called;

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp) {
      called = true;
      subject = SubjectUtils.getCurrentSubject();
    }
  }
}
