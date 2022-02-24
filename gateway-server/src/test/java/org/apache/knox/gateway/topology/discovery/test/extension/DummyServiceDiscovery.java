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
package org.apache.knox.gateway.topology.discovery.test.extension;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.topology.discovery.ServiceDiscovery;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This implementation is intended to be used by tests for which the actual service URLs are of no importance, such that
 * tests can be written without having a valid service registry (e.g., Ambari) available.
 */
public class DummyServiceDiscovery implements ServiceDiscovery {

    static final String TYPE = "DUMMY";

    private static final Cluster DUMMY = new Cluster() {
        @Override
        public String getName() {
            return "dummy";
        }

        @Override
        public List<String> getServiceURLs(String serviceName) {
            return Collections.singletonList("http://servicehost:9999/dummy");
        }

        @Override
        public List<String> getServiceURLs(String serviceName, Map<String, String> serviceParams) {
            return getServiceURLs(serviceName);
        }

        @Override
        public ZooKeeperConfig getZooKeeperConfiguration(String serviceName) {
            return null;
        }
    };

    private static final Map<String, Cluster> CLUSTERS = new HashMap<>();
    static {
        CLUSTERS.put(DUMMY.getName(), DUMMY);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Cluster discover(GatewayConfig gwConfig, ServiceDiscoveryConfig config, String clusterName) {
        return DUMMY;
    }

    @Override
    public Cluster discover(GatewayConfig gwConfig, ServiceDiscoveryConfig config, String clusterName, Collection<String> includedServices) {
      return DUMMY;
    }
}
