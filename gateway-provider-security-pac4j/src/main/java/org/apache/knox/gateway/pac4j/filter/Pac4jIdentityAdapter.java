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
package org.apache.knox.gateway.pac4j.filter;

import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.profile.ProfileManager;

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
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Optional;

/**
 * <p>This filter retrieves the authenticated user saved by the pac4j provider and injects it into the J2E HTTP request.</p>
 *
 * @since 0.8.0
 */
public class Pac4jIdentityAdapter implements Filter {

  private static final Logger logger = LogManager.getLogger(Pac4jIdentityAdapter.class);

  public static final String PAC4J_ID_ATTRIBUTE = "pac4j.id_attribute";
  private static final String PAC4J_CONFIG = "pac4j.config";

  private static AuditService auditService = AuditServiceFactory.getAuditService();
  private static Auditor auditor = auditService.getAuditor(
      AuditConstants.DEFAULT_AUDITOR_NAME, AuditConstants.KNOX_SERVICE_NAME,
      AuditConstants.KNOX_COMPONENT_NAME );

  private String testIdentifier;

  private String idAttribute;

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    idAttribute = filterConfig.getInitParameter(PAC4J_ID_ATTRIBUTE);
  }

  @Override
  public void destroy() {
  }

  @Override
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
      throws IOException, ServletException {

    final HttpServletRequest request = (HttpServletRequest) servletRequest;
    final HttpServletResponse response = (HttpServletResponse) servletResponse;
    final JEEContext context = new JEEContext(request, response, ((Config)request.getAttribute(PAC4J_CONFIG)).getSessionStore());
    final ProfileManager<CommonProfile> manager = new ProfileManager<>(context);
    final Optional<CommonProfile> optional = manager.get(true);
    if (optional.isPresent()) {
      CommonProfile profile = optional.get();
      logger.debug("User authenticated as: {}", profile);
      manager.remove(true);
      String id = null;
      if (idAttribute != null) {
        Object attribute = profile.getAttribute(idAttribute);
        if (attribute != null) {
          id = attribute.toString();
        }
        if (id == null) {
          logger.error("Invalid attribute_id: {} configured to be used as principal"
              + " falling back to default id", idAttribute);
        }
      }
      if (id == null) {
        id = profile.getId();
      }
      testIdentifier = id;
      PrimaryPrincipal pp = new PrimaryPrincipal(id);
      Subject subject = new Subject();
      subject.getPrincipals().add(pp);

      AuditContext auditContext = auditService.getContext();
      auditContext.setUsername(id);
      auditService.attachContext(auditContext);
      String sourceUri = (String)request.getAttribute( AbstractGatewayFilter.SOURCE_REQUEST_CONTEXT_URL_ATTRIBUTE_NAME );
      auditor.audit(Action.AUTHENTICATION, sourceUri, ResourceType.URI, ActionOutcome.SUCCESS);

      doAs(request, response, chain, subject);
    }
  }

  private void doAs(final ServletRequest request,
      final ServletResponse response, final FilterChain chain, Subject subject)
      throws IOException, ServletException {
    try {
      Subject.doAs(
          subject,
          new PrivilegedExceptionAction<Object>() {
            @Override
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
   * @param auditService AuditService to set
   */
  public static void setAuditService(AuditService auditService) {
    Pac4jIdentityAdapter.auditService = auditService;
  }

  /**
   * For tests only.
   * @param auditor Auditor to set
   */
  public static void setAuditor(Auditor auditor) {
    Pac4jIdentityAdapter.auditor = auditor;
  }

  /**
   * For tests only.
   * @return testIdentifier
   */
  public String getTestIdentifier() {
    return testIdentifier;
  }
}
