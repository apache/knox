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
package org.apache.knox.gateway.service.config.remote.config;

import org.apache.knox.gateway.config.GatewayConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A set of RemoteConfigurationRegistry configurations based on a set of property name-value pairs.
 */
class DefaultRemoteConfigurationRegistries extends RemoteConfigurationRegistries {

    private static final String PROPERTY_DELIM       = ";";
    private static final String PROPERTY_VALUE_DELIM = "=";

    private List<RemoteConfigurationRegistry> configuredRegistries = new ArrayList<>();

    /**
     * Derive the remote registry configurations from the specified GatewayConfig.
     *
     * @param gc The source GatewayConfig
     */
    DefaultRemoteConfigurationRegistries(GatewayConfig gc) {
        List<String> configRegistryNames = gc.getRemoteRegistryConfigurationNames();
        for (String configRegistryName : configRegistryNames) {
            configuredRegistries.add(extractConfigForRegistry(gc, configRegistryName));
        }
    }

    /**
     * Extract the configuration for the specified registry configuration name.
     *
     * @param gc           The GatewayConfig from which to extract the registry config.
     * @param registryName The name of the registry config.
     *
     * @return The resulting RemoteConfigurationRegistry object, or null.
     */
    private static RemoteConfigurationRegistry extractConfigForRegistry(GatewayConfig gc, String registryName) {
        RemoteConfigurationRegistry result = new RemoteConfigurationRegistry();

        result.setName(registryName);

        Map<String, String> properties = parsePropertyValue(gc.getRemoteRegistryConfiguration(registryName));

        result.setRegistryType(properties.get(GatewayConfig.REMOTE_CONFIG_REGISTRY_TYPE));
        result.setConnectionString(properties.get(GatewayConfig.REMOTE_CONFIG_REGISTRY_ADDRESS));
        result.setNamespace(properties.get(GatewayConfig.REMOTE_CONFIG_REGISTRY_NAMESPACE));
        result.setAuthType(properties.get(GatewayConfig.REMOTE_CONFIG_REGISTRY_AUTH_TYPE));
        result.setPrincipal(properties.get(GatewayConfig.REMOTE_CONFIG_REGISTRY_PRINCIPAL));
        result.setCredentialAlias(properties.get(GatewayConfig.REMOTE_CONFIG_REGISTRY_CREDENTIAL_ALIAS));
        result.setKeytab(properties.get(GatewayConfig.REMOTE_CONFIG_REGISTRY_KEYTAB));
        result.setUseKeytab(Boolean.valueOf(properties.get(GatewayConfig.REMOTE_CONFIG_REGISTRY_USE_KEYTAB)));
        result.setUseTicketCache(Boolean.valueOf(properties.get(GatewayConfig.REMOTE_CONFIG_REGISTRY_USE_TICKET_CACHE)));
        result.setBackwardsCompatible(Boolean.valueOf(properties.getOrDefault(GatewayConfig.ZOOKEEPER_REMOTE_CONFIG_REGISTRY_BACKWARDS_COMPATIBLE, "false")));
        return result;
    }

    /**
     * Parse the specified registry config properties String.
     *
     * @param value The property value content from GatewayConfig.
     *
     * @return A Map of the parsed properties and their respective values.
     */
    private static Map<String, String> parsePropertyValue(final String value) {
        Map<String, String> result = new HashMap<>();

        if (value != null) {
            String[] props = value.split(PROPERTY_DELIM);
            for (String prop : props) {
                String[] split = prop.split(PROPERTY_VALUE_DELIM);
                String propName  = split[0];
                String propValue = (split.length > 1) ? split[1] : null;
                result.put(propName, propValue);
            }
        }

        return result;
    }

    @Override
    List<RemoteConfigurationRegistry> getRegistryConfigurations() {
        return configuredRegistries;
    }

}
