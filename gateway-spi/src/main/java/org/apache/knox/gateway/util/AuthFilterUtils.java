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

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.authorize.AuthorizationException;
import org.apache.hadoop.security.authorize.ProxyUsers;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import java.security.Principal;
import java.util.Enumeration;
import java.util.Set;
import java.util.StringTokenizer;

public class AuthFilterUtils {
  public static final String DEFAULT_AUTH_UNAUTHENTICATED_PATHS_PARAM = "/knoxtoken/api/v1/jwks.json";

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

  public static Configuration getProxyUserConfiguration(ServletContext context, String prefix) {
    if (context == null) {
      throw new IllegalArgumentException("Cannot get proxyuser configuration from NULL context");
    }
    return getProxyUserConfiguration(context, null, prefix);
  }

  public static Configuration getProxyUserConfiguration(FilterConfig filterConfig, String prefix) {
    if (filterConfig == null) {
      throw new IllegalArgumentException("Cannot get proxyuser configuration from NULL filter config");
    }
    return getProxyUserConfiguration(null, filterConfig, prefix);
  }

  private static Configuration getProxyUserConfiguration(ServletContext context, FilterConfig filterConfig, String prefix) {
    final Configuration conf = new Configuration(false);
    final Enumeration<?> names = context == null ? filterConfig.getInitParameterNames() : context.getInitParameterNames();
    if (names != null) {
      while (names.hasMoreElements()) {
        String name = (String) names.nextElement();
        if (name.startsWith(prefix + ".")) {
          String value = context == null ? filterConfig.getInitParameter(name) : context.getInitParameter(name);
          conf.set(name, value);
        }
      }
    }

    return conf;
  }

  public static HttpServletRequest getProxyRequest(HttpServletRequest request, String doAsUser) throws AuthorizationException {
    final UserGroupInformation remoteRequestUgi = getRemoteRequestUgi(request, doAsUser);
    if (remoteRequestUgi != null) {
      authorizeImpersonationRequest(request, remoteRequestUgi);

      return new HttpServletRequestWrapper(request) {
        @Override
        public String getRemoteUser() {
          return remoteRequestUgi.getShortUserName();
        }

        @Override
        public Principal getUserPrincipal() {
          return remoteRequestUgi::getUserName;
        }
      };

    }
    return null;
  }

  public static void authorizeImpersonationRequest(HttpServletRequest request, String doAsUser) throws AuthorizationException {
    final UserGroupInformation remoteRequestUgi = getRemoteRequestUgi(request, doAsUser);
    if (remoteRequestUgi != null) {
      authorizeImpersonationRequest(request, remoteRequestUgi);
    }
  }

  private static void authorizeImpersonationRequest(HttpServletRequest request, UserGroupInformation remoteRequestUgi) throws AuthorizationException {
    ProxyUsers.authorize(remoteRequestUgi, request.getRemoteAddr());
  }

  private static UserGroupInformation getRemoteRequestUgi(HttpServletRequest request, String doAsUser) {
    if (request.getUserPrincipal() != null) {
      final String remoteUser = request.getUserPrincipal().getName();
      final UserGroupInformation remoteUserUgi = UserGroupInformation.createRemoteUser(remoteUser);
      return UserGroupInformation.createProxyUser(doAsUser, remoteUserUgi);
    }
    return null;
  }

}
