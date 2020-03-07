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

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.topology.discovery.ClusterConfigurationMonitor;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class AmbariConfigurationMonitor implements ClusterConfigurationMonitor {
    private static final String TYPE = "Ambari";

    private static final String CLUSTERS_DATA_DIR_NAME = "clusters";

    private static final String PERSISTED_FILE_COMMENT = "Generated File. Do Not Edit!";

    private static final String PROP_CLUSTER_PREFIX = "cluster.";
    private static final String PROP_CLUSTER_SOURCE = PROP_CLUSTER_PREFIX + "source";
    private static final String PROP_CLUSTER_NAME   = PROP_CLUSTER_PREFIX + "name";
    private static final String PROP_CLUSTER_USER   = PROP_CLUSTER_PREFIX + "user";
    private static final String PROP_CLUSTER_ALIAS  = PROP_CLUSTER_PREFIX + "pwd.alias";

    static final String INTERVAL_PROPERTY_NAME = "org.apache.knox.gateway.topology.discovery.ambari.monitor.interval";

    private static final AmbariServiceDiscoveryMessages log = MessagesFactory.get(AmbariServiceDiscoveryMessages.class);

    // Ambari address
    //    clusterName -> ServiceDiscoveryConfig
    //
    Map<String, Map<String, ServiceDiscoveryConfig>> clusterMonitorConfigurations = new HashMap<>();

    // Ambari address
    //    clusterName
    //        configType -> version
    //
    Map<String, Map<String, Map<String, String>>> ambariClusterConfigVersions = new HashMap<>();

    ReadWriteLock configVersionsLock = new ReentrantReadWriteLock();

    private List<ConfigurationChangeListener> changeListeners = new ArrayList<>();

    private AmbariClientCommon ambariClient;

    PollingConfigAnalyzer internalMonitor;

    GatewayConfig gatewayConfig;

    static String getType() {
        return TYPE;
    }

    AmbariConfigurationMonitor(GatewayConfig config, AliasService aliasService, KeystoreService keystoreService) {
        this.gatewayConfig   = config;
        this.ambariClient    = new AmbariClientCommon(config, aliasService, keystoreService);
        this.internalMonitor = new PollingConfigAnalyzer(this);

        // Override the default polling interval if it has been configured
        int interval = config.getClusterMonitorPollingInterval(getType());
        if (interval > 0) {
            setPollingInterval(interval);
        }

        init();
    }

    @Override
    public void setPollingInterval(int interval) {
        internalMonitor.setInterval(interval);
    }

    private void init() {
        loadDiscoveryConfiguration();
        loadClusterVersionData();
    }

    /**
     * Load any previously-persisted service discovery configurations.
     * This is necessary for checking previously-deployed topologies.
     */
    private void loadDiscoveryConfiguration() {
        File persistenceDir = getPersistenceDir();
        if (persistenceDir != null) {
            Collection<File> persistedConfigs = FileUtils.listFiles(persistenceDir, new String[]{"conf"}, false);
            for (File persisted : persistedConfigs) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(persisted.toPath())) {
                    props.load(in);

                    addDiscoveryConfig(props.getProperty(PROP_CLUSTER_NAME), new ServiceDiscoveryConfig() {
                                                            @Override
                                                            public String getAddress() {
                                                                return props.getProperty(PROP_CLUSTER_SOURCE);
                                                            }

                                                            @Override
                                                            public String getCluster() {
                                                                return props.getProperty(PROP_CLUSTER_NAME);
                                                            }

                                                            @Override
                                                            public String getUser() {
                                                                return props.getProperty(PROP_CLUSTER_USER);
                                                            }

                                                            @Override
                                                            public String getPasswordAlias() {
                                                                return props.getProperty(PROP_CLUSTER_ALIAS);
                                                            }
                                                        });
                } catch (IOException e) {
                    log.failedToLoadClusterMonitorServiceDiscoveryConfig(getType(), e);
                }
            }
        }
    }

    /**
     * Load any previously-persisted cluster configuration version records, so the monitor will check
     * previously-deployed topologies against the current cluster configuration.
     */
    private void loadClusterVersionData() {
        File persistenceDir = getPersistenceDir();
        if (persistenceDir != null) {
            Collection<File> persistedConfigs = FileUtils.listFiles(persistenceDir, new String[]{"ver"}, false);
            for (File persisted : persistedConfigs) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(persisted.toPath())) {
                    props.load(in);

                    String source = props.getProperty(PROP_CLUSTER_SOURCE);
                    String clusterName = props.getProperty(PROP_CLUSTER_NAME);

                    Map<String, String> configVersions = new HashMap<>();
                    for (String name : props.stringPropertyNames()) {
                        if (!name.startsWith(PROP_CLUSTER_PREFIX)) { // Ignore implementation-specific properties
                            configVersions.put(name, props.getProperty(name));
                        }
                    }

                    // Map the config versions to the cluster name
                    addClusterConfigVersions(source, clusterName, configVersions);
                } catch (IOException e) {
                    log.failedToLoadClusterMonitorConfigVersions(getType(), e);
                }
            }
        }
    }

    private void persistDiscoveryConfiguration(String clusterName, ServiceDiscoveryConfig sdc) {
        File persistenceDir = getPersistenceDir();
        if (persistenceDir != null) {

            Properties props = new Properties();
            props.setProperty(PROP_CLUSTER_NAME, clusterName);
            props.setProperty(PROP_CLUSTER_SOURCE, sdc.getAddress());

            String username = sdc.getUser();
            if (username != null) {
                props.setProperty(PROP_CLUSTER_USER, username);
            }
            String pwdAlias = sdc.getPasswordAlias();
            if (pwdAlias != null) {
                props.setProperty(PROP_CLUSTER_ALIAS, pwdAlias);
            }

            persist(props, getDiscoveryConfigPersistenceFile(sdc.getAddress(), clusterName));
        }
    }

    private void persistClusterVersionData(String address, String clusterName, Map<String, String> configVersions) {
        File persistenceDir = getPersistenceDir();
        if (persistenceDir != null) {
            Properties props = new Properties();
            props.setProperty(PROP_CLUSTER_NAME, clusterName);
            props.setProperty(PROP_CLUSTER_SOURCE, address);
            for (Entry<String, String> configVersion : configVersions.entrySet()) {
                props.setProperty(configVersion.getKey(), configVersion.getValue());
            }

            persist(props, getConfigVersionsPersistenceFile(address, clusterName));
        }
    }

    private void persist(Properties props, File dest) {
        try (OutputStream out = Files.newOutputStream(dest.toPath())) {
            props.store(out, PERSISTED_FILE_COMMENT);
            out.flush();
        } catch (Exception e) {
            log.failedToPersistClusterMonitorData(getType(), dest.getAbsolutePath(), e);
        }
    }

    private File getPersistenceDir() {
        File persistenceDir = null;

        File dataDir = new File(gatewayConfig.getGatewayDataDir());
        if (dataDir.exists()) {
            File clustersDir = new File(dataDir, CLUSTERS_DATA_DIR_NAME);
            if (!clustersDir.exists()) {
                clustersDir.mkdirs();
            }
            persistenceDir = clustersDir;
        }

        return persistenceDir;
    }

    private File getDiscoveryConfigPersistenceFile(String address, String clusterName) {
        return getPersistenceFile(address, clusterName, "conf");
    }

    private File getConfigVersionsPersistenceFile(String address, String clusterName) {
        return getPersistenceFile(address, clusterName, "ver");
    }

    private File getPersistenceFile(String address, String clusterName, String ext) {
        String fileName = address.replace(":", "_").replace("/", "_") + "-" + clusterName + "." + ext;
        return new File(getPersistenceDir(), fileName);
    }

    /**
     * Add cluster configuration details to the monitor's in-memory record.
     *
     * @param address        An Ambari instance address.
     * @param clusterName    The name of a cluster associated with the Ambari instance.
     * @param configVersions A Map of configuration types and their corresponding versions.
     */
    private void addClusterConfigVersions(String address, String clusterName, Map<String, String> configVersions) {
        configVersionsLock.writeLock().lock();
        try {
            ambariClusterConfigVersions.computeIfAbsent(address, k -> new HashMap<>())
                                       .put(clusterName, configVersions);
        } finally {
            configVersionsLock.writeLock().unlock();
        }
    }

    @SuppressWarnings("PMD.DoNotUseThreads")
    @Override
    public void start() {
        (new Thread(internalMonitor, "AmbariConfigurationMonitor")).start();
    }

    @Override
    public void stop() {
        internalMonitor.stop();
    }

    @Override
    public void addListener(ConfigurationChangeListener listener) {
        changeListeners.add(listener);
    }

    @Override
    public void clearCache(String source, String clusterName) {
        this.removeClusterConfigVersions(source, clusterName);
    }

    /**
     * Add discovery configuration details for the specified cluster, so the monitor knows how to connect to check for
     * changes.
     *
     * @param clusterName The name of the cluster.
     * @param config      The associated service discovery configuration.
     */
    void addDiscoveryConfig(String clusterName, ServiceDiscoveryConfig config) {
        clusterMonitorConfigurations.computeIfAbsent(config.getAddress(), k -> new HashMap<>()).put(clusterName, config);
    }

    /**
     * Get the service discovery configuration associated with the specified Ambari instance and cluster.
     *
     * @param address     An Ambari instance address.
     * @param clusterName The name of a cluster associated with the Ambari instance.
     *
     * @return The associated ServiceDiscoveryConfig object.
     */
    ServiceDiscoveryConfig getDiscoveryConfig(String address, String clusterName) {
        ServiceDiscoveryConfig config = null;
        if (clusterMonitorConfigurations.containsKey(address)) {
            config = clusterMonitorConfigurations.get(address).get(clusterName);
        }
        return config;
    }

    /**
     * Add cluster configuration data to the monitor, which it will use when determining if configuration has changed.
     *
     * @param cluster         An AmbariCluster object.
     * @param discoveryConfig The discovery configuration associated with the cluster.
     */
    void addClusterConfigVersions(AmbariCluster cluster, ServiceDiscoveryConfig discoveryConfig) {

        String clusterName = cluster.getName();

        // Register the cluster discovery configuration for the monitor connections
        persistDiscoveryConfiguration(clusterName, discoveryConfig);
        addDiscoveryConfig(clusterName, discoveryConfig);

        // Build the set of configuration versions
        Map<String, String> configVersions = new HashMap<>();
        Map<String, Map<String, AmbariCluster.ServiceConfiguration>> serviceConfigs = cluster.getServiceConfigurations();
        for (Entry<String, Map<String, AmbariCluster.ServiceConfiguration>> serviceConfig : serviceConfigs.entrySet()) {
            Map<String, AmbariCluster.ServiceConfiguration> configTypeVersionMap = serviceConfig.getValue();
            for (AmbariCluster.ServiceConfiguration config : configTypeVersionMap.values()) {
                String configType = config.getType();
                String version = config.getVersion();
                configVersions.put(configType, version);
            }
        }

        persistClusterVersionData(discoveryConfig.getAddress(), clusterName, configVersions);
        addClusterConfigVersions(discoveryConfig.getAddress(), clusterName, configVersions);
    }

    /**
     * Remove the configuration record for the specified Ambari instance and cluster name.
     *
     * @param address     An Ambari instance address.
     * @param clusterName The name of a cluster associated with the Ambari instance.
     *
     * @return The removed data; A Map of configuration types and their corresponding versions.
     */
    Map<String, String> removeClusterConfigVersions(String address, String clusterName) {
        Map<String, String> result = new HashMap<>();

        configVersionsLock.writeLock().lock();
        try {
            if (ambariClusterConfigVersions.containsKey(address)) {
                result.putAll(ambariClusterConfigVersions.get(address).remove(clusterName));
            }
        } finally {
            configVersionsLock.writeLock().unlock();
        }

        // Delete the associated persisted record
        File persisted = getConfigVersionsPersistenceFile(address, clusterName);
        if (persisted.exists()) {
            persisted.delete();
        }

        return result;
    }

    /**
     * Get the cluster configuration details for the specified cluster and Ambari instance.
     *
     * @param address     An Ambari instance address.
     * @param clusterName The name of a cluster associated with the Ambari instance.
     *
     * @return A Map of configuration types and their corresponding versions.
     */
    Map<String, String> getClusterConfigVersions(String address, String clusterName) {
        Map<String, String> result = new HashMap<>();

        configVersionsLock.readLock().lock();
        try {
            if (ambariClusterConfigVersions.containsKey(address)) {
                result.putAll(ambariClusterConfigVersions.get(address).get(clusterName));
            }
        } finally {
            configVersionsLock.readLock().unlock();
        }

        return result;
    }

    /**
     * Get all the clusters the monitor knows about.
     *
     * @return A Map of Ambari instance addresses to associated cluster names.
     */
    Map<String, List<String>> getClusterNames() {
        Map<String, List<String>> result = new HashMap<>();

        configVersionsLock.readLock().lock();
        try {
            for (Entry<String, Map<String, Map<String, String>>> ambariClusterConfigVersion : ambariClusterConfigVersions.entrySet()) {
              List<String> clusterNames = new ArrayList<>(ambariClusterConfigVersion.getValue().keySet());
                result.put(ambariClusterConfigVersion.getKey(), clusterNames);
            }
        } finally {
            configVersionsLock.readLock().unlock();
        }

        return result;
    }

    /**
     * Notify registered change listeners.
     *
     * @param source      The address of the Ambari instance from which the cluster details were determined.
     * @param clusterName The name of the cluster whose configuration details have changed.
     */
    void notifyChangeListeners(String source, String clusterName) {
        for (ConfigurationChangeListener listener : changeListeners) {
            listener.onConfigurationChange(source, clusterName);
        }
    }

    /**
     * Request the current active configuration version info from Ambari.
     *
     * @param address     The Ambari instance address.
     * @param clusterName The name of the cluster for which the details are desired.
     *
     * @return A Map of service configuration types and their corresponding versions.
     */
    Map<String, String> getUpdatedConfigVersions(String address, String clusterName) {
        Map<String, String> configVersions = new HashMap<>();

        ServiceDiscoveryConfig sdc = getDiscoveryConfig(address, clusterName);
        if (sdc != null) {
            Map<String, Map<String, AmbariCluster.ServiceConfiguration>> serviceConfigs =
                                                       ambariClient.getActiveServiceConfigurations(clusterName, sdc);

            for (Map<String, AmbariCluster.ServiceConfiguration> serviceConfig : serviceConfigs.values()) {
                for (AmbariCluster.ServiceConfiguration config : serviceConfig.values()) {
                    configVersions.put(config.getType(), config.getVersion());
                }
            }
        }

        return configVersions;
    }

    /**
     * The thread that polls Ambari for configuration details for clusters associated with discovered topologies,
     * compares them with the current recorded values, and notifies any listeners when differences are discovered.
     */
    @SuppressWarnings("PMD.DoNotUseThreads")
    static final class PollingConfigAnalyzer implements Runnable {

        private static final int DEFAULT_POLLING_INTERVAL = 60;

        // Polling interval in seconds
        private int interval;

        private AmbariConfigurationMonitor delegate;

        private boolean isActive;

        PollingConfigAnalyzer(AmbariConfigurationMonitor delegate) {
            this.delegate = delegate;
            this.interval = Integer.getInteger(INTERVAL_PROPERTY_NAME, PollingConfigAnalyzer.DEFAULT_POLLING_INTERVAL);
        }

        void setInterval(int interval) {
            this.interval = interval;
        }

        void stop() {
            isActive = false;
        }

        @Override
        public void run() {
            isActive = true;

            log.startedAmbariConfigMonitor(interval);

            while (isActive) {
                for (Map.Entry<String, List<String>> entry : delegate.getClusterNames().entrySet()) {
                    String address = entry.getKey();
                    for (String clusterName : entry.getValue()) {
                        Map<String, String> configVersions = delegate.getClusterConfigVersions(address, clusterName);
                        if (configVersions != null && !configVersions.isEmpty()) {
                            Map<String, String> updatedVersions = delegate.getUpdatedConfigVersions(address, clusterName);
                            if (updatedVersions != null && !updatedVersions.isEmpty()) {
                                boolean configHasChanged = false;

                                // If the config sets don't match in size, then something has changed
                                if (updatedVersions.size() != configVersions.size()) {
                                    configHasChanged = true;
                                } else {
                                    // Perform the comparison of all the config versions
                                    for (Map.Entry<String, String> configVersion : configVersions.entrySet()) {
                                        if (!updatedVersions.get(configVersion.getKey()).equals(configVersion.getValue())) {
                                            configHasChanged = true;
                                            break;
                                        }
                                    }
                                }

                                // If a change has occurred, notify the listeners
                                if (configHasChanged) {
                                    delegate.notifyChangeListeners(address, clusterName);
                                }
                            }
                        }
                    }
                }

                try {
                    Thread.sleep(interval * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
