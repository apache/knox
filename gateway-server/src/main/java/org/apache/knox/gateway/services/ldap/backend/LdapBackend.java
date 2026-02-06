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
import org.apache.directory.api.ldap.model.schema.SchemaManager;

import java.util.List;
import java.util.Map;

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
     * Get the name of this backend implementation
     */
    String getName();

    /**
     * Initialize the backend with configuration
     * @param config Configuration properties
     */
    void initialize(Map<String, String> config) throws Exception;

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
     * @return List of group names
     */
    List<String> getUserGroups(String username) throws Exception;

    /**
     * Search for users matching a filter
     * @param filter LDAP filter string (simplified)
     * @param schemaManager Schema manager for creating entries
     * @return List of matching entries
     */
    List<Entry> searchUsers(String filter, SchemaManager schemaManager) throws Exception;
}
