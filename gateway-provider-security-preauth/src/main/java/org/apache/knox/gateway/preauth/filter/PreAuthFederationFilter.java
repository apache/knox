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
package org.apache.knox.gateway.preauth.filter;

import java.io.IOException;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.List;

import javax.security.auth.Subject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.knox.gateway.security.PrimaryPrincipal;

public class PreAuthFederationFilter implements Filter {
  private static final String CUSTOM_HEADER_PARAM = "preauth.customHeader";
  private List<PreAuthValidator> validators = null;
  private FilterConfig filterConfig;
  private String headerName = "SM_USER";

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    String customHeader = filterConfig.getInitParameter(CUSTOM_HEADER_PARAM);
    if (customHeader != null) {
      headerName = customHeader;
    }
    this.filterConfig = filterConfig;
    validators = PreAuthService.getValidators(filterConfig);
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
                       FilterChain chain) throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    if (httpRequest.getHeader(headerName) != null) {
      if (PreAuthService.validate(httpRequest, filterConfig, validators)) {
        // TODO: continue as subject
        chain.doFilter(request, response);
      } else {
        // TODO: log preauthenticated SSO validation failure
        ((HttpServletResponse) response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing Required Header for SSO Validation");
      }
    } else {
      ((HttpServletResponse) response).sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing Required Header for PreAuth SSO Federation");
    }
  }

  /* (non-Javadoc)
   * @see javax.servlet.Filter#destroy()
   */
  @Override
  public void destroy() {
    // TODO Auto-generated method stub

  }

  /**
   * Recreate the current Subject based upon the provided mappedPrincipal
   * and look for the groups that should be associated with the new Subject.
   * Upon finding groups mapped to the principal - add them to the new Subject.
   * @param principal
   * @throws ServletException
   * @throws IOException
   */
  protected void continueChainAsPrincipal(final ServletRequest request, final ServletResponse response,
                                          final FilterChain chain, String principal) throws IOException, ServletException {
    Subject subject = null;
    Principal primaryPrincipal = null;

    // do some check to ensure that the extracted identity matches any existing security context
    // if not, there is may be someone tampering with the request - consult config to determine
    // how we are to handle it

    // TODO: make sure that this makes sense with existing sessions or lack thereof
    Subject currentSubject = Subject.getSubject(AccessController.getContext());
    if (currentSubject != null) {
      primaryPrincipal = (PrimaryPrincipal) currentSubject.getPrincipals(PrimaryPrincipal.class).toArray()[0];
      if (primaryPrincipal != null) {
        if (!primaryPrincipal.getName().equals(principal)) {
          // TODO?
        }
      }
    }

    subject = new Subject();
    subject.getPrincipals().add(primaryPrincipal);
    doAs(request, response, chain, subject);
  }

  private void doAs(final ServletRequest request,
                    final ServletResponse response, final FilterChain chain, Subject subject)
      throws IOException, ServletException {
    try {
      Subject.doAs(
          subject,
          new PrivilegedExceptionAction<Object>() {
            public Object run() throws Exception {
              doFilterInternal(request, response, chain);
              return null;
            }
          }
      );
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
  }

  private void doFilterInternal(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    chain.doFilter(request, response);
  }

}
