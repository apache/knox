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
import org.apache.hadoop.gateway.services.security.token.JWTokenAuthority;
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
    JWTokenAuthority authority = null;
    if (context != null) {
      GatewayServices services = (GatewayServices) context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
      if (services != null) {
        authority = (JWTokenAuthority) services.getService(GatewayServices.TOKEN_SERVICE);
      }
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

    config.setSessionStore(new KnoxSessionStore(authority));
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
