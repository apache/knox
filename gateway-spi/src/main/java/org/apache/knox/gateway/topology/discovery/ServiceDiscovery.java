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
package org.apache.knox.gateway.topology.discovery;

import org.apache.knox.gateway.config.GatewayConfig;

import java.util.Collection;
import java.util.List;
import java.util.Map;


/**
 * Implementations provide the means by which Hadoop service endpoint URLs are discovered from a source with knowledge
 * about the service topology of one or more clusters.
 */
public interface ServiceDiscovery {

    String CONFIG_DIR_PROPERTY = "org.apache.knox.gateway.conf.dir";

    /**
     * This is the type specified in a simple descriptor to indicate which ServiceDiscovery implementation to employ.
     *
     * @return The identifier for the service discovery type.
     */
    String getType();

    /**
     * Discover details for a single cluster.
     *
     * @param gwConfig The gateway configuration
     * @param config The configuration for the discovery invocation
     * @param clusterName The name of a particular cluster
     *
     * @return The discovered service data for the specified cluster
     */
    Cluster discover(GatewayConfig gwConfig, ServiceDiscoveryConfig config, String clusterName);

    /**
     * Discover details for a single cluster filtered by the given <code>services</code> list.
     *
     * @param gwConfig The gateway configuration
     * @param config The configuration for the discovery invocation
     * @param clusterName The name of a particular cluster
     * @param includedServices The list of service names to be discovered. If that's set to <code>null</code> or <code>an empty collection</code>, all services within the given cluster will be discovered.
     *
     * @return The discovered service data for the specified cluster
     */
    Cluster discover(GatewayConfig gwConfig, ServiceDiscoveryConfig config, String clusterName, Collection<String> includedServices);


    /**
     * A handle to the service discovery result.
     */
    interface Cluster {

        /**
         * @return The name of the cluster
         */
        String getName();

        /**
         * @param serviceName The name of the service
         *
         * @return The URLs for the specified service in this cluster.
         */
        List<String> getServiceURLs(String serviceName);

        /**
         * @param serviceName   The name of the service.
         * @param serviceParams A map of parameters and their corresponding values for the specified service.
         *
         * @return The URLs for the specified service in this cluster.
         */
        List<String> getServiceURLs(String serviceName, Map<String, String> serviceParams);

        /**
         * @param serviceName The name of the service
         *
         * @return The HA configuration properties for the specified service in this cluster.
         */
        ZooKeeperConfig getZooKeeperConfiguration(String serviceName);

        interface ZooKeeperConfig {

            boolean isEnabled();

            String getEnsemble();

            String getNamespace();
        }
    }


}
