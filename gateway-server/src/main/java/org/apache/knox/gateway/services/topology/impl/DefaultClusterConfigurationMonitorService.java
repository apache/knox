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
package org.apache.knox.gateway.services.topology.impl;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.topology.ClusterConfigurationMonitorService;
import org.apache.knox.gateway.topology.discovery.ClusterConfigurationMonitor;
import org.apache.knox.gateway.topology.discovery.ClusterConfigurationMonitorProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

public class DefaultClusterConfigurationMonitorService implements ClusterConfigurationMonitorService {
    private AliasService aliasService;
    private KeystoreService keystoreService;

    private Map<String, ClusterConfigurationMonitor> monitors = new HashMap<>();

    @Override
    public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
        ServiceLoader<ClusterConfigurationMonitorProvider> providers =
                                                        ServiceLoader.load(ClusterConfigurationMonitorProvider.class);
        for (ClusterConfigurationMonitorProvider provider : providers) {
            // Check the gateway configuration to determine if this type of monitor is enabled
            if (config.isClusterMonitorEnabled(provider.getType())) {
                ClusterConfigurationMonitor monitor = provider.newInstance(config, aliasService, keystoreService);
                if (monitor != null) {
                    monitors.put(provider.getType(), monitor);
                }
            }
        }
    }

    @Override
    public void start() {
        for (ClusterConfigurationMonitor monitor : monitors.values()) {
            monitor.start();
        }
    }

    @Override
    public void stop() {
        for (ClusterConfigurationMonitor monitor : monitors.values()) {
            monitor.stop();
        }
    }

    @Override
    public ClusterConfigurationMonitor getMonitor(String type) {
        return monitors.get(type);
    }

    @Override
    public void addListener(ClusterConfigurationMonitor.ConfigurationChangeListener listener) {
        for (ClusterConfigurationMonitor monitor : monitors.values()) {
            monitor.addListener(listener);
        }
    }

    @Override
    public void clearCache(String source, String clusterName) {
        for (ClusterConfigurationMonitor monitor : monitors.values()) {
            monitor.clearCache(source, clusterName);
        }
    }

    public void setAliasService(AliasService aliasService) {
        this.aliasService = aliasService;
    }

    public void setKeystoreService(KeystoreService keystoreService) {
        this.keystoreService = keystoreService;
    }
}
