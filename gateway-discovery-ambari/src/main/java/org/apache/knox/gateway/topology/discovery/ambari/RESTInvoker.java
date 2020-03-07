/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.discovery.ambari;

import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.apache.knox.gateway.config.ConfigurationException;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.util.TruststoreSSLContextUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

class RESTInvoker {

    private static final String DEFAULT_USER_ALIAS = "ambari.discovery.user";
    private static final String DEFAULT_PWD_ALIAS  = "ambari.discovery.password";

    private static final AmbariServiceDiscoveryMessages log = MessagesFactory.get(AmbariServiceDiscoveryMessages.class);

    private static final int DEFAULT_TIMEOUT = 10000;

    private AliasService aliasService;

    private KeystoreService keystoreService;

    private CloseableHttpClient httpClient;

    RESTInvoker(AliasService aliasService, KeystoreService keystoreService) {
        this(null, aliasService, keystoreService);
    }

    RESTInvoker(GatewayConfig config, AliasService aliasService, KeystoreService keystoreService) {
        this.aliasService = aliasService;
        this.keystoreService = keystoreService;

        // Initialize the HTTP client
        this.httpClient = getHttpClient(config);
    }

    private CloseableHttpClient getHttpClient(GatewayConfig config) {
      return HttpClientBuilder.create()
                 .setSSLContext(TruststoreSSLContextUtils.getTruststoreSSLContext(keystoreService))
                 .setDefaultRequestConfig(getRequestConfig(config))
                 .build();
    }

    private RequestConfig getRequestConfig(GatewayConfig config) {
        RequestConfig.Builder builder = RequestConfig.custom();
        if (config != null) {
            builder.setConnectTimeout(config.getHttpClientConnectionTimeout())
                   .setConnectionRequestTimeout(config.getHttpClientConnectionTimeout())
                   .setSocketTimeout(config.getHttpClientSocketTimeout());
        } else {
            builder.setConnectTimeout(DEFAULT_TIMEOUT)
                   .setConnectionRequestTimeout(DEFAULT_TIMEOUT)
                   .setSocketTimeout(DEFAULT_TIMEOUT);
        }
        return builder.build();
    }

    JSONObject invoke(String url, String username, String passwordAlias) {
        JSONObject result = null;

        try {
            HttpGet request = new HttpGet(url);

            // If no configured username, then use default username alias
            String password = null;
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
                    throw new ConfigurationException("No username is configured for Ambari service discovery.");
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
                throw new ConfigurationException("No password is configured for Ambari service discovery.");
            }

            // Add an auth header if credentials are available
            String encodedCreds = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            request.addHeader(new BasicHeader("Authorization", "Basic " + encodedCreds));

            // Ambari CSRF protection
            request.addHeader("X-Requested-By", "Knox");

            try(CloseableHttpResponse response = httpClient.execute(request)){
              if (HttpStatus.SC_OK == response.getStatusLine().getStatusCode()) {
                HttpEntity entity = response.getEntity();
                if (entity != null) {
                  result = (JSONObject) JSONValue.parse((EntityUtils.toString(entity)));
                  log.debugJSON(result.toJSONString());
                } else {
                  log.noJSON(url);
                }
              } else {
                log.unexpectedRestResponseStatusCode(url, response.getStatusLine().getStatusCode());
              }
            }
        } catch (ConnectTimeoutException e) {
            log.restInvocationTimedOut(url, e);
        } catch (IOException e) {
            log.restInvocationError(url, e);
        }
        return result;
    }
}
