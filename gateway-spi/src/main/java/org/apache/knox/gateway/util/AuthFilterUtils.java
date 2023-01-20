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
package org.apache.knox.gateway.util;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.authorize.DefaultImpersonationProvider;
import org.apache.hadoop.security.authorize.ImpersonationProvider;
import org.apache.knox.gateway.i18n.GatewaySpiMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

public class AuthFilterUtils {
  public static final String DEFAULT_AUTH_UNAUTHENTICATED_PATHS_PARAM = "/knoxtoken/api/v1/jwks.json";
  public static final String PROXYUSER_PREFIX = "hadoop.proxyuser";
  public static final String QUERY_PARAMETER_DOAS = "doAs";
  public static final String REAL_USER_NAME_ATTRIBUTE = "real.user.name";

  private static final GatewaySpiMessages LOG = MessagesFactory.get(GatewaySpiMessages.class);
  private static final Map<String, Map<String, ImpersonationProvider>> TOPOLOGY_IMPERSONATION_PROVIDERS = new ConcurrentHashMap<>();
  private static final Lock refreshSuperUserGroupsLock = new ReentrantLock();

  /**
   * A helper method that checks whether request contains
   * unauthenticated path
   * @param request
   * @return
   */
  public static boolean doesRequestContainUnauthPath(
      final Set<String> unAuthenticatedPaths, final ServletRequest request) {
    /* make sure the path matches EXACTLY to prevent auth bypass */
    return unAuthenticatedPaths.contains(((HttpServletRequest) request).getPathInfo());
  }

  /**
   * A helper method that parses a string and adds to the
   * provided unauthenticated set.
   * @param unAuthenticatedPaths
   * @param list
   */
  public static void parseStringThenAdd(final Set<String> unAuthenticatedPaths, final String list) {
    final StringTokenizer tokenizer = new StringTokenizer(list, ";,");
    while (tokenizer.hasMoreTokens()) {
      unAuthenticatedPaths.add(tokenizer.nextToken());
    }
  }

  /**
   * A method that parses a string (delimiters = ;,) and adds them to the
   * provided un-authenticated path set.
   * @param unAuthenticatedPaths
   * @param list
   * @param defaultList
   */
  public static void addUnauthPaths(final Set<String> unAuthenticatedPaths, final String list, final String defaultList) {
    /* add default unauthenticated paths list */
    parseStringThenAdd(unAuthenticatedPaths, defaultList);
    /* add provided unauthenticated paths list if specified */
    if (!StringUtils.isBlank(list)) {
      AuthFilterUtils.parseStringThenAdd(unAuthenticatedPaths, list);
    }
  }

  public static void refreshSuperUserGroupsConfiguration(ServletContext context, List<String> initParameterNames, String topologyName, String role) {
    if (context == null) {
      throw new IllegalArgumentException("Cannot get proxyuser configuration from NULL context");
    }
    refreshSuperUserGroupsConfiguration(context, null, initParameterNames, topologyName, role);
  }

  public static void refreshSuperUserGroupsConfiguration(FilterConfig filterConfig, List<String> initParameterNames, String topologyName, String role) {
    if (filterConfig == null) {
      throw new IllegalArgumentException("Cannot get proxyuser configuration from NULL filter config");
    }
    refreshSuperUserGroupsConfiguration(null, filterConfig, initParameterNames, topologyName, role);
  }

  private static void refreshSuperUserGroupsConfiguration(ServletContext context, FilterConfig filterConfig, List<String> initParameterNames, String topologyName, String role) {
    final Configuration conf = new Configuration(false);
    if (initParameterNames != null) {
      initParameterNames.stream().filter(name -> name.startsWith(PROXYUSER_PREFIX + ".")).forEach(name -> {
        String value = context == null ? filterConfig.getInitParameter(name) : context.getInitParameter(name);
        conf.set(name, value);
      });
    }

    saveImpersonationProvider(topologyName, role, conf);
  }

  private static void saveImpersonationProvider(String topologyName, String role, final Configuration conf) {
    refreshSuperUserGroupsLock.lock();
    try {
      final ImpersonationProvider impersonationProvider = new DefaultImpersonationProvider();
      impersonationProvider.setConf(conf);
      impersonationProvider.init(PROXYUSER_PREFIX);
      LOG.createImpersonationProvider(topologyName, role, PROXYUSER_PREFIX, conf.getPropsWithPrefix(PROXYUSER_PREFIX + ".").toString());
      TOPOLOGY_IMPERSONATION_PROVIDERS.putIfAbsent(topologyName, new ConcurrentHashMap<String, ImpersonationProvider>());
      TOPOLOGY_IMPERSONATION_PROVIDERS.get(topologyName).put(role, impersonationProvider);
    } finally {
      refreshSuperUserGroupsLock.unlock();
    }
  }

  public static HttpServletRequest getProxyRequest(HttpServletRequest request, String doAsUser, String topologyName, String role) throws AuthorizationException {
    return getProxyRequest(request, request.getUserPrincipal().getName(), doAsUser, topologyName, role);
  }

  public static HttpServletRequest getProxyRequest(HttpServletRequest request, String remoteUser, String doAsUser, String topologyName, String role) throws AuthorizationException {
    final UserGroupInformation remoteRequestUgi = getRemoteRequestUgi(remoteUser, doAsUser);
    if (remoteRequestUgi != null) {
      authorizeImpersonationRequest(request, remoteRequestUgi, topologyName, role);

      return new HttpServletRequestWrapper(request) {
        @Override
        public String getRemoteUser() {
          return remoteRequestUgi.getShortUserName();
        }

        @Override
        public Principal getUserPrincipal() {
          return remoteRequestUgi::getUserName;
        }

        @Override
        public Object getAttribute(String name) {
          if (name != null && name.equals(REAL_USER_NAME_ATTRIBUTE)) {
            return remoteRequestUgi.getRealUser().getShortUserName();
          } else {
            return super.getAttribute(name);
          }
        }
      };

    }
    return null;
  }

  public static void authorizeImpersonationRequest(HttpServletRequest request, String remoteUser, String doAsUser, String topologyName, String role) throws AuthorizationException {
    final UserGroupInformation remoteRequestUgi = getRemoteRequestUgi(remoteUser, doAsUser);
    if (remoteRequestUgi != null) {
      authorizeImpersonationRequest(request, remoteRequestUgi, topologyName, role);
    }
  }

  private static void authorizeImpersonationRequest(HttpServletRequest request, UserGroupInformation remoteRequestUgi, String topologyName, String role)
      throws AuthorizationException {

    final ImpersonationProvider impersonationProvider = getImpersonationProvider(topologyName, role);

    if (impersonationProvider != null) {
      try {
        impersonationProvider.authorize(remoteRequestUgi, request.getRemoteAddr());
      } catch (org.apache.hadoop.security.authorize.AuthorizationException e) {
        throw new AuthorizationException(e);
      }
    } else {
      throw new AuthorizationException("ImpersonationProvider for " + topologyName + " / " + role + " not found!");
    }
  }

  private static ImpersonationProvider getImpersonationProvider(String topologyName, String role) {
    refreshSuperUserGroupsLock.lock();
    final ImpersonationProvider impersonationProvider;
    try {
      impersonationProvider = (TOPOLOGY_IMPERSONATION_PROVIDERS.getOrDefault(topologyName, Collections.emptyMap())).get(role);
    } finally {
      refreshSuperUserGroupsLock.unlock();
    }
    return impersonationProvider;
  }

  private static UserGroupInformation getRemoteRequestUgi(String remoteUser, String doAsUser) {
    if (remoteUser != null) {
      final UserGroupInformation remoteUserUgi = UserGroupInformation.createRemoteUser(remoteUser);
      return UserGroupInformation.createProxyUser(doAsUser, remoteUserUgi);
    }
    return null;
  }

  public static boolean hasProxyConfig(String topologyName, String role) {
    return getImpersonationProvider(topologyName, role) != null;
  }

  public static void removeProxyUserConfig(String topologyName, String role) {
    if (hasProxyConfig(topologyName, role)) {
      refreshSuperUserGroupsLock.lock();
      try {
        TOPOLOGY_IMPERSONATION_PROVIDERS.get(topologyName).remove(role);
      } finally {
        refreshSuperUserGroupsLock.unlock();
      }
    }
  }

  /**
   * FilterConfig.getInitParameters() returns an enumeration and the first time we
   * iterate thru on its elements we can process the parameter names as desired
   * (because hasMoreElements returns true). The subsequent calls, however, will not
   * succeed because getInitParameters() returns the same object where the
   * hasMoreElements returns false.
   * <p>
   * In classes where there are multiple iterations should be conducted, a
   * collection should be used instead.
   *
   * @return the names of the filter's initialization parameters as a List of
   *         String objects, or an empty List if the filter has no initialization
   *         parameters.
   */
  public static List<String> getInitParameterNamesAsList(FilterConfig filterConfig) {
    return filterConfig.getInitParameterNames() == null ? Collections.emptyList() : Collections.list(filterConfig.getInitParameterNames());
  }

}
