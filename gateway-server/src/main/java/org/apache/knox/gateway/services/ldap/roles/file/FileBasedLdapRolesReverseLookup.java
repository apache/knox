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
package org.apache.knox.gateway.services.ldap.roles.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.services.ldap.RoleAssignees;
import org.apache.knox.gateway.services.ldap.RoleAssignment;
import org.apache.knox.gateway.services.ldap.roles.LdapRolesReverseLookup;
import org.apache.knox.gateway.services.ldap.roles.RoleException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * File-based implementation of LdapRolesReverseLookup.
 * Mimics the REST API backend by reading a JSON file representing the role assignments database.
 * Expects a JSON file with a list of mapping entries:
 * [
 *   {
 *     "id": "alice",
 *     "type": "user",
 *     "roles": [ {"scope": "platform", "name": "awc-admin"} ]
 *   },
 *   {
 *     "id": "engineering",
 *     "type": "group",
 *     "roles": [ {"scope": "ml-workspace-abc", "name": "viewer"} ]
 *   }
 * ]
 */
public class FileBasedLdapRolesReverseLookup implements LdapRolesReverseLookup {
    private final String filePath;
    private final ObjectMapper mapper = new ObjectMapper();

    public FileBasedLdapRolesReverseLookup(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public Collection<String> listRoles() throws RoleException {
        final List<RoleMappingEntry> entries = readRoleMappingFile();

        final Set<String> roles = new HashSet<>();

        for (RoleMappingEntry entry : entries) {
            for (RoleAssignment roleAssignment : entry.getRoles()) {
                roles.add(roleAssignment.getDisplayValue());
            }
        }

        return roles;
    }

    @Override
    public RoleAssignees lookupRoleAssignees(String role) throws RoleException {
        final List<RoleMappingEntry> entries = readRoleMappingFile();

        final Set<String> users = new HashSet<>();
        final Set<String> groups = new HashSet<>();

        for (RoleMappingEntry entry : entries) {
            if (entry.getRoles().stream()
                    .anyMatch(roleAssignment ->
                            role.equalsIgnoreCase(roleAssignment.getDisplayValue()))) {
                if ("user".equalsIgnoreCase(entry.getType())) {
                    users.add(entry.getId());
                }  else if ("group".equalsIgnoreCase(entry.getType())) {
                    groups.add(entry.getId());
                }
            }
        }

        return new RoleAssignees(List.copyOf(users), List.copyOf(groups));
    }

    private List<RoleMappingEntry> readRoleMappingFile() throws RoleException {
        File roleMappingFile = new File(filePath);
        if (!roleMappingFile.exists()) {
            throw new RoleException("Role mapping file " + roleMappingFile + " does not exist");
        }
        try {
            final String jsonInput = FileUtils.readFileToString(roleMappingFile, StandardCharsets.UTF_8);
            final List<RoleMappingEntry> entries = mapper.readValue(jsonInput, new TypeReference<>() {});
            return entries;
        } catch (IOException e) {
            throw new RoleException("Error reading roles mapping file: " + filePath, e);
        }
    }
}
