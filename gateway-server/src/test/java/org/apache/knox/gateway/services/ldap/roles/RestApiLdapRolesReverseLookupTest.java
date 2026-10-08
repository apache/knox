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
import org.apache.knox.gateway.services.ldap.roles.rest.RestApiLdapRolesReverseLookup;
import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RestApiLdapRolesReverseLookupTest {
    private Server server;
    private String listEndpoint;
    private String getEndpoint;

    // raw "path?query" of every request, in arrival order
    private final List<String> requests = new CopyOnWriteArrayList<>();
    // list responses keyed by the offset query parameter
    private final Map<String, String> listResponsesByOffset = new ConcurrentHashMap<>();
    // get responses keyed by raw path
    private final Map<String, String> getResponsesByPath = new ConcurrentHashMap<>();
    private final AtomicInteger listStatus = new AtomicInteger(200);
    private final AtomicInteger getStatus = new AtomicInteger(200);

    @Before
    public void setUp() throws Exception {
        server = new Server();
        // allow an encoded slash (%2F) in the path, which Jetty rejects by default
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setUriCompliance(UriCompliance.UNSAFE);
        ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
        connector.setHost("127.0.0.1");
        connector.setPort(0);
        server.addConnector(connector);
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) {
                String rawPath = request.getHttpURI().getPath();
                String query = request.getHttpURI().getQuery();
                if (rawPath.startsWith("/list")) {
                    requests.add(rawPath + "?" + query);
                    String offset = "0";
                    for (String param : query.split("&")) {
                        if (param.startsWith("offset=")) {
                            offset = param.substring("offset=".length());
                        }
                    }
                    respond(response, callback, listStatus.get(), listResponsesByOffset.getOrDefault(offset, "{\"data\":[],\"pagination\":{\"total\":0}}"));
                } else {
                    requests.add(rawPath);
                    respond(response, callback, getStatus.get(), getResponsesByPath.getOrDefault(rawPath, "{}"));
                }
                return true;
            }
        });
        server.start();
        String base = "http://127.0.0.1:" + connector.getLocalPort();
        listEndpoint = base + "/list";
        getEndpoint = base + "/get";
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
    }

    private static void respond(Response response, Callback callback, int status, String body) {
        response.setStatus(status);
        response.getHeaders().put("Content-Type", "application/json");
        response.write(true, ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8)), callback);
    }

    private RestApiLdapRolesReverseLookup lookup() {
        return new RestApiLdapRolesReverseLookup(listEndpoint, getEndpoint);
    }

    @Test
    public void testListRolesSinglePage() throws Exception {
        listResponsesByOffset.put("0", """
                {"data":[
                   {"id":"1","name":"awc-admin","scope":"platform","unknown":"ignored"},
                   {"id":"2","name":"viewer"}
                 ],"pagination":{"limit":10,"offset":0,"total":2,"extra":true}}""");

        assertEquals(Set.of("platform:awc-admin", "viewer"), new HashSet<>(lookup().listRoles()));
        assertEquals(1, requests.size());
        assertEquals("/list?offset=0", requests.get(0));
    }

    @Test
    public void testListRolesFollowsPaginationUntilTotalReached() throws Exception {
        listResponsesByOffset.put("0", """
                {"data":[{"id":"1","name":"a","scope":"s"},{"id":"2","name":"b","scope":"s"}],
                 "pagination":{"limit":2,"offset":0,"total":3}}""");
        listResponsesByOffset.put("2", """
                {"data":[{"id":"3","name":"c","scope":"s"}],
                 "pagination":{"limit":2,"offset":2,"total":3}}""");

        assertEquals(Set.of("s:a", "s:b", "s:c"), new HashSet<>(lookup().listRoles()));
        assertEquals(List.of("/list?offset=0", "/list?offset=2"), new ArrayList<>(requests));
    }

    @Test
    public void testListRolesWithNoRolesReturnsEmptyList() throws Exception {
        listResponsesByOffset.put("0", "{\"data\":[],\"pagination\":{\"total\":0}}");

        assertTrue(lookup().listRoles().isEmpty());
        assertEquals(1, requests.size());
    }

    @Test
    public void testListRolesFailsWhenPageIsEmptyBeforeTotalReached() {
        listResponsesByOffset.put("0", "{\"data\":[{\"id\":\"1\",\"name\":\"a\"}],\"pagination\":{\"total\":3}}");
        listResponsesByOffset.put("1", "{\"data\":[],\"pagination\":{\"total\":3}}");

        assertThrows(RoleException.class, () -> lookup().listRoles());
        assertEquals(List.of("/list?offset=0", "/list?offset=1"), new ArrayList<>(requests));
    }

    @Test
    public void testListRolesHttpErrorFails() {
        listStatus.set(500);
        try {
            lookup().listRoles();
            throw new AssertionError("expected RoleException");
        } catch (RoleException e) {
            assertTrue(e.getMessage().contains("500"));
        }
    }

    @Test
    public void testLookupRoleAssigneesSplitsMembersByType() throws Exception {
        listResponsesByOffset.put("0", """
                {"data":[
                   {"id":"other","name":"awc-admin","scope":"another"},
                   {"id":"role-1","name":"awc-admin","scope":"platform"}
                 ],"pagination":{"total":2}}""");
        getResponsesByPath.put("/get/role-1", """
                {"id":"role-1","name":"awc-admin","scope":"platform","members":[
                   {"id":"m1","member_type":"user","member_id":"alice"},
                   {"id":"m2","member_type":"GROUP","member_id":"engineering"},
                   {"id":"m3","member_type":"user","member_id":"bob"},
                   {"id":"m4","member_type":"service","member_id":"ignored"}
                 ]}""");

        RoleAssignees assignees = lookup().lookupRoleAssignees("platform:awc-admin");

        assertEquals(List.of("alice", "bob"), assignees.getUsers());
        assertEquals(List.of("engineering"), assignees.getGroups());
        assertEquals(List.of("/list?name=awc-admin&scope=platform&offset=0", "/get/role-1"), new ArrayList<>(requests));
    }

    @Test
    public void testLookupRoleWithoutScopeOmitsScopeParameterAndMatchesUnscopedRole() throws Exception {
        listResponsesByOffset.put("0", """
                {"data":[
                   {"id":"scoped","name":"viewer","scope":"ws"},
                   {"id":"plain","name":"viewer"}
                 ],"pagination":{"total":2}}""");
        getResponsesByPath.put("/get/plain", """
                {"id":"plain","name":"viewer","members":[{"id":"m1","member_type":"user","member_id":"carol"}]}""");

        RoleAssignees assignees = lookup().lookupRoleAssignees("viewer");

        assertEquals(List.of("carol"), assignees.getUsers());
        assertTrue(assignees.getGroups().isEmpty());
        assertEquals("/list?name=viewer&offset=0", requests.get(0));
    }

    @Test
    public void testLookupRoleIdIsUrlEncoded() throws Exception {
        listResponsesByOffset.put("0", """
                {"data":[{"id":"a/b c","name":"viewer"}],"pagination":{"total":1}}""");
        getResponsesByPath.put("/get/a%2Fb%20c", """
                {"members":[{"member_type":"user","member_id":"dave"}]}""");

        RoleAssignees assignees = lookup().lookupRoleAssignees("viewer");

        assertEquals(List.of("dave"), assignees.getUsers());
        assertEquals("/get/a%2Fb%20c", requests.get(1));
    }

    @Test
    public void testLookupUnknownRoleReturnsEmptyAssigneesWithoutGetRequest() throws Exception {
        listResponsesByOffset.put("0", """
                {"data":[{"id":"1","name":"other","scope":"platform"}],"pagination":{"total":1}}""");

        RoleAssignees assignees = lookup().lookupRoleAssignees("platform:missing");

        assertTrue(assignees.getUsers().isEmpty());
        assertTrue(assignees.getGroups().isEmpty());
        assertEquals(1, requests.size());
    }

    @Test
    public void testLookupNullRoleReturnsEmptyAssigneesWithoutRequests() throws Exception {
        RoleAssignees assignees = lookup().lookupRoleAssignees(null);

        assertTrue(assignees.getUsers().isEmpty());
        assertTrue(assignees.getGroups().isEmpty());
        assertTrue(requests.isEmpty());
    }

    @Test
    public void testLookupRoleWithoutMembersReturnsEmptyAssignees() throws Exception {
        listResponsesByOffset.put("0", """
                {"data":[{"id":"1","name":"viewer"}],"pagination":{"total":1}}""");
        getResponsesByPath.put("/get/1", "{\"id\":\"1\",\"name\":\"viewer\"}");

        RoleAssignees assignees = lookup().lookupRoleAssignees("viewer");

        assertTrue(assignees.getUsers().isEmpty());
        assertTrue(assignees.getGroups().isEmpty());
    }

    @Test(expected = RoleException.class)
    public void testGetHttpErrorFails() throws Exception {
        listResponsesByOffset.put("0", """
                {"data":[{"id":"1","name":"viewer"}],"pagination":{"total":1}}""");
        getStatus.set(404);
        lookup().lookupRoleAssignees("viewer");
    }

    @Test(expected = RoleException.class)
    public void testListHttpErrorDuringLookupFails() throws Exception {
        listStatus.set(503);
        lookup().lookupRoleAssignees("viewer");
    }

    @Test(expected = RoleException.class)
    public void testMalformedListResponseFails() throws Exception {
        listResponsesByOffset.put("0", "not json");
        lookup().listRoles();
    }
}
