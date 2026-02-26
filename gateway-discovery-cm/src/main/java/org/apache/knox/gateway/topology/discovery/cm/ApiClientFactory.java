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
package org.apache.knox.gateway.topology.discovery.cm;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;

import java.security.KeyStore;

public class ApiClientFactory {

    private static final ClouderaManagerServiceDiscoveryMessages LOG = MessagesFactory.get(ClouderaManagerServiceDiscoveryMessages.class);
    static final String TRUSTSTORE_PASSWORD_SYSTEM_PROPERTY = "javax.net.ssl.trustStorePassword";
    public static final String TRUSTSTORE_PASSWORD_ALIAS = "cm.discovery.trustStorePassword";

    public static DiscoveryApiClient getApiClient(final GatewayConfig gatewayConfig, final ServiceDiscoveryConfig discoveryConfig,
                                                  final AliasService aliasService, final KeyStore truststore) {
        try {
            final char[] trustStorePassword = aliasService.getPasswordFromAliasForGateway(TRUSTSTORE_PASSWORD_ALIAS);
            if (trustStorePassword != null) {
                System.setProperty(TRUSTSTORE_PASSWORD_SYSTEM_PROPERTY, new String(trustStorePassword));
            }
            return new DiscoveryApiClient(gatewayConfig, discoveryConfig, aliasService, truststore);
        } catch (AliasServiceException e) {
            LOG.clouderaManagerApiClientBuildError(e);
            throw new ServiceDiscoveryException("Unable to retrieve CM service discovery truststore password", e);
        } finally {
            System.clearProperty(TRUSTSTORE_PASSWORD_SYSTEM_PROPERTY);
        }
    }
}
