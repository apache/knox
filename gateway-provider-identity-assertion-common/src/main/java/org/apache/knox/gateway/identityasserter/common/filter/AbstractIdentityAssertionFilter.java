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
package org.apache.knox.gateway.identityasserter.common.filter;

import java.io.IOException;
import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.Set;

import javax.security.auth.Subject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.apache.knox.gateway.IdentityAsserterMessages;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.filter.security.AbstractIdentityAssertionBase;
import org.apache.knox.gateway.i18n.GatewaySpiResources;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.i18n.resources.ResourcesFactory;
import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.ImpersonatedPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;

public abstract class AbstractIdentityAssertionFilter extends
  AbstractIdentityAssertionBase implements Filter {

  private IdentityAsserterMessages LOG = MessagesFactory.get(IdentityAsserterMessages.class);

  private static final GatewaySpiResources RES = ResourcesFactory.get( GatewaySpiResources.class );
  private static AuditService auditService = AuditServiceFactory.getAuditService();
  private static Auditor auditor = auditService.getAuditor(
        AuditConstants.DEFAULT_AUDITOR_NAME, AuditConstants.KNOX_SERVICE_NAME,
        AuditConstants.KNOX_COMPONENT_NAME );

  public AbstractIdentityAssertionFilter() {
    super();
  }

  /**
   * This method returns a Stringp[] of new group principal names to use
   * based on implementation specific mapping or lookup mechanisms.
   * Returning null means that whatever set of GroupPrincipals is in the
   * provided Subject is sufficient to use and no additional mapping is required.
   * @param mappedPrincipalName username for the authenticated identity - post mapUserPrincipal mapping.
   * @param subject the existing Subject from the authentication event which may or may not contain GroupPrincipals.
   * @return String[] of new principal names to use as GroupPrincipals or null.
   */
  public abstract String[] mapGroupPrincipals(String mappedPrincipalName, Subject subject);

  /**
   * This method is used to map the username of the authenticated identity to some other
   * principal name based on an implementation specific mechanism. It will either return
   * a new principal name or the provided principal name if there is no mapping required.
   * @param principalName principal to try to map
   * @return new username or the provided principalName
   */
  public abstract String mapUserPrincipal(String principalName);

  protected void continueChainAsPrincipal(HttpServletRequestWrapper request, ServletResponse response,
      FilterChain chain, String mappedPrincipalName, String[] groups) throws IOException,
      ServletException {
        Subject subject;
        Principal impersonationPrincipal;
        Principal primaryPrincipal;

        // get the current subject and determine whether we need another doAs with
        // an impersonatedPrincipal and/or mapped group principals
        boolean impersonationNeeded = false;
        boolean groupsMapped;

        // look up the current Java Subject and assosciated group principals
        Subject currentSubject = Subject.getSubject(AccessController.getContext());
        if (currentSubject == null) {
          LOG.subjectNotAvailable();
          throw new IllegalStateException("Required Subject Missing");
        }

        Set<?> currentGroups = currentSubject.getPrincipals(GroupPrincipal.class);

        primaryPrincipal = (PrimaryPrincipal) currentSubject.getPrincipals(PrimaryPrincipal.class).toArray()[0];
        if (primaryPrincipal != null) {
          if (!primaryPrincipal.getName().equals(mappedPrincipalName)) {
            impersonationNeeded = true;
            AuditContext context = auditService.getContext();
            context.setProxyUsername( mappedPrincipalName );
            auditService.attachContext(context);
            auditor.audit( Action.IDENTITY_MAPPING, primaryPrincipal.getName(),
                ResourceType.PRINCIPAL, ActionOutcome.SUCCESS, RES.effectiveUser(mappedPrincipalName) );
          }
        }
        else {
          // something is amiss - authentication/federation providers should have run
          // before identity assertion and should have ensured that the appropriate
          // principals were added to the current subject
          // TODO: log as appropriate
          primaryPrincipal = new PrimaryPrincipal(((HttpServletRequest) request).getUserPrincipal().getName());
        }

        groupsMapped = groups != null || !currentGroups.isEmpty();

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
            addMappedGroupsToSubject(mappedPrincipalName, groups, subject);
          }
          doAs(request, response, chain, subject);
        }
        else {
          doFilterInternal(request, response, chain);
        }
      }

  private void doAs(final ServletRequest request, final ServletResponse response, final FilterChain chain, Subject subject)
      throws IOException, ServletException {
    try {
      Subject.doAs(
        subject,
        new PrivilegedExceptionAction<Object>() {
          @Override
          public Object run() throws Exception {
            doFilterInternal(request, response, chain);
            return null;
          }
        });
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

  private void addMappedGroupsToSubject(String mappedPrincipalName, String[] groups, Subject subject) {
    if (groups != null) {
      auditor.audit( Action.IDENTITY_MAPPING, mappedPrincipalName, ResourceType.PRINCIPAL,
          ActionOutcome.SUCCESS, RES.groupsList( Arrays.toString( groups ) ) );

      for (String group : groups) {
        subject.getPrincipals().add(new GroupPrincipal(group));
      }
    }
  }

  private void doFilterInternal(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    chain.doFilter(request, response);
  }
}
