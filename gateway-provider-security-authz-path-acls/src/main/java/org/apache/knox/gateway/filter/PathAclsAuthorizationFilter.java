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
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.util.urltemplate.Matcher;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Template;

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
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PathAclsAuthorizationFilter implements Filter {
  private static PathAclsAuthorizationMessages log = MessagesFactory.get(
      PathAclsAuthorizationMessages.class);
  private static Auditor auditor = AuditServiceFactory.getAuditService()
      .getAuditor(AuditConstants.DEFAULT_AUDITOR_NAME,
          AuditConstants.KNOX_SERVICE_NAME, AuditConstants.KNOX_COMPONENT_NAME);
  private static String PATH_ACL_POSTFIX = ".path.acl";
  private String resourceRole;
  private String aclProcessingMode;
  private PathAclParser pathAclParser = new PathAclParser();
  private List<String> adminGroups = new ArrayList<>();
  private List<String> adminUsers = new ArrayList<>();
  private Map<String, String> rawRules = new HashMap<>();

  @Override
  public void init(FilterConfig filterConfig) {
    resourceRole = getInitParameter(filterConfig, "resource.role");
    log.initializingForResourceRole(resourceRole);

    String adminGroups = filterConfig.getInitParameter("knox.admin.groups");
    if (adminGroups != null) {
      parseAdminGroupConfig(adminGroups);
    }

    String adminUsers = filterConfig.getInitParameter("knox.admin.users");
    if (adminUsers != null) {
      parseAdminUserConfig(adminUsers);
    }

    /* Get the path rules defined for this services */
    final Enumeration<String> rules = filterConfig.getInitParameterNames();
    while (rules.hasMoreElements()) {
      String rule = rules.nextElement();
      if (StringUtils.endsWithIgnoreCase(rule, PATH_ACL_POSTFIX)
          && StringUtils.containsIgnoreCase(rule, resourceRole)) {
        rawRules.put(rule, filterConfig.getInitParameter(rule));
      }
    }

    /* in case there is a super rule for all services `path.acl` add that too */
    final String pathAcls = getInitParameter(filterConfig, "path.acl");
    if (pathAcls != null) {
      rawRules.put("path.acl", pathAcls);
    }

    /* in case there is a resource rule for the services `{service}.path.acl` add that too */
    final String pathResourceAcls = getInitParameter(filterConfig,
        resourceRole + ".path.acl");
    if (pathResourceAcls != null) {
      rawRules.put(resourceRole + ".path.acl", pathResourceAcls);
    }

    aclProcessingMode = getInitParameter(filterConfig,
        resourceRole + ".path.acl.mode");
    if (aclProcessingMode == null) {
      aclProcessingMode = getInitParameter(filterConfig, "path.acl.mode");
      if (aclProcessingMode == null) {
        aclProcessingMode = "AND";
      }
    }
    log.aclProcessingMode(aclProcessingMode);
    /* Rules for services */
    pathAclParser.parsePathAcls(resourceRole, rawRules);
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

  @Override
  public void destroy() {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain) throws IOException, ServletException {

    boolean accessGranted = enforceAclAuthorizationPolicy(request);
    log.accessGranted(accessGranted);
    String sourceUrl = (String) request.getAttribute(
        AbstractGatewayFilter.SOURCE_REQUEST_CONTEXT_URL_ATTRIBUTE_NAME);
    if (accessGranted) {
      auditor.audit(Action.AUTHORIZATION, sourceUrl, ResourceType.URI,
          ActionOutcome.SUCCESS);
      chain.doFilter(request, response);
    } else {
      auditor.audit(Action.AUTHORIZATION, sourceUrl, ResourceType.URI,
          ActionOutcome.FAILURE);
      sendForbidden((HttpServletResponse) response);
    }
  }

  protected boolean enforceAclAuthorizationPolicy(final ServletRequest request)
      throws IOException {
    /*
       Before enforcing acls check whether there are no acls defined
       which would mean that there are no restrictions
    */
    if (rawRules.isEmpty()) {
      /* no rules defined, free to go */
      return true;
    }
    try {
      String requestURL = ((HttpServletRequest) request).getRequestURL().toString();
      if (((HttpServletRequest) request).getQueryString() != null
          && StringUtils.isNotBlank(
          ((HttpServletRequest) request).getQueryString())) {
        requestURL = requestURL + "?"
            + (((HttpServletRequest) request).getQueryString());
      }

      final Template requestUrlTemplate = Parser.parseLiteral(requestURL);
      final Map<Matcher, AclParser> rulesMap = pathAclParser.getRulesMap();

      /**
       *
       * TODO NOTE: Important thing here is that we have the ability to capture the path or
       * query parameters of incoming request. This gives us an ability to rewrite
       * them based on rules or simply inspect them conditionally based on their values.
       * I don't have a use case in mind but it would be a nice feature if we have a
       * use case in the future.
       */
      /* See if we have any path match */
      for (final Map.Entry<Matcher, AclParser> e : rulesMap.entrySet()) {
        final Matcher<String>.Match match = e.getKey()
            .match(requestUrlTemplate);
        if (match != null) {
          /* we have a path match, now check ACLs */
          return checkACLs(e.getValue(), request);
        }
      }

    } catch (URISyntaxException e) {
      log.errorParsingUrl(e.toString());
      throw new IOException(e);
    }
    return false;
  }

  /* This helper function check whether the user has proper permissions */
  private boolean checkACLs(final AclParser aclParser,
      final ServletRequest request) {

    if (aclParser.users.isEmpty() && aclParser.groups.isEmpty()
        && aclParser.ipv.getIPAddresses().isEmpty()) {
      return true;
    }

    boolean groupAccess = false;
    boolean ipAddrAccess;

    final Subject subject = SubjectUtils.getCurrentSubject();
    final String effectivePrincipalName = SubjectUtils.getEffectivePrincipalName(
        subject);
    log.effectivePrincipal(effectivePrincipalName);
    boolean userAccess = checkUserAcls(effectivePrincipalName, aclParser);
    log.effectivePrincipalHasAccess(userAccess);

    Object[] groups = subject.getPrincipals(GroupPrincipal.class).toArray();
    if (groups.length > 0) {
      groupAccess = checkGroupAcls(groups, aclParser);
      log.groupPrincipalHasAccess(groupAccess);
    } else {
      // if we have no groups in the subject then make
      // it true if there is an anyGroup acl
      // for AND mode and acls like *;*;127.0.0.* we need to
      // make it pass
      if (aclParser.anyGroup && "AND".equals(aclProcessingMode)) {
        groupAccess = true;
      }
    }
    log.remoteIPAddress(((HttpServletRequest) request).getRemoteAddr());
    ipAddrAccess = checkRemoteIpAcls(
        ((HttpServletRequest) request).getRemoteAddr(), aclParser);
    log.remoteIPAddressHasAccess(ipAddrAccess);

    if ("OR".equals(aclProcessingMode)) {
      // need to interpret '*' as excluded for OR semantics
      // to make sense and not grant access to everyone by mistake.
      // exclusion in OR is equivalent to denied
      // so, let's set each one that contains '*' to false.
      if (aclParser.anyUser) {
        userAccess = false;
      }
      if (aclParser.anyGroup) {
        groupAccess = false;
      }
      if (aclParser.ipv.allowsAnyIP()) {
        ipAddrAccess = false;
      }

      return (userAccess || groupAccess || ipAddrAccess);
    } else if ("AND".equals(aclProcessingMode)) {
      return (userAccess && groupAccess && ipAddrAccess);
    }
    return false;
  }

  private boolean checkRemoteIpAcls(final String remoteAddr,
      final AclParser aclParser) {
    boolean allowed;
    if (remoteAddr == null) {
      return false;
    }
    allowed = aclParser.ipv.validateIpAddress(remoteAddr);
    return allowed;
  }

  boolean checkUserAcls(final String userName, final AclParser aclParser) {
    boolean allowed = false;
    if (userName == null) {
      return false;
    }
    if (aclParser.anyUser) {
      allowed = true;
    } else {
      if (aclParser.users.contains(userName)) {
        allowed = true;
      } else if (aclParser.users.contains("KNOX_ADMIN_USERS")
          && adminUsers.contains(userName)) {
        allowed = true;
      }
    }
    return allowed;
  }

  boolean checkGroupAcls(final Object[] userGroups, final AclParser aclParser) {
    boolean allowed;
    if (userGroups == null) {
      return false;
    }
    if (aclParser.anyGroup) {
      allowed = true;
    } else {
      allowed = hasAllowedPrincipal(aclParser.groups, userGroups);
      if (!allowed && aclParser.groups.contains("KNOX_ADMIN_GROUPS")) {
        allowed = hasAllowedPrincipal(adminGroups, userGroups);
      }
    }
    return allowed;
  }

  private boolean hasAllowedPrincipal(List<String> allowed,
      Object[] userGroups) {
    boolean rc = false;
    for (Object userGroup : userGroups) {
      if (allowed.contains(((Principal) userGroup).getName())) {
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
    } catch (final IOException e) {
      log.errorSendCode(e.toString());
    }
  }
}
