/**
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
package org.apache.hadoop.gateway.preauth.filter;

import java.io.IOException;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Set;

import javax.security.auth.Subject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.annotations.VisibleForTesting;
import org.apache.hadoop.gateway.security.PrimaryPrincipal;

/**
 *
 */
public abstract class AbstractPreAuthFederationFilter implements Filter {

  private PreAuthValidator validator = null;
  private FilterConfig filterConfig;

  /**
   * 
   */
  public AbstractPreAuthFederationFilter() {
    super();
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    this.filterConfig = filterConfig;
    validator = PreAuthService.getValidator(filterConfig);
  }

  @VisibleForTesting
  public PreAuthValidator getValidator() {
    return validator;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest)request;
    String principal = getPrimaryPrincipal(httpRequest);
    if (principal != null) {
      if (isValid(httpRequest)) {
        Subject subject = new Subject();
        subject.getPrincipals().add(new PrimaryPrincipal(principal));
        addGroupPrincipals(httpRequest, subject.getPrincipals());
        doAs(httpRequest, response, chain, subject);
      }
      else {
        // TODO: log preauthenticated SSO validation failure
        ((HttpServletResponse)response).sendError(HttpServletResponse.SC_FORBIDDEN, "SSO Validation Failure.");
      }
    } 
    else {
      ((HttpServletResponse)response).sendError(HttpServletResponse.SC_FORBIDDEN, "Missing Required Header for PreAuth SSO Federation");
    }
  }

  /**
   * @return
   */
  private boolean isValid(HttpServletRequest httpRequest) {
    try {
      return validator.validate(httpRequest, filterConfig);
    } catch (PreAuthValidationException e) {
      // TODO log exception
      return false;
    }
  }

  @Override
  public void destroy() {
  }

  private void doAs(final ServletRequest request, final ServletResponse response, final FilterChain chain, Subject subject)
    throws IOException, ServletException {
    try {
      Subject.doAs(
          subject,
          new PrivilegedExceptionAction<Object>() {
            public Object run() throws Exception {
              chain.doFilter(request, response);
              return null;
            }
          }
          );
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
  }
  
  /**
   * @param httpRequest
   */
  abstract protected String getPrimaryPrincipal(HttpServletRequest httpRequest);

  /**
   * @param principals
   */
  abstract protected void addGroupPrincipals(HttpServletRequest request, Set<Principal> principals);
}