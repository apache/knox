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
package org.apache.knox.gateway.topology;

import org.apache.knox.gateway.services.Service;
import org.apache.knox.gateway.topology.discovery.ClusterConfigurationMonitor;

/**
 * Gateway service for managing cluster configuration monitors.
 */
public interface ClusterConfigurationMonitorService extends Service {

    /**
     *
     * @param type The type of monitor (e.g., Ambari)
     *
     * @return The monitor associated with the specified type, or null if there is no such monitor.
     */
    ClusterConfigurationMonitor getMonitor(String type);


    /**
     * Register for configuration change notifications from <em>any</em> of the monitors managed by this service.
     *
     * @param listener The listener to register.
     */
    void addListener(ClusterConfigurationMonitor.ConfigurationChangeListener listener);

    /**
     * Clear the cluster configuration data cache for the specified source and cluster.
     *
     * @param source      The identifier of configuration source being monitored.
     * @param clusterName The name of an associated cluster being monitored.
     */
    void clearCache(String source, String clusterName);

}
