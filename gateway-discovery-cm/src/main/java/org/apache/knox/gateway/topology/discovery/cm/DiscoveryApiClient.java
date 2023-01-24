/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.discovery.cm;

import static org.apache.knox.gateway.topology.discovery.cm.ClouderaManagerServiceDiscovery.API_PATH;
import static org.apache.knox.gateway.topology.discovery.cm.ClouderaManagerServiceDiscovery.DEFAULT_PWD_ALIAS;
import static org.apache.knox.gateway.topology.discovery.cm.ClouderaManagerServiceDiscovery.DEFAULT_USER_ALIAS;

import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.security.auth.Subject;

import org.apache.knox.gateway.config.ConfigurationException;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;
import org.apache.knox.gateway.topology.discovery.cm.auth.AuthUtils;
import org.apache.knox.gateway.topology.discovery.cm.auth.SpnegoAuthInterceptor;
import org.apache.knox.gateway.util.TruststoreSSLContextUtils;

import com.cloudera.api.swagger.client.ApiClient;
import com.cloudera.api.swagger.client.Pair;
import com.cloudera.api.swagger.client.auth.Authentication;
import com.cloudera.api.swagger.client.auth.HttpBasicAuth;
import com.squareup.okhttp.ConnectionSpec;

/**
 * Cloudera Manager ApiClient extension for service discovery.
 */
public class DiscoveryApiClient extends ApiClient {

  private ClouderaManagerServiceDiscoveryMessages log =
      MessagesFactory.get(ClouderaManagerServiceDiscoveryMessages.class);

  private boolean isKerberos;

  private ServiceDiscoveryConfig config;

  public DiscoveryApiClient(GatewayConfig gatewayConfig, ServiceDiscoveryConfig discoveryConfig, AliasService aliasService,
      KeyStore trustStore) {
    this.config = discoveryConfig;
    configure(gatewayConfig, aliasService, trustStore);
  }

  ServiceDiscoveryConfig getConfig() {
    return config;
  }

  boolean isKerberos() {
    return isKerberos;
  }

  private void configure(GatewayConfig gatewayConfig, AliasService aliasService, KeyStore trustStore) {
    String apiAddress = config.getAddress();
    apiAddress += (apiAddress.endsWith("/") ? API_PATH : "/" + API_PATH);

    setBasePath(apiAddress);

    String username = config.getUser();
    String passwordAlias = config.getPasswordAlias();

    String password = null;
    // If no configured username, then use default username alias
    if (username == null) {
      if (aliasService != null) {
        try {
          char[] defaultUser = aliasService.getPasswordFromAliasForGateway(DEFAULT_USER_ALIAS);
          if (defaultUser != null) {
            username = new String(defaultUser);
          }
        } catch (AliasServiceException e) {
          log.aliasServiceUserError(DEFAULT_USER_ALIAS, e.getLocalizedMessage());
        }
      }

      // If username is still null
      if (username == null) {
        log.aliasServiceUserNotFound();
        throw new ConfigurationException("No username is configured for Cloudera Manager service discovery.");
      }
    }

    if (aliasService != null) {
      // If no password alias is configured, then try the default alias
      if (passwordAlias == null) {
        passwordAlias = DEFAULT_PWD_ALIAS;
      }

      try {
        char[] pwd = aliasService.getPasswordFromAliasForGateway(passwordAlias);
        if (pwd != null) {
          password = new String(pwd);
        }

      } catch (AliasServiceException e) {
        log.aliasServicePasswordError(passwordAlias, e.getLocalizedMessage());
      }
    }

    // If the password could not be determined
    if (password == null) {
      log.aliasServicePasswordNotFound();
      isKerberos = Boolean.getBoolean(GatewayConfig.HADOOP_KERBEROS_SECURED);
    }

    setUsername(username);
    setPassword(password);

    if (isKerberos) {
      // If there is a Kerberos subject, then add the SPNEGO auth interceptor
      Subject subject = AuthUtils.getKerberosSubject();
      if (subject != null) {
        SpnegoAuthInterceptor spnegoInterceptor = new SpnegoAuthInterceptor(subject);
        getHttpClient().interceptors().add(spnegoInterceptor);
      }
    }

    configureSsl(gatewayConfig, trustStore);
  }

  @Override
  public String buildUrl(String path, List<Pair> queryParams) {
    // If kerberos is enabled, then for every request, we're going to include a doAs query param
    if (isKerberos()) {
      String user = getUsername();
      if (user != null) {
        queryParams.add(new Pair("doAs", user));
      }
    }
    return super.buildUrl(path, queryParams);
  }

  /**
   * @return The username set from the discovery configuration when this instance was initialized.
   */
  private String getUsername() {
    String username = null;
    Authentication basicAuth = getAuthentication("basic");
    if (basicAuth instanceof HttpBasicAuth) {
      username = ((HttpBasicAuth) basicAuth).getUsername();
    }
    return username;
  }

  private void configureSsl(GatewayConfig gatewayConfig, KeyStore trustStore) {
    final SSLContext truststoreSSLContext = TruststoreSSLContextUtils.getTruststoreSSLContext(trustStore);

    if (truststoreSSLContext != null) {
      final ConnectionSpec.Builder connectionSpecBuilder = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS);
      final List<String> includedSslCiphers = gatewayConfig.getIncludedSSLCiphers();
      if (includedSslCiphers == null || includedSslCiphers.isEmpty()) {
        connectionSpecBuilder.cipherSuites(truststoreSSLContext.getSupportedSSLParameters().getCipherSuites());
      } else {
        connectionSpecBuilder.cipherSuites(includedSslCiphers.toArray(new String[0]));
      }
      final Set<String> includedSslProtocols = gatewayConfig.getIncludedSSLProtocols();
      if (includedSslProtocols.isEmpty()) {
        connectionSpecBuilder.tlsVersions(truststoreSSLContext.getSupportedSSLParameters().getProtocols());
      } else {
        connectionSpecBuilder.tlsVersions(includedSslProtocols.toArray(new String[0]));
      }
      getHttpClient().setConnectionSpecs(Arrays.asList(connectionSpecBuilder.build()));
      getHttpClient().setSslSocketFactory(truststoreSSLContext.getSocketFactory());
    } else {
      log.failedToConfigureTruststore();
    }
  }

}
