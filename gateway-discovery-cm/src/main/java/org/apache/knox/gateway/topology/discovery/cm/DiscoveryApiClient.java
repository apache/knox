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

import com.cloudera.api.swagger.client.ApiClient;
import org.apache.knox.gateway.config.ConfigurationException;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;

import static org.apache.knox.gateway.topology.discovery.cm.ClouderaManagerServiceDiscovery.API_PATH;
import static org.apache.knox.gateway.topology.discovery.cm.ClouderaManagerServiceDiscovery.DEFAULT_USER_ALIAS;
import static org.apache.knox.gateway.topology.discovery.cm.ClouderaManagerServiceDiscovery.DEFAULT_PWD_ALIAS;


public class DiscoveryApiClient extends ApiClient {

  private ClouderaManagerServiceDiscoveryMessages log =
      MessagesFactory.get(ClouderaManagerServiceDiscoveryMessages.class);

  private boolean isKerberos;

  DiscoveryApiClient(ServiceDiscoveryConfig discoveryConfig, AliasService aliasService) {
    configure(discoveryConfig, aliasService);
  }

  boolean isKerberos() {
    return isKerberos;
  }

  private void configure(ServiceDiscoveryConfig config, AliasService aliasService) {
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
  }

}
