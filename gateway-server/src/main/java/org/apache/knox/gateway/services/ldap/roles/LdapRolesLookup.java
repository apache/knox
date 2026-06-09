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

import java.util.Collection;

/**
 * Interface for looking up roles for a user, potentially replacing their LDAP groups.
 */
public interface LdapRolesLookup {
    /**
     * Look up roles for the given user and their groups.
     *
     * @param userId the user ID
     * @param groups the list of groups for the user
     * @return a list of roles (in the format "scope:name" or just "name" depending on implementation)
     * @throws RoleLookupException if an error occurs during lookup
     */
    Collection<String> lookupRoles(String userId, Collection<String> groups) throws RoleLookupException;
}
