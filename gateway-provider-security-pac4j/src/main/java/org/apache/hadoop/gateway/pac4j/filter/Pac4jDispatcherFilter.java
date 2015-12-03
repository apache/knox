/**
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
package org.apache.hadoop.gateway.pac4j.filter;

import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.pac4j.Pac4jMessages;
import org.apache.hadoop.gateway.pac4j.session.KnoxSessionStore;
import org.apache.hadoop.gateway.services.GatewayServices;
import org.apache.hadoop.gateway.services.security.AliasService;
import org.apache.hadoop.gateway.services.security.AliasServiceException;
import org.apache.hadoop.gateway.services.security.CryptoService;
import org.pac4j.config.client.ConfigPropertiesFactory;
import org.pac4j.core.client.Client;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.config.ConfigSingleton;
import org.pac4j.core.context.J2EContext;
import org.pac4j.core.context.Pac4jConstants;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.http.client.indirect.IndirectBasicAuthClient;
import org.pac4j.http.credentials.authenticator.test.SimpleTestUsernamePasswordAuthenticator;
import org.pac4j.j2e.filter.CallbackFilter;
import org.pac4j.j2e.filter.RequiresAuthenticationFilter;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>This is the main filter for the pac4j provider. The pac4j provider module heavily relies on the j2e-pac4j library (https://github.com/pac4j/j2e-pac4j).</p>
 * <p>This filter dispatches the HTTP calls between the j2e-pac4j filters:</p>
 * <ul>
 *     <li>to the {@link CallbackFilter} if the <code>client_name</code> parameter exists: it finishes the authentication process</li>
 *     <li>to the {@link RequiresAuthenticationFilter} otherwise: it starts the authentication process (redirection to the identity provider) if the user is not authenticated</li>
 * </ul>
 * <p>It uses the {@link KnoxSessionStore} to manage session data.</p>
 * <p>The mechanism used for the authentication is defined via servlet parameters.</p>
 *
 * @author Jerome Leleu
 * @since 0.7.0
 */
public class Pac4jDispatcherFilter implements Filter {

  private static Pac4jMessages log = MessagesFactory.get(Pac4jMessages.class);

  private static final String TEST_BASIC_AUTH = "testBasicAuth";

  private static final String SSO_AUTHENTICATION_PROVIDER_URL = "sso.authentication.provider.url";

  private CallbackFilter callbackFilter;

  private RequiresAuthenticationFilter requiresAuthenticationFilter;

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    // JWT service
    final ServletContext context = filterConfig.getServletContext();
    CryptoService cryptoService = null;
    AliasService aliasService = null;
    String clusterName = null;
    if (context != null) {
      GatewayServices services = (GatewayServices) context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
      clusterName = (String) context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE);
      if (services != null) {
        cryptoService = (CryptoService) services.getService(GatewayServices.CRYPTO_SERVICE);
        aliasService = (AliasService) services.getService(GatewayServices.ALIAS_SERVICE);
      }
    }
    // crypto service, alias service and cluster name are mandatory
    if (cryptoService == null || aliasService == null || clusterName == null) {
      log.cryptoServiceAndAliasServiceAndClusterNameRequired();
      throw new ServletException("The crypto service, alias service and cluster name are required.");
    }
    try {
      aliasService.getPasswordFromAliasForCluster(clusterName, KnoxSessionStore.PAC4J_PASSWORD, true);
    } catch (AliasServiceException e) {
      log.unableToGenerateAPasswordForEncryption(e);
      throw new ServletException("Unable to generate a password for encryption.");
    }

    // url to SSO authentication provider
    final String authenticationProviderUrl = filterConfig.getInitParameter(SSO_AUTHENTICATION_PROVIDER_URL);
    if (authenticationProviderUrl == null) {
      log.ssoAuthenticationProviderUrlRequired();
      throw new ServletException("Required authentication provider URL is missing.");
    }

    final Config config;
    final String clientName;
    // client name from servlet parameter (if defined)
    final String clientNameParameter = filterConfig.getInitParameter(Pac4jConstants.CLIENT_NAME);
    if (TEST_BASIC_AUTH.equalsIgnoreCase(clientNameParameter)) {
      // test configuration
      final IndirectBasicAuthClient indirectBasicAuthClient = new IndirectBasicAuthClient(new SimpleTestUsernamePasswordAuthenticator());
      indirectBasicAuthClient.setRealmName("Knox TEST");
      config = new Config(authenticationProviderUrl, indirectBasicAuthClient);
      clientName = "IndirectBasicAuthClient";
    } else {
      // get clients from the init parameters
      final Map<String, String> properties = new HashMap<>();
      final Enumeration<String> names = filterConfig.getInitParameterNames();
      while (names.hasMoreElements()) {
        final String key = names.nextElement();
        properties.put(key, filterConfig.getInitParameter(key));
      }
      final ConfigPropertiesFactory configPropertiesFactory = new ConfigPropertiesFactory(authenticationProviderUrl, properties);
      config = configPropertiesFactory.build();
      final List<Client> clients = config.getClients().getClients();
      if (clients == null || clients.size() ==0) {
        log.atLeastOnePac4jClientMustBeDefined();
        throw new ServletException("At least one pac4j client must be defined.");
      }
      if (CommonHelper.isBlank(clientNameParameter)) {
        clientName = clients.get(0).getName();
      } else {
        clientName = clientNameParameter;
      }
    }

    callbackFilter = new CallbackFilter();
    requiresAuthenticationFilter = new RequiresAuthenticationFilter();
    requiresAuthenticationFilter.setClientName(clientName);
    requiresAuthenticationFilter.setConfig(config);

    config.setSessionStore(new KnoxSessionStore(cryptoService, clusterName));
    ConfigSingleton.setConfig(config);
  }

  @Override
  public void doFilter( ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

    final HttpServletRequest request = (HttpServletRequest) servletRequest;
    final HttpServletResponse response = (HttpServletResponse) servletResponse;
    final J2EContext context = new J2EContext(request, response, ConfigSingleton.getConfig().getSessionStore());

    // it's a callback from an identity provider
    if (request.getParameter(Clients.DEFAULT_CLIENT_NAME_PARAMETER) != null) {
      // apply CallbackFilter
      callbackFilter.doFilter(servletRequest, servletResponse, filterChain);
    } else {
      // otherwise just apply security and requires authentication
      // apply RequiresAuthenticationFilter
      requiresAuthenticationFilter.doFilter(servletRequest, servletResponse, filterChain);
    }
  }

  @Override
  public void destroy() { }
}
