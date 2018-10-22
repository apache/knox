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

import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.ImpersonatedPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;

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
import java.security.AccessController;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AclsAuthorizationFilter implements Filter {
  private static AclsAuthorizationMessages log = MessagesFactory.get( AclsAuthorizationMessages.class );
  private static Auditor auditor = AuditServiceFactory.getAuditService().getAuditor( AuditConstants.DEFAULT_AUDITOR_NAME,
          AuditConstants.KNOX_SERVICE_NAME, AuditConstants.KNOX_COMPONENT_NAME );

  private String resourceRole = null;
  private String aclProcessingMode = null;
  private AclParser parser = new AclParser();
  private ArrayList<String> adminGroups = new ArrayList<>();;
  private ArrayList<String> adminUsers = new ArrayList<>();;

  
  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    String adminGroups = filterConfig.getInitParameter("knox.admin.groups");
    if (adminGroups != null) {
      parseAdminGroupConfig(adminGroups);
    }
    
    String adminUsers = filterConfig.getInitParameter("knox.admin.users");
    if (adminUsers != null) {
      parseAdminUserConfig(adminUsers);
    }

    resourceRole = getInitParameter(filterConfig, "resource.role");
    log.initializingForResourceRole(resourceRole);
    aclProcessingMode = getInitParameter(filterConfig, resourceRole + ".acl.mode");
    if (aclProcessingMode == null) {
      aclProcessingMode = getInitParameter(filterConfig, "acl.mode");
      if (aclProcessingMode == null) {
        aclProcessingMode = "AND";
      }
    }
    log.aclProcessingMode(aclProcessingMode);
    String acls = getInitParameter(filterConfig, resourceRole + ".acl");
    parser.parseAcls(resourceRole, acls);
  }

  private String getInitParameter(FilterConfig filterConfig, String paramName) {
    return filterConfig.getInitParameter(paramName.toLowerCase(Locale.ROOT));
  }

  private void parseAdminGroupConfig(String groups) {
    Collections.addAll(adminGroups, groups.split(","));
  }

  private void parseAdminUserConfig(String users) {
    Collections.addAll(adminUsers, users.split(","));
  }

  public void destroy() {
  }

  public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain) throws IOException, ServletException {
    boolean accessGranted = enforceAclAuthorizationPolicy(request, response, chain);
    log.accessGranted(accessGranted);
    String sourceUrl = (String)request.getAttribute( AbstractGatewayFilter.SOURCE_REQUEST_CONTEXT_URL_ATTRIBUTE_NAME );
    if (accessGranted) {
      auditor.audit( Action.AUTHORIZATION, sourceUrl, ResourceType.URI, ActionOutcome.SUCCESS );
      chain.doFilter(request, response);
    }
    else {
      auditor.audit( Action.AUTHORIZATION, sourceUrl, ResourceType.URI, ActionOutcome.FAILURE );
      sendForbidden((HttpServletResponse) response);
    }
  }

  protected boolean enforceAclAuthorizationPolicy(ServletRequest request,
      ServletResponse response, FilterChain chain) {
    HttpServletRequest req = (HttpServletRequest) request;
    
    // before enforcing acls check whether there are no acls defined 
    // which would mean that there are no restrictions
    if (parser.users.size() == 0 && parser.groups.size() == 0 && parser.ipv.getIPAddresses().size() == 0) {
      return true;
    }

    boolean userAccess = false;
    boolean groupAccess = false;
    boolean ipAddrAccess = false;
    
    Subject subject = Subject.getSubject(AccessController.getContext());
    Principal primaryPrincipal = (Principal)subject.getPrincipals(PrimaryPrincipal.class).toArray()[0];
    log.primaryPrincipal(primaryPrincipal.getName());
    Object[] impersonations = subject.getPrincipals(ImpersonatedPrincipal.class).toArray();
    if (impersonations.length > 0) {
      log.impersonatedPrincipal(((Principal)impersonations[0]).getName());
      userAccess = checkUserAcls((Principal)impersonations[0]);
      log.impersonatedPrincipalHasAccess(userAccess);
    }
    else {
      userAccess = checkUserAcls(primaryPrincipal);
      log.primaryPrincipalHasAccess(userAccess);
    }
    Object[] groups = subject.getPrincipals(GroupPrincipal.class).toArray();
    if (groups.length > 0) {
//      System.out.println("GroupPrincipal: " + ((Principal)groups[0]).getName());
      groupAccess = checkGroupAcls(groups);
      log.groupPrincipalHasAccess(groupAccess);
    }
    else {
      // if we have no groups in the subject then make
      // it true if there is an anyGroup acl
      // for AND mode and acls like *;*;127.0.0.* we need to
      // make it pass
      if (parser.anyGroup && "AND".equals(aclProcessingMode)) {
        groupAccess = true;
      }
    }
    log.remoteIPAddress(req.getRemoteAddr());
    ipAddrAccess = checkRemoteIpAcls(req.getRemoteAddr());
    log.remoteIPAddressHasAccess(ipAddrAccess);
    
    if ("OR".equals(aclProcessingMode)) {
      // need to interpret '*' as excluded for OR semantics
      // to make sense and not grant access to everyone by mistake.
      // exclusion in OR is equivalent to denied
      // so, let's set each one that contains '*' to false.
      if (parser.anyUser) userAccess = false;
      if (parser.anyGroup) groupAccess = false;
      if (parser.ipv.allowsAnyIP()) ipAddrAccess = false;
      
      return (userAccess || groupAccess || ipAddrAccess);
    }
    else if ("AND".equals(aclProcessingMode)) {
      return (userAccess && groupAccess && ipAddrAccess);
    }
    return false;
  }

  private boolean checkRemoteIpAcls(String remoteAddr) {
    boolean allowed = false;
    if (remoteAddr == null) {
      return false;
    }
    allowed = parser.ipv.validateIpAddress(remoteAddr);
    return allowed;
  }

  boolean checkUserAcls(Principal user) {
    boolean allowed = false;
    if (user == null) {
      return false;
    }
    if (parser.anyUser) {
      allowed = true;
    }
    else {
      if (parser.users.contains(user.getName())) {
        allowed = true;
      }
      else if (parser.users.contains("KNOX_ADMIN_USERS") &&
          adminUsers.contains(user.getName())) {
        allowed = true;
      }
    }
    return allowed;
  }

  boolean checkGroupAcls(Object[] userGroups) {
    boolean allowed = false;
    if (userGroups == null) {
      return false;
    }
    if (parser.anyGroup) {
      allowed = true;
    }
    else {
      allowed = hasAllowedPrincipal(parser.groups, userGroups);
      if (!allowed && parser.groups.contains("KNOX_ADMIN_GROUPS")) {
        allowed = hasAllowedPrincipal(adminGroups, userGroups);
      }
    }
    return allowed;
  }

  private boolean hasAllowedPrincipal(List<String> allowed, Object[] userGroups) {
    boolean rc = false;
    for (int i = 0; i < userGroups.length; i++) {
      if (allowed.contains(((Principal)userGroups[i]).getName())) {
        rc = true;
        break;
      }
    }
    return rc;
  }

  private void sendForbidden(HttpServletResponse res) {
    sendErrorCode(res, 403);
  }

  private void sendErrorCode(HttpServletResponse res, int code) {
    try {
      res.sendError(code);
    } catch (IOException e) {
      // TODO: log appropriately
      e.printStackTrace();
    }
  }
}
