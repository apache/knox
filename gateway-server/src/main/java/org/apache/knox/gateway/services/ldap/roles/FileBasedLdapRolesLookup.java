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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.services.ldap.RoleAssignment;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * File-based implementation of LdapRolesLookup.
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
public class FileBasedLdapRolesLookup implements LdapRolesLookup {
    private final String filePath;
    private final ObjectMapper mapper = new ObjectMapper();

    public FileBasedLdapRolesLookup(String filePath) {
        this.filePath = filePath;
    }

    @Override
    public Collection<String> lookupRoles(String userId, Collection<String> groups) throws RoleLookupException {
        File roleMappingFile = new File(filePath);
        if (!roleMappingFile.exists()) {
            throw new RoleLookupException("Role mapping file " + roleMappingFile + " does not exist");
        }

        try {
            final String jsonInput = FileUtils.readFileToString(roleMappingFile, StandardCharsets.UTF_8);
            final List<RoleMappingEntry> entries = mapper.readValue(jsonInput, new TypeReference<>() {});
            final Set<String> roles = new HashSet<>();

            for (RoleMappingEntry entry : entries) {
                if ("user".equalsIgnoreCase(entry.getType())) {
                    if (entry.getId() != null && entry.getId().equals(userId)) {
                        addRoles(roles, entry.getRoles());
                    }
                } else if ("group".equalsIgnoreCase(entry.getType())) {
                    if (groups != null && groups.contains(entry.getId())) {
                        addRoles(roles, entry.getRoles());
                    }
                }
            }

            return new ArrayList<>(roles);
        } catch (IOException e) {
            throw new RoleLookupException("Error reading roles mapping file: " + filePath, e);
        }
    }

    private void addRoles(Set<String> roles, List<RoleAssignment> roleAssignments) {
        if (roleAssignments != null) {
            for (RoleAssignment assignment : roleAssignments) {
                String displayValue = assignment.getDisplayValue();
                if (displayValue != null) {
                    roles.add(displayValue);
                }
            }
        }
    }

    private static class RoleMappingEntry {
        @JsonProperty("id")
        private String id;

        @JsonProperty("type")
        private String type;

        @JsonProperty("roles")
        private List<RoleAssignment> roles;

        public String getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public List<RoleAssignment> getRoles() {
            return roles;
        }
    }
}
