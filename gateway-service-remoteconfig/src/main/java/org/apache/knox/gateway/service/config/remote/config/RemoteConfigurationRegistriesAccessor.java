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
import org.apache.knox.gateway.service.config.remote.RemoteConfigurationRegistryConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RemoteConfigurationRegistriesAccessor {

    // System property for specifying a reference to an XML configuration external to the gateway config
    private static final String XML_CONFIG_REFERENCE_SYSTEM_PROPERTY_NAME =
                                                                "org.apache.knox.gateway.remote.registry.config.file";


    public static List<RemoteConfigurationRegistryConfig> getRemoteRegistryConfigurations(GatewayConfig gatewayConfig) {
        List<RemoteConfigurationRegistryConfig> result = new ArrayList<>();

        boolean useReferencedFile = false;

        // First check for the system property pointing to a valid XML config for the remote registries
        String remoteConfigRegistryConfigFilename = System.getProperty(XML_CONFIG_REFERENCE_SYSTEM_PROPERTY_NAME);
        if (remoteConfigRegistryConfigFilename != null) {
            File remoteConfigRegistryConfigFile = new File(remoteConfigRegistryConfigFilename);
            if (remoteConfigRegistryConfigFile.exists()) {
                useReferencedFile = true;
                // Parse the file, and build the registry config set
                result.addAll(RemoteConfigurationRegistriesParser.getConfig(remoteConfigRegistryConfigFilename));
            }
        }

        // If the system property was not set to a valid reference to another config file, then try to derive the
        // registry configurations from the gateway config.
        if (!useReferencedFile) {
            RemoteConfigurationRegistries remoteConfigRegistries =
                                                            new DefaultRemoteConfigurationRegistries(gatewayConfig);
            result.addAll(remoteConfigRegistries.getRegistryConfigurations());
        }

        return result;
    }

}
