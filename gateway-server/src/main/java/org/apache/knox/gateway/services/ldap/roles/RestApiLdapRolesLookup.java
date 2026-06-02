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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.knox.gateway.services.ldap.RoleAssignment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * REST API based implementation of LdapRolesLookup.
 */
public class RestApiLdapRolesLookup implements LdapRolesLookup {
    private final ObjectMapper mapper = new ObjectMapper();
    private final String endpoint;

    public RestApiLdapRolesLookup(String endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public Collection<String> lookupRoles(String userId, Collection<String> groups) throws RoleLookupException {

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            LookupRolesRequest request = new LookupRolesRequest(userId, new ArrayList<>(groups));
            String jsonRequest = mapper.writeValueAsString(request);
            HttpPost httpPost = new HttpPost(endpoint);
            httpPost.setEntity(new StringEntity(jsonRequest, ContentType.APPLICATION_JSON));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    throw new RoleLookupException("Failed to lookup roles: HTTP " + statusCode);
                }

                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    throw new RoleLookupException("Empty response from role lookup API");
                }

                final String jsonResponse = EntityUtils.toString(entity);
                final LookupRolesResponse lookupResponse = mapper.readValue(jsonResponse, LookupRolesResponse.class);
                return parseResponse(lookupResponse);
            }
        } catch (IOException e) {
            throw new RoleLookupException("Error while executing role lookup", e);
        }
    }

    private static List<String> parseResponse(LookupRolesResponse lookupResponse) {
        List<String> roles = new ArrayList<>();
        if (lookupResponse.getRoles() != null) {
            for (RoleAssignment assignment : lookupResponse.getRoles()) {
                String displayValue = assignment.getDisplayValue();
                if (displayValue != null) {
                    roles.add(displayValue);
                }
            }
        }
        return roles;
    }
}
