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
package org.apache.knox.gateway.identityasserter.common.filter;

import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.apache.commons.lang.ArrayUtils;
import org.apache.knox.gateway.security.principal.PrincipalMappingException;
import org.apache.knox.gateway.security.principal.SimplePrincipalMapper;

import java.io.IOException;
import java.security.AccessController;
import java.util.HashMap;

public class CommonIdentityAssertionFilter extends AbstractIdentityAssertionFilter {
  private static final String GROUP_PRINCIPAL_MAPPING = "group.principal.mapping";
  private static final String PRINCIPAL_MAPPING = "principal.mapping";
  private SimplePrincipalMapper mapper = new SimplePrincipalMapper();
  private HashMap<String, Impersonation> impMap = new HashMap<String, Impersonation>();
  private String resourceRole = null;

  /* (non-Javadoc)
   * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
   */
  @Override
  public void init(FilterConfig filterConfig) throws ServletException {

    /*
     <param>
       <name>path.segment.impersonated.principal</name>
       <value>IDBROKER;credentials</value>
     </param>

     <param>
       <name>header.impersonated.principal</name>
       <value>*;SM_USER</value>
     </param>

     <param>
       <name>query.param.impersonated.principal</name>
       <value>WEBHDFS,HIVE;doas</value>
     </param>
    */

    resourceRole = filterConfig.getInitParameter("resource.role");
    String pathSegmentImpersonation = filterConfig.getInitParameter("path.segment.impersonated.principal");
    if (pathSegmentImpersonation != null) {
      String services = pathSegmentImpersonation.substring(0, pathSegmentImpersonation.indexOf(";"));
      String[] svcArray = services.split(",");
      String service = null;
      int segmentIndex;
      for (int i = 0; i < svcArray.length; i++) {
        service = svcArray[i];
        segmentIndex = pathSegmentImpersonation.indexOf(";");
        if (segmentIndex != -1) {
          Impersonation imp = new Impersonation(service, "path.segment", 
              pathSegmentImpersonation.substring(segmentIndex+1));
          impMap.put(svcArray[i], imp);
        }
      }
    }

    String principalMapping = filterConfig.getInitParameter(PRINCIPAL_MAPPING);
    if (principalMapping == null || principalMapping.isEmpty()) {
      principalMapping = filterConfig.getServletContext().getInitParameter(PRINCIPAL_MAPPING);
    }
    String groupPrincipalMapping = filterConfig.getInitParameter(GROUP_PRINCIPAL_MAPPING);
    if (groupPrincipalMapping == null || groupPrincipalMapping.isEmpty()) {
      groupPrincipalMapping = filterConfig.getServletContext().getInitParameter(GROUP_PRINCIPAL_MAPPING);
    }
    if (principalMapping != null && !principalMapping.isEmpty() || groupPrincipalMapping != null && !groupPrincipalMapping.isEmpty()) {
      try {
        mapper.loadMappingTable(principalMapping, groupPrincipalMapping);
      } catch (PrincipalMappingException e) {
        throw new ServletException("Unable to load principal mapping table.", e);
      }
    }
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

    String mappedPrincipalName = null;
    
    Impersonation imp = impMap.get(resourceRole);
    if (imp != null) {
      if (imp.type.equals("path.segment")) {
        String pathInfo = ((HttpServletRequest)request).getPathInfo();
        String[] segments = pathInfo.split("/");
        for (int i = 0; i < segments.length; i++) {
          if (segments[i].equals(imp.name)) {
            if (segments.length > i+1) {
              mappedPrincipalName = segments[i+1];
              break;
            }
          }
        }
      }
    }

    if (mappedPrincipalName != null && !mappedPrincipalName.equals(principalName)) {
      // TODO: audit this
      principalName = mappedPrincipalName;
    }

    mappedPrincipalName = mapUserPrincipalBase(principalName);
    mappedPrincipalName = mapUserPrincipal(mappedPrincipalName);
    String[] mappedGroups = mapGroupPrincipals(mappedPrincipalName, subject);
    String[] groups = mapGroupPrincipals(mappedPrincipalName, subject);
    groups = combineGroupMappings(mappedGroups, groups);

    HttpServletRequestWrapper wrapper = wrapHttpServletRequest(
        request, mappedPrincipalName);

    continueChainAsPrincipal(wrapper, response, chain, mappedPrincipalName, groups);
  }

  /**
   * @param mappedGroups
   * @param groups
   * @return
   */
  private String[] combineGroupMappings(String[] mappedGroups, String[] groups) {
    if (mappedGroups != null && groups != null) {
      return (String[])ArrayUtils.addAll(mappedGroups, groups);
    }
    else {
      return groups != null ? groups : mappedGroups;
    }
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

  protected String[] mapGroupPrincipalsBase(String mappedPrincipalName, Subject subject) {
    return mapper.mapGroupPrincipal(mappedPrincipalName);
  }

  protected String mapUserPrincipalBase(String principalName) {
    return mapper.mapUserPrincipal(principalName);
  }

  /* (non-Javadoc)
   * @see AbstractIdentityAssertionFilter#mapGroupPrincipals(java.lang.String, javax.security.auth.Subject)
   */
  @Override
  public String[] mapGroupPrincipals(String mappedPrincipalName, Subject subject) {
    // NOP
    return null;
  }

  /* (non-Javadoc)
   * @see AbstractIdentityAssertionFilter#mapUserPrincipal(java.lang.String)
   */
  @Override
  public String mapUserPrincipal(String principalName) {
    // NOP
    return principalName;
  }
  
  private class Impersonation {
    public String service = null;
    public String type = null;
    public String name = null;
    
    public Impersonation(String service, String type, String name) {
      this.service = service;
      this.type = type;
      this.name = name;
    }
  }
}
