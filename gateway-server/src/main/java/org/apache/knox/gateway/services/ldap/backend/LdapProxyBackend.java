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

import org.apache.directory.api.ldap.model.cursor.CursorException;
import org.apache.directory.api.ldap.model.cursor.EntryCursor;
import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.LdapNetworkConnection;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ldap.LdapMessages;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * LDAP backend that proxies to an external LDAP server.
 * Can use central LDAP configuration or backend-specific configuration.
 */
public class LdapProxyBackend implements LdapBackend {
    private static final LdapMessages LOG = MessagesFactory.get(LdapMessages.class);

    private String ldapUrl;
    private String bindDn;
    private String bindPassword;
    private String userSearchBase;
    private String groupSearchBase;
    private String proxyBaseDn;  // Base DN for proxy entries (e.g., dc=proxy,dc=com)
    private String remoteBaseDn;  // Base DN for remote server searches (e.g., dc=hadoop,dc=apache,dc=org)
    private int port;
    private String host;

    // Configurable attributes for AD/LDAP compatibility
    private String userIdentifierAttribute = "uid"; // uid for LDAP, sAMAccountName for AD
    private String userSearchFilter = "({userIdAttr}={username})"; // Will be populated with userIdentifierAttribute
    private String groupMemberAttribute = "memberUid"; // member for AD, memberUid for POSIX
    private boolean useMemberOf = false; // Use memberOf attribute for group lookup (efficient for AD)

    @Override
    public String getName() {
        return "ldap";
    }

    @Override
    public void initialize(Map<String, String> config) throws Exception {
        // Proxy base DN is for entries created in the proxy LDAP server
        proxyBaseDn = config.get("baseDn");
        if (proxyBaseDn == null || proxyBaseDn.isEmpty()) {
            throw new IllegalArgumentException("baseDn is required for LDAP proxy backend");
        }

        // Remote base DN is for searching the remote LDAP server
        remoteBaseDn = config.get("remoteBaseDn");
        if (remoteBaseDn == null || remoteBaseDn.isEmpty()) {
            throw new IllegalArgumentException("remoteBaseDn is required for LDAP proxy backend - this is the base DN of the remote LDAP server");
        }

        // Support both url and host/port configuration
        ldapUrl = config.get("url");
        if (ldapUrl != null && !ldapUrl.isEmpty()) {
            // Parse URL to extract host and port
            parseLdapUrl(ldapUrl);
        } else {
            host = config.get("host");
            if (host == null || host.isEmpty()) {
                throw new IllegalArgumentException("Either 'url' or 'host' is required for LDAP proxy backend");
            }
            String portStr = config.get("port");
            if (portStr == null || portStr.isEmpty()) {
                throw new IllegalArgumentException("'port' is required when using 'host' configuration");
            }
            port = Integer.parseInt(portStr);
            ldapUrl = "ldap://" + host + ":" + port;
        }

        // Support both naming conventions: bindDn/bindPassword and systemUsername/systemPassword
        bindDn = config.get("bindDn");
        if (bindDn == null || bindDn.isEmpty()) {
            bindDn = config.get("systemUsername");
        }

        bindPassword = config.get("bindPassword");
        if (bindPassword == null || bindPassword.isEmpty()) {
            bindPassword = config.get("systemPassword");
        }

        // Search bases use the remote server's base DN
        userSearchBase = config.getOrDefault("userSearchBase", "ou=people," + remoteBaseDn);
        groupSearchBase = config.getOrDefault("groupSearchBase", "ou=groups," + remoteBaseDn);

        // Configure attribute mappings for AD/LDAP compatibility
        userIdentifierAttribute = config.getOrDefault("userIdentifierAttribute", "uid");
        config.getOrDefault("userDnTemplate", "uid={username},ou=Users,{baseDn}");
        groupMemberAttribute = config.getOrDefault("groupMemberAttribute", "memberUid");
        useMemberOf = Boolean.parseBoolean(config.getOrDefault("useMemberOf", "false"));

        // Build search filter template
        userSearchFilter = "(" + userIdentifierAttribute + "={username})";

        LOG.ldapBackendLoading(getName(), "Proxying " + proxyBaseDn + " to " + ldapUrl + " (" + remoteBaseDn + ") with " +
                              userIdentifierAttribute + " attribute" +
                              (useMemberOf ? " using memberOf lookups" : " using group searches"));
    }

    private void parseLdapUrl(String url) {
        // Simple URL parsing for ldap://host:port
        if (url.startsWith("ldap://")) {
            String hostPort = url.substring(7);
            int colonIdx = hostPort.indexOf(':');
            if (colonIdx > 0) {
                host = hostPort.substring(0, colonIdx);
                try {
                    port = Integer.parseInt(hostPort.substring(colonIdx + 1));
                } catch (NumberFormatException e) {
                    port = 389;
                }
            } else {
                host = hostPort;
                port = 389;
            }
        } else if (url.startsWith("ldaps://")) {
            String hostPort = url.substring(8);
            int colonIdx = hostPort.indexOf(':');
            if (colonIdx > 0) {
                host = hostPort.substring(0, colonIdx);
                try {
                    port = Integer.parseInt(hostPort.substring(colonIdx + 1));
                } catch (NumberFormatException e) {
                    port = 636;
                }
            } else {
                host = hostPort;
                port = 636;
            }
        }
    }

    private LdapConnection connect() throws LdapException, IOException {
        LdapConnection connection = new LdapNetworkConnection(host, port);
        connection.connect();

        // Bind if credentials provided, otherwise anonymous
        if (bindDn != null && !bindDn.isEmpty()) {
            connection.bind(bindDn, bindPassword);
        } else {
            connection.anonymousBind();
        }

        return connection;
    }

    @Override
    public Entry getUser(String username, SchemaManager schemaManager) throws Exception {
        try (LdapConnection connection = connect()) {
            // Search for user using configurable attribute
            String filter = userSearchFilter.replace("{username}", username);
            EntryCursor cursor = connection.search(userSearchBase, filter, SearchScope.SUBTREE, "*");

            if (cursor.next()) {
                Entry sourceEntry = cursor.get();

                // Standard proxy approach: return entry with backend DN unchanged
                // This preserves DN integrity for bind operations and DN references
                Entry entry = new DefaultEntry(schemaManager);
                entry.setDn(sourceEntry.getDn());

                // Copy all attributes as-is from backend
                copyAttribute(sourceEntry, entry, "objectClass");
                copyAttribute(sourceEntry, entry, userIdentifierAttribute);
                // Map identifier attribute to uid for consistency if needed
                if (!"uid".equals(userIdentifierAttribute)) {
                    Attribute idAttr = sourceEntry.get(userIdentifierAttribute);
                    if (idAttr != null) {
                        entry.add("uid", idAttr.getString());
                    }
                }
                copyAttribute(sourceEntry, entry, "cn");
                copyAttribute(sourceEntry, entry, "sn");
                copyAttribute(sourceEntry, entry, "mail");
                copyAttribute(sourceEntry, entry, "description");
                copyAttribute(sourceEntry, entry, "memberOf");  // Preserve group memberships

                // Get user's groups
                List<String> groups = getUserGroupsInternal(connection, sourceEntry.getDn().toString(), username);
                if (!groups.isEmpty()) {
                    entry.add("description", "Groups: " + String.join(", ", groups));
                }

                cursor.close();
                return entry;
            }

            cursor.close();
        }
        return null;
    }

    @Override
    public List<String> getUserGroups(String username) throws Exception {
        try (LdapConnection connection = connect()) {
            if (useMemberOf) {
                // Use memberOf attribute for efficient AD lookups
                return getUserGroupsViaMemberOf(connection, username);
            } else {
                // Use traditional group search approach
                String filter = userSearchFilter.replace("{username}", username);
                EntryCursor cursor = connection.search(userSearchBase, filter, SearchScope.SUBTREE, "dn");

                if (cursor.next()) {
                    String userDn = cursor.get().getDn().toString();
                    cursor.close();
                    return getUserGroupsInternal(connection, userDn, username);
                }

                cursor.close();
            }
        }
        return Collections.emptyList();
    }

    private List<String> getUserGroupsViaMemberOf(LdapConnection connection, String username) throws LdapException, CursorException, IOException {
        List<String> groups = new ArrayList<>();

        // Search for user and retrieve memberOf attribute
        String filter = userSearchFilter.replace("{username}", username);
        EntryCursor cursor = connection.search(userSearchBase, filter, SearchScope.SUBTREE, "memberOf");

        if (cursor.next()) {
            Entry userEntry = cursor.get();
            Attribute memberOfAttr = userEntry.get("memberOf");

            if (memberOfAttr != null) {
                // Extract group names from DNs
                for (org.apache.directory.api.ldap.model.entry.Value value : memberOfAttr) {
                    String groupDn = value.getString();
                    String groupName = extractGroupNameFromDn(groupDn);
                    if (groupName != null) {
                        groups.add(groupName);
                    }
                }
            }
        }

        cursor.close();
        return groups;
    }

    private String extractGroupNameFromDn(String groupDn) {
        // Extract CN from DN like "CN=Domain Admins,CN=Users,DC=company,DC=com"
        if (groupDn.toLowerCase(Locale.ROOT).startsWith("cn=")) {
            int commaIdx = groupDn.indexOf(',');
            if (commaIdx > 0) {
                return groupDn.substring(3, commaIdx);
            }
        }
        return null;
    }

    private List<String> getUserGroupsInternal(LdapConnection connection, String userDn, String username) throws LdapException, CursorException, IOException {
        List<String> groups = new ArrayList<>();

        // Search for groups where user is a member - build filter based on configuration
        String filter;
        if ("member".equals(groupMemberAttribute)) {
            // AD style - uses full DN
            filter = "(|" +
                    "(member=" + userDn + ")" +
                    "(uniqueMember=" + userDn + ")" +
                    ")";
        } else {
            // POSIX style - uses username
            filter = "(|" +
                    "(memberUid=" + username + ")" +
                    "(member=" + userDn + ")" +
                    "(uniqueMember=" + userDn + ")" +
                    ")";
        }

        EntryCursor cursor = connection.search(groupSearchBase, filter, SearchScope.SUBTREE, "cn");

        while (cursor.next()) {
            Entry groupEntry = cursor.get();
            Attribute cnAttr = groupEntry.get("cn");
            if (cnAttr != null) {
                groups.add(cnAttr.getString());
            }
        }

        cursor.close();
        return groups;
    }

    @Override
    public List<Entry> searchUsers(String filter, SchemaManager schemaManager) throws Exception {
        List<Entry> results = new ArrayList<>();

        try (LdapConnection connection = connect()) {
            String searchValue = filter.contains("*") ? "*" : filter;
            String ldapFilter = "(" + userIdentifierAttribute + "=" + searchValue + ")";
            EntryCursor cursor = connection.search(userSearchBase, ldapFilter, SearchScope.SUBTREE, "*");

            while (cursor.next()) {
                Entry sourceEntry = cursor.get();
                Attribute idAttr = sourceEntry.get(userIdentifierAttribute);
                if (idAttr != null) {
                    String username = idAttr.getString();

                    // Standard proxy approach: return entry with backend DN unchanged
                    Entry entry = new DefaultEntry(schemaManager);
                    entry.setDn(sourceEntry.getDn());

                    // Copy all attributes as-is from backend
                    copyAttribute(sourceEntry, entry, "objectClass");
                    copyAttribute(sourceEntry, entry, userIdentifierAttribute);
                    // Map identifier attribute to uid for consistency if needed
                    if (!"uid".equals(userIdentifierAttribute)) {
                        Attribute userIdAttr = sourceEntry.get(userIdentifierAttribute);
                        if (userIdAttr != null) {
                            entry.add("uid", userIdAttr.getString());
                        }
                    }
                    copyAttribute(sourceEntry, entry, "cn");
                    copyAttribute(sourceEntry, entry, "sn");
                    copyAttribute(sourceEntry, entry, "mail");
                    copyAttribute(sourceEntry, entry, "description");
                    copyAttribute(sourceEntry, entry, "memberOf");  // Preserve group memberships

                    // Get user's groups using the same connection
                    List<String> groups = getUserGroupsInternal(connection, sourceEntry.getDn().toString(), username);
                    if (!groups.isEmpty()) {
                        entry.add("description", "Groups: " + String.join(", ", groups));
                    }

                    results.add(entry);
                }
            }

            cursor.close();
        }

        return results;
    }

    private void copyAttribute(Entry source, Entry target, String attributeName) throws LdapException {
        Attribute attr = source.get(attributeName);
        if (attr != null) {
            // Copy all values of the attribute (important for multi-valued attributes like objectClass)
            for (org.apache.directory.api.ldap.model.entry.Value value : attr) {
                target.add(attributeName, value.getString());
            }
        }
    }
}
