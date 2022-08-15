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
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
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
import org.apache.knox.gateway.plang.AbstractSyntaxTree;
import org.apache.knox.gateway.plang.Parser;
import org.apache.knox.gateway.plang.SyntaxException;
import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.principal.PrincipalMappingException;
import org.apache.knox.gateway.security.principal.SimplePrincipalMapper;

import static org.apache.knox.gateway.identityasserter.common.filter.AbstractIdentityAsserterDeploymentContributor.IMPERSONATION_PARAMS;

public class CommonIdentityAssertionFilter extends AbstractIdentityAssertionFilter {
  public static final String VIRTUAL_GROUP_MAPPING_PREFIX = "group.mapping.";
  private IdentityAsserterMessages LOG = MessagesFactory.get(IdentityAsserterMessages.class);

  public static final String GROUP_PRINCIPAL_MAPPING = "group.principal.mapping";
  public static final String PRINCIPAL_MAPPING = "principal.mapping";

  private static final String PRINCIPAL_PARAM = "user.name";
  private static final String DOAS_PRINCIPAL_PARAM = "doAs";
  private SimplePrincipalMapper mapper = new SimplePrincipalMapper();
  private final Parser parser = new Parser();
  private VirtualGroupMapper virtualGroupMapper;
  /* List of all default and configured impersonation params */
  protected final List<String> impersonationParamsList = new ArrayList<>();

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
    String impersonationListFromConfig = filterConfig.getInitParameter(IMPERSONATION_PARAMS);
    if (impersonationListFromConfig == null || impersonationListFromConfig.isEmpty()) {
      impersonationListFromConfig = filterConfig.getServletContext().getInitParameter(IMPERSONATION_PARAMS);
    }
    initImpersonationParamsList(impersonationListFromConfig);
  }

  /**
   * Initialize the impersonation params list.
   * This list contains query params that needs to be scrubbed
   * from the outgoing request.
   * @param impersonationListFromConfig
   * @return
   */
  private void initImpersonationParamsList(final String impersonationListFromConfig) {
    /* Add default impersonation params */
    impersonationParamsList.add(DOAS_PRINCIPAL_PARAM);
    impersonationParamsList.add(PRINCIPAL_PARAM);
    if(null == impersonationListFromConfig || impersonationListFromConfig.isEmpty()) {
      return;
    } else {
      /* Add configured impersonation params */
      LOG.impersonationConfig(impersonationListFromConfig);
      final StringTokenizer t = new StringTokenizer(impersonationListFromConfig, ",");
      while(t.hasMoreElements()) {
        final String token = t.nextToken().trim();
        if(!impersonationParamsList.contains(token)) {
          impersonationParamsList.add(token);
        }
      }
    }
  }

  private Map<String, AbstractSyntaxTree> loadVirtualGroups(FilterConfig filterConfig) {
    Map<String, AbstractSyntaxTree> predicateToGroupMapping = new HashMap<>();
    loadVirtualGroupConfig(filterConfig, predicateToGroupMapping);
    if (predicateToGroupMapping.isEmpty() && filterConfig.getServletContext() != null) {
      loadVirtualGroupConfig(filterConfig.getServletContext(), predicateToGroupMapping);
    }
    return predicateToGroupMapping;
  }

  private void loadVirtualGroupConfig(FilterConfig config, Map<String, AbstractSyntaxTree> result) {
    for (String paramName : virtualGroupParameterNames(config.getInitParameterNames())) {
      addGroup(result, paramName, config.getInitParameter(paramName));
    }
  }

  private void loadVirtualGroupConfig(ServletContext context, Map<String, AbstractSyntaxTree> result) {
    for (String paramName : virtualGroupParameterNames(context.getInitParameterNames())) {
      addGroup(result, paramName, context.getInitParameter(paramName));
    }
  }

  private void addGroup(Map<String, AbstractSyntaxTree> result, String paramName, String predicate) {
    try {
      AbstractSyntaxTree ast = parser.parse(predicate);
      String groupName = paramName.substring(VIRTUAL_GROUP_MAPPING_PREFIX.length()).trim();
      if (StringUtils.isBlank(groupName)) {
        LOG.missingVirtualGroupName();
      } else {
        result.put(groupName, ast);
      }
    } catch (SyntaxException e) {
      LOG.parseError(paramName, predicate, e);
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
    String[] virtualGroups = virtualGroupMapper.mapGroups(mappedPrincipalName, combine(subject, groups), request).toArray(new String[0]);
    groups = combineGroupMappings(mappedGroups, groups);
    groups = combineGroupMappings(virtualGroups, groups);

    HttpServletRequestWrapper wrapper = wrapHttpServletRequest(
        request, mappedPrincipalName);

    continueChainAsPrincipal(wrapper, response, chain, mappedPrincipalName, unique(groups));
  }

  private Set<String> combine(Subject subject, String[] groups) {
    Set<String> result = groups(subject);
    if (groups != null) {
      result.addAll(Arrays.asList(groups));
    }
    return result;
  }

  private static String[] unique(String[] groups) {
    return new HashSet<>(Arrays.asList(groups)).toArray(new String[0]);
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
        mappedPrincipalName,
        impersonationParamsList);
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
