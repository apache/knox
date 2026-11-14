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

import static java.util.Locale.ROOT;

import com.google.gson.Gson;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapNoSuchObjectException;
import org.apache.directory.api.ldap.model.filter.ExprNode;
import org.apache.directory.api.ldap.model.filter.FilterParser;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ldap.LdapMessages;
import org.apache.knox.gateway.services.ldap.LdapUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
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

    static final String TYPE = "file";

    private Map<String, UserData> users = new HashMap<>();
    private final String dataFile;
    private final String baseDn;
    private final String userSearchBase;
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
        userSearchBase = "ou=people," + baseDn;
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

    @Override
    public boolean isSupportedSearchBase(String searchBase) {
        return searchBase != null && searchBase.toLowerCase(ROOT).endsWith(userSearchBase.toLowerCase(ROOT));
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
        entry.setDn("uid=" + userData.username + "," + userSearchBase);
        entry.add("objectClass", "top");
        entry.add("objectClass", "person");
        entry.add("objectClass", "organizationalPerson");
        entry.add("objectClass", "inetOrgPerson");
        entry.add("uid", userData.username);
        // Expose the login name under sAMAccountName as well so AD-style user searches
        // (userSearchAttributeName=sAMAccountName) resolve the same user as uid searches.
        entry.add("sAMAccountName", userData.username);
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
        final ExprNode parsedFilter = parseFilter(filter, schemaManager);
        List<Entry> results = new ArrayList<>();
        // Evaluate the real LDAP filter against each user entry so that wildcards, presence,
        // substring and boolean (AND/OR/NOT) assertions all follow standard LDAP semantics.
        for (String username : users.keySet()) {
            Entry entry = getUser(username, schemaManager);
            if (entry != null && LdapUtils.matchesFilter(entry, parsedFilter)) {
                results.add(entry);
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
    public List<Entry> search(String searchBase, SearchScope searchScope, String filter, SchemaManager schemaManager, String[] attributes, long sizeLimit, int timeLimit) throws Exception {
        // timeLimit is not enforced: this backend serves from an in-memory map, so a search always
        // completes effectively instantaneously and cannot exceed a time limit.
        final Dn base = new Dn(schemaManager, searchBase);
        final Dn people = new Dn(schemaManager, userSearchBase);
        if (!base.equals(people) && base.isDescendantOf(people)) {
            return applySizeLimit(searchUserEntry(base, people, searchScope, filter, schemaManager), sizeLimit);
        }
        // The users sit below the search base, so a base-object search finds none of them
        return searchScope == SearchScope.OBJECT ? Collections.emptyList() : applySizeLimit(searchUsers(filter, schemaManager), sizeLimit);
    }

    /**
     * Truncates the result list to at most {@code sizeLimit} entries. A {@code sizeLimit} of
     * {@code 0} (or negative) means no limit, so the list is returned unchanged.
     */
    private static List<Entry> applySizeLimit(List<Entry> entries, long sizeLimit) {
        if (sizeLimit <= 0 || entries.size() <= sizeLimit) {
            return entries;
        }
        return new ArrayList<>(entries.subList(0, (int) sizeLimit));
    }

    /*
     * A search rooted at an entry below ou=people: the only entries this backend has there are the
     * users (uid=<username>,ou=people,<baseDn>), which have no children.
     */
    private List<Entry> searchUserEntry(Dn base, Dn people, SearchScope searchScope, String filter, SchemaManager schemaManager) throws Exception {
        final UserData userData = base.size() == people.size() + 1 ? findUser(LdapUtils.extractUsernameFromDn(base)) : null;
        if (userData == null) {
            throw new LdapNoSuchObjectException("No such entry in backend " + name + ": " + base);
        }
        if (searchScope == SearchScope.ONELEVEL) {
            return Collections.emptyList();
        }
        final Entry entry = getUser(userData.username, schemaManager);
        return LdapUtils.matchesFilter(entry, parseFilter(filter, schemaManager))
                ? Collections.singletonList(entry)
                : Collections.emptyList();
    }

    private UserData findUser(String username) {
        if (username != null) {
            for (UserData userData : users.values()) {
                if (username.equalsIgnoreCase(userData.username)) {
                    return userData;
                }
            }
        }
        return null;
    }

    /**
     * Parses a search filter into an expression tree. The filter has likely already been annotated by
     * other interceptors, so the annotations are stripped before parsing. A blank filter is treated as
     * the LDAP default presence filter {@code (objectClass=*)}, which matches every entry.
     */
    private static ExprNode parseFilter(String filter, SchemaManager schemaManager) throws ParseException {
        if (filter == null || filter.trim().isEmpty()) {
            return FilterParser.parse(schemaManager, "(objectClass=*)");
        }
        final String rawFilter = filter.replaceAll(":\\[.*?\\]", "").replaceAll("=\\s+", "=");
        return FilterParser.parse(schemaManager, rawFilter);
    }
}
