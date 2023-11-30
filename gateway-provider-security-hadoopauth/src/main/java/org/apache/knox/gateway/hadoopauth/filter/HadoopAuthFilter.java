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

import org.apache.knox.gateway.GatewayServer;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.context.ContextAttributes;
import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.hadoopauth.HadoopAuthMessages;
import org.apache.knox.gateway.hadoopauth.deploy.HadoopAuthDeploymentContributor;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.util.AuthFilterUtils;
import org.apache.knox.gateway.util.AuthorizationException;
import org.apache.knox.gateway.util.HttpExceptionUtils;

import javax.security.auth.Subject;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import static org.apache.knox.gateway.util.AuthFilterUtils.DEFAULT_AUTH_UNAUTHENTICATED_PATHS_PARAM;

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

  static final String SUPPORT_JWT = "support.jwt";

  private static final HadoopAuthMessages LOG = MessagesFactory.get(HadoopAuthMessages.class);

  /* A semicolon separated list of paths that need to bypass authentication */
  private static final String HADOOP_AUTH_UNAUTHENTICATED_PATHS_PARAM = "hadoop.auth.unauthenticated.path.list";
  private static AuditService auditService = AuditServiceFactory.getAuditService();
  private static Auditor auditor = auditService.getAuditor(
      AuditConstants.DEFAULT_AUDITOR_NAME, AuditConstants.KNOX_SERVICE_NAME,
      AuditConstants.KNOX_COMPONENT_NAME );

  private final Set<String> ignoreDoAs = new HashSet<>();
  private JWTFederationFilter jwtFilter;
  private Set<String> unAuthenticatedPaths = new HashSet<>(20);
  private String topologyName;

  @Override
  protected Properties getConfiguration(String configPrefix, FilterConfig filterConfig) throws ServletException {
    GatewayServices services = GatewayServer.getGatewayServices();
    AliasService aliasService = services.getService(ServiceType.ALIAS_SERVICE);

    return getConfiguration(aliasService, configPrefix, filterConfig);
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    this.topologyName = (String) filterConfig.getInitParameter("clusterName");
    final List<String> initParameterNames = AuthFilterUtils.getInitParameterNamesAsList(filterConfig);
    AuthFilterUtils.refreshSuperUserGroupsConfiguration(filterConfig, initParameterNames, topologyName, HadoopAuthDeploymentContributor.NAME);
    filterConfig.getServletContext().setAttribute(ContextAttributes.IMPERSONATION_ENABLED_ATTRIBUTE, Boolean.TRUE);

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

    final String supportJwt = filterConfig.getInitParameter(SUPPORT_JWT);
    final boolean jwtSupported = Boolean.parseBoolean(supportJwt == null ? "false" : supportJwt);
    if (jwtSupported) {
      jwtFilter = new JWTFederationFilter();
      jwtFilter.init(filterConfig);
      LOG.initializedJwtFilter();
    }

    final String unAuthPathString = filterConfig
        .getInitParameter(HADOOP_AUTH_UNAUTHENTICATED_PATHS_PARAM);
    /* prepare a list of allowed unauthenticated paths */
    AuthFilterUtils.addUnauthPaths(unAuthenticatedPaths, unAuthPathString, DEFAULT_AUTH_UNAUTHENTICATED_PATHS_PARAM);
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) throws IOException, ServletException {
    /* check for unauthenticated paths to bypass */

    if(AuthFilterUtils.doesRequestContainUnauthPath(unAuthenticatedPaths, request)) {
      continueWithAnonymousSubject(request, response, filterChain);
      return;
    }
    if (shouldUseJwtFilter(jwtFilter, (HttpServletRequest) request)) {
      LOG.useJwtFilter();
      jwtFilter.doFilter(request, response, filterChain);
    } else {
      super.doFilter(request, response, filterChain);
    }
  }

  @Override
  protected void doFilter(FilterChain filterChain, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    /* check for unauthenticated paths to bypass */
    if(AuthFilterUtils.doesRequestContainUnauthPath(unAuthenticatedPaths, request)) {
      continueWithAnonymousSubject(request, response, filterChain);
      return;
    }
    if (shouldUseJwtFilter(jwtFilter, request)) {
      LOG.useJwtFilter();
      jwtFilter.doFilter(request, response, filterChain);
      return;
    }

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
    HttpServletRequest proxyRequest = null;
    final String remoteUser = request.getRemoteUser();
    if (!ignoreDoAs(remoteUser)) {
      final String doAsUser = request.getParameter(AuthFilterUtils.QUERY_PARAMETER_DOAS);
      if (doAsUser != null && !doAsUser.equals(remoteUser)) {
        LOG.hadoopAuthDoAsUser(doAsUser, remoteUser, request.getRemoteAddr());
        if (request.getUserPrincipal() != null) {
          try {
            proxyRequest = AuthFilterUtils.getProxyRequest(request, doAsUser, topologyName, HadoopAuthDeploymentContributor.NAME);
            LOG.hadoopAuthProxyUserSuccess();
          } catch (AuthorizationException ex) {
            HttpExceptionUtils.createServletExceptionResponse(response, HttpServletResponse.SC_FORBIDDEN, ex);
            LOG.hadoopAuthProxyUserFailed(ex);
            return;
          }
        }
      }
    }

    super.doFilter(filterChain, proxyRequest == null ? request : proxyRequest, response);
  }

  /**
   * A function that let's configured unauthenticated path requests to
   * pass through without requiring authentication.
   * An anonymous subject is created and the request is audited.
   *
   * Fail gracefully by logging error message.
   * @param request
   * @param response
   * @param chain
   */
  private void continueWithAnonymousSubject(final ServletRequest request,
      final ServletResponse response, final FilterChain chain)
      throws ServletException, IOException {
    try {
      /* This path is configured as an unauthenticated path let the request through */
      final Subject sub = new Subject();
      sub.getPrincipals().add(new PrimaryPrincipal("anonymous"));
      LOG.unauthenticatedPathBypass(((HttpServletRequest) request).getRequestURI(), unAuthenticatedPaths.toString());
      continueWithEstablishedSecurityContext(sub, (HttpServletRequest) request, (HttpServletResponse) response, chain);

    } catch (final Exception e) {
      LOG.unauthenticatedPathError(
          ((HttpServletRequest) request).getRequestURI(), e.toString());
      throw e;
    }
  }

  protected void continueWithEstablishedSecurityContext(final Subject subject, final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain) throws IOException, ServletException {
    Principal principal = (Principal) subject.getPrincipals(PrimaryPrincipal.class).toArray()[0];
    AuditContext context = auditService.getContext();
    if (context != null) {
      context.setUsername( principal.getName() );
      auditService.attachContext(context);
      String sourceUri = (String)request.getAttribute( AbstractGatewayFilter.SOURCE_REQUEST_CONTEXT_URL_ATTRIBUTE_NAME );
      if (sourceUri != null) {
        auditor.audit( Action.AUTHENTICATION , sourceUri, ResourceType.URI, ActionOutcome.SUCCESS );
      }
    }

    try {
      Subject.doAs(
          subject,
          new PrivilegedExceptionAction<Object>() {
            @Override
            public Object run() throws Exception {
              chain.doFilter(new AnonymousRequest(request, principal), response);
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

  static boolean shouldUseJwtFilter(JWTFederationFilter jwtFilter, HttpServletRequest request)
      throws IOException, ServletException {
    return jwtFilter == null ? false : jwtFilter.getWireToken(request) != null;
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

  // Visible for testing
  Properties getConfiguration(AliasService aliasService, String configPrefix, FilterConfig filterConfig) throws ServletException {
    final Properties props = new Properties();
    final Enumeration<String> names = filterConfig.getInitParameterNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      if (name.startsWith(configPrefix)) {
        String value = handleAlias(aliasService, filterConfig, filterConfig.getInitParameter(name), name);
        props.put(name.substring(configPrefix.length()), value);
      }
    }
    return props;
  }

  private String handleAlias(AliasService aliasService, FilterConfig filterConfig, String value, String name) throws ServletException {
    String result = value;
    // Handle the case value is an alias
    if (value.startsWith("${ALIAS=") && value.endsWith("}")) {
      try {
        final String clusterName = filterConfig.getInitParameter("clusterName");
        final String alias = value.substring("${ALIAS=".length(), value.length() - 1);
        final char[] topologyLevelAliasValue = aliasService.getPasswordFromAliasForCluster(clusterName, alias);
        if (topologyLevelAliasValue == null) {
          //try on gateway-level
          final char[] gatewayLevelAliasValue = aliasService.getPasswordFromAliasForGateway(alias);
          if (gatewayLevelAliasValue != null) {
            result = String.valueOf(gatewayLevelAliasValue);
          } else {
            LOG.noAliasStored(clusterName, alias);
          }
        } else {
          result = String.valueOf(topologyLevelAliasValue);
        }
      } catch (AliasServiceException e) {
        throw new ServletException("Unable to retrieve alias for config: " + name, e);
      }
    }
    return result;
  }

  boolean isJwtSupported() {
    return jwtFilter != null;
  }

  /**
   * A wrapper around the request that returns anonymous subject.
   */
  private class AnonymousRequest extends HttpServletRequestWrapper {
    private Principal principal;

    AnonymousRequest(HttpServletRequest req, Principal principal) {
      super(req);
      this.principal = principal;
    }

    /**
     * The default behavior of this method is to return getRemoteUser()
     * on the wrapped request object.
     */
    @Override
    public String getRemoteUser() {
      return principal.getName();
    }

    /**
     * The default behavior of this method is to return getUserPrincipal()
     * on the wrapped request object.
     */
    @Override
    public Principal getUserPrincipal() {
      return principal;
    }
  }
}
