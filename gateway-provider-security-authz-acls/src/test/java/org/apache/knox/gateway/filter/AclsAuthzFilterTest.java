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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import javax.security.auth.Subject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

public class AclsAuthzFilterTest {
  private boolean accessGranted;
  private Filter filter;

  @Before
  public void setUp() {
    filter = new AclsAuthorizationFilter() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response,
                           FilterChain chain) throws IOException, ServletException {
        boolean accessGranted = enforceAclAuthorizationPolicy(request, response, chain);
        if (accessGranted) {
          chain.doFilter(request, response);
        }
      }

      @Override
      protected boolean enforceAclAuthorizationPolicy(ServletRequest request,
                                                      ServletResponse response, FilterChain chain) {
        accessGranted = super.enforceAclAuthorizationPolicy(request, response, chain);
        return accessGranted;
      }
    };
  }

  @Test
  public void testKnoxAdminGroupsValid() throws ServletException, IOException {
    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter("knox.admin.users")).andReturn(null);
    EasyMock.expect(config.getInitParameter("knox.admin.groups")).andReturn("admin");
    EasyMock.expect(config.getInitParameter("resource.role")).andReturn("KNOX");
    EasyMock.expect(config.getInitParameter("knox.acl.mode")).andReturn("OR");
    EasyMock.expect(config.getInitParameter("knox.acl")).andReturn("*;KNOX_ADMIN_GROUPS;*");
    EasyMock.replay( config );

    final HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.replay( request );

    final HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( response );

    final FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response)
          throws IOException, ServletException {
      }
    };

    filter.init(config);

    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal("larry"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admin"));
    try {
      Subject.doAs(
        subject,
        new PrivilegedExceptionAction<Object>() {
          @Override
          public Object run() throws Exception {
            filter.doFilter(request, response, chain);
            return null;
          }
        });
    }
    catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      }
      else if (t instanceof ServletException) {
        throw (ServletException) t;
      }
      else {
        throw new ServletException(t);
      }
    }
    assertTrue(accessGranted);
  }

  @Test
  public void testKnoxAdminGroupsInvalid() throws ServletException, IOException {
    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter("knox.admin.users")).andReturn(null);
    EasyMock.expect(config.getInitParameter("knox.admin.groups")).andReturn("admin");
    EasyMock.expect(config.getInitParameter("resource.role")).andReturn("KNOX");
    EasyMock.expect(config.getInitParameter("knox.acl.mode")).andReturn("OR");
    EasyMock.expect(config.getInitParameter("knox.acl")).andReturn("*;KNOX_ADMIN_GROUPS;*");
    EasyMock.replay( config );

    final HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.replay( request );

    final HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( response );

    final FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) {
      }
    };

    filter.init(config);

    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal("larry"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("nonadmin"));
    try {
      Subject.doAs(
        subject,
        new PrivilegedExceptionAction<Object>() {
          @Override
          public Object run() throws Exception {
            filter.doFilter(request, response, chain);
            return null;
          }
        });
    }
    catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      }
      else if (t instanceof ServletException) {
        throw (ServletException) t;
      }
      else {
        throw new ServletException(t);
      }
    }
    assertFalse(accessGranted);
  }

  @Test
  public void testKnoxAdminUsersValid() throws ServletException, IOException {
    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter("knox.admin.users")).andReturn("adminuser");
    EasyMock.expect(config.getInitParameter("knox.admin.groups")).andReturn(null);
    EasyMock.expect(config.getInitParameter("resource.role")).andReturn("KNOX");
    EasyMock.expect(config.getInitParameter("knox.acl.mode")).andReturn("OR");
    EasyMock.expect(config.getInitParameter("knox.acl")).andReturn("KNOX_ADMIN_USERS;*;*");
    EasyMock.replay( config );

    final HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.replay( request );

    final HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( response );

    final FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) {
      }
    };

    filter.init(config);

    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal("adminuser"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admin"));
    try {
      Subject.doAs(
        subject,
        new PrivilegedExceptionAction<Object>() {
          @Override
          public Object run() throws Exception {
            filter.doFilter(request, response, chain);
            return null;
          }
        });
    }
    catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      }
      else if (t instanceof ServletException) {
        throw (ServletException) t;
      }
      else {
        throw new ServletException(t);
      }
    }
    assertTrue(accessGranted);
  }

  @Test
  public void testKnoxAdminUsersInvalid() throws ServletException, IOException {
    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter("knox.admin.users")).andReturn("adminuser");
    EasyMock.expect(config.getInitParameter("knox.admin.groups")).andReturn(null);
    EasyMock.expect(config.getInitParameter("resource.role")).andReturn("KNOX");
    EasyMock.expect(config.getInitParameter("knox.acl.mode")).andReturn("OR");
    EasyMock.expect(config.getInitParameter("knox.acl")).andReturn("KNOX_ADMIN_USERS;*;*");
    EasyMock.replay( config );

    final HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.replay( request );

    final HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( response );

    final FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) {
      }
    };

    filter.init(config);

    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal("larry"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admin"));
    try {
      Subject.doAs(
        subject,
        new PrivilegedExceptionAction<Object>() {
          @Override
          public Object run() throws Exception {
            filter.doFilter(request, response, chain);
            return null;
          }
        });
    }
    catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      }
      else if (t instanceof ServletException) {
        throw (ServletException) t;
      }
      else {
        throw new ServletException(t);
      }
    }
    assertFalse(accessGranted);
  }

  @Test
  public void testKnoxAdminUsersInvalidButACLUsersValid() throws ServletException, IOException {
    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter("knox.admin.users")).andReturn("adminuser");
    EasyMock.expect(config.getInitParameter("knox.admin.groups")).andReturn(null);
    EasyMock.expect(config.getInitParameter("resource.role")).andReturn("KNOX");
    EasyMock.expect(config.getInitParameter("knox.acl.mode")).andReturn("OR");
    EasyMock.expect(config.getInitParameter("knox.acl")).andReturn("KNOX_ADMIN_USERS,larry;*;*");
    EasyMock.replay( config );

    final HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.replay( request );

    final HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( response );

    final FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response)
          throws IOException, ServletException {
      }
    };

    filter.init(config);

    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal("larry"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admin"));
    try {
      Subject.doAs(
        subject,
        new PrivilegedExceptionAction<Object>() {
          @Override
          public Object run() throws Exception {
            filter.doFilter(request, response, chain);
            return null;
          }
        });
    }
    catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      }
      else if (t instanceof ServletException) {
        throw (ServletException) t;
      }
      else {
        throw new ServletException(t);
      }
    }
    assertTrue(accessGranted);
  }

  @Test
  public void testKnoxAdminUsersInvalidButACLGroupValid() throws ServletException, IOException {
    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter("knox.admin.users")).andReturn("adminuser");
    EasyMock.expect(config.getInitParameter("knox.admin.groups")).andReturn(null);
    EasyMock.expect(config.getInitParameter("resource.role")).andReturn("KNOX");
    EasyMock.expect(config.getInitParameter("knox.acl.mode")).andReturn("OR");
    EasyMock.expect(config.getInitParameter("knox.acl")).andReturn("KNOX_ADMIN_USERS;admin;*");
    EasyMock.replay( config );

    final HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.replay( request );

    final HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( response );

    final FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) {
      }
    };

    filter.init(config);

    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal("larry"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admin"));
    try {
      Subject.doAs(
        subject,
        new PrivilegedExceptionAction<Object>() {
          @Override
          public Object run() throws Exception {
            filter.doFilter(request, response, chain);
            return null;
          }
        });
    }
    catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      }
      else if (t instanceof ServletException) {
        throw (ServletException) t;
      }
      else {
        throw new ServletException(t);
      }
    }
    assertTrue(accessGranted);
  }

  @Test
  public void testKnoxAdminUsersInvalidButKnoxAdminGroupValid() throws ServletException, IOException {
    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect(config.getInitParameter("knox.admin.users")).andReturn("adminuser");
    EasyMock.expect(config.getInitParameter("knox.admin.groups")).andReturn("admingroup");
    EasyMock.expect(config.getInitParameter("resource.role")).andReturn("KNOX");
    EasyMock.expect(config.getInitParameter("knox.acl.mode")).andReturn("OR");
    EasyMock.expect(config.getInitParameter("knox.acl")).andReturn("KNOX_ADMIN_USERS;KNOX_ADMIN_GROUPS,admin;*");
    EasyMock.replay( config );

    final HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.replay( request );

    final HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.replay( response );

    final FilterChain chain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) {
      }
    };

    filter.init(config);

    Subject subject = new Subject();
    subject.getPrincipals().add(new PrimaryPrincipal("larry"));
    subject.getPrincipals().add(new GroupPrincipal("users"));
    subject.getPrincipals().add(new GroupPrincipal("admingroup"));
    try {
      Subject.doAs(
        subject,
        new PrivilegedExceptionAction<Object>() {
          @Override
          public Object run() throws Exception {
            filter.doFilter(request, response, chain);
            return null;
          }
        });
    }
    catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      }
      else if (t instanceof ServletException) {
        throw (ServletException) t;
      }
      else {
        throw new ServletException(t);
      }
    }
    assertTrue(accessGranted);
  }
}
