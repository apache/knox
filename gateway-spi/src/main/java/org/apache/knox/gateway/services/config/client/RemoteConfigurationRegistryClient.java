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
package org.apache.knox.gateway.services.config.client;

import java.util.List;

public interface RemoteConfigurationRegistryClient extends AutoCloseable {

    String getAddress();

    boolean isAuthenticationConfigured();

    boolean entryExists(String path);

    List<EntryACL> getACL(String path);

    void setACL(String path, List<EntryACL> acls);

    List<String> listChildEntries(String path);

    String getEntryData(String path);

    String getEntryData(String path, String encoding);

    void createEntry(String path);

    void createEntry(String path, String data);

    void createEntry(String path, String data, String encoding);

    int setEntryData(String path, String data);

    int setEntryData(String path, String data, String encoding);

    void deleteEntry(String path);

    void addChildEntryListener(String path, ChildEntryListener listener) throws Exception;

    void addEntryListener(String path, EntryListener listener) throws Exception;

    void removeEntryListener(String path) throws Exception;

    String authenticationType();

    boolean isBackwardsCompatible();

    interface ChildEntryListener {

        enum Type {
            ADDED,
            REMOVED,
            UPDATED
        }

        void childEvent(RemoteConfigurationRegistryClient client, ChildEntryListener.Type type, String path);
    }

    interface EntryListener {
        void entryChanged(RemoteConfigurationRegistryClient client, String path, byte[] data);
    }

    interface EntryACL {
        String getId();
        String getType();
        Object getPermissions();
        boolean canRead();
        boolean canWrite();
    }

}
