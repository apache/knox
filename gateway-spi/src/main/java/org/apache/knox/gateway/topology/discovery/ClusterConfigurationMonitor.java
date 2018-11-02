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
package org.apache.knox.gateway.topology.discovery;

public interface ClusterConfigurationMonitor {

    /**
     * Start the monitor.
     */
    void start();

    /**
     * Stop the monitor.
     */
    void stop();

    /**
     *
     * @param interval The polling interval, in seconds
     */
    void setPollingInterval(int interval);

    /**
     * Register for notifications from the monitor.
     * @param listener ConfigurationChangeListener
     */
    void addListener(ConfigurationChangeListener listener);

    /**
     * Clear the configuration data cache for the specified source and cluster name.
     * @param source source to clear for
     * @param clusterName clusterName to clear for
     */
    void clearCache(String source, String clusterName);

    /**
     * Monitor listener interface for receiving notifications that a configuration has changed.
     */
    interface ConfigurationChangeListener {
        void onConfigurationChange(String source, String clusterName);
    }
}
