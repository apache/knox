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

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.authorize.AuthorizationException;
import org.apache.hadoop.security.authorize.ProxyUsers;
import org.apache.hadoop.util.HttpExceptionUtils;
import org.apache.knox.gateway.GatewayServer;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.hadoopauth.HadoopAuthMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

/*
 * see http://hadoop.apache.org/docs/current/hadoop-auth/Configuration.html
 *
 * CONFIG_PREFIX = "config.prefix
 * AUTH_TYPE = "type", AUTH_TOKEN_VALIDITY = "token.validity"
 * COOKIE_DOMAIN = "cookie.domain", COOKIE_PATH = "cookie.path"
 * SIGNATURE_SECRET = "signature.secret
 * TYPE = "kerberos", PRINCIPAL = TYPE + ".principal", KEYTAB = TYPE + ".keytab"

 * config.prefix=hadoop.auth.config (default: null)
 * hadoop.auth.config.signature.secret=SECRET (default: a simple random number)
 * hadoop.auth.config.type=simple|kerberos|CLASS (default: none, would throw exception)
 * hadoop.auth.config.token.validity=SECONDS (default: 3600 seconds)
 * hadoop.auth.config.cookie.domain=DOMAIN(default: null)
 * hadoop.auth.config.cookie.path=PATH (default: null)
 * hadoop.auth.config.kerberos.principal=HTTP/localhost@LOCALHOST (default: null)
 * hadoop.auth.config.kerberos.keytab=/etc/knox/conf/knox.service.keytab (default: null)
 */

public class HadoopAuthFilter extends
    org.apache.hadoop.security.authentication.server.AuthenticationFilter {

  private static final String QUERY_PARAMETER_DOAS = "doAs";
  private static final String PROXYUSER_PREFIX = "hadoop.proxyuser";

  private static final HadoopAuthMessages LOG = MessagesFactory.get(HadoopAuthMessages.class);

  private final Set<String> ignoreDoAs = new HashSet<>();

  @Override
  protected Properties getConfiguration(String configPrefix, FilterConfig filterConfig) throws ServletException {
    GatewayServices services = GatewayServer.getGatewayServices();
    AliasService aliasService = services.getService(ServiceType.ALIAS_SERVICE);

    return getConfiguration(aliasService, configPrefix, filterConfig);
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    Configuration conf = getProxyuserConfiguration(filterConfig);
    ProxyUsers.refreshSuperUserGroupsConfiguration(conf, PROXYUSER_PREFIX);

    Collection<String> ignoredServices = null;

    // Look for GatewayConfig.PROXYUSER_SERVICES_IGNORE_DOAS value in the filter context, which was created
    // using the relevant topology file...
    String configValue = filterConfig.getInitParameter(GatewayConfig.PROXYUSER_SERVICES_IGNORE_DOAS);
    if (configValue != null) {
      configValue = configValue.trim();
      if (!configValue.isEmpty()) {
        ignoredServices = Arrays.asList(configValue.toLowerCase(Locale.ROOT).split("\\s*,\\s*"));
      }
    }

    // If not set in the topology, look for GatewayConfig.PROXYUSER_SERVICES_IGNORE_DOAS in the
    // gateway site context
    if (ignoredServices == null) {
      Object attributeValue = filterConfig.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
      if (attributeValue instanceof GatewayConfig) {
        ignoredServices = ((GatewayConfig) attributeValue).getServicesToIgnoreDoAs();
      }
    }

    if (ignoredServices != null) {
      ignoreDoAs.addAll(ignoredServices);
    }

    super.init(filterConfig);
  }

  @Override
  protected void doFilter(FilterChain filterChain, HttpServletRequest request,
                          HttpServletResponse response) throws IOException, ServletException {

    /*
     * If impersonation is not ignored for the authenticated user, attempt to set a proxied user if
     * one was specified in the doAs query parameter.  A comma-delimited list of services/users to
     * be ignored may be set in either the relevant topology file or the Gateway's gateway-site
     * configuration file using a property named `gateway.proxyuser.services.ignore.doas`
     *
     * If setting a proxy user, proper authorization checks are made to ensure the authenticated user
     * (proxy user) is allowed to set specified proxied user. It is expected that the relevant
     * topology file has the required hadoop.proxyuser configurations set.
     */
    if (!ignoreDoAs(request.getRemoteUser())) {
      String doAsUser = request.getParameter(QUERY_PARAMETER_DOAS);
      if (doAsUser != null && !doAsUser.equals(request.getRemoteUser())) {
        LOG.hadoopAuthDoAsUser(doAsUser, request.getRemoteUser(), request.getRemoteAddr());

        UserGroupInformation requestUgi = (request.getUserPrincipal() != null)
            ? UserGroupInformation.createRemoteUser(request.getRemoteUser())
            : null;

        if (requestUgi != null) {
          requestUgi = UserGroupInformation.createProxyUser(doAsUser, requestUgi);

          try {
            ProxyUsers.authorize(requestUgi, request.getRemoteAddr());

            final UserGroupInformation ugiF = requestUgi;
            request = new HttpServletRequestWrapper(request) {
              @Override
              public String getRemoteUser() {
                return ugiF.getShortUserName();
              }

              @Override
              public Principal getUserPrincipal() {
                return ugiF::getUserName;
              }
            };

            LOG.hadoopAuthProxyUserSuccess();
          } catch (AuthorizationException ex) {
            HttpExceptionUtils.createServletExceptionResponse(response,
                HttpServletResponse.SC_FORBIDDEN, ex);
            LOG.hadoopAuthProxyUserFailed(ex);
            return;
          }
        }
      }
    }

    super.doFilter(filterChain, request, response);
  }

  /**
   * Tests if the authenticated user/service has impersonation enabled based on previously calculated
   * data (see {@link #init(FilterConfig)}.
   * <p>
   * By default, impersonation is enabled for all.  However if an entry in the pre-calculated data
   * declares that is it disabled, then return false.
   *
   * @param userName the user name to test
   * @return <code>true</code>, if impersonation is enabled for the relative principal; otherwise, <code>false</code>
   */
  boolean ignoreDoAs(String userName) {
    // Return true if one the following conditions have been met:
    // * the userPrincipal is null
    // * the user principal exists on the ignoreDoAs set.
    return (userName == null) || userName.isEmpty() || ignoreDoAs.contains(userName.toLowerCase(Locale.ROOT));
  }

  /**
   * Return a {@link Configuration} instance with the proxy user (<code>hadoop.proxyuser.*</code>)
   * properties set using parameter information from the filterConfig.
   *
   * @param filterConfig the {@link FilterConfig} to query
   * @return a {@link Configuration}
   */
  private Configuration getProxyuserConfiguration(FilterConfig filterConfig) {
    Configuration conf = new Configuration(false);

    // Iterate through the init parameters of the filter configuration to add Hadoop proxyuser
    // parameters to the configuration instance
    Enumeration<?> names = filterConfig.getInitParameterNames();
    while (names.hasMoreElements()) {
      String name = (String) names.nextElement();
      if (name.startsWith(PROXYUSER_PREFIX + ".")) {
        String value = filterConfig.getInitParameter(name);
        conf.set(name, value);
      }
    }

    return conf;
  }

  // Visible for testing
  Properties getConfiguration(AliasService aliasService, String configPrefix,
                                        FilterConfig filterConfig) throws ServletException {

    String clusterName = filterConfig.getInitParameter("clusterName");

    Properties props = new Properties();
    Enumeration<String> names = filterConfig.getInitParameterNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      if (name.startsWith(configPrefix)) {
        String value = filterConfig.getInitParameter(name);

        // Handle the case value is an alias
        if (value.startsWith("${ALIAS=") && value.endsWith("}")) {
          String alias = value.substring("${ALIAS=".length(), value.length() - 1);
          try {
            value = String.valueOf(
                aliasService.getPasswordFromAliasForCluster(clusterName, alias));
          } catch (AliasServiceException e) {
            throw new ServletException("Unable to retrieve alias for config: " + name, e);
          }
        }

        props.put(name.substring(configPrefix.length()), value);
      }
    }
    return props;
  }
}
