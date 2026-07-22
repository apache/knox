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

import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;

import java.util.List;

/**
 * Interface for pluggable LDAP backends.
 * Implementations can provide user/group data from various sources:
 * - File-based (JSON, LDIF, properties)
 * - JDBC databases
 * - Remote LDAP servers (proxy/federation)
 * - REST APIs (Knox, Ranger, etc.)
 */
public interface LdapBackend {

    /**
     * Get the name of this backend
     */
    String getName();

    /**
     * Get the type of this backend implementation
     */
    String getType();

    /**
     * Get the base dn of this backend
     */
    String getBaseDn();

    /**
     * Returns whether a search base is supported by this backend
     * @param searchBase the base dn for a search
     * @return True if the base dn is supported by this backend
     */
    boolean isSupportedSearchBase(String searchBase);

    /**
     * Get a user entry by username
     * @param username The username to look up
     * @param schemaManager Schema manager for creating entries
     * @return Entry or null if not found
     */
    Entry getUser(String username, SchemaManager schemaManager) throws Exception;

    /**
     * Get groups for a user
     * @param username The username
     * @param schemaManager Schema manager for creating entries
     * @return List of group names or null if not found
     */
    List<String> getUserGroups(String username, SchemaManager schemaManager) throws Exception;

    /**
     * Search for users matching a filter
     * @param filter LDAP filter string (simplified)
     * @param schemaManager Schema manager for creating entries
     * @return List of matching entries
     */
    List<Entry> searchUsers(String filter, SchemaManager schemaManager) throws Exception;

    /**
     * Search for entries matching a filter
     * @param searchBase The base DN for the search
     * @param searchScope The scope of the search
     * @param filter LDAP filter string (simplified)
     * @param schemaManager Schema manager for creating entries
     * @return List of matching entries
     */
    List<Entry> search(String searchBase, SearchScope searchScope, String filter, SchemaManager schemaManager) throws Exception;

    /**
     * Authenticate a user with password
     *
     * @param userDn   The user's Distinguished Name
     * @param password The user's password
     * @return true if authentication is successful, false otherwise
     */
    boolean authenticate(Dn userDn, String password);
}
