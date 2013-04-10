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
package org.apache.hadoop.gateway.filter;


import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.hadoop.gateway.filter.security.AbstractIdentityAssertionFilter;

import java.io.IOException;
import java.security.AccessController;

public class PseudoIdentityAssertionFilter extends AbstractIdentityAssertionFilter {

  /**
   * Obtain the standard javax.security.auth.Subject, retrieve the caller principal, map
   * to the identity to be asserted as appropriate and create the provider specific
   * assertion token. Add the assertion token to the request.
   */
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
      throws IOException, ServletException {
//    System.out.println("+++++++++++++ Identity Assertion Filtering");
    Subject subject = Subject.getSubject(AccessController.getContext());

    String principalName = getPrincipalName(subject);
    principalName = mapper.mapPrincipal(principalName);
//    System.out.println("+++++++++++++ Identity Assertion Filtering with Principal: " + principalName);

    IdentityAssertionHttpServletRequestWrapper wrapper = 
        new IdentityAssertionHttpServletRequestWrapper(
        (HttpServletRequest)request, 
        principalName);
    chain.doFilter( wrapper, response );
  }

}
