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

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ldap.roles.file.FileBasedLdapRolesReverseLookup;
import org.apache.knox.gateway.services.ldap.roles.rest.RestApiLdapRolesReverseLookup;

import static org.apache.knox.gateway.config.GatewayConfig.LDAP_ROLES_GET_REST_API_ENDPOINT;
import static org.apache.knox.gateway.config.GatewayConfig.LDAP_ROLES_LIST_REST_API_ENDPOINT;
import static org.apache.knox.gateway.config.GatewayConfig.LDAP_ROLES_LOOKUP_FILE_PATH;

public class LdapRolesReverseLookupFactory {

    public static LdapRolesReverseLookup create(GatewayConfig config) throws RoleException {
        // Use same strategy for reverse lookups that we use for lookups
        final String strategy = config.getLdapRolesLookupStrategy();

        if ("file".equalsIgnoreCase(strategy)) {
            String filePath = config.getLdapRolesLookupFilePath();
            if (filePath != null && !filePath.isEmpty()) {
                return new FileBasedLdapRolesReverseLookup(filePath);
            } else {
                throw new RoleException(LDAP_ROLES_LOOKUP_FILE_PATH + " is required for file-based role reverse lookups");
            }
        } else if ("rest".equalsIgnoreCase(strategy)) {
            String listEndpoint = config.getLdapRolesListRestApiEndpoint();
            String getEndpoint = config.getLdapRolesGetRestApiEndpoint();
            if (listEndpoint == null || listEndpoint.isEmpty()) {
                throw new RoleException(LDAP_ROLES_LIST_REST_API_ENDPOINT + " is required for REST API based role reverse lookups");
            }
            if (getEndpoint == null || getEndpoint.isEmpty()) {
                throw new RoleException(LDAP_ROLES_GET_REST_API_ENDPOINT + " is required for REST API based role reverse lookups");
            }
            return new RestApiLdapRolesReverseLookup(listEndpoint, getEndpoint);
        } else if (StringUtils.isNotBlank(strategy)) {
            throw new RoleException("Invalid role lookup strategy: " + strategy);
        }

        //role lookup is not configured
        return null;
    }
}
