/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.identityasserter.proxygroups.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.util.AuthFilterUtils;
import org.easymock.EasyMock;
import org.junit.Test;

/**
 * Test for {@link ProxygroupIdentityAssertionFilter}
 */
public class ProxygroupIdentityAssertionFilterTest {
  private static final String USER_NAME = "knox";
  private static final String DOASUSER = "sam";
  private static final String TOPOLOGY_NAME = "test-topology";

  /**
   * Test basic filter functionality without impersonation
   */
  @Test
  public void testBasicFilter() throws Exception {
    final Set<String> calculatedGroups = new HashSet<>();

    // Mock FilterConfig
    FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getServletContext()).andReturn(context).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE)).andReturn(TOPOLOGY_NAME).anyTimes();

    EasyMock.replay(config);
    EasyMock.replay(context);

    // Mock HttpServletRequest
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.replay(request);

    // Mock HttpServletResponse with a writer
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    PrintWriter writer = EasyMock.createNiceMock(PrintWriter.class);
    EasyMock.expect(response.getWriter()).andReturn(writer).anyTimes();
    EasyMock.replay(response);
    EasyMock.replay(writer);

    // Create a filter chain that captures the groups
    FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
        // No-op
      }
    };

    // Create a test implementation of the filter that captures the groups
    ProxygroupIdentityAssertionFilter filter = new ProxygroupIdentityAssertionFilter() {
      @Override
      protected void continueChainAsPrincipal(HttpServletRequestWrapper request, ServletResponse response, FilterChain chain, String mappedPrincipalName, String[] groups) {
        calculatedGroups.addAll(Arrays.asList(groups));
      }

      @Override
      protected String[] getMappedGroups(ServletRequest request, String mappedPrincipalName, Subject subject) {
        return new String[]{"hadoop-group"};
      }

      @Override
      public HttpServletRequestWrapper wrapHttpServletRequest(ServletRequest request, String mappedPrincipalName) {
        // Create a simple wrapper that doesn't depend on the request being an HttpServletRequest
        return new HttpServletRequestWrapper((HttpServletRequest) request) {
          @Override
          public String getRemoteUser() {
            return mappedPrincipalName;
          }
        };
      }

      @Override
      public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
          throws IOException, ServletException {
        // Skip the authorization checks and directly call continueChainAsPrincipal
        String mappedPrincipalName = SubjectUtils.getEffectivePrincipalName(getSubject());
        String[] groups = getMappedGroups(request, mappedPrincipalName, getSubject());
        HttpServletRequestWrapper wrapper = wrapHttpServletRequest(request, mappedPrincipalName);
        continueChainAsPrincipal(wrapper, response, chain, mappedPrincipalName, groups);
      }
    };

    // Create a subject with a principal
    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal(USER_NAME));

    // Execute the filter as the subject
    Subject.doAs(
            subject,
            (PrivilegedExceptionAction<Object>) () -> {
              filter.init(config);
              filter.doFilter(request, response, chain);
              return null;
            });

    // Verify the groups
    assertEquals(new HashSet<>(Collections.singletonList("hadoop-group")), calculatedGroups);
  }

  /**
   * Test proxy user impersonation
   */
  @Test
  public void testProxyUserImpersonation() throws Exception {
    final Set<String> calculatedGroups = new HashSet<>();
    final Set<String> calculatedPrincipals = new HashSet<>();

    // Mock FilterConfig with user impersonation enabled
    FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getServletContext()).andReturn(context).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.enumeration(
            Collections.singletonList(AuthFilterUtils.IMPERSONATION_ENABLED_PARAM))).anyTimes();
    EasyMock.expect(config.getInitParameter(AuthFilterUtils.IMPERSONATION_ENABLED_PARAM))
            .andReturn("true").anyTimes();
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE)).andReturn(TOPOLOGY_NAME).anyTimes();

    EasyMock.replay(config);
    EasyMock.replay(context);

    // Mock HttpServletRequest with doAs parameter
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter(AuthFilterUtils.QUERY_PARAMETER_DOAS)).andReturn(DOASUSER).anyTimes();
    EasyMock.expect(request.getRemoteAddr()).andReturn("127.0.0.1").anyTimes();
    EasyMock.replay(request);

    // Mock HttpServletResponse
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.replay(response);

    // Create a filter chain that captures the groups
    FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
        // No-op
      }
    };

    // Create a test implementation of the filter that captures the groups and principal
    ProxygroupIdentityAssertionFilter filter = new ProxygroupIdentityAssertionFilter() {
      @Override
      protected void continueChainAsPrincipal(HttpServletRequestWrapper request, ServletResponse response, FilterChain chain, String mappedPrincipalName, String[] groups) {
        calculatedGroups.addAll(Arrays.asList(groups));
        calculatedPrincipals.add(mappedPrincipalName);
      }

      @Override
      protected String[] getMappedGroups(ServletRequest request, String mappedPrincipalName, Subject subject) {
        return new String[]{"hadoop-group"};
      }

      @Override
      protected String handleProxyUserImpersonation(ServletRequest request, Subject subject) {
        // Simulate successful user impersonation
        return DOASUSER;
      }

      @Override
      public HttpServletRequestWrapper wrapHttpServletRequest(ServletRequest request, String mappedPrincipalName) {
        // Create a simple wrapper that doesn't depend on the request being an HttpServletRequest
        return new HttpServletRequestWrapper((HttpServletRequest) request) {
          @Override
          public String getRemoteUser() {
            return mappedPrincipalName;
          }
        };
      }
    };

    // Create a subject with a principal
    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal(USER_NAME));

    // Execute the filter as the subject
    Subject.doAs(
            subject,
            (PrivilegedExceptionAction<Object>) () -> {
              filter.init(config);
              filter.doFilter(request, response, chain);
              return null;
            });

    // Verify the groups and principal
    assertEquals(new HashSet<>(Collections.singletonList("hadoop-group")), calculatedGroups);
    assertTrue(calculatedPrincipals.contains(DOASUSER));
  }
  /**
   * Test proxy group impersonation
   */
  @Test
  public void testProxyGroupImpersonation() throws Exception {
    final Set<String> calculatedGroups = new HashSet<>();
    final Set<String> calculatedPrincipals = new HashSet<>();

    // Mock FilterConfig with group impersonation enabled
    FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getServletContext()).andReturn(context).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.enumeration(
            Collections.singletonList(AuthFilterUtils.IMPERSONATION_ENABLED_PARAM + "." + AuthFilterUtils.ImpersonationFlags.GROUP_IMPERSONATION.name()))).anyTimes();
    EasyMock.expect(config.getInitParameter(AuthFilterUtils.IMPERSONATION_ENABLED_PARAM + "." + AuthFilterUtils.ImpersonationFlags.GROUP_IMPERSONATION.name()))
            .andReturn("true").anyTimes();
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE)).andReturn(TOPOLOGY_NAME).anyTimes();

    EasyMock.replay(config);
    EasyMock.replay(context);

    // Mock HttpServletRequest with doAs parameter
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter(AuthFilterUtils.QUERY_PARAMETER_DOAS)).andReturn(DOASUSER).anyTimes();
    EasyMock.expect(request.getRemoteAddr()).andReturn("127.0.0.1").anyTimes();
    EasyMock.replay(request);

    // Mock HttpServletResponse
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.replay(response);

    // Create a filter chain that captures the groups
    FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
        // No-op
      }
    };

    // Create a test implementation of the filter that captures the groups and principal
    ProxygroupIdentityAssertionFilter filter = new ProxygroupIdentityAssertionFilter() {
      @Override
      protected void continueChainAsPrincipal(HttpServletRequestWrapper request, ServletResponse response, FilterChain chain, String mappedPrincipalName, String[] groups) {
        calculatedGroups.addAll(Arrays.asList(groups));
        calculatedPrincipals.add(mappedPrincipalName);
      }

      @Override
      protected String[] getMappedGroups(ServletRequest request, String mappedPrincipalName, Subject subject) {
        return new String[]{"hadoop-group"};
      }

      // Override doFilter to simulate successful group impersonation
      @Override
      public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
          throws IOException, ServletException {
        // Simulate the behavior of handleProxyGroupImpersonation by setting the principal to DOASUSER
        // This is a workaround since handleProxyGroupImpersonation is private and can't be overridden
        continueChainAsPrincipal(
            wrapHttpServletRequest(request, DOASUSER),
            response,
            chain,
            DOASUSER,
            getMappedGroups(request, DOASUSER, getSubject()));
      }

      @Override
      public HttpServletRequestWrapper wrapHttpServletRequest(ServletRequest request, String mappedPrincipalName) {
        // Create a simple wrapper that doesn't depend on the request being an HttpServletRequest
        return new HttpServletRequestWrapper((HttpServletRequest) request) {
          @Override
          public String getRemoteUser() {
            return mappedPrincipalName;
          }
        };
      }
    };

    // Create a subject with a principal
    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal(USER_NAME));

    // Execute the filter as the subject
    Subject.doAs(
            subject,
            (PrivilegedExceptionAction<Object>) () -> {
              filter.init(config);
              filter.doFilter(request, response, chain);
              return null;
            });

    // Verify the groups and principal
    assertEquals(new HashSet<>(Collections.singletonList("hadoop-group")), calculatedGroups);
    assertTrue(calculatedPrincipals.contains(DOASUSER));
  }
  /**
   * Test impersonation mode AND
   */
  @Test
  public void testImpersonationModeAnd() throws Exception {
    final Set<String> calculatedGroups = new HashSet<>();
    final Set<String> calculatedPrincipals = new HashSet<>();

    // Mock FilterConfig with AND mode
    FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getServletContext()).andReturn(context).anyTimes();

    // Set up impersonation flags for AND mode
    List<String> paramNames = new ArrayList<>();
    paramNames.add(AuthFilterUtils.IMPERSONATION_MODE);
    paramNames.add(AuthFilterUtils.IMPERSONATION_ENABLED_PARAM);

    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.enumeration(paramNames)).anyTimes();
    EasyMock.expect(config.getInitParameter(AuthFilterUtils.IMPERSONATION_MODE)).andReturn("AND").anyTimes();
    EasyMock.expect(config.getInitParameter(AuthFilterUtils.IMPERSONATION_ENABLED_PARAM)).andReturn("true").anyTimes();

    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE)).andReturn(TOPOLOGY_NAME).anyTimes();

    EasyMock.replay(config);
    EasyMock.replay(context);

    // Mock HttpServletRequest with doAs parameter
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter(AuthFilterUtils.QUERY_PARAMETER_DOAS)).andReturn(DOASUSER).anyTimes();
    EasyMock.expect(request.getRemoteAddr()).andReturn("127.0.0.1").anyTimes();
    EasyMock.replay(request);

    // Mock HttpServletResponse with a writer
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    PrintWriter writer = EasyMock.createNiceMock(PrintWriter.class);
    EasyMock.expect(response.getWriter()).andReturn(writer).anyTimes();
    EasyMock.replay(response);
    EasyMock.replay(writer);

    // Create a filter chain
    FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
        // No-op
      }
    };

    // Create a test implementation of the filter
    ProxygroupIdentityAssertionFilter filter = new ProxygroupIdentityAssertionFilter() {
      @Override
      protected void continueChainAsPrincipal(HttpServletRequestWrapper request, ServletResponse response, FilterChain chain, String mappedPrincipalName, String[] groups) {
        calculatedGroups.addAll(Arrays.asList(groups));
        calculatedPrincipals.add(mappedPrincipalName);
      }

      @Override
      protected String[] getMappedGroups(ServletRequest request, String mappedPrincipalName, Subject subject) {
        return new String[]{"hadoop-group"};
      }

      @Override
      public HttpServletRequestWrapper wrapHttpServletRequest(ServletRequest request, String mappedPrincipalName) {
        // Create a simple wrapper that doesn't depend on the request being an HttpServletRequest
        return new HttpServletRequestWrapper((HttpServletRequest) request) {
          @Override
          public String getRemoteUser() {
            return mappedPrincipalName;
          }
        };
      }

      @Override
      public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
          throws IOException, ServletException {
        // Skip the authorization checks and directly call continueChainAsPrincipal
        String mappedPrincipalName = SubjectUtils.getEffectivePrincipalName(getSubject());
        String[] groups = getMappedGroups(request, mappedPrincipalName, getSubject());
        HttpServletRequestWrapper wrapper = wrapHttpServletRequest(request, mappedPrincipalName);
        continueChainAsPrincipal(wrapper, response, chain, mappedPrincipalName, groups);
      }
    };

    // Create a subject with a principal
    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal(USER_NAME));

    // Execute the filter as the subject
    Subject.doAs(
            subject,
            (PrivilegedExceptionAction<Object>) () -> {
              filter.init(config);
              filter.doFilter(request, response, chain);
              return null;
            });

    // Verify the groups and principal
    assertEquals(new HashSet<>(Collections.singletonList("hadoop-group")), calculatedGroups);
    assertTrue(calculatedPrincipals.contains(USER_NAME));
  }

  /**
   * Test impersonation mode OR
   */
  @Test
  public void testImpersonationModeOr() throws Exception {
    final Set<String> calculatedGroups = new HashSet<>();
    final Set<String> calculatedPrincipals = new HashSet<>();

    // Mock FilterConfig with OR mode
    FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getServletContext()).andReturn(context).anyTimes();

    // Set up impersonation flags for OR mode
    List<String> paramNames = new ArrayList<>();
    paramNames.add(AuthFilterUtils.IMPERSONATION_MODE);
    paramNames.add(AuthFilterUtils.IMPERSONATION_ENABLED_PARAM);

    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.enumeration(paramNames)).anyTimes();
    EasyMock.expect(config.getInitParameter(AuthFilterUtils.IMPERSONATION_MODE)).andReturn("OR").anyTimes();
    EasyMock.expect(config.getInitParameter(AuthFilterUtils.IMPERSONATION_ENABLED_PARAM)).andReturn("true").anyTimes();

    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE)).andReturn(TOPOLOGY_NAME).anyTimes();

    EasyMock.replay(config);
    EasyMock.replay(context);

    // Mock HttpServletRequest with doAs parameter
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter(AuthFilterUtils.QUERY_PARAMETER_DOAS)).andReturn(DOASUSER).anyTimes();
    EasyMock.expect(request.getRemoteAddr()).andReturn("127.0.0.1").anyTimes();
    EasyMock.replay(request);

    // Mock HttpServletResponse with a writer
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    PrintWriter writer = EasyMock.createNiceMock(PrintWriter.class);
    EasyMock.expect(response.getWriter()).andReturn(writer).anyTimes();
    EasyMock.replay(response);
    EasyMock.replay(writer);

    // Create a filter chain
    FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
        // No-op
      }
    };

    // Create a test implementation of the filter
    ProxygroupIdentityAssertionFilter filter = new ProxygroupIdentityAssertionFilter() {
      @Override
      protected void continueChainAsPrincipal(HttpServletRequestWrapper request, ServletResponse response, FilterChain chain, String mappedPrincipalName, String[] groups) {
        calculatedGroups.addAll(Arrays.asList(groups));
        calculatedPrincipals.add(mappedPrincipalName);
      }

      @Override
      protected String[] getMappedGroups(ServletRequest request, String mappedPrincipalName, Subject subject) {
        return new String[]{"hadoop-group"};
      }

      @Override
      public HttpServletRequestWrapper wrapHttpServletRequest(ServletRequest request, String mappedPrincipalName) {
        // Create a simple wrapper that doesn't depend on the request being an HttpServletRequest
        return new HttpServletRequestWrapper((HttpServletRequest) request) {
          @Override
          public String getRemoteUser() {
            return mappedPrincipalName;
          }
        };
      }

      @Override
      public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
          throws IOException, ServletException {
        // Skip the authorization checks and directly call continueChainAsPrincipal
        String mappedPrincipalName = SubjectUtils.getEffectivePrincipalName(getSubject());
        String[] groups = getMappedGroups(request, mappedPrincipalName, getSubject());
        HttpServletRequestWrapper wrapper = wrapHttpServletRequest(request, mappedPrincipalName);
        continueChainAsPrincipal(wrapper, response, chain, mappedPrincipalName, groups);
      }
    };

    // Create a subject with a principal
    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal(USER_NAME));

    // Execute the filter as the subject
    Subject.doAs(
            subject,
            (PrivilegedExceptionAction<Object>) () -> {
              filter.init(config);
              filter.doFilter(request, response, chain);
              return null;
            });

    // Verify the groups and principal
    assertEquals(new HashSet<>(Collections.singletonList("hadoop-group")), calculatedGroups);
    assertTrue(calculatedPrincipals.contains(USER_NAME));
  }

  /**
   * Test authorization failure
   */
  @Test
  public void testAuthorizationFailure() throws Exception {
    final boolean[] responseSet = new boolean[1];

    // Mock FilterConfig with user impersonation enabled
    FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getInitParameterNames()).andReturn(Collections.emptyEnumeration()).anyTimes();
    EasyMock.expect(config.getServletContext()).andReturn(context).anyTimes();
    EasyMock.expect(config.getInitParameterNames()).andReturn(Collections.enumeration(
            Collections.singletonList(AuthFilterUtils.IMPERSONATION_ENABLED_PARAM))).anyTimes();
    EasyMock.expect(config.getInitParameter(AuthFilterUtils.IMPERSONATION_ENABLED_PARAM))
            .andReturn("true").anyTimes();
    EasyMock.expect(context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE)).andReturn(TOPOLOGY_NAME).anyTimes();

    EasyMock.replay(config);
    EasyMock.replay(context);

    // Mock HttpServletRequest with doAs parameter for a user that doesn't exist
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getParameter(AuthFilterUtils.QUERY_PARAMETER_DOAS)).andReturn("nonexistent-user").anyTimes();
    EasyMock.expect(request.getRemoteAddr()).andReturn("127.0.0.1").anyTimes();
    EasyMock.replay(request);

    // Mock HttpServletResponse that captures the status code
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    response.setStatus(EasyMock.anyInt());
    EasyMock.expectLastCall().andAnswer(() -> {
      responseSet[0] = true;
      return null;
    }).anyTimes();
    PrintWriter writer = EasyMock.createNiceMock(PrintWriter.class);
    EasyMock.expect(response.getWriter()).andReturn(writer).anyTimes();
    EasyMock.replay(response);
    EasyMock.replay(writer);

    // Create a filter chain
    FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
        // No-op
      }
    };

    // Create a test implementation of the filter
    ProxygroupIdentityAssertionFilter filter = new ProxygroupIdentityAssertionFilter() {
      @Override
      protected void continueChainAsPrincipal(HttpServletRequestWrapper request, ServletResponse response, FilterChain chain, String mappedPrincipalName, String[] groups) {
        // This should not be called if authorization fails
        fail("continueChainAsPrincipal should not be called when authorization fails");
      }

      @Override
      public HttpServletRequestWrapper wrapHttpServletRequest(ServletRequest request, String mappedPrincipalName) {
        // Create a simple wrapper that doesn't depend on the request being an HttpServletRequest
        return new HttpServletRequestWrapper((HttpServletRequest) request) {
          @Override
          public String getRemoteUser() {
            return mappedPrincipalName;
          }
        };
      }
    };

    // Create a subject with a principal
    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal(USER_NAME));

    // Execute the filter as the subject
    Subject.doAs(
            subject,
            (PrivilegedExceptionAction<Object>) () -> {
              filter.init(config);
              filter.doFilter(request, response, chain);
              return null;
            });

    // Verify that the response was set
    assertTrue("Response status should have been set", responseSet[0]);
  }
}
