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
package org.apache.knox.gateway.service.config.remote.zk;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.ACLProvider;
import org.apache.curator.framework.imps.DefaultACLProvider;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.NodeCache;
import org.apache.curator.framework.recipes.cache.NodeCacheListener;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.knox.gateway.config.ConfigurationException;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.service.config.remote.RemoteConfigurationMessages;
import org.apache.knox.gateway.service.config.remote.RemoteConfigurationRegistryConfig;
import org.apache.knox.gateway.service.config.remote.config.RemoteConfigurationRegistriesAccessor;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClient;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClient.ChildEntryListener;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClient.EntryListener;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.client.ZKClientConfig;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;
import org.apache.zookeeper.data.Stat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RemoteConfigurationRegistryClientService implementation that employs the Curator ZooKeeper client framework.
 */
class CuratorClientService implements ZooKeeperClientService {

    private static final String LOGIN_CONTEXT_NAME_PROPERTY = ZKClientConfig.LOGIN_CONTEXT_NAME_KEY;

    private static final String DEFAULT_LOGIN_CONTEXT_NAME = "Client";

    private static final RemoteConfigurationMessages log =
                                                        MessagesFactory.get(RemoteConfigurationMessages.class);

    private Map<String, RemoteConfigurationRegistryClient> clients = new HashMap<>();

    private AliasService aliasService;


    @Override
    public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {

      // Load the remote registry configurations
      List<RemoteConfigurationRegistryConfig> registryConfigs = new ArrayList<>(RemoteConfigurationRegistriesAccessor.getRemoteRegistryConfigurations(config));

      // Configure registry authentication
      try {
        RemoteConfigurationRegistryJAASConfig.configure(registryConfigs, aliasService);
      } catch (ConfigurationException e) {
        throw new ServiceLifecycleException("Error while configuring registry authentication", e);
      }

        if (registryConfigs.size() > 1) {
            // Warn about current limit on number of supported client configurations
            log.multipleRemoteRegistryConfigurations();
        }

        // Create the clients
        for (RemoteConfigurationRegistryConfig registryConfig : registryConfigs) {
            if (TYPE.equalsIgnoreCase(registryConfig.getRegistryType())) {
                RemoteConfigurationRegistryClient registryClient = createClient(registryConfig);
                clients.put(registryConfig.getName(), registryClient);
            }
        }
    }

    @Override
    public void setAliasService(AliasService aliasService) {
        this.aliasService = aliasService;
    }

    @Override
    public void start() throws ServiceLifecycleException {
    }

    @Override
    public void stop() throws ServiceLifecycleException {
        for(RemoteConfigurationRegistryClient client : clients.values()) {
            try {
                client.close();
            } catch (Exception e) {
                throw new ServiceLifecycleException("failed to close client", e);
            }
        }
    }

    @Override
    public RemoteConfigurationRegistryClient get(String name) {
        return clients.get(name);
    }

    private RemoteConfigurationRegistryClient createClient(RemoteConfigurationRegistryConfig config) {
        ACLProvider aclProvider;
        if (config.isSecureRegistry()) {
            configureSasl(config);
            if(AUTH_TYPE_KERBEROS.equalsIgnoreCase(config.getAuthType()) &&
                !config.isBackwardsCompatible()) {
                aclProvider = new SASLOwnerACLProvider(true);
            } else {
                aclProvider = new SASLOwnerACLProvider(false);
            }

        } else {
            // Clear SASL system property
            System.clearProperty(LOGIN_CONTEXT_NAME_PROPERTY);
            aclProvider = new DefaultACLProvider();
        }

        CuratorFramework client = CuratorFrameworkFactory.builder()
                                                         .connectString(config.getConnectionString())
                                                         .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                                                         .aclProvider(aclProvider)
                                                         .build();

        client.start();

        return (new ClientAdapter(client, config));
    }


    private void configureSasl(RemoteConfigurationRegistryConfig config) {
        String registryName = config.getName();
        if (registryName == null) {
            registryName = DEFAULT_LOGIN_CONTEXT_NAME;
        }
        System.setProperty(LOGIN_CONTEXT_NAME_PROPERTY, registryName);
    }


    private static final class ClientAdapter implements RemoteConfigurationRegistryClient {

        private CuratorFramework delegate;

        private RemoteConfigurationRegistryConfig config;

        private Map<String, NodeCache> entryNodeCaches = new HashMap<>();

        ClientAdapter(CuratorFramework delegate, RemoteConfigurationRegistryConfig config) {
            this.delegate = delegate;
            this.config = config;
        }

        @Override
        public String getAddress() {
            return config.getConnectionString();
        }

        @Override
        public boolean isAuthenticationConfigured() {
            return config.isSecureRegistry();
        }

        @Override
        public boolean entryExists(String path) {
            Stat s = null;
            try {
                s = delegate.checkExists().forPath(path);
            } catch (Exception e) {
                // Ignore
            }
            return (s != null);
        }

        @Override
        public List<RemoteConfigurationRegistryClient.EntryACL> getACL(String path) {
            List<RemoteConfigurationRegistryClient.EntryACL> acl = new ArrayList<>();
            try {
                List<ACL> zkACL = delegate.getACL().forPath(path);
                if (zkACL != null) {
                    for (ACL aclEntry : zkACL) {
                        RemoteConfigurationRegistryClient.EntryACL entryACL = new ZooKeeperACLAdapter(aclEntry);
                        acl.add(entryACL);
                    }
                }
            } catch (Exception e) {
                log.errorHandlingRemoteConfigACL(path, e);
            }
            return acl;
        }

        @Override
        public void setACL(String path, List<EntryACL> entryACLs) {
            // Translate the abstract ACLs into ZooKeeper ACLs
            List<ACL> delegateACLs = new ArrayList<>();
            for (EntryACL entryACL : entryACLs) {
                String scheme = entryACL.getType();
                String id = entryACL.getId();
                int permissions = 0;
                if (entryACL.canWrite()) {
                    permissions = ZooDefs.Perms.ALL;
                } else if (entryACL.canRead()){
                    permissions = ZooDefs.Perms.READ;
                }
                delegateACLs.add(new ACL(permissions, new Id(scheme, id)));
            }

            try {
                // Set the ACLs for the path
                delegate.setACL().withACL(delegateACLs).forPath(path);
            } catch (Exception e) {
                log.errorSettingEntryACL(path, e);
            }
        }

        @Override
        public List<String> listChildEntries(String path) {
            List<String> result = null;
            try {
                result = delegate.getChildren().forPath(path);
            } catch (Exception e) {
                log.errorInteractingWithRemoteConfigRegistry(e);
            }
            return result;
        }

        @Override
        public void addChildEntryListener(String path, ChildEntryListener listener) throws Exception {
            PathChildrenCache childCache = new PathChildrenCache(delegate, path, false);
            childCache.getListenable().addListener(new ChildEntryListenerAdapter(this, listener));
            childCache.start();
        }

        @Override
        public void addEntryListener(String path, EntryListener listener) throws Exception {
            NodeCache nodeCache = new NodeCache(delegate, path);
            nodeCache.getListenable().addListener(new EntryListenerAdapter(this, nodeCache, listener));
            nodeCache.start();
            entryNodeCaches.put(path, nodeCache);
        }

        @Override
        public void removeEntryListener(String path) throws Exception {
            NodeCache nodeCache = entryNodeCaches.remove(path);
            if (nodeCache != null) {
                nodeCache.close();
            }
        }

        @Override
        public String authenticationType() {
            return config.getAuthType();
        }

        @Override
        public boolean isBackwardsCompatible() {
            return config.isBackwardsCompatible();
        }

        @Override
        public String getEntryData(String path) {
            return getEntryData(path, StandardCharsets.UTF_8.name());
        }

        @Override
        public String getEntryData(String path, String encoding) {
            String result = null;
            try {
                byte[] data = delegate.getData().forPath(path);
                if (data != null) {
                    result = new String(data, Charset.forName(encoding));
                }
            } catch (Exception e) {
                log.errorInteractingWithRemoteConfigRegistry(e);
            }
            return result;
        }

        @Override
        public void createEntry(String path) {
            createEntry(path, null);
        }

        @Override
        public void createEntry(String path, String data) {
            createEntry(path, data, StandardCharsets.UTF_8.name());
        }

        @Override
        public void createEntry(String path, String data, String encoding) {
            try {
                byte[] dataBytes;
                if(data == null) {
                    // Match default znode value like curator
                    // {@see CuratorFrameworkImpl#getDefaultData}
                    dataBytes = new byte[0];
                } else {
                    dataBytes = data.getBytes(encoding);
                }
                if (delegate.checkExists().forPath(path) == null) {
                    delegate.create().forPath(path, dataBytes);
                }
            } catch (Exception e) {
                log.errorInteractingWithRemoteConfigRegistry(e);
            }
        }

        @Override
        public int setEntryData(String path, String data) {
            return setEntryData(path, data, StandardCharsets.UTF_8.name());
        }

        @Override
        public int setEntryData(String path, String data, String encoding) {
            int version = 0;
            try {
                Stat s = delegate.setData().forPath(path, data.getBytes(Charset.forName(encoding)));
                if (s != null) {
                    version = s.getVersion();
                }
            } catch (Exception e) {
                log.errorInteractingWithRemoteConfigRegistry(e);
            }
            return version;
        }

        @Override
        public void deleteEntry(String path) {
            try {
                delegate.delete().forPath(path);
            } catch (Exception e) {
                log.errorInteractingWithRemoteConfigRegistry(e);
            }
        }

        @Override
        public void close() throws Exception {
            delegate.close();
        }
    }

    /**
     * SASL ACLProvider
     */
    private static class SASLOwnerACLProvider implements ACLProvider {

        private final List<ACL> saslACL = new ArrayList();

        SASLOwnerACLProvider(boolean isKerberos) {
            if(isKerberos) {
                saslACL.add(new ACL(ZooDefs.Perms.ALL, new Id("sasl", "knox")));
            } else {
                this.saslACL.addAll(ZooDefs.Ids.CREATOR_ALL_ACL); // All permissions for any authenticated user
            }
        }

        @Override
        public List<ACL> getDefaultAcl() {
            return saslACL;
        }

        @Override
        public List<ACL> getAclForPath(String path) {
            return getDefaultAcl();
        }
    }

    private static final class ChildEntryListenerAdapter implements PathChildrenCacheListener {
        private RemoteConfigurationRegistryClient client;
        private ChildEntryListener delegate;

        ChildEntryListenerAdapter(RemoteConfigurationRegistryClient client, ChildEntryListener delegate) {
            this.client = client;
            this.delegate = delegate;
        }

        @Override
        public void childEvent(CuratorFramework curatorFramework, PathChildrenCacheEvent pathChildrenCacheEvent)
                throws Exception {
            ChildData childData = pathChildrenCacheEvent.getData();
            if (childData != null) {
                ChildEntryListener.Type eventType = adaptType(pathChildrenCacheEvent.getType());
                if (eventType != null) {
                    delegate.childEvent(client, eventType, childData.getPath());
                }
            }
        }

        private ChildEntryListener.Type adaptType(PathChildrenCacheEvent.Type type) {
            ChildEntryListener.Type adapted = null;

            switch(type) {
                case CHILD_ADDED:
                    adapted = ChildEntryListener.Type.ADDED;
                    break;
                case CHILD_REMOVED:
                    adapted = ChildEntryListener.Type.REMOVED;
                    break;
                case CHILD_UPDATED:
                    adapted = ChildEntryListener.Type.UPDATED;
                    break;
            }

            return adapted;
        }
    }

    private static final class EntryListenerAdapter implements NodeCacheListener {

        private RemoteConfigurationRegistryClient client;
        private EntryListener delegate;
        private NodeCache nodeCache;

        EntryListenerAdapter(RemoteConfigurationRegistryClient client, NodeCache nodeCache, EntryListener delegate) {
            this.client = client;
            this.nodeCache = nodeCache;
            this.delegate = delegate;
        }

        @Override
        public void nodeChanged() throws Exception {
            String path = null;
            byte[] data = null;

            ChildData cd = nodeCache.getCurrentData();
            if (cd != null) {
                path = cd.getPath();
                data = cd.getData();
            }

            if (path != null) {
                delegate.entryChanged(client, path, data);
            }
        }
    }

    /**
     * ACL adapter
     */
    private static final class ZooKeeperACLAdapter implements RemoteConfigurationRegistryClient.EntryACL {
        private String type;
        private String id;
        private int permissions;

        ZooKeeperACLAdapter(ACL acl) {
            this.permissions = acl.getPerms();
            this.type = acl.getId().getScheme();
            this.id = acl.getId().getId();
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public Object getPermissions() {
            return permissions;
        }

        @Override
        public boolean canRead() {
            return (permissions >= ZooDefs.Perms.READ);
        }

        @Override
        public boolean canWrite() {
            return (permissions >= ZooDefs.Perms.WRITE);
        }
    }

}
