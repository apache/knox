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
package org.apache.knox.gateway.service.config.remote;

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.service.config.remote.config.RemoteConfigurationRegistriesAccessor;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClient;
import org.apache.knox.gateway.services.config.client.RemoteConfigurationRegistryClientService;
import org.apache.knox.gateway.services.security.AliasService;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * An implementation of RemoteConfigurationRegistryClientService intended to be used for testing without having to
 * connect to an actual remote configuration registry.
 */
public class LocalFileSystemRemoteConfigurationRegistryClientService implements RemoteConfigurationRegistryClientService {

    public static final String TYPE = "LocalFileSystem";

    private Map<String, RemoteConfigurationRegistryClient> clients = new HashMap<>();


    @Override
    public void setAliasService(AliasService aliasService) {
        // N/A
    }

    @Override
    public RemoteConfigurationRegistryClient get(String name) {
        return clients.get(name);
    }

    @Override
    public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
        List<RemoteConfigurationRegistryConfig> registryConfigurations =
                                        RemoteConfigurationRegistriesAccessor.getRemoteRegistryConfigurations(config);
        for (RemoteConfigurationRegistryConfig registryConfig : registryConfigurations) {
            if (TYPE.equalsIgnoreCase(registryConfig.getRegistryType())) {
                RemoteConfigurationRegistryClient registryClient = createClient(registryConfig);
                clients.put(registryConfig.getName(), registryClient);
            }
        }
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

    private RemoteConfigurationRegistryClient createClient(RemoteConfigurationRegistryConfig config) {
        String rootDir = config.getConnectionString();

        return new RemoteConfigurationRegistryClient() {
            @Override
            public void close() throws Exception {

            }

            private File root = new File(rootDir);

            @Override
            public String getAddress() {
                return root.getAbsolutePath();
            }

            @Override
            public boolean entryExists(String path) {
                return (new File(root, path)).exists();
            }

            @Override
            public List<EntryACL> getACL(String path) {
                List<EntryACL> result = new ArrayList<>();

                Path resolved = Paths.get(rootDir, path);
                try {
                    Map<String, List<String>> collected = new HashMap<>();

                    Set<PosixFilePermission> perms = Files.getPosixFilePermissions(resolved);
                    for (PosixFilePermission perm : perms) {
                        String[] parsed = perm.toString().split("_");
                        collected.computeIfAbsent(parsed[0].toLowerCase(Locale.ROOT), s -> new ArrayList<>()).add(parsed[1].toLowerCase(Locale.ROOT));
                    }

                    for (String id : collected.keySet()) {
                        EntryACL acl = new EntryACL() {
                            @Override
                            public String getId() {
                                return id;
                            }

                            @Override
                            public String getType() {
                                return "fs";
                            }

                            @Override
                            public Object getPermissions() {
                                return collected.get(id).toString();
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
                        result.add(acl);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return result;
            }

            @Override
            public List<String> listChildEntries(String path) {
                List<String> result = new ArrayList<>();

                File entry = new File(root, path);
                if (entry.exists() && entry.isDirectory()) {
                    String[] list = entry.list();
                    if (list != null) {
                        result.addAll(Arrays.asList(entry.list()));
                    }
                }

                return result;
            }

            @Override
            public String getEntryData(String path) {
                return getEntryData(path, StandardCharsets.UTF_8.name());
            }

            @Override
            public String getEntryData(String path, String encoding) {
                String result = null;
                File entry = new File(root, path);
                if (entry.isFile() && entry.exists()) {
                    try {
                        result = FileUtils.readFileToString(entry, encoding);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return result;
            }

            @Override
            public void createEntry(String path) {
                createEntry(path, "");
            }

            @Override
            public void createEntry(String path, String data) {
                createEntry(path, data, StandardCharsets.UTF_8.name());
            }

            @Override
            public void createEntry(String path, String data, String encoding) {
                File entry = new File(root, path);
                if (!entry.exists()) {
                    if (data != null) {
                        try {
                            FileUtils.writeStringToFile(entry, data, encoding);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            @Override
            public int setEntryData(String path, String data) {
                setEntryData(path, data, StandardCharsets.UTF_8.name());
                return 0;
            }

            @Override
            public int setEntryData(String path, String data, String encoding) {
                File entry = new File(root, path);
                if (entry.exists()) {
                    try {
                        FileUtils.writeStringToFile(entry, data, encoding);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return 0;
            }

            @Override
            public boolean isAuthenticationConfigured() {
                return false;
            }

            @Override
            public void setACL(String path, List<EntryACL> acls) {
                //
            }

            @Override
            public void deleteEntry(String path) {
                File entry = new File(root, path);
                if (entry.exists()) {
                    entry.delete();
                }
            }

            @Override
            public void addChildEntryListener(String path, ChildEntryListener listener) throws Exception {
                // N/A
            }

            @Override
            public void addEntryListener(String path, EntryListener listener) throws Exception {
                // N/A
            }

            @Override
            public void removeEntryListener(String path) throws Exception {
                // N/A
            }

            @Override
            public String authenticationType() {
                return null;
            }

            @Override
            public boolean isBackwardsCompatible() {
                return false;
            }
        };
    }

}
