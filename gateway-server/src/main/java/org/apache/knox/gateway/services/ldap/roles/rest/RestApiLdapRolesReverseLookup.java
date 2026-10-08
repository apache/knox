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
package org.apache.knox.gateway.services.ldap.roles.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ldap.LdapMessages;
import org.apache.knox.gateway.services.ldap.RoleAssignees;
import org.apache.knox.gateway.services.ldap.RoleAssignment;
import org.apache.knox.gateway.services.ldap.roles.LdapRolesReverseLookup;
import org.apache.knox.gateway.services.ldap.roles.RoleException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * REST API based implementation of LdapRolesReverseLookup.
 */
public class RestApiLdapRolesReverseLookup implements LdapRolesReverseLookup {
    private static final LdapMessages LOG = MessagesFactory.get(LdapMessages.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final String listEndpoint;
    private final String getEndpoint;

    public RestApiLdapRolesReverseLookup(String listEndpoint, String getEndpoint) {
        this.listEndpoint = listEndpoint;
        this.getEndpoint = getEndpoint;
    }

    @Override
    public Collection<String> listRoles() throws RoleException {
        final List<Role> roles = listRoles(null);
        Set<String> result = new HashSet<>();
        for (Role role : roles) {
            result.add(new RoleAssignment(role.getScope(), role.getName()).getDisplayValue());
        }
        return result;
    }

    @Override
    public RoleAssignees lookupRoleAssignees(String role) throws RoleException {
        if (role != null) {
            RoleAssignment roleAssignment = RoleAssignment.createFromDisplayValue(role);
            List<Role> rolesList = listRoles(roleAssignment);
            String roleId = findRoleId(roleAssignment, rolesList);
            if (roleId != null) {
                List<String> users = new ArrayList<>();
                List<String> groups = new ArrayList<>();
                List<RoleMember> members = getMembers(roleId);
                if (members != null) {
                    for (RoleMember member : members) {
                        if (member.getMemberType().equalsIgnoreCase("user")) {
                            users.add(member.getMemberId());
                        } else if (member.getMemberType().equalsIgnoreCase("group")) {
                            groups.add(member.getMemberId());
                        } else {
                            // ignore unknown member types
                            LOG.restRolesAssigneeTypeUnknown(role, member.getMemberType());
                        }
                    }
                }
                return new RoleAssignees(users, groups);
            }
        }
        return new RoleAssignees(List.of(), List.of());
    }

    private List<RoleMember> getMembers(String roleId) throws RoleException {
        String encodedRoleId = URLEncoder.encode(roleId, StandardCharsets.UTF_8)
                .replace("+", "%20"); // URLEncoder converts spaces to '+', path params need '%20'
        String getUrl = getEndpoint + "/" + encodedRoleId;

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            LOG.restRolesGetRequest(getUrl, roleId);
            HttpGet getRequest = new HttpGet(getUrl);
            getRequest.setHeader("Accept", "application/json");
            try (CloseableHttpResponse response = httpClient.execute(getRequest)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity entity = response.getEntity();
                final String jsonResponse = entity == null ? null : EntityUtils.toString(entity);
                LOG.restRolesGetResponse(getUrl, statusCode, jsonResponse);

                if (statusCode != 200) {
                    LOG.restRolesLookupHttpError(getUrl, statusCode, jsonResponse);
                    throw new RoleException("Failed to get roles: HTTP " + statusCode);
                }

                if (jsonResponse == null) {
                    throw new RoleException("Empty response from role get API");
                }

                return mapper.readValue(jsonResponse, Role.class).getMembers();
            }
        } catch (IOException e) {
            throw new RoleException("Error while executing role get", e);
        }
    }

    private List<Role> listRoles(RoleAssignment roleAssignment) throws RoleException {
        String listUrl = null;
        int numRolesRetrieved = 0;
        int totalExpectedRoles = 0;
        List<Role> roles = new ArrayList<>();

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            do {
                URIBuilder uriBuilder = new URIBuilder(listEndpoint);
                if (roleAssignment != null) {
                    if (roleAssignment.getName() != null) {
                        uriBuilder.addParameter("name", roleAssignment.getName());
                    }
                    if (roleAssignment.getScope() != null) {
                        uriBuilder.addParameter("scope", roleAssignment.getScope());
                    }
                }
                uriBuilder.addParameter("offset", Integer.toString(numRolesRetrieved));
                URI listURI = uriBuilder.build();
                listUrl = listURI.toString();
                LOG.restRolesListRequest(listUrl);

                HttpGet listRequest = new HttpGet(listUrl);
                listRequest.setHeader("Accept", "application/json");
                try (CloseableHttpResponse response = httpClient.execute(listRequest)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    HttpEntity entity = response.getEntity();
                    final String jsonResponse = entity == null ? null : EntityUtils.toString(entity);
                    LOG.restRolesListResponse(listUrl, statusCode, jsonResponse);

                    if (statusCode != 200) {
                        LOG.restRolesLookupHttpError(listUrl, statusCode, jsonResponse);
                        throw new RoleException("Failed to list roles: HTTP " + statusCode);
                    }

                    if (jsonResponse == null) {
                        throw new RoleException("Empty response from role list API");
                    }

                    ListRolesResponse listRolesResponse = mapper.readValue(jsonResponse, ListRolesResponse.class);
                    numRolesRetrieved += listRolesResponse.getData().size();
                    totalExpectedRoles = listRolesResponse.getPagination().getTotal();
                    roles.addAll(listRolesResponse.getData());
                    if (listRolesResponse.getData().isEmpty() && numRolesRetrieved < totalExpectedRoles) {
                        throw new RoleException("No more roles to list");
                    }
                }
            } while (numRolesRetrieved < totalExpectedRoles);
            return roles;
        } catch (URISyntaxException e) {
            throw new RoleException("Invalid URI for role lookup: " + listUrl, e);
        } catch (IOException e) {
            throw new RoleException("Error while executing role lookup", e);
        }
    }

    private String findRoleId(RoleAssignment roleAssignment, List<Role> roleList) {
        if (roleList != null) {
            for (Role r : roleList) {
                if (StringUtils.equalsIgnoreCase(roleAssignment.getName(), r.getName()) &&
                    StringUtils.equalsIgnoreCase(roleAssignment.getScope(), r.getScope())) {
                    return r.getId();
                }
            }
        }
        return null;
    }
}
