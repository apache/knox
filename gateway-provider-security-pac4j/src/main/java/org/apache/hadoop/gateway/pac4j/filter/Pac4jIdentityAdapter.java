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
package org.apache.hadoop.gateway.pac4j.filter;

import org.apache.hadoop.gateway.audit.api.*;
import org.apache.hadoop.gateway.audit.log4j.audit.AuditConstants;
import org.apache.hadoop.gateway.filter.AbstractGatewayFilter;
import org.apache.hadoop.gateway.security.PrimaryPrincipal;
import org.pac4j.core.config.ConfigSingleton;
import org.pac4j.core.context.J2EContext;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

/**
 * <p>This filter retrieves the authenticated user saved by the pac4j provider and injects it into the J2E HTTP request.</p>
 *
 * @since 0.8.0
 */
public class Pac4jIdentityAdapter implements Filter {

  private final static Logger logger = LoggerFactory.getLogger(Pac4jIdentityAdapter.class);

  private static AuditService auditService = AuditServiceFactory.getAuditService();
  private static Auditor auditor = auditService.getAuditor(
      AuditConstants.DEFAULT_AUDITOR_NAME, AuditConstants.KNOX_SERVICE_NAME,
      AuditConstants.KNOX_COMPONENT_NAME );

  private String testIdentifier;

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
  }

  public void destroy() {
  }

  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
      throws IOException, ServletException {

    final HttpServletRequest request = (HttpServletRequest) servletRequest;
    final HttpServletResponse response = (HttpServletResponse) servletResponse;
    final J2EContext context = new J2EContext(request, response, ConfigSingleton.getConfig().getSessionStore());
    final ProfileManager manager = new ProfileManager(context);
    final UserProfile profile = manager.get(true);
    logger.debug("User authenticated as: {}", profile);
    manager.remove(true);
    final String id = profile.getId();
    testIdentifier = id;
    PrimaryPrincipal pp = new PrimaryPrincipal(id);
    Subject subject = new Subject();
    subject.getPrincipals().add(pp);
    auditService.getContext().setUsername(id);
    String sourceUri = (String)request.getAttribute( AbstractGatewayFilter.SOURCE_REQUEST_CONTEXT_URL_ATTRIBUTE_NAME );
    auditor.audit(Action.AUTHENTICATION, sourceUri, ResourceType.URI, ActionOutcome.SUCCESS);
    
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
              chain.doFilter(request, response);
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

  /**
   * For tests only.
   */
  public static void setAuditService(AuditService auditService) {
    Pac4jIdentityAdapter.auditService = auditService;
  }

  /**
   * For tests only.
   */
  public static void setAuditor(Auditor auditor) {
    Pac4jIdentityAdapter.auditor = auditor;
  }

  /**
   * For tests only.
     */
  public String getTestIdentifier() {
    return testIdentifier;
  }
}
