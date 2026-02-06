/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.services.ldap.backend;

import com.google.gson.Gson;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ldap.LdapMessages;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * File-based backend that reads user/group data from JSON
 */
public class FileBackend implements LdapBackend {
    private static final LdapMessages LOG = MessagesFactory.get(LdapMessages.class);

    private Map<String, UserData> users = new HashMap<>();
    private String dataFile;
    private String baseDn;

    static class UserData {
        String username;
        String cn;
        String sn;
        List<String> groups;
        Map<String, String> attributes;
    }

    static class BackendData {
        List<UserData> users;
    }

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public void initialize(Map<String, String> config) throws Exception {
        dataFile = config.getOrDefault("dataFile", "ldap-users.json");
        baseDn = config.getOrDefault("baseDn", "dc=proxy,dc=com");
        loadData();
    }

    private void loadData() throws Exception {
        Path path = Paths.get(dataFile);

        if (!Files.exists(path)) {
            LOG.ldapDataFileNotFound(dataFile);
            throw new Exception("LDAP data file not found: " + dataFile + ". Please create the file with user data before starting the service.");
        }

        String json = Files.readString(path);
        Gson gson = new Gson();
        BackendData data = gson.fromJson(json, BackendData.class);

        if (data != null && data.users != null) {
            for (UserData user : data.users) {
                users.put(user.username, user);
            }
            LOG.ldapUsersLoaded(users.size(), dataFile);
        }
    }

    @Override
    public Entry getUser(String username, SchemaManager schemaManager) throws Exception {
        UserData userData = users.get(username);
        if (userData == null) {
            return null;
        }

        Entry entry = new DefaultEntry(schemaManager);
        entry.setDn("uid=" + userData.username + ",ou=Users," + baseDn);
        entry.add("objectClass", "top");
        entry.add("objectClass", "person");
        entry.add("objectClass", "organizationalPerson");
        entry.add("objectClass", "inetOrgPerson");
        entry.add("uid", userData.username);
        entry.add("cn", userData.cn);
        entry.add("sn", userData.sn);

        // Add groups as description
        if (userData.groups != null && !userData.groups.isEmpty()) {
            entry.add("description", "Groups: " + String.join(", ", userData.groups));
        }

        // Add custom attributes
        if (userData.attributes != null) {
            for (Map.Entry<String, String> attr : userData.attributes.entrySet()) {
                entry.add(attr.getKey(), attr.getValue());
            }
        }

        return entry;
    }

    @Override
    public List<String> getUserGroups(String username) throws Exception {
        UserData userData = users.get(username);
        return userData != null && userData.groups != null ? userData.groups : Collections.emptyList();
    }

    @Override
    public List<Entry> searchUsers(String filter, SchemaManager schemaManager) throws Exception {
        List<Entry> results = new ArrayList<>();

        // Simple filter matching - just check if username matches
        for (String username : users.keySet()) {
            if (filter.contains("uid=" + username) || filter.contains("*")) {
                Entry entry = getUser(username, schemaManager);
                if (entry != null) {
                    results.add(entry);
                }
            }
        }

        return results;
    }
}
