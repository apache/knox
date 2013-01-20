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
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;


import java.io.IOException;
import java.security.AccessController;
import java.security.Principal;
import java.util.HashMap;
import java.util.Set;
import java.util.StringTokenizer;

public class IdentityAssertionFilter implements Filter {

  private HashMap<String, String> table = new HashMap<String, String>();

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    // load principal mappings
    String principalMapping = filterConfig.getServletContext().getInitParameter("principal.mapping");
    loadMappingTable(principalMapping);
  }

  /**
   * Load the internal principal mapping table from the provided
   * string value which conforms to the following semicolon delimited format:
   * actual[,another-actual]=mapped;...
   * @param principalMapping
   */
  private void loadMappingTable(String principalMapping) {
//    System.out.println("+++++++++++++ Loading the Mapping Table");
    if (principalMapping != null) {
      StringTokenizer t = new StringTokenizer(principalMapping, ";");
      do {
        String mapping = t.nextToken();
//        System.out.println("+++++++++++++ Mapping: " + mapping);
        String principals = mapping.substring(0, mapping.indexOf('='));
//        System.out.println("+++++++++++++ Principals: " + principals);
        String value = mapping.substring(mapping.indexOf('=')+1);
        String[] p = principals.split(",");
        for(int i = 0; i < p.length; i++) {
          table.put(p[i], value);
//          System.out.println("+++++++++++++ Mapping into Table: " + p[i] + "->" + value);
        }
      } while(t.hasMoreTokens());
    }
  }

  public void destroy() {
    
  }

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
    principalName = mapPrincipal(principalName);
//    System.out.println("+++++++++++++ Identity Assertion Filtering with Principal: " + principalName);

    IdentityAssertionHttpServletRequestWrapper wrapper = 
        new IdentityAssertionHttpServletRequestWrapper(
        (HttpServletRequest)request, 
        principalName);
    chain.doFilter( wrapper, response );
  }

  /**
   * Acquire a mapped principal name from the mapping table
   * as appropriate. Otherwise, the provided principalName
   * will be used.
   * @param principalName
   * @return principal name to be used in the assertion
   */
  private String mapPrincipal(String principalName) {
    String p = null;
    
    p = table.get(principalName);
    if (p == null) {
      p = principalName;
    }
    
    return p;
  }

  /**
   * Retrieve the principal to represent the asserted identity from
   * the provided Subject.
   * @param subject
   * @return principalName
   */
  private String getPrincipalName(Subject subject) {
    // LJM TODO: this implementation assumes the first one found 
    // should configure through context param based on knowledge
    // of the authentication provider in use
    String name = null;
    Set<Principal> principals = subject.getPrincipals();
    for (Principal p : principals) {
      name = p.getName();
      break;
    }
    return name;
  }

}
