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
package org.apache.knox.gateway.services.ldap.roles;

import org.apache.knox.gateway.services.ldap.RoleAssignees;

import java.util.Collection;

/**
 * Interface for listing roles and looking up roles for a user.
 */
public interface LdapRolesReverseLookup {
    /**
     * List all roles
     *
     * @return a collection of roles
     * @throws RoleException if an error occurs during listing roles
     */
    Collection<String> listRoles() throws RoleException;

    /**
     * Look up role assignees (users and groups) for a given role
     *
     * @param role the role (in the format "scope:name" or just "name" depending on implementation)
     * @return the role assignees
     * @throws RoleException if an error occurs during reverse lookup
     */
    RoleAssignees lookupRoleAssignees(String role) throws RoleException;
}
