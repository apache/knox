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

//import javax.security.auth.Subject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

//import org.apache.shiro.SecurityUtils;
//import org.apache.shiro.subject.Subject;

import java.io.IOException;
//import java.security.AccessController;

public class IdentityAssertionFilter implements Filter {

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
  }

  public void destroy() {
    
  }

  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
      throws IOException, ServletException {
//    Subject subject = Subject.getSubject(AccessController.getContext());
//    System.out.println("&&&&&&&&&&&&&&&&& " + subject.getPrincipals());

//    Subject subject = SecurityUtils.getSubject();
//    String principal = (String) subject.getPrincipal();
//    IdentityAssertionHttpServletRequestWrapper wrapper = new IdentityAssertionHttpServletRequestWrapper((HttpServletRequest)request, principal);

//    if (principal != null) {
//      System.out.println("&&&&&&&&&&&&&&&&& Current Subject PrimaryPrincipal: " + principal + " and is isAuthenticated: " + subject.isAuthenticated());
//    }
//    else {
//      System.out.println("&&&&&&&&&&&&&&&&& Current Subject PrimaryPrincipal: " + null + " and is isAuthenticated: " + subject.isAuthenticated());
//    }

//    chain.doFilter( wrapper, response );
  }

}
