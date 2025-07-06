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

import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.Subject;

import com.cloudera.api.swagger.client.ApiClient;
import okhttp3.ConnectionSpec;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import org.apache.knox.gateway.config.ConfigurationException;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;
import org.apache.knox.gateway.topology.discovery.cm.auth.AuthUtils;
import org.apache.knox.gateway.topology.discovery.cm.auth.DoAsQueryParameterInterceptor;
import org.apache.knox.gateway.topology.discovery.cm.auth.SpnegoAuthInterceptor;
import org.apache.knox.gateway.util.TruststoreSSLContextUtils;

/**
 * Cloudera Manager ApiClient extension for service discovery.
 */
public class DiscoveryApiClient extends ApiClient {

  private ClouderaManagerServiceDiscoveryMessages log =
      MessagesFactory.get(ClouderaManagerServiceDiscoveryMessages.class);

  private boolean isKerberos;

  private final ServiceDiscoveryConfig config;

  private static final String API_PATH_PREFIX = "api/";
  private static final String DEFAULT_USER_ALIAS = "cm.discovery.user";
  private static final String DEFAULT_PWD_ALIAS  = "cm.discovery.password";

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
    String apiAddress = getApiAddress(config, gatewayConfig);
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

    if (isKerberos()) {
      // If there is a Kerberos subject, then add the SPNEGO auth interceptor
      Subject subject = AuthUtils.getKerberosSubject();
      if (subject != null) {
        addInterceptor(new SpnegoAuthInterceptor(subject));
      }
      addInterceptor(new DoAsQueryParameterInterceptor(username));
    }
    configureTimeouts(gatewayConfig);

    configureSsl(gatewayConfig, trustStore);
  }

  private String getApiPath(GatewayConfig gatewayConfig) {
    if (gatewayConfig == null) {
      return API_PATH_PREFIX + GatewayConfig.DEFAULT_CLOUDERA_MANAGER_SERVICE_DISCOVERY_API_VERSION;
    } else {
      return API_PATH_PREFIX + gatewayConfig.getClouderaManagerServiceDiscoveryApiVersion();
    }
  }

  private String getApiAddress(ServiceDiscoveryConfig serviceDiscoveryConfig, GatewayConfig gatewayConfig) {
    String address = serviceDiscoveryConfig.getAddress();
    String apiPath = getApiPath(gatewayConfig);
    return (address.endsWith("/") ? address + apiPath : address + "/" + apiPath);
  }

  private void addInterceptor(Interceptor interceptor) {
    OkHttpClient newClient = getHttpClient().newBuilder().addInterceptor(interceptor).build();
    setHttpClient(newClient);
  }

  private void configureTimeouts(GatewayConfig config) {
    OkHttpClient.Builder builder = getHttpClient().newBuilder();
    builder.connectTimeout(config.getClouderaManagerServiceDiscoveryConnectTimeoutMillis(), TimeUnit.MILLISECONDS);
    builder.readTimeout(config.getClouderaManagerServiceDiscoveryReadTimeoutMillis(), TimeUnit.MILLISECONDS);
    builder.writeTimeout(config.getClouderaManagerServiceDiscoveryWriteTimeoutMillis(), TimeUnit.MILLISECONDS);
    OkHttpClient client = builder.build();
    setHttpClient(client);
    log.discoveryClientTimeout(client.connectTimeoutMillis(), client.readTimeoutMillis(), client.writeTimeoutMillis());
  }

  private void configureSsl(GatewayConfig gatewayConfig, KeyStore trustStore) {
    final SSLContext truststoreSSLContext = TruststoreSSLContextUtils.getTruststoreSSLContext(trustStore);
    final X509TrustManager trustManager = TruststoreSSLContextUtils.getTrustManager(trustStore);

    if (truststoreSSLContext != null && trustManager != null) {
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
      OkHttpClient.Builder builder = getHttpClient().newBuilder();
      builder.connectionSpecs(Arrays.asList(connectionSpecBuilder.build()));
      builder.sslSocketFactory(truststoreSSLContext.getSocketFactory(), trustManager);
      setHttpClient(builder.build());
    } else {
      log.failedToConfigureTruststore();
    }
  }

}
