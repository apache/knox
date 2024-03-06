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
package org.apache.knox.gateway.filter;

import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import javax.security.auth.Subject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class PathAclsAuthzFilterTest {
  private AtomicBoolean accessGranted;
  private Filter filter;

  @Before
  public void setUp() {
    filter = new PathAclsAuthorizationFilter() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response,
          FilterChain chain) throws IOException, ServletException {
        boolean accessGranted = enforceAclAuthorizationPolicy(request);
        if (accessGranted) {
          chain.doFilter(request, response);
        }
      }

      @Override
      protected boolean enforceAclAuthorizationPolicy(ServletRequest request)
          throws IOException {
        accessGranted = new AtomicBoolean(
            super.enforceAclAuthorizationPolicy(request));
        return accessGranted.get();
      }
    };
  }

  @After
  public void tearDown() {
    accessGranted = null;
    filter = null;
  }

  @Test
  public void testValidPathAcls() throws ServletException, IOException {
    FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(config.getInitParameterNames()).andReturn(
            Collections.enumeration(
                Arrays.asList("knox.admin.users", "knox.admin.groups",
                    "resource.role", "knox.acl.mode", "knox.rule_group.path.acl")))
        .anyTimes();
    EasyMock.expect(config.getInitParameter("knox.admin.users"))
        .andReturn(null);
    EasyMock.expect(config.getInitParameter("knox.admin.groups"))
        .andReturn("admin");
    EasyMock.expect(config.getInitParameter("resource.role")).andReturn("KNOX");
    EasyMock.expect(config.getInitParameter("knox.path.acl.mode"))
        .andReturn("OR");
    EasyMock.expect(config.getInitParameter("knox.rule_group.path.acl"))
        .andReturn("https://*:*/foo/group/*;*;KNOX_ADMIN_GROUPS;*");
    EasyMock.replay(config);

    final HttpServletRequest request = EasyMock.createNiceMock(
        HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL())
        .andReturn(new StringBuffer("https://example.com/foo/group/bar"));
    EasyMock.expect(((HttpServletRequest) request).getQueryString())
        .andReturn("foz=baz");
    EasyMock.expect(((HttpServletRequest) request).getRemoteAddr())
        .andReturn("127.1.97.24").anyTimes();

    EasyMock.replay(request);

    final HttpServletResponse response = EasyMock.createNiceMock(
        HttpServletResponse.class);
    EasyMock.replay(response);

    final FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response)
          throws IOException, ServletException {
      }
    };

    filter.init(config);

    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal("more"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admin"));
    try {
      Subject.doAs(subject, new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          filter.doFilter(request, response, chain);
          return null;
        }
      });
    } catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      } else if (t instanceof ServletException) {
        throw (ServletException) t;
      } else {
        throw new ServletException(t);
      }
    }
    assertTrue(accessGranted.get());
  }

  /**
   * Test to make sure `path.acl` property is applied to ALL services
   */
  @Test
  public void testDefaultPathAcls() throws ServletException, IOException {
    FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(config.getInitParameterNames()).andReturn(
        Collections.enumeration(
            Arrays.asList("knox.admin.users", "knox.admin.groups",
                "resource.role", "knox.acl.mode"))).anyTimes();
    EasyMock.expect(config.getInitParameter("knox.admin.users"))
        .andReturn(null);
    EasyMock.expect(config.getInitParameter("knox.admin.groups"))
        .andReturn("admin");
    EasyMock.expect(config.getInitParameter("resource.role")).andReturn("KNOX");
    EasyMock.expect(config.getInitParameter("knox.path.acl.mode"))
        .andReturn("OR");
    EasyMock.expect(config.getInitParameter("path.acl"))
        .andReturn("https://*:*/foo/group/*;*;KNOX_ADMIN_GROUPS;*");
    EasyMock.replay(config);

    final HttpServletRequest request = EasyMock.createNiceMock(
        HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL())
        .andReturn(new StringBuffer("https://example.com/foo/group/bar"));
    EasyMock.expect(((HttpServletRequest) request).getQueryString())
        .andReturn("foz=baz");
    EasyMock.expect(((HttpServletRequest) request).getRemoteAddr())
        .andReturn("127.1.97.24").anyTimes();

    EasyMock.replay(request);

    final HttpServletResponse response = EasyMock.createNiceMock(
        HttpServletResponse.class);
    EasyMock.replay(response);

    final FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response)
          throws IOException, ServletException {
      }
    };

    filter.init(config);

    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal("more"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admin"));
    try {
      Subject.doAs(subject, new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          filter.doFilter(request, response, chain);
          return null;
        }
      });
    } catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      } else if (t instanceof ServletException) {
        throw (ServletException) t;
      } else {
        throw new ServletException(t);
      }
    }
    assertTrue(accessGranted.get());
  }

  /**
   * Test to make sure `{service}.path.acl` property is applied only to provided
   * services. In this test we apply path ACLs to `test` service and use AND
   * mode. Access should be granted because knox service does not have any ALCs
   * defined.
   */
  @Test
  public void testDefaultResourcePathAcls()
      throws ServletException, IOException {
    FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(config.getInitParameterNames()).andReturn(
        Collections.enumeration(
            Arrays.asList("knox.admin.users", "knox.admin.groups",
                "resource.role", "test.acl.mode", "test.path.acl"))).anyTimes();
    EasyMock.expect(config.getInitParameter("knox.admin.users"))
        .andReturn(null);
    EasyMock.expect(config.getInitParameter("knox.admin.groups"))
        .andReturn("admin");
    EasyMock.expect(config.getInitParameter("resource.role")).andReturn("KNOX");
    EasyMock.expect(config.getInitParameter("test.path.acl.mode"))
        .andReturn("AND");
    EasyMock.expect(config.getInitParameter("test.path.acl"))
        .andReturn("https://*:*/foo/group/*;random_user;random_group;*");
    EasyMock.replay(config);

    final HttpServletRequest request = EasyMock.createNiceMock(
        HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL())
        .andReturn(new StringBuffer("https://example.com/foo/group/bar"));
    EasyMock.expect(((HttpServletRequest) request).getQueryString())
        .andReturn("foz=baz");
    EasyMock.expect(((HttpServletRequest) request).getRemoteAddr())
        .andReturn("127.1.97.24").anyTimes();

    EasyMock.replay(request);

    final HttpServletResponse response = EasyMock.createNiceMock(
        HttpServletResponse.class);
    EasyMock.replay(response);

    final FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response)
          throws IOException, ServletException {
      }
    };

    filter.init(config);

    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal("more"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admin"));
    try {
      Subject.doAs(subject, new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          filter.doFilter(request, response, chain);
          return null;
        }
      });
    } catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      } else if (t instanceof ServletException) {
        throw (ServletException) t;
      } else {
        throw new ServletException(t);
      }
    }
    assertTrue(accessGranted.get());
  }

  /**
   * Test to make sure AND acls work.
   */
  @Test
  public void testANDAclsMode() throws ServletException, IOException {
    FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(config.getInitParameterNames()).andReturn(
            Collections.enumeration(
                Arrays.asList("knox.admin.users", "knox.admin.groups",
                    "resource.role", "knox.acl.mode", "knox.rule_group.path.acl")))
        .anyTimes();
    EasyMock.expect(config.getInitParameter("knox.admin.users"))
        .andReturn(null);
    EasyMock.expect(config.getInitParameter("knox.admin.groups"))
        .andReturn("admin");
    EasyMock.expect(config.getInitParameter("resource.role")).andReturn("KNOX");
    EasyMock.expect(config.getInitParameter("knox.path.acl.mode"))
        .andReturn("AND");
    EasyMock.expect(config.getInitParameter("knox.rule_group.path.acl"))
        .andReturn(
            "https://*:*/foo/group/*;more;KNOX_ADMIN_GROUPS;127.1.97.24");
    EasyMock.replay(config);

    final HttpServletRequest request = EasyMock.createNiceMock(
        HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL())
        .andReturn(new StringBuffer("https://example.com/foo/group/bar"));
    EasyMock.expect(((HttpServletRequest) request).getQueryString())
        .andReturn("foz=baz");
    EasyMock.expect(((HttpServletRequest) request).getRemoteAddr())
        .andReturn("127.1.97.24").anyTimes();

    EasyMock.replay(request);

    final HttpServletResponse response = EasyMock.createNiceMock(
        HttpServletResponse.class);
    EasyMock.replay(response);

    final FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response)
          throws IOException, ServletException {
      }
    };

    filter.init(config);

    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal("more"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admin"));
    try {
      Subject.doAs(subject, new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          filter.doFilter(request, response, chain);
          return null;
        }
      });
    } catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      } else if (t instanceof ServletException) {
        throw (ServletException) t;
      } else {
        throw new ServletException(t);
      }
    }
    assertTrue(accessGranted.get());
  }

  /**
   * Test to make sure AND acls work (AND evaluates to false) Note: user is
   * `more` so user `knox` should not grant access
   */
  @Test
  public void testNegativeANDAclsMode() throws ServletException, IOException {

    final TestPathAclsAuthorizationFilter testFilter = new TestPathAclsAuthorizationFilter();

    final FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(config.getInitParameterNames()).andReturn(
        Collections.enumeration(
            Arrays.asList("knox.admin.users", "knox.admin.groups",
                "resource.role", "path.acl.mode",
                "knox.rule_group_1.path.acl"))).anyTimes();
    EasyMock.expect(config.getInitParameter("knox.admin.users"))
        .andReturn(null);
    EasyMock.expect(config.getInitParameter("knox.admin.groups"))
        .andReturn("admin");
    EasyMock.expect(config.getInitParameter("resource.role")).andReturn("KNOX");
    EasyMock.expect(config.getInitParameter("path.acl.mode")).andReturn("AND");
    /* Note: user is `more` so user `knox` should not grant access */
    EasyMock.expect(config.getInitParameter("knox.rule_group_1.path.acl"))
        .andReturn(
            "https://*:*/foo/group/*;knox;KNOX_ADMIN_GROUPS;127.1.97.24");
    EasyMock.replay(config);

    final HttpServletRequest request = EasyMock.createNiceMock(
        HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL())
        .andReturn(new StringBuffer("https://example.com/foo/group/bar"));
    EasyMock.expect(((HttpServletRequest) request).getQueryString())
        .andReturn("foz=baz");
    EasyMock.expect(((HttpServletRequest) request).getRemoteAddr())
        .andReturn("127.1.97.24").anyTimes();

    final HttpServletResponse response = EasyMock.createNiceMock(
        HttpServletResponse.class);
    EasyMock.replay(request, response);

    final FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response)
          throws IOException, ServletException {
      }
    };

    testFilter.init(config);

    final Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal("more"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admin"));
    try {
      Subject.doAs(subject, new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          testFilter.doFilter(request, response, chain);
          return null;
        }
      });
    } catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      } else if (t instanceof ServletException) {
        throw (ServletException) t;
      } else {
        throw new ServletException(t);
      }
    }

    assertFalse(testFilter.access.get());
  }

  /**
   * Test to make sure OR acls work. Note: user is `more` so user `knox` should
   * not match ACLs but ALCs mode is OR so access should be granted
   */
  @Test
  public void testORAclsMode() throws ServletException, IOException {
    FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(config.getInitParameterNames()).andReturn(
            Collections.enumeration(
                Arrays.asList("knox.admin.users", "knox.admin.groups",
                    "resource.role", "knox.acl.mode", "knox.rule_group.path.acl")))
        .anyTimes();
    EasyMock.expect(config.getInitParameter("knox.admin.users"))
        .andReturn(null);
    EasyMock.expect(config.getInitParameter("knox.admin.groups"))
        .andReturn("admin");
    EasyMock.expect(config.getInitParameter("resource.role")).andReturn("KNOX");
    EasyMock.expect(config.getInitParameter("knox.path.acl.mode"))
        .andReturn("OR");
    /* Note: user is `more` so user `knox` should not match ACLs but ALCs mode is OR so access should be granted  */
    EasyMock.expect(config.getInitParameter("knox.rule_group.path.acl"))
        .andReturn(
            "https://*:*/foo/group/*;knox;KNOX_ADMIN_GROUPS;127.1.97.24");
    EasyMock.replay(config);

    final HttpServletRequest request = EasyMock.createNiceMock(
        HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL())
        .andReturn(new StringBuffer("https://example.com/foo/group/bar"));
    EasyMock.expect(((HttpServletRequest) request).getQueryString())
        .andReturn("foz=baz");
    EasyMock.expect(((HttpServletRequest) request).getRemoteAddr())
        .andReturn("127.1.97.24").anyTimes();

    EasyMock.replay(request);

    final HttpServletResponse response = EasyMock.createNiceMock(
        HttpServletResponse.class);
    EasyMock.replay(response);

    final FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response)
          throws IOException, ServletException {
      }
    };

    filter.init(config);

    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal("more"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admin"));
    try {
      Subject.doAs(subject, new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          filter.doFilter(request, response, chain);
          return null;
        }
      });
    } catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      } else if (t instanceof ServletException) {
        throw (ServletException) t;
      } else {
        throw new ServletException(t);
      }
    }
    assertTrue(accessGranted.get());
  }

  /**
   * THest the case where multiple paths are defined with different ACL
   * conditions
   *
   * @throws ServletException
   * @throws IOException
   */
  @Test
  public void testMultiplePathPathAcls_1()
      throws ServletException, IOException {
    FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(config.getInitParameterNames()).andReturn(
            Collections.enumeration(
                Arrays.asList("knox.admin.users", "knox.admin.groups",
                    "resource.role", "knox.acl.mode", "knox.rule_group.path.acl",
                    "knox.user_group.path.acl", "knox.user_query_group.path.acl")))
        .anyTimes();
    EasyMock.expect(config.getInitParameter("knox.admin.users"))
        .andReturn(null);
    EasyMock.expect(config.getInitParameter("knox.admin.groups"))
        .andReturn("admin");
    EasyMock.expect(config.getInitParameter("resource.role")).andReturn("KNOX");
    EasyMock.expect(config.getInitParameter("knox.rule_group.path.acl"))
        .andReturn("https://*:*/foo/group/*;*;KNOX_ADMIN_GROUPS;*");
    EasyMock.expect(config.getInitParameter("knox.user_group.path.acl"))
        .andReturn("https://*:*/foo/user/*;more;*;*");
    EasyMock.expect(config.getInitParameter("knox.user_query_group.path.acl"))
        .andReturn("https://*:*/foo/bar/{**}?{**};more;*;*");
    EasyMock.replay(config);

    final HttpServletRequest request_group = EasyMock.createNiceMock(
        HttpServletRequest.class);
    EasyMock.expect(request_group.getRequestURL())
        .andReturn(new StringBuffer("https://example.com/foo/group/bar"));
    EasyMock.expect(((HttpServletRequest) request_group).getRemoteAddr())
        .andReturn("127.1.97.24").anyTimes();

    EasyMock.replay(request_group);

    final HttpServletResponse response = EasyMock.createNiceMock(
        HttpServletResponse.class);
    EasyMock.replay(response);

    final FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response)
          throws IOException, ServletException {
      }
    };

    filter.init(config);
    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal("more"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admin"));
    try {
      Subject.doAs(subject, new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          filter.doFilter(request_group, response, chain);
          return null;
        }
      });
    } catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      } else if (t instanceof ServletException) {
        throw (ServletException) t;
      } else {
        throw new ServletException(t);
      }
    }
    assertTrue(accessGranted.get());
  }

  /**
   * THest the case where multiple paths are defined with different ACL
   * conditions
   *
   * @throws ServletException
   * @throws IOException
   */
  @Test
  public void testMultiplePathPathAcls_2()
      throws ServletException, IOException {
    FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(config.getInitParameterNames()).andReturn(
            Collections.enumeration(
                Arrays.asList("knox.admin.users", "knox.admin.groups",
                    "resource.role", "knox.acl.mode", "knox.rule_group.path.acl",
                    "knox.user_group.path.acl", "knox.user_query_group.path.acl")))
        .anyTimes();
    EasyMock.expect(config.getInitParameter("knox.admin.users"))
        .andReturn(null);
    EasyMock.expect(config.getInitParameter("knox.admin.groups"))
        .andReturn("admin");
    EasyMock.expect(config.getInitParameter("resource.role")).andReturn("KNOX");
    EasyMock.expect(config.getInitParameter("knox.rule_group.path.acl"))
        .andReturn("https://*:*/foo/group/*;*;KNOX_ADMIN_GROUPS;*");
    EasyMock.expect(config.getInitParameter("knox.user_group.path.acl"))
        .andReturn("https://*:*/foo/user/*;more;*;*");
    EasyMock.expect(config.getInitParameter("knox.user_query_group.path.acl"))
        .andReturn("https://*:*/foo/bar/{**}?{**};more;*;*");
    EasyMock.replay(config);

    final HttpServletRequest request_user = EasyMock.createNiceMock(
        HttpServletRequest.class);
    EasyMock.expect(request_user.getRequestURL())
        .andReturn(new StringBuffer("https://example.com/foo/group/bar"));
    EasyMock.expect(((HttpServletRequest) request_user).getRemoteAddr())
        .andReturn("127.1.97.24").anyTimes();

    EasyMock.replay(request_user);

    final HttpServletResponse response = EasyMock.createNiceMock(
        HttpServletResponse.class);
    EasyMock.replay(response);

    final FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response)
          throws IOException, ServletException {
      }
    };

    filter.init(config);
    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal("more"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admin"));

    // check for request_user
    try {
      Subject.doAs(subject, new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          filter.doFilter(request_user, response, chain);
          return null;
        }
      });
    } catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      } else if (t instanceof ServletException) {
        throw (ServletException) t;
      } else {
        throw new ServletException(t);
      }
    }
    assertTrue(accessGranted.get());
  }

  /**
   * THest the case where multiple paths are defined with different ACL
   * conditions
   *
   * @throws ServletException
   * @throws IOException
   */
  @Test
  public void testMultiplePathPathAcls_3()
      throws ServletException, IOException {
    FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(config.getInitParameterNames()).andReturn(
            Collections.enumeration(
                Arrays.asList("knox.admin.users", "knox.admin.groups",
                    "resource.role", "knox.acl.mode", "knox.rule_group.path.acl",
                    "knox.user_group.path.acl", "knox.user_query_group.path.acl")))
        .anyTimes();
    EasyMock.expect(config.getInitParameter("knox.admin.users"))
        .andReturn(null);
    EasyMock.expect(config.getInitParameter("knox.admin.groups"))
        .andReturn("admin");
    EasyMock.expect(config.getInitParameter("resource.role")).andReturn("KNOX");
    EasyMock.expect(config.getInitParameter("knox.rule_group.path.acl"))
        .andReturn("https://*:*/foo/group/*;*;KNOX_ADMIN_GROUPS;*");
    EasyMock.expect(config.getInitParameter("knox.user_group.path.acl"))
        .andReturn("https://*:*/foo/user/*;more;*;*");
    EasyMock.expect(config.getInitParameter("knox.user_query_group.path.acl"))
        .andReturn("https://*:*/foo/bar/{**}?{**};more;*;*");
    EasyMock.replay(config);

    final HttpServletRequest request_user_query = EasyMock.createNiceMock(
        HttpServletRequest.class);
    EasyMock.expect(request_user_query.getRequestURL())
        .andReturn(new StringBuffer("https://example.com/foo/group/bar"));
    EasyMock.expect(((HttpServletRequest) request_user_query).getQueryString())
        .andReturn("foz=baz");
    EasyMock.expect(((HttpServletRequest) request_user_query).getRemoteAddr())
        .andReturn("127.1.97.24").anyTimes();

    EasyMock.replay(request_user_query);

    final HttpServletResponse response = EasyMock.createNiceMock(
        HttpServletResponse.class);
    EasyMock.replay(response);

    final FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response)
          throws IOException, ServletException {
      }
    };

    filter.init(config);
    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal("more"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admin"));

    /* check for request_user_query */
    try {
      Subject.doAs(subject, new PrivilegedExceptionAction<Object>() {
        @Override
        public Object run() throws Exception {
          filter.doFilter(request_user_query, response, chain);
          return null;
        }
      });
    } catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      } else if (t instanceof ServletException) {
        throw (ServletException) t;
      } else {
        throw new ServletException(t);
      }
    }
    assertTrue(accessGranted.get());
  }

  class TestPathAclsAuthorizationFilter extends PathAclsAuthorizationFilter {

    private AtomicBoolean access;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
        FilterChain chain) throws IOException, ServletException {
      boolean accessGranted = enforceAclAuthorizationPolicy(request);
      if (accessGranted) {
        chain.doFilter(request, response);
      }
    }

    @Override
    protected boolean enforceAclAuthorizationPolicy(ServletRequest request)
        throws IOException {
      access = new AtomicBoolean(super.enforceAclAuthorizationPolicy(request));
      return access.get();
    }
  }

}
