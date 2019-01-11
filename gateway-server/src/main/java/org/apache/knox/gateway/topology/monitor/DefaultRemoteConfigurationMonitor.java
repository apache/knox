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
package org.apache.knox.gateway.topology.monitor;

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClient;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClient.ChildEntryListener;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClient.EntryListener;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.apache.zookeeper.ZooDefs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class DefaultRemoteConfigurationMonitor implements RemoteConfigurationMonitor {

    private static final String NODE_KNOX = "/knox";
    private static final String NODE_KNOX_CONFIG = NODE_KNOX + "/config";
    private static final String NODE_KNOX_PROVIDERS = NODE_KNOX_CONFIG + "/shared-providers";
    private static final String NODE_KNOX_DESCRIPTORS = NODE_KNOX_CONFIG + "/descriptors";

    private static GatewayMessages log = MessagesFactory.get(GatewayMessages.class);

    // N.B. This is ZooKeeper-specific, and should be abstracted when another registry is supported
    private static final RemoteConfigurationRegistryClient.EntryACL AUTHENTICATED_USERS_ALL;
    static {
        AUTHENTICATED_USERS_ALL = new RemoteConfigurationRegistryClient.EntryACL() {
            @Override
            public String getId() {
                return "";
            }

            @Override
            public String getType() {
                return "auth";
            }

            @Override
            public Object getPermissions() {
                return ZooDefs.Perms.ALL;
            }

            @Override
            public boolean canRead() {
                return true;
            }

            @Override
            public boolean canWrite() {
                return true;
            }
        };
    }

    private static final RemoteConfigurationRegistryClient.EntryACL WORLD_ANYONE_READ;
    static {
        WORLD_ANYONE_READ = new RemoteConfigurationRegistryClient.EntryACL() {
            @Override
            public String getId() {
                return "anyone";
            }

            @Override
            public String getType() {
                return "world";
            }

            @Override
            public Object getPermissions() {
                return ZooDefs.Perms.READ;
            }

            @Override
            public boolean canRead() {
                return true;
            }

            @Override
            public boolean canWrite() {
                return false;
            }
        };
    }

    private RemoteConfigurationRegistryClient client;

    private File providersDir;
    private File descriptorsDir;

    private final List<RemoteConfigurationRegistryClient.EntryACL> replacementACL = new ArrayList<>();

    /**
     * @param config                The gateway configuration
     * @param registryClientService The service from which the remote registry client should be acquired.
     */
    DefaultRemoteConfigurationMonitor(GatewayConfig                            config,
                                      RemoteConfigurationRegistryClientService registryClientService) {
        this.providersDir   = new File(config.getGatewayProvidersConfigDir());
        this.descriptorsDir = new File(config.getGatewayDescriptorsDir());

        if (registryClientService != null) {
            String clientName = config.getRemoteConfigurationMonitorClientName();
            if (clientName != null) {
                this.client = registryClientService.get(clientName);
                if (this.client == null) {
                    log.unresolvedClientConfigurationForRemoteMonitoring(clientName);
                } else if (config.allowUnauthenticatedRemoteRegistryReadAccess()) {
                    replacementACL.add(WORLD_ANYONE_READ);
                }
            } else {
                log.missingClientConfigurationForRemoteMonitoring();
                throw new IllegalStateException("Missing required configuration.");
            }
        }

        replacementACL.add(AUTHENTICATED_USERS_ALL);
    }

    @Override
    public RemoteConfigurationRegistryClient getClient() {
        return client;
    }

    @Override
    public void start() throws Exception {
        if (client == null) {
            throw new IllegalStateException("Failed to acquire a remote configuration registry client.");
        }

        final String monitorSource = client.getAddress();
        log.startingRemoteConfigurationMonitor(monitorSource);

        // Ensure the existence of the expected entries and their associated ACLs
        ensureEntries();

        // Confirm access to the remote provider configs directory znode
        List<String> providerConfigs = client.listChildEntries(NODE_KNOX_PROVIDERS);
        if (providerConfigs == null) {
            // Either the ZNode does not exist, or there is an authentication problem
            throw new IllegalStateException("Unable to access remote path: " + NODE_KNOX_PROVIDERS);
        } else {
            // Download any existing provider configs in the remote registry, which either do not exist locally, or have
            // been modified, so that they are certain to be present when this monitor downloads any descriptors that
            // reference them.
            for (String providerConfig : providerConfigs) {
                File localFile = new File(providersDir, providerConfig);

                byte[] remoteContent = client.getEntryData(NODE_KNOX_PROVIDERS + "/" + providerConfig).getBytes(StandardCharsets.UTF_8);
                if (!localFile.exists() || !Arrays.equals(remoteContent, FileUtils.readFileToByteArray(localFile))) {
                    FileUtils.writeByteArrayToFile(localFile, remoteContent);
                    log.downloadedRemoteConfigFile(providersDir.getName(), providerConfig);
                }
            }
        }

        // Confirm access to the remote descriptors directory znode
        List<String> descriptors = client.listChildEntries(NODE_KNOX_DESCRIPTORS);
        if (descriptors == null) {
            // Either the ZNode does not exist, or there is an authentication problem
            throw new IllegalStateException("Unable to access remote path: " + NODE_KNOX_DESCRIPTORS);
        }

        // Register a listener for provider config znode additions/removals
        client.addChildEntryListener(NODE_KNOX_PROVIDERS, new ConfigDirChildEntryListener(providersDir));

        // Register a listener for descriptor znode additions/removals
        client.addChildEntryListener(NODE_KNOX_DESCRIPTORS, new ConfigDirChildEntryListener(descriptorsDir));

        log.monitoringRemoteConfigurationSource(monitorSource);
    }


    @Override
    public void stop() throws Exception {
        client.removeEntryListener(NODE_KNOX_PROVIDERS);
        client.removeEntryListener(NODE_KNOX_DESCRIPTORS);
    }

    private void ensureEntries() {
        ensureEntry(NODE_KNOX);
        ensureEntry(NODE_KNOX_CONFIG);
        ensureEntry(NODE_KNOX_PROVIDERS);
        ensureEntry(NODE_KNOX_DESCRIPTORS);
    }

    private void ensureEntry(String name) {
        if (!client.entryExists(name)) {
            client.createEntry(name);
        } else {
            // Validate the ACL
            List<RemoteConfigurationRegistryClient.EntryACL> entryACLs = client.getACL(name);
            for (RemoteConfigurationRegistryClient.EntryACL entryACL : entryACLs) {
                // N.B. This is ZooKeeper-specific, and should be abstracted when another registry is supported
                // For now, check for ZooKeeper world:anyone with ANY permissions (even read-only)
                if (entryACL.getType().equals("world") && entryACL.getId().equals("anyone")) {
                    log.suspectWritableRemoteConfigurationEntry(name);

                    // If the client is authenticated, but "anyone" can write the content, then the content may not
                    // be trustworthy.
                    if (client.isAuthenticationConfigured()) {
                        log.correctingSuspectWritableRemoteConfigurationEntry(name);

                        // Replace the existing ACL with the replacement ACL for the authentication scenario
                        client.setACL(name, replacementACL);
                    }
                }
            }
        }
    }

    private static class ConfigDirChildEntryListener implements ChildEntryListener {
        File localDir;

        ConfigDirChildEntryListener(File localDir) {
            this.localDir = localDir;
        }

        @Override
        public void childEvent(RemoteConfigurationRegistryClient client, Type type, String path) {
            File localFile = new File(localDir, path.substring(path.lastIndexOf('/') + 1));

            switch (type) {
                case REMOVED:
                    FileUtils.deleteQuietly(localFile);
                    log.deletedRemoteConfigFile(localDir.getName(), localFile.getName());
                    try {
                        client.removeEntryListener(path);
                    } catch (Exception e) {
                        log.errorRemovingRemoteConfigurationListenerForPath(path, e);
                    }
                    break;
                case ADDED:
                    try {
                        client.addEntryListener(path, new ConfigEntryListener(localDir));
                    } catch (Exception e) {
                        log.errorAddingRemoteConfigurationListenerForPath(path, e);
                    }
                    break;
            }
        }
    }

    private static class ConfigEntryListener implements EntryListener {
        private File localDir;

        ConfigEntryListener(File localDir) {
            this.localDir = localDir;
        }

        @Override
        public void entryChanged(RemoteConfigurationRegistryClient client, String path, byte[] data) {
            File localFile = new File(localDir, path.substring(path.lastIndexOf('/')));
            if (data != null) {
                try {
                    // If there is no corresponding local file, or the content is different from the existing local
                    // file, write the data to the local file.
                    if (!localFile.exists() || !Arrays.equals(FileUtils.readFileToByteArray(localFile), data)) {
                        FileUtils.writeByteArrayToFile(localFile, data);
                        log.downloadedRemoteConfigFile(localDir.getName(), localFile.getName());
                    }
                } catch (IOException e) {
                    log.errorDownloadingRemoteConfiguration(path, e);
                }
            } else {
                FileUtils.deleteQuietly(localFile);
                log.deletedRemoteConfigFile(localDir.getName(), localFile.getName());
            }
        }
    }

}
