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
package org.apache.knox.gateway.hadoopauth.filter;

import static org.apache.knox.gateway.hadoopauth.filter.HadoopAuthFilter.SUPPORT_JWT;
import static org.apache.knox.gateway.hadoopauth.filter.HadoopAuthFilter.shouldUseJwtFilter;

import java.io.IOException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.text.ParseException;
import java.util.stream.Collectors;

import javax.security.auth.Subject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.knox.gateway.security.ImpersonatedPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.hadoopauth.HadoopAuthMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.util.AuthFilterUtils;

public class HadoopAuthPostFilter implements Filter {

  private static HadoopAuthMessages log = MessagesFactory.get( HadoopAuthMessages.class );
  private static AuditService auditService = AuditServiceFactory.getAuditService();
  private static Auditor auditor = auditService.getAuditor(
      AuditConstants.DEFAULT_AUDITOR_NAME, AuditConstants.KNOX_SERVICE_NAME,
      AuditConstants.KNOX_COMPONENT_NAME );

  private JWTFederationFilter jwtFilter;

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    final String supportJwt = filterConfig.getInitParameter(SUPPORT_JWT);
    final boolean jwtSupported = Boolean.parseBoolean(supportJwt == null ? "false" : supportJwt);
    if (jwtSupported) {
      jwtFilter = new JWTFederationFilter();
      jwtFilter.init(filterConfig);
    }
  }

  @Override
  public void destroy() {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    Subject subject = null;
    if (shouldUseJwtFilter(jwtFilter, (HttpServletRequest) request)) {
      try {
        Pair<JWTFederationFilter.TokenType, String> wireToken = jwtFilter.getWireToken(request);
        JWTFederationFilter.TokenType tokenType = wireToken.getLeft();
        String token = wireToken.getRight();
        if (JWTFederationFilter.TokenType.JWT.equals(tokenType)) {
          subject = jwtFilter.createSubjectFromToken(token);
        } else if (JWTFederationFilter.TokenType.Passcode.equals(tokenType)) {
          subject = jwtFilter.createSubjectFromTokenIdentifier(token);
        }
      } catch (ParseException | UnknownTokenException e) {
        // NOP: subject remains null -> SC_FORBIDDEN will be returned
      }
    } else {
      final String principal = ((HttpServletRequest) request).getRemoteUser();
      if (principal != null) {
        subject = new Subject();
        // see org.apache.knox.gateway.util.AuthFilterUtils.getProxyRequest(...).new HttpServletRequestWrapper() {...}.getAttribute(String)
        final String realUserName = (String) request.getAttribute(AuthFilterUtils.REAL_USER_NAME_ATTRIBUTE);
        if (StringUtils.isNotBlank(realUserName)) {
          // this means the 'doAs' query parameter was set and proxyuser authorization went well
          // in this case, we need to set two principals:
          // - the primary principal should be the original user who initiated the request
          // - the impersonated principal should be the 'doAs' user (which)
          subject.getPrincipals().add(new PrimaryPrincipal(realUserName));
          subject.getPrincipals().add(new ImpersonatedPrincipal(principal));
        } else {
          // simple case: no 'doAs' -> only the primary principal should be carried over (= the original user who initiated the request)
          subject.getPrincipals().add(new PrimaryPrincipal(principal));
        }
        AuditContext context = auditService.getContext();
        context.setUsername(principal);
        auditService.attachContext(context);
      }
    }

    if (subject != null) {
        log.hadoopAuthAssertedPrincipal(getPrincipalsAsString(subject));
        String sourceUri = (String)request.getAttribute( AbstractGatewayFilter.SOURCE_REQUEST_CONTEXT_URL_ATTRIBUTE_NAME );
        auditor.audit( Action.AUTHENTICATION , sourceUri, ResourceType.URI, ActionOutcome.SUCCESS );
        doAs(request, response, chain, subject);
    } else {
      ((HttpServletResponse)response).sendError(HttpServletResponse.SC_FORBIDDEN, "User not authenticated");
    }
  }

  private String getPrincipalsAsString(Subject subject) {
    return String.join(",", subject.getPrincipals().stream().map(principal -> principal.getName()).collect(Collectors.toSet()));
  }

  private void doAs(final ServletRequest request, final ServletResponse response, final FilterChain chain, Subject subject)
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
    } catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof IOException) {
        throw (IOException) t;
      } else if (t instanceof ServletException) {
        throw (ServletException) t;
      } else {
        throw new ServletException(t);
      }
    }
  }
}
