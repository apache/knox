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

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.ShiroMessages;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.util.urltemplate.Matcher;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Template;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.Principal;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

public class ShiroSubjectIdentityAdapter implements Filter {
  private static final ShiroMessages LOG = MessagesFactory.get(ShiroMessages.class);
  private static final String SUBJECT_USER_GROUPS = "subject.userGroups";
  private static AuditService auditService = AuditServiceFactory.getAuditService();
  private static Auditor auditor = auditService.getAuditor(
      AuditConstants.DEFAULT_AUDITOR_NAME, AuditConstants.KNOX_SERVICE_NAME,
      AuditConstants.KNOX_COMPONENT_NAME );
  private static final String SHIRO_URL_CONFIG = "urls";

  /* List of URLs with anon authentication */
  private static List<Matcher> anonUrls = new ArrayList<>();

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    /* Create a shiro urls config map  */
    final Enumeration<String> params = filterConfig.getInitParameterNames();
    while (params.hasMoreElements()) {
      String param = params.nextElement();
      if (StringUtils.startsWithIgnoreCase(param, SHIRO_URL_CONFIG)) {
        String value = filterConfig.getInitParameter(param);
        final String pathParam = param.substring(param.indexOf('.') + 1);
        if("anon".equalsIgnoreCase(value)) {
          final Template urlPatternTemplate;
          final Matcher urlMatcher = new Matcher();
          try {
            urlPatternTemplate = Parser.parseTemplate(pathParam);
            urlMatcher.add(urlPatternTemplate, pathParam);
            anonUrls.add(urlMatcher);
          } catch (URISyntaxException e) {
            LOG.invalidURLPattern(param);
            throw new ServletException(e);
          }

        }

      }
    }
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

    @SuppressWarnings("unchecked")
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

      /**
       * For cases when we want anonymous authentication to urls in shiro.
       * This is when do not want authentication for jwks endpoints using
       * shiro.
       */
      if (shiroSubject == null || shiroSubject.getPrincipal() == null) {

        if(!isRequestPathInShiroConfig(((HttpServletRequest)request))) {
          throw new IllegalStateException("Unable to determine authenticated user from Shiro, please check that your Knox Shiro configuration is correct");
        }

        LOG.unauthenticatedPathBypass(
            ((HttpServletRequest) request).getRequestURI());
        final String principal = "anonymous";
        javax.security.auth.Subject subject = new javax.security.auth.Subject();
        subject.getPrincipals().add(new PrimaryPrincipal(principal));
        AuditContext context = auditService.getContext();
        context.setUsername(principal);
        auditService.attachContext(context);
        javax.security.auth.Subject.doAs(subject, action);
      } else {

        final String principal = shiroSubject.getPrincipal().toString();
        Set<Principal> principals = new HashSet<>();
        Principal p = new PrimaryPrincipal(principal);
        principals.add(p);
        AuditContext context = auditService.getContext();
        context.setUsername(principal);
        auditService.attachContext(context);
        String sourceUri = (String) request.getAttribute(
            AbstractGatewayFilter.SOURCE_REQUEST_CONTEXT_URL_ATTRIBUTE_NAME);
        auditor.audit(Action.AUTHENTICATION, sourceUri, ResourceType.URI,
            ActionOutcome.SUCCESS);

        Set<String> userGroups;
        // map ldap groups saved in session to Java Subject GroupPrincipal(s)
        if (SecurityUtils.getSubject().getSession()
            .getAttribute(SUBJECT_USER_GROUPS) != null) {
          userGroups = (Set<String>) SecurityUtils.getSubject().getSession()
              .getAttribute(SUBJECT_USER_GROUPS);
        } else { // KnoxLdapRealm case
          if (shiroSubject.getPrincipal() instanceof String) {
            userGroups = new HashSet<>(shiroSubject.getPrincipals().asSet());
            userGroups.remove(principal);
          } else { // KnoxPamRealm case
            Set<Principal> shiroPrincipals = new HashSet<>(
                shiroSubject.getPrincipals().asSet());
            userGroups = new HashSet<>(); // Here we are creating a new UserGroup
            // so we don't need to remove a Principal
            // In the case of LDAP the userGroup may have already Principal
            // added to the list of groups, so it is not needed.
            for (Principal shiroPrincipal : shiroPrincipals) {
              userGroups.add(shiroPrincipal.toString());
            }
          }
        }
        for (String userGroup : userGroups) {
          Principal gp = new GroupPrincipal(userGroup);
          principals.add(gp);
        }
        auditor.audit(Action.AUTHENTICATION, sourceUri, ResourceType.URI,
            ActionOutcome.SUCCESS, "Groups: " + userGroups);

        // The newly constructed Sets check whether this Subject has been set read-only
        // before permitting subsequent modifications. The newly created Sets also prevent
        // illegal modifications by ensuring that callers have sufficient permissions.
        //
        // To modify the Principals Set, the caller must have AuthPermission("modifyPrincipals").
        // To modify the public credential Set, the caller must have AuthPermission("modifyPublicCredentials").
        // To modify the private credential Set, the caller must have AuthPermission("modifyPrivateCredentials").
        javax.security.auth.Subject subject = new javax.security.auth.Subject(
            true, principals, Collections.emptySet(), Collections.emptySet());
        javax.security.auth.Subject.doAs(subject, action);
      }

      return null;
    }
  }

  /**
   * A helper function that checks whether the request path is defined in shiro
   * config, specifically under the urls section with anon authentication. e.g.
   * <param>
   * <name>urls./knoxtoken/api/v1/jwks.json</name>
   * <value>anon</value>
   * </param>
   *
   * @param request
   * @return true if request has anon auth.
   * @throws URISyntaxException
   */
  private static boolean isRequestPathInShiroConfig(
      final HttpServletRequest request) throws URISyntaxException {
    boolean isPathInConfig = false;
    final String requestContextPath = StringUtils.startsWith(
        request.getPathInfo(), "/") ?
        request.getPathInfo() :
        "/" + request.getPathInfo();
    final Template requestUrlTemplate = Parser.parseLiteral(requestContextPath);
    for (final Matcher m : anonUrls) {
      if (m.match(requestUrlTemplate) != null) {
        isPathInConfig = true;
      }
    }
    return isPathInConfig;
  }
}
