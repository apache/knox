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
package org.apache.knox.gateway.pac4j.filter;

import org.apache.commons.lang.StringUtils;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.pac4j.Pac4jMessages;
import org.apache.knox.gateway.pac4j.session.KnoxSessionStore;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.CryptoService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.MasterService;
import org.pac4j.config.client.PropertiesConfigFactory;
import org.pac4j.core.client.Client;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.session.J2ESessionStore;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.http.client.indirect.IndirectBasicAuthClient;
import org.pac4j.http.credentials.authenticator.test.SimpleTestUsernamePasswordAuthenticator;
import org.pac4j.j2e.filter.CallbackFilter;
import org.pac4j.j2e.filter.SecurityFilter;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
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
 *     <li>to the {@link SecurityFilter} otherwise: it starts the authentication process (redirection to the identity provider) if the user is not authenticated</li>
 * </ul>
 * <p>It uses the {@link KnoxSessionStore} to manage session data. The generated cookies are defined on a domain name
 * which can be configured via the domain suffix parameter: <code>pac4j.cookie.domain.suffix</code>.</p>
 * <p>The callback url must be defined to the current protected url (KnoxSSO service for example) via the parameter: <code>pac4j.callbackUrl</code>.</p>
 *
 * @since 0.8.0
 */
public class Pac4jDispatcherFilter implements Filter {

  private static Pac4jMessages log = MessagesFactory.get(Pac4jMessages.class);

  public static final String TEST_BASIC_AUTH = "testBasicAuth";

  public static final String PAC4J_CALLBACK_URL = "pac4j.callbackUrl";

  public static final String PAC4J_CALLBACK_PARAMETER = "pac4jCallback";

  private static final String PAC4J_COOKIE_DOMAIN_SUFFIX_PARAM = "pac4j.cookie.domain.suffix";

  private static final String PAC4J_CONFIG = "pac4j.config";

  private static final String PAC4J_SESSION_STORE = "pac4j.session.store";

  private CallbackFilter callbackFilter;

  private SecurityFilter securityFilter;
  private MasterService masterService = null;
  private KeystoreService keystoreService = null;
  private AliasService aliasService = null;

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    // JWT service
    final ServletContext context = filterConfig.getServletContext();
    CryptoService cryptoService = null;
    String clusterName = null;
    if (context != null) {
      GatewayServices services = (GatewayServices) context.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
      clusterName = (String) context.getAttribute(GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE);
      if (services != null) {
        keystoreService = (KeystoreService) services.getService(GatewayServices.KEYSTORE_SERVICE);
        cryptoService = (CryptoService) services.getService(GatewayServices.CRYPTO_SERVICE);
        aliasService = (AliasService) services.getService(GatewayServices.ALIAS_SERVICE);
        masterService = (MasterService) services.getService("MasterService");
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
    String pac4jCallbackUrl = filterConfig.getInitParameter(PAC4J_CALLBACK_URL);
    if (pac4jCallbackUrl == null) {
      log.ssoAuthenticationProviderUrlRequired();
      throw new ServletException("Required pac4j callback URL is missing.");
    }
    // add the callback parameter to know it's a callback
    pac4jCallbackUrl = CommonHelper.addParameter(pac4jCallbackUrl, PAC4J_CALLBACK_PARAMETER, "true");

    final Config config;
    final String clientName;
    // client name from servlet parameter (mandatory)
    final String clientNameParameter = filterConfig.getInitParameter("clientName");
    if (clientNameParameter == null) {
      log.clientNameParameterRequired();
      throw new ServletException("Required pac4j clientName parameter is missing.");
    }
    if (TEST_BASIC_AUTH.equalsIgnoreCase(clientNameParameter)) {
      // test configuration
      final IndirectBasicAuthClient indirectBasicAuthClient = new IndirectBasicAuthClient(new SimpleTestUsernamePasswordAuthenticator());
      indirectBasicAuthClient.setRealmName("Knox TEST");
      config = new Config(pac4jCallbackUrl, indirectBasicAuthClient);
      clientName = "IndirectBasicAuthClient";
    } else {
      // get clients from the init parameters
      final Map<String, String> properties = new HashMap<>();
      final Enumeration<String> names = filterConfig.getInitParameterNames();
      addDefaultConfig(clientNameParameter, properties);
      while (names.hasMoreElements()) {
        final String key = names.nextElement();
        properties.put(key, filterConfig.getInitParameter(key));
      }
      final PropertiesConfigFactory propertiesConfigFactory = new PropertiesConfigFactory(pac4jCallbackUrl, properties);
      config = propertiesConfigFactory.build();
      final List<Client> clients = config.getClients().getClients();
      if (clients == null || clients.size() == 0) {
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
    callbackFilter.init(filterConfig);
    callbackFilter.setConfigOnly(config);
    securityFilter = new SecurityFilter();
    securityFilter.setClients(clientName);
    securityFilter.setConfigOnly(config);

    final String domainSuffix = filterConfig.getInitParameter(PAC4J_COOKIE_DOMAIN_SUFFIX_PARAM);
    final String sessionStoreVar = filterConfig.getInitParameter(PAC4J_SESSION_STORE);

    SessionStore sessionStore;

    if(!StringUtils.isBlank(sessionStoreVar) && J2ESessionStore.class.getName().contains(sessionStoreVar) ) {
      sessionStore = new J2ESessionStore();
    } else {
      sessionStore = new KnoxSessionStore(cryptoService, clusterName, domainSuffix);
    }

    config.setSessionStore(sessionStore);

  }

  private void addDefaultConfig(String clientNameParameter, Map<String, String> properties) {
    // add default saml params
    if (clientNameParameter.contains("SAML2Client")) {
      properties.put(PropertiesConfigFactory.SAML_KEYSTORE_PATH,
          keystoreService.getKeystorePath());

      properties.put(PropertiesConfigFactory.SAML_KEYSTORE_PASSWORD,
          new String(masterService.getMasterSecret()));

      // check for provisioned alias for private key
      char[] gip = null;
      try {
        gip = aliasService.getGatewayIdentityPassphrase();
      }
      catch(AliasServiceException ase) {
        log.noPrivateKeyPasshraseProvisioned(ase);
      }
      if (gip != null) {
        properties.put(PropertiesConfigFactory.SAML_PRIVATE_KEY_PASSWORD,
            new String(gip));
      }
      else {
        // no alias provisioned then use the master
        properties.put(PropertiesConfigFactory.SAML_PRIVATE_KEY_PASSWORD,
            new String(masterService.getMasterSecret()));
      }
    }
  }

  @Override
  public void doFilter( ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

    final HttpServletRequest request = (HttpServletRequest) servletRequest;
    final HttpServletResponse response = (HttpServletResponse) servletResponse;
    request.setAttribute(PAC4J_CONFIG, securityFilter.getConfig());
//    final J2EContext context = new J2EContext(request, response, securityFilter.getConfig().getSessionStore());

    // it's a callback from an identity provider
    if (request.getParameter(PAC4J_CALLBACK_PARAMETER) != null) {
      // apply CallbackFilter
      callbackFilter.doFilter(servletRequest, servletResponse, filterChain);
    } else {
      // otherwise just apply security and requires authentication
      // apply RequiresAuthenticationFilter
      securityFilter.doFilter(servletRequest, servletResponse, filterChain);
    }
  }

  @Override
  public void destroy() { }
}
