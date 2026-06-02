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
import org.apache.knox.gateway.services.ldap.RoleAssignment;
import java.util.List;

public class LookupRolesResponse {
    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("roles")
    private List<RoleAssignment> roles;

    public LookupRolesResponse() {}

    public LookupRolesResponse(String userId, List<RoleAssignment> roles) {
        this.userId = userId;
        this.roles = roles;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<RoleAssignment> getRoles() {
        return roles;
    }

    public void setRoles(List<RoleAssignment> roles) {
        this.roles = roles;
    }
}
