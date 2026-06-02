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

import static org.apache.knox.gateway.config.GatewayConfig.LDAP_ROLES_LOOKUP_FILE_PATH;
import static org.apache.knox.gateway.config.GatewayConfig.LDAP_ROLES_LOOKUP_REST_API_ENDPOINT;

public class LdapRolesLookupFactory {

    public static LdapRolesLookup create(GatewayConfig config) throws RoleLookupException {
        final String strategy = config.getLdapRolesLookupStrategy();

        if ("file".equalsIgnoreCase(strategy)) {
            String filePath = config.getLdapRolesLookupFilePath();
            if (filePath != null && !filePath.isEmpty()) {
                return new FileBasedLdapRolesLookup(filePath);
            } else {
                throw new RoleLookupException(LDAP_ROLES_LOOKUP_FILE_PATH + "is required for file-based role lookups");
            }
        } else if ("rest".equalsIgnoreCase(strategy)) {
            String endpoint = config.getLdapRolesLookupRestApiEndpoint();
            if (endpoint != null && !endpoint.isEmpty()) {
                return new RestApiLdapRolesLookup(endpoint);
            } else {
                throw new RoleLookupException(LDAP_ROLES_LOOKUP_REST_API_ENDPOINT + "is required for REST API based role lookups");
            }
        } else if (StringUtils.isNotBlank(strategy)) {
            throw new RoleLookupException("Invalid role lookup strategy: " + strategy);
        }

        //role lookup is not configured
        return null;
    }
}
