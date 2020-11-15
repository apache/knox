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
package org.apache.knox.gateway.clientcert.filter;

import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.util.X500PrincipalParser;

import java.io.IOException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.cert.X509Certificate;

import javax.security.auth.Subject;
import javax.security.auth.x500.X500Principal;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ClientCertFilter implements Filter {
  private static ClientCertMessages log = MessagesFactory.get( ClientCertMessages.class );
  private static final String CLIENT_CERT_PRINCIPAL_ATTRIBUTE_NAME = "client.cert.principal.attribute.name";
  private static AuditService auditService = AuditServiceFactory.getAuditService();
  private static Auditor auditor = auditService.getAuditor(
      AuditConstants.DEFAULT_AUDITOR_NAME, AuditConstants.KNOX_SERVICE_NAME,
      AuditConstants.KNOX_COMPONENT_NAME );
  private String principalAttributeName;

  @Override
  public void init(FilterConfig filterConfig) {
    principalAttributeName = filterConfig.getInitParameter(CLIENT_CERT_PRINCIPAL_ATTRIBUTE_NAME);
    if (principalAttributeName == null) {
      principalAttributeName = "DN";
    }
    else if (!"DN".equalsIgnoreCase(principalAttributeName) &&
        !"CN".equalsIgnoreCase(principalAttributeName)) {
      log.unknownCertificateAttribute(principalAttributeName);
      principalAttributeName = "DN";
    }
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest)request;
    X509Certificate cert = extractCertificate(httpRequest);
    if (cert != null) {
      String principal = extractPrincipalFromCert(cert);

      Subject subject = new Subject();
      subject.getPrincipals().add(new PrimaryPrincipal(principal));
      auditService.getContext().setUsername(principal);
      String sourceUri = (String) request.getAttribute(AbstractGatewayFilter.SOURCE_REQUEST_CONTEXT_URL_ATTRIBUTE_NAME);
      auditor.audit(Action.AUTHENTICATION, sourceUri, ResourceType.URI, ActionOutcome.SUCCESS);
      continueWithEstablishedSecurityContext(subject, httpRequest, (HttpServletResponse) response, filterChain);
    } else {
      ((HttpServletResponse)response).sendError(HttpServletResponse.SC_FORBIDDEN, "User not authenticated");
    }
  }

  private String extractPrincipalFromCert(X509Certificate cert) {
    String p = null;
    if ("DN".equalsIgnoreCase(principalAttributeName)) {
      p =  cert.getSubjectDN().getName();
    }
    else if ("CN".equalsIgnoreCase(principalAttributeName)) {
      X500Principal x500Principal = cert.getSubjectX500Principal();
      X500PrincipalParser parser = new X500PrincipalParser(x500Principal);
      p = parser.getCN();
    }
    else {
      log.unknownCertificateAttribute(principalAttributeName);
      p =  cert.getSubjectDN().getName();
    }

    return p;
  }

  private X509Certificate extractCertificate(HttpServletRequest req) {
    X509Certificate[] certs = (X509Certificate[]) req.getAttribute("javax.servlet.request.X509Certificate");
    if (null != certs && certs.length > 0) {
      return certs[0];
    }
    return null;
  }

  private void continueWithEstablishedSecurityContext(Subject subject, final HttpServletRequest request,
                                                      final HttpServletResponse response, final FilterChain chain)
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

  @Override
  public void destroy() {

  }
}
