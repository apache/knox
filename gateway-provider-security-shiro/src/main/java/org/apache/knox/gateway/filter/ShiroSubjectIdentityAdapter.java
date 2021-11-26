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

import java.io.IOException;
import java.security.Principal;
import java.security.PrivilegedExceptionAction;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;

public class ShiroSubjectIdentityAdapter implements Filter {
  private static final String SUBJECT_USER_GROUPS = "subject.userGroups";
  private static AuditService auditService = AuditServiceFactory.getAuditService();
  private static Auditor auditor = auditService.getAuditor(
      AuditConstants.DEFAULT_AUDITOR_NAME, AuditConstants.KNOX_SERVICE_NAME,
      AuditConstants.KNOX_COMPONENT_NAME );

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
  }

  @Override
  public void destroy() {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    Subject subject = SecurityUtils.getSubject();

    // trigger call to shiro authorization realm
    // we use shiro authorization realm to look up groups
    subject.hasRole("authenticatedUser");

    CallableChain callableChain = new CallableChain(request, response, chain);
    SecurityUtils.getSubject().execute(callableChain);
  }

  private static class CallableChain implements Callable<Void> {
    private FilterChain chain;
    ServletRequest request;
    ServletResponse response;

    CallableChain(ServletRequest request, ServletResponse response, FilterChain chain) {
      this.request = request;
      this.response = response;
      this.chain = chain;
    }

    @Override
    public Void call() throws Exception {
      PrivilegedExceptionAction<Void> action = new PrivilegedExceptionAction<Void>() {
        @Override
        public Void run() throws Exception {
          chain.doFilter( request, response );
          return null;
        }
      };
      Subject shiroSubject = SecurityUtils.getSubject();

      if (shiroSubject == null || shiroSubject.getPrincipal() == null) {
        throw new IllegalStateException("Unable to determine authenticated user from Shiro, please check that your Knox Shiro configuration is correct");
      }

      final String principal = shiroSubject.getPrincipal().toString();
      HashSet emptySet = new HashSet();
      Set<Principal> principals = new HashSet<>();
      Principal p = new PrimaryPrincipal(principal);
      principals.add(p);
      AuditContext context = auditService.getContext();
      context.setUsername( principal );
      auditService.attachContext(context);
      String sourceUri = (String)request.getAttribute( AbstractGatewayFilter.SOURCE_REQUEST_CONTEXT_URL_ATTRIBUTE_NAME );
      auditor.audit( Action.AUTHENTICATION , sourceUri, ResourceType.URI, ActionOutcome.SUCCESS );

      Set<String> userGroups;
      // map ldap groups saved in session to Java Subject GroupPrincipal(s)
      if (SecurityUtils.getSubject().getSession().getAttribute(SUBJECT_USER_GROUPS) != null) {
        userGroups = (Set<String>)SecurityUtils.getSubject().getSession().getAttribute(SUBJECT_USER_GROUPS);
      } else { // KnoxLdapRealm case
        if(  shiroSubject.getPrincipal() instanceof String ) {
           userGroups = new HashSet<>(shiroSubject.getPrincipals().asSet());
           userGroups.remove(principal);
        } else { // KnoxPamRealm case
           Set<Principal> shiroPrincipals = new HashSet<>(shiroSubject.getPrincipals().asSet());
           userGroups = new HashSet<>(); // Here we are creating a new UserGroup
                                               // so we don't need to remove a Principal
                                               // In the case of LDAP the userGroup may have already Principal
                                               // added to the list of groups, so it is not needed.
           for( Principal shiroPrincipal: shiroPrincipals ) {
                userGroups.add(shiroPrincipal.toString() );
           }
        }
      }
      for (String userGroup : userGroups) {
        Principal gp = new GroupPrincipal(userGroup);
        principals.add(gp);
      }
      auditor.audit( Action.AUTHENTICATION , sourceUri, ResourceType.URI, ActionOutcome.SUCCESS, "Groups: " + userGroups );

      // The newly constructed Sets check whether this Subject has been set read-only
      // before permitting subsequent modifications. The newly created Sets also prevent
      // illegal modifications by ensuring that callers have sufficient permissions.
      //
      // To modify the Principals Set, the caller must have AuthPermission("modifyPrincipals").
      // To modify the public credential Set, the caller must have AuthPermission("modifyPublicCredentials").
      // To modify the private credential Set, the caller must have AuthPermission("modifyPrivateCredentials").
      javax.security.auth.Subject subject = new javax.security.auth.Subject(true, principals, emptySet, emptySet);
      javax.security.auth.Subject.doAs( subject, action );

      return null;
    }
  }
}
