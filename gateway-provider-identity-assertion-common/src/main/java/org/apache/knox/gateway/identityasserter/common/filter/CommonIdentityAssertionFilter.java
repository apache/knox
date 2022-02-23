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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.IdentityAsserterMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.plang.Ast;
import org.apache.knox.gateway.plang.Parser;
import org.apache.knox.gateway.plang.SyntaxException;
import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.principal.PrincipalMappingException;
import org.apache.knox.gateway.security.principal.SimplePrincipalMapper;

public class CommonIdentityAssertionFilter extends AbstractIdentityAssertionFilter {
  public static final String VIRTUAL_GROUP_MAPPING_PREFIX = "virtual.group.mapping.";
  private IdentityAsserterMessages LOG = MessagesFactory.get(IdentityAsserterMessages.class);

  public static final String GROUP_PRINCIPAL_MAPPING = "group.principal.mapping";
  public static final String PRINCIPAL_MAPPING = "principal.mapping";
  private SimplePrincipalMapper mapper = new SimplePrincipalMapper();
  private final Parser parser = new Parser();
  private VirtualGroupMapper virtualGroupMapper;

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    String principalMapping = filterConfig.getInitParameter(PRINCIPAL_MAPPING);
    if (principalMapping == null || principalMapping.isEmpty()) {
      principalMapping = filterConfig.getServletContext().getInitParameter(PRINCIPAL_MAPPING);
    }
    String groupPrincipalMapping = filterConfig.getInitParameter(GROUP_PRINCIPAL_MAPPING);
    if (groupPrincipalMapping == null || groupPrincipalMapping.isEmpty()) {
      groupPrincipalMapping = filterConfig.getServletContext().getInitParameter(GROUP_PRINCIPAL_MAPPING);
    }
    if (principalMapping != null && !principalMapping.isEmpty() || groupPrincipalMapping != null && !groupPrincipalMapping.isEmpty()) {
      try {
        mapper.loadMappingTable(principalMapping, groupPrincipalMapping);
      } catch (PrincipalMappingException e) {
        throw new ServletException("Unable to load principal mapping table.", e);
      }
    }
    virtualGroupMapper = new VirtualGroupMapper(loadVirtualGroups(filterConfig));
  }

  private Map<String, Ast> loadVirtualGroups(FilterConfig filterConfig) {
    Map<String, Ast> predicateToGroupMapping = new HashMap<>();
    loadVirtualGroupConfig(filterConfig, predicateToGroupMapping);
    if (predicateToGroupMapping.isEmpty() && filterConfig.getServletContext() != null) {
      loadVirtualGroupConfig(filterConfig.getServletContext(), predicateToGroupMapping);
    }
    if (predicateToGroupMapping.keySet().stream().anyMatch(StringUtils::isBlank)) {
      LOG.missingVirtualGroupName();
    }
    return predicateToGroupMapping;
  }

  private void loadVirtualGroupConfig(FilterConfig config, Map<String, Ast> result) {
    for (String paramName : virtualGroupParameterNames(config.getInitParameterNames())) {
      try {
        Ast ast = parser.parse(config.getInitParameter(paramName));
        result.put(paramName.substring(VIRTUAL_GROUP_MAPPING_PREFIX.length()).trim(), ast);
      } catch (SyntaxException e) {
        LOG.parseError(paramName, config.getInitParameter(paramName), e);
      }
    }
  }

  private void loadVirtualGroupConfig(ServletContext context, Map<String, Ast> result) {
    for (String paramName : virtualGroupParameterNames(context.getInitParameterNames())) {
      try {
        Ast ast = parser.parse(context.getInitParameter(paramName));
        result.put(paramName.substring(VIRTUAL_GROUP_MAPPING_PREFIX.length()).trim(), ast);
      } catch (SyntaxException e) {
        LOG.parseError(paramName, context.getInitParameter(paramName), e);
      }
    }
  }

  private static List<String> virtualGroupParameterNames(Enumeration<String> initParameterNames) {
    List<String> result = new ArrayList<>();
    if (initParameterNames == null) {
      return result;
    }
    while (initParameterNames.hasMoreElements()) {
      String name = initParameterNames.nextElement();
      if (name.startsWith(VIRTUAL_GROUP_MAPPING_PREFIX)) {
        result.add(name);
      }
    }
    return result;
  }

  @Override
  public void destroy() {
  }

  /**
   * Obtain the standard javax.security.auth.Subject, retrieve the caller principal, map
   * to the identity to be asserted as appropriate and create the provider specific
   * assertion token. Add the assertion token to the request.
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    Subject subject = Subject.getSubject(AccessController.getContext());

    if (subject == null) {
      LOG.subjectNotAvailable();
      throw new IllegalStateException("Required Subject Missing");
    }

    String principalName = getPrincipalName(subject);

    String mappedPrincipalName = mapUserPrincipalBase(principalName);
    mappedPrincipalName = mapUserPrincipal(mappedPrincipalName);
    String[] mappedGroups = mapGroupPrincipalsBase(mappedPrincipalName, subject);
    String[] groups = mapGroupPrincipals(mappedPrincipalName, subject);
    String[] virtualGroups = virtualGroupMapper.mapGroups(mappedPrincipalName, groups(subject), request).toArray(new String[0]);
    groups = combineGroupMappings(mappedGroups, groups);
    groups = combineGroupMappings(virtualGroups, groups);

    HttpServletRequestWrapper wrapper = wrapHttpServletRequest(
        request, mappedPrincipalName);

    continueChainAsPrincipal(wrapper, response, chain, mappedPrincipalName, groups);
  }

  protected String[] combineGroupMappings(String[] mappedGroups, String[] groups) {
    if (mappedGroups != null && groups != null) {
      return ArrayUtils.addAll(mappedGroups, groups);
    }
    else {
      return groups != null ? groups : mappedGroups;
    }
  }

  public HttpServletRequestWrapper wrapHttpServletRequest(
      ServletRequest request, String mappedPrincipalName) {
    // wrap the request so that the proper principal is returned
    // from request methods
    return new IdentityAsserterHttpServletRequestWrapper(
        (HttpServletRequest) request,
        mappedPrincipalName);
  }

  protected String[] mapGroupPrincipalsBase(String mappedPrincipalName, Subject subject) {
    return mapper.mapGroupPrincipal(mappedPrincipalName);
  }

  protected String mapUserPrincipalBase(String principalName) {
    return mapper.mapUserPrincipal(principalName);
  }

  private Set<String> groups(Subject subject) {
    return subject.getPrincipals(GroupPrincipal.class).stream()
            .map(GroupPrincipal::getName)
            .collect(Collectors.toSet());
  }

  @Override
  public String[] mapGroupPrincipals(String mappedPrincipalName, Subject subject) {
    // NOP
    return null;
  }

  @Override
  public String mapUserPrincipal(String principalName) {
    // NOP
    return principalName;
  }
}
