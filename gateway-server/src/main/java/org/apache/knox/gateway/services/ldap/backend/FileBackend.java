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
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ldap.LdapMessages;
import org.apache.knox.gateway.services.ldap.LdapUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * File-based backend that reads user/group data from JSON
 */
public class FileBackend implements LdapBackend {
    private static final LdapMessages LOG = MessagesFactory.get(LdapMessages.class);

    private static final Pattern UID_PATTERN = Pattern.compile(".*\\(uid=([^)]+)\\).*");
    private static final Pattern CN_PATTERN = Pattern.compile(".*\\(cn=([^)]+)\\).*");
    private static final Pattern SAMAACCOUNTNAME_PATTERN = Pattern.compile(".*\\(sAMAccountName=([^)]+)\\).*");

    static final String TYPE = "file";

    private Map<String, UserData> users = new HashMap<>();
    private final String dataFile;
    private final String baseDn;
    private final String name;

    static class UserData {
        String username;
        String password;
        String cn;
        String sn;
        List<String> groups;
        Map<String, String> attributes;
    }

    static class BackendData {
        List<UserData> users;
    }

    public FileBackend(String name, Map<String, String> config) throws Exception {
        this.name = name;
        dataFile = config.getOrDefault("dataFile", "ldap-users.json");
        baseDn = config.getOrDefault("baseDn", "dc=proxy,dc=com");
        loadData();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getBaseDn() {
        return baseDn;
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
    public List<String> getUserGroups(String username, SchemaManager schemaManager) throws Exception {
        UserData userData = users.get(username);
        return userData != null && userData.groups != null ? userData.groups : Collections.emptyList();
    }

    @Override
    public List<Entry> searchUsers(String filter, SchemaManager schemaManager) throws Exception {
        List<Entry> results = new ArrayList<>();

        String userFilter = extractUser(filter).toLowerCase(Locale.ROOT);

        // Simple filter matching - just check if username matches
        for (String username : users.keySet()) {
            String usernameLowerCase = username.toLowerCase(Locale.ROOT);
            if (userFilter.equalsIgnoreCase(usernameLowerCase) ||
                    (userFilter.contains("*") && userFilter.contains(usernameLowerCase))) {
                Entry entry = getUser(username, schemaManager);
                if (entry != null) {
                    results.add(entry);
                }
            }
        }

        return results;
    }

    @Override
    public boolean authenticate(Dn userDn, String password) {
        // Extract username from DN (e.g., uid=admin,  ou=people,dc=hadoop,dc=apache,dc=org)
        final String username = LdapUtils.extractUsernameFromDn(userDn);

        if (username != null) {
            UserData userData = users.get(username);
            return userData != null && password != null && password.equals(userData.password);
        }

        return false;
    }

    @Override
    public List<Entry> search(String searchBase, SearchScope searchScope, String filter, SchemaManager schemaManager) throws Exception {
        return searchUsers(filter, schemaManager);
    }

    private String extractUser(String filter) {
        Matcher uidMatcher = UID_PATTERN.matcher(filter);
        if (uidMatcher.matches()) {
            return uidMatcher.group(1);
        }

        Matcher cnMatcher = CN_PATTERN.matcher(filter);
        if (cnMatcher.matches()) {
            return cnMatcher.group(1);
        }

        Matcher samaaccountnameMatcher = SAMAACCOUNTNAME_PATTERN.matcher(filter);
        if (samaaccountnameMatcher.matches()) {
            return samaaccountnameMatcher.group(1);
        }

        return null;
    }
}
