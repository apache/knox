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
package org.apache.knox.gateway.filter.security;

import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.i18n.GatewaySpiMessages;
import org.apache.knox.gateway.i18n.GatewaySpiResources;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.i18n.resources.ResourcesFactory;
import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.ImpersonatedPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.security.principal.PrincipalMapper;
import org.apache.knox.gateway.security.principal.PrincipalMappingException;
import org.apache.knox.gateway.security.principal.SimplePrincipalMapper;

import javax.security.auth.Subject;

import java.io.IOException;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.ServletException;

import java.util.Arrays;
import java.util.Set;

public abstract class AbstractIdentityAssertionFilter extends AbstractIdentityAssertionBase implements Filter {

  private static final GatewaySpiMessages LOG = MessagesFactory.get( GatewaySpiMessages.class );
  private static final GatewaySpiResources RES = ResourcesFactory.get( GatewaySpiResources.class );
  private static AuditService auditService = AuditServiceFactory.getAuditService();
  private static Auditor auditor = auditService.getAuditor(
      AuditConstants.DEFAULT_AUDITOR_NAME, AuditConstants.KNOX_SERVICE_NAME,
      AuditConstants.KNOX_COMPONENT_NAME );
  protected PrincipalMapper mapper = new SimplePrincipalMapper();

  public AbstractIdentityAssertionFilter() {
    super();
  }

  protected void loadPrincipalMappings(FilterConfig filterConfig) {
    String principalMapping = filterConfig.getServletContext().getInitParameter("principal.mapping");
    String groupMapping = filterConfig.getServletContext().getInitParameter("group.principal.mapping");
    if (principalMapping != null && !principalMapping.isEmpty() || groupMapping != null && !groupMapping.isEmpty()) {
      try {
        mapper.loadMappingTable(principalMapping, groupMapping);
      }
      catch (PrincipalMappingException pme) {
        LOG.failedToLoadPrincipalMappingTable( pme );
      }
    }
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    // load principal mappings
    loadPrincipalMappings(filterConfig);
  }

  public void destroy() {
    
  }

  /**
   * Recreate the current Subject based upon the provided mappedPrincipal
   * and look for the groups that should be associated with the new Subject.
   * Upon finding groups mapped to the principal - add them to the new Subject.
   * @param mappedPrincipalName
   * @throws ServletException 
   * @throws IOException 
   */
  protected void continueChainAsPrincipal(final ServletRequest request, final ServletResponse response, 
      final FilterChain chain, String mappedPrincipalName) throws IOException, ServletException {
    Subject subject = null;
    Principal impersonationPrincipal = null;
    Principal primaryPrincipal = null;
    
    // get the current subject and determine whether we need another doAs with 
    // an impersonatedPrincipal and/or mapped group principals
    boolean impersonationNeeded = false;
    boolean groupsMapped = false;
    
    // look up the current Java Subject and assosciated group principals
    Subject currentSubject = Subject.getSubject(AccessController.getContext());
    Set<?> currentGroups = currentSubject.getPrincipals(GroupPrincipal.class);
    
    primaryPrincipal = (PrimaryPrincipal) currentSubject.getPrincipals(PrimaryPrincipal.class).toArray()[0];
    if (primaryPrincipal != null) {
      if (!primaryPrincipal.getName().equals(mappedPrincipalName)) {
        impersonationNeeded = true;
        auditService.getContext().setProxyUsername( mappedPrincipalName );
        auditor.audit( Action.IDENTITY_MAPPING, primaryPrincipal.getName(), ResourceType.PRINCIPAL, ActionOutcome.SUCCESS );
      }
    }
    else {
      // something is amiss - authentication/federation providers should have run
      // before identity assertion and should have ensured that the appropriate
      // principals were added to the current subject
      // TODO: log as appropriate
      primaryPrincipal = new PrimaryPrincipal(((HttpServletRequest) request).getUserPrincipal().getName());
    }
    
    groupsMapped = areGroupsMappedForPrincipal(mappedPrincipalName) || !currentGroups.isEmpty();
    
    if (impersonationNeeded || groupsMapped) {
      // gonna need a new subject and doAs
      subject = new Subject();
      Set<Principal> principals = subject.getPrincipals();
      principals.add(primaryPrincipal);
      
      // map group principals from current Subject into newly created Subject
      for (Object obj : currentGroups) {
        principals.add((Principal)obj);
      }
      
      if (impersonationNeeded) {
        impersonationPrincipal = new ImpersonatedPrincipal(mappedPrincipalName);
        subject.getPrincipals().add(impersonationPrincipal);
      }
      if (groupsMapped) {
        addMappedGroupsToSubject(mappedPrincipalName, subject);
        addMappedGroupsToSubject("*", subject);
      }
      doAs(request, response, chain, subject);
    }
    else {
      doFilterInternal(request, response, chain);
    }
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

  private void addMappedGroupsToSubject(String mappedPrincipalName, Subject subject) {
    String[] groups = mapper.mapGroupPrincipal(mappedPrincipalName);
    if (groups != null) {
      auditor.audit( Action.IDENTITY_MAPPING, mappedPrincipalName, ResourceType.PRINCIPAL, ActionOutcome.SUCCESS, RES.groupsList( Arrays.toString( groups ) ) );
      for (int i = 0; i < groups.length; i++) {
        subject.getPrincipals().add(new GroupPrincipal(groups[i]));
      }
    }
  }
  
  private boolean areGroupsMappedForPrincipal(String principalName) {
    boolean mapped = false;
    mapped = (mapper.mapGroupPrincipal(principalName) != null ? true : false);
    if (!mapped) {
      mapped = (mapper.mapGroupPrincipal("*") != null ? true : false);
    }
    return mapped;
  }

  private void doFilterInternal(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    chain.doFilter(request, response);
  }
}
