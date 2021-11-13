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

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.pac4j.Pac4jMessages;
import org.apache.knox.gateway.pac4j.config.ClientConfigurationDecorator;
import org.apache.knox.gateway.pac4j.config.Pac4jClientConfigurationDecorator;
import org.apache.knox.gateway.pac4j.session.KnoxSessionStore;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.CryptoService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.MasterService;
import org.pac4j.config.client.PropertiesConfigFactory;
import org.pac4j.config.client.PropertiesConstants;
import org.pac4j.core.client.Client;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.session.JEESessionStore;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.http.client.indirect.IndirectBasicAuthClient;
import org.pac4j.http.credentials.authenticator.test.SimpleTestUsernamePasswordAuthenticator;
import org.pac4j.jee.filter.CallbackFilter;
import org.pac4j.jee.filter.SecurityFilter;
import org.pac4j.oidc.client.AzureAdClient;
import org.pac4j.saml.client.SAML2Client;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
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
  private static final String ALIAS_PREFIX = "${ALIAS=";
  private static Pac4jMessages log = MessagesFactory.get(Pac4jMessages.class);

  private static final ClientConfigurationDecorator PAC4J_CLIENT_CONFIGURATION_DECORATOR = new Pac4jClientConfigurationDecorator();

  public static final String TEST_BASIC_AUTH = "testBasicAuth";

  public static final String PAC4J_CALLBACK_URL = "pac4j.callbackUrl";

  public static final String PAC4J_CALLBACK_PARAMETER = "pac4jCallback";

  public static final String PAC4J_OICD_TYPE_AZURE = "azure";

  public static final String URL_PATH_SEPARATOR = "/";

  private static final String PAC4J_COOKIE_DOMAIN_SUFFIX_PARAM = "pac4j.cookie.domain.suffix";

  private static final String PAC4J_CONFIG = "pac4j.config";

  private static final String PAC4J_SESSION_STORE = "pac4j.session.store";

  public static final String PAC4J_SESSION_STORE_EXCLUDE_GROUPS = "pac4j.session.store.exclude.groups";

  public static final String PAC4J_SESSION_STORE_EXCLUDE_ROLES = "pac4j.session.store.exclude.roles";

  public static final String PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS = "pac4j.session.store.exclude.permissions";

  /* A comma seperated list of attributes that needed to be excluded */
  public static final String PAC4J_SESSION_STORE_EXCLUDE_CUSTOM_ATTRIBUTES = "pac4j.session.store.exclude.custom.attributes";

  public static final String PAC4J_SESSION_STORE_EXCLUDE_CUSTOM_ATTRIBUTES_DEFAULT = "";

  public static final String PAC4J_SESSION_STORE_EXCLUDE_GROUPS_DEFAULT = "true";

  public static final String PAC4J_SESSION_STORE_EXCLUDE_ROLES_DEFAULT = "true";

  public static final String PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS_DEFAULT = "true";

  private static final String PAC4J_CLIENT_NAME_PARAM = "clientName";

  private static final String PAC4J_OIDC_TYPE = "oidc.type";

  private CallbackFilter callbackFilter;

  private SecurityFilter securityFilter;
  private MasterService masterService;
  private KeystoreService keystoreService;
  private AliasService aliasService;
  private Map<String, String> sessionStoreConfigs = new HashMap();

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
        keystoreService = services.getService(ServiceType.KEYSTORE_SERVICE);
        cryptoService = services.getService(ServiceType.CRYPTO_SERVICE);
        aliasService = services.getService(ServiceType.ALIAS_SERVICE);
        masterService = services.getService(ServiceType.MASTER_SERVICE);
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

    // client name from servlet parameter (mandatory)
    final String clientNameParameter = filterConfig.getInitParameter(PAC4J_CLIENT_NAME_PARAM);
    if (clientNameParameter == null) {
      log.clientNameParameterRequired();
      throw new ServletException("Required pac4j clientName parameter is missing.");
    }

    final String oidcType = filterConfig.getInitParameter(PAC4J_OIDC_TYPE);
    /*
       add the callback parameter to know it's a callback,
       Azure AD does not honor query param so we add callback param as path element.
    */
    if (AzureAdClient.class.getSimpleName().equals(clientNameParameter) || (
        !StringUtils.isBlank(oidcType) && PAC4J_OICD_TYPE_AZURE
            .equals(oidcType))) {
      pac4jCallbackUrl = pac4jCallbackUrl + URL_PATH_SEPARATOR + PAC4J_CALLBACK_PARAMETER;
    } else {
      pac4jCallbackUrl = CommonHelper.addParameter(pac4jCallbackUrl, PAC4J_CALLBACK_PARAMETER, "true");
    }

    final Config config;
    final String clientName;

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
        properties.put(key, resolveAlias(clusterName, key, filterConfig.getInitParameter(key)));
      }
      final PropertiesConfigFactory propertiesConfigFactory = new PropertiesConfigFactory(pac4jCallbackUrl, properties);
      config = propertiesConfigFactory.build();
      final List<Client> clients = config.getClients().getClients();
      if (clients == null || clients.isEmpty()) {
        log.atLeastOnePac4jClientMustBeDefined();
        throw new ServletException("At least one pac4j client must be defined.");
      }

      clientName = CommonHelper.isBlank(clientNameParameter) ? clients.get(0).getName() : clientNameParameter;
      /* do we need to exclude groups? */
      setSessionStoreConfig(filterConfig, PAC4J_SESSION_STORE_EXCLUDE_GROUPS, PAC4J_SESSION_STORE_EXCLUDE_GROUPS_DEFAULT);
      /* do we need to exclude roles? */
      setSessionStoreConfig(filterConfig, PAC4J_SESSION_STORE_EXCLUDE_ROLES, PAC4J_SESSION_STORE_EXCLUDE_ROLES_DEFAULT);
      /* do we need to exclude permissions? */
      setSessionStoreConfig(filterConfig, PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS, PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS_DEFAULT);
      /* do we need to exclude custom attributes? */
      setSessionStoreConfig(filterConfig, PAC4J_SESSION_STORE_EXCLUDE_CUSTOM_ATTRIBUTES, PAC4J_SESSION_STORE_EXCLUDE_CUSTOM_ATTRIBUTES_DEFAULT);
      //decorating client configuration (if needed)
      PAC4J_CLIENT_CONFIGURATION_DECORATOR.decorateClients(clients, properties);
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

    if(!StringUtils.isBlank(sessionStoreVar) && JEESessionStore.class.getName().contains(sessionStoreVar) ) {
      sessionStore = new JEESessionStore();
    } else {
      sessionStore = new KnoxSessionStore(cryptoService, clusterName, domainSuffix, sessionStoreConfigs);
    }

    config.setSessionStore(sessionStore);

  }

  /**
   * A helper method to set filter config value
   * @param filterConfig
   * @param configName
   * @param configDefault
   */
  private void setSessionStoreConfig(final FilterConfig filterConfig, final String configName, final String configDefault) {
    final String configValue = filterConfig.getInitParameter(configName);
    sessionStoreConfigs.put(configName, configValue == null ? configDefault : configValue);
  }


  private String resolveAlias(String clusterName, String key, String value) throws ServletException {
    if (value.startsWith(ALIAS_PREFIX) && value.endsWith("}")) {
      String alias = value.substring(ALIAS_PREFIX.length(), value.length() - 1);
      try {
        return new String(aliasService.getPasswordFromAliasForCluster(clusterName, alias));
      } catch (AliasServiceException e) {
        throw new ServletException("Unable to retrieve alias for config: " + key, e);
      }
    }
    return value;
  }

  private void addDefaultConfig(String clientNameParameter, Map<String, String> properties) {
    // add default saml params
    if (clientNameParameter.contains(SAML2Client.class.getSimpleName())) {
      properties.put(PropertiesConstants.SAML_KEYSTORE_PATH,
          keystoreService.getKeystorePath());

      // check for provisioned alias for keystore password
      char[] giksp = null;
      try {
        giksp = aliasService.getGatewayIdentityKeystorePassword();
      } catch (AliasServiceException e) {
        log.noKeystorePasswordProvisioned(e);
      }
      if (giksp == null) {
        // no alias provisioned then use the master
        giksp = masterService.getMasterSecret();
      }
      properties.put(PropertiesConstants.SAML_KEYSTORE_PASSWORD, new String(giksp));

      // check for provisioned alias for private key
      char[] gip = null;
      try {
        gip = aliasService.getGatewayIdentityPassphrase();
      }
      catch(AliasServiceException ase) {
        log.noPrivateKeyPasshraseProvisioned(ase);
      }
      if (gip == null) {
        // no alias provisioned then use the master
        gip = masterService.getMasterSecret();
      }
      properties.put(PropertiesConstants.SAML_PRIVATE_KEY_PASSWORD, new String(gip));
    }
  }

  @Override
  public void doFilter( ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

    final HttpServletRequest request = (HttpServletRequest) servletRequest;
    request.setAttribute(PAC4J_CONFIG, securityFilter.getSharedConfig());

    // it's a callback from an identity provider
    if (request.getParameter(PAC4J_CALLBACK_PARAMETER) != null || (
        request.getContextPath() != null && request.getRequestURI()
            .contains(PAC4J_CALLBACK_PARAMETER))) {
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
