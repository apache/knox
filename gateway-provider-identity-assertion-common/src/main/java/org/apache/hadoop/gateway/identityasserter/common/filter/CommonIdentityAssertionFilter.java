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
package org.apache.hadoop.gateway.identityasserter.common.filter;

import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.apache.hadoop.gateway.identityasserter.common.filter.AbstractIdentityAssertionFilter;

import java.io.IOException;
import java.security.AccessController;

public class CommonIdentityAssertionFilter extends AbstractIdentityAssertionFilter {
  /* (non-Javadoc)
   * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
   */
  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
  }

  /* (non-Javadoc)
   * @see javax.servlet.Filter#destroy()
   */
  @Override
  public void destroy() {
  }

  /**
   * Obtain the standard javax.security.auth.Subject, retrieve the caller principal, map
   * to the identity to be asserted as appropriate and create the provider specific
   * assertion token. Add the assertion token to the request.
   */
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
      throws IOException, ServletException {
    Subject subject = Subject.getSubject(AccessController.getContext());

    String principalName = getPrincipalName(subject);
    
    String mappedPrincipalName = mapUserPrincipal(principalName);
    String[] groups = mapGroupPrincipals(mappedPrincipalName, subject);

    HttpServletRequestWrapper wrapper = wrapHttpServletRequest(
        request, mappedPrincipalName);

    continueChainAsPrincipal(wrapper, response, chain, mappedPrincipalName, groups);
  }

  public HttpServletRequestWrapper wrapHttpServletRequest(
      ServletRequest request, String mappedPrincipalName) {
    // wrap the request so that the proper principal is returned
    // from request methods
    IdentityAsserterHttpServletRequestWrapper wrapper =
        new IdentityAsserterHttpServletRequestWrapper(
        (HttpServletRequest)request, 
        mappedPrincipalName);
    return wrapper;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.identityasserter.common.filter.AbstractIdentityAssertionFilter#mapGroupPrincipals(java.lang.String, javax.security.auth.Subject)
   */
  @Override
  public String[] mapGroupPrincipals(String mappedPrincipalName, Subject subject) {
    // NOP
    return null;
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.gateway.identityasserter.common.filter.AbstractIdentityAssertionFilter#mapUserPrincipal(java.lang.String)
   */
  @Override
  public String mapUserPrincipal(String principalName) {
    // NOP
    return principalName;
  }
}
