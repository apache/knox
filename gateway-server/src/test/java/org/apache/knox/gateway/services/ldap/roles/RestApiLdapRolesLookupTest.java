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

import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.easymock.EasyMock;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PrepareForTest({HttpClients.class, RestApiLdapRolesLookup.class})
public class RestApiLdapRolesLookupTest {

    @Test
    public void testLookupRoles() throws Exception {
        String endpoint = "http://localhost:8080/api/v0/auth/roles/lookup";
        String jsonResponse = "{\n" +
                "  \"user_id\": \"alice\",\n" +
                "  \"roles\": [\n" +
                "    { \"scope\": \"platform\", \"name\": \"awc-admin\" },\n" +
                "    { \"scope\": \"ml-workspace-abc\", \"name\": \"viewer\" }\n" +
                "  ]\n" +
                "}";

        CloseableHttpClient httpClient = EasyMock.createMock(CloseableHttpClient.class);
        CloseableHttpResponse response = EasyMock.createMock(CloseableHttpResponse.class);
        StatusLine statusLine = EasyMock.createMock(StatusLine.class);
        HttpEntity entity = EasyMock.createMock(HttpEntity.class);

        PowerMock.mockStatic(HttpClients.class);
        EasyMock.expect(HttpClients.createDefault()).andReturn(httpClient);
        EasyMock.expect(httpClient.execute(EasyMock.anyObject(HttpPost.class))).andReturn(response);
        EasyMock.expect(response.getStatusLine()).andReturn(statusLine);
        EasyMock.expect(statusLine.getStatusCode()).andReturn(200);
        EasyMock.expect(response.getEntity()).andReturn(entity);
        EasyMock.expect(entity.getContentType()).andReturn(null).anyTimes();
        EasyMock.expect(entity.getContent()).andReturn(new ByteArrayInputStream(jsonResponse.getBytes(StandardCharsets.UTF_8)));
        // EntityUtils.toString uses entity.getContent() or other methods.
        // We might need to mock EntityUtils or provide a better mock for entity.
        EasyMock.expect(entity.getContentLength()).andReturn((long) jsonResponse.length()).anyTimes();

        response.close();
        EasyMock.expectLastCall().anyTimes();
        httpClient.close();
        EasyMock.expectLastCall().anyTimes();

        PowerMock.replayAll();
        EasyMock.replay(httpClient, response, statusLine, entity);

        RestApiLdapRolesLookup lookup = new RestApiLdapRolesLookup(endpoint);
        Collection<String> roles = lookup.lookupRoles("alice", List.of("group1"));

        assertEquals(2, roles.size());
        assertTrue(roles.contains("platform:awc-admin"));
        assertTrue(roles.contains("ml-workspace-abc:viewer"));

        PowerMock.verifyAll();
    }
}
