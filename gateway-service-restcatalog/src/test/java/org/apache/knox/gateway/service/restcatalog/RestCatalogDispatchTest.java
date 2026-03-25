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
package org.apache.knox.gateway.service.restcatalog;

import org.apache.http.client.methods.HttpUriRequest;
import org.apache.knox.gateway.security.CommonTokenConstants;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;

public class RestCatalogDispatchTest {

    private final RestCatalogDispatchTestUtils testUtils = new RestCatalogDispatchTestUtils();

    /**
     * If there are no client credentials in the request, then the metadata access and header additions should be
     * skipped altogether.
     *
     * @throws Exception
     */
    @Test
    public void testNoClientCredentialsInRequest() throws Exception {
        HttpUriRequest outboundRequest =
                testUtils.doTest(Collections.emptyMap(), "email,test", null, null, null);

        assertEquals("There should be no headers in the outbound request because the inbound request does not include client credentials.",
                0, outboundRequest.getAllHeaders().length);
    }

    /**
     * Verify that metadata items configured for inclusion in outbound requests as headers are indeed included iff the
     * metadata item actually exists for the client_id, and that no other metadata items manifest as headers except the
     * default userName metadata.
     *
     * @throws Exception
     */
    @Test
    public void testIncludeConfiguredMetatadataAsHeaders() throws Exception {
        final String client_id = "4606f5e1-9d02-41a1-9071-26a9e74e4ab9";
        final String clientSecret =
            "TkRZd05tWTFaVEV0T1dRd01pMDBNV0V4TFRrd056RXRNalpoT1dVM05HVTBZV0k1OjpNV1JrWkRBMk1tVXRPV0ppTnkwMFkyWTVMVGxqTUdJdFpqWXhZalV5WTJGa1lURmw";

        final String metadata_email = "user@host.com";
        final String metadata_userName = "someuser";
        final String metadata_category = "test category";
        final String metadata_arbitrary = "somevalue";

        Map<String, String> metaMap = new HashMap<>();
        metaMap.put("email", metadata_email);
        metaMap.put("userName", metadata_userName);
        metaMap.put("category", metadata_category);
        metaMap.put("arbitrary", metadata_arbitrary);
        metaMap.put("enabled", "true");

        HttpUriRequest outboundRequest =
            testUtils.doTest(metaMap, "email, test", CommonTokenConstants.CLIENT_CREDENTIALS, client_id, clientSecret);

        assertEquals("Unexpected number of headers in the outbound request",2, outboundRequest.getAllHeaders().length);
        assertEquals("userName is not configured as metadata which should result in an outbound header, but it is included by default.",
                1, outboundRequest.getHeaders(TokenMetadataHeaderHandler.DEFAULT_HEADER_PREFIX + "userName").length);
        assertEquals("email is configured as metadata which should result in an outbound header.",
                1, outboundRequest.getHeaders(TokenMetadataHeaderHandler.DEFAULT_HEADER_PREFIX + "email").length);
        assertEquals("Even though the test metadata is configured, there should be no header since there is no actual such metadata.",
                0,outboundRequest.getHeaders(TokenMetadataHeaderHandler.DEFAULT_HEADER_PREFIX + "arbitrary").length);
        assertEquals("Even though there is metadata by this name, it is not configured to be conveyed as a header, so it should be ignored.",
                0, outboundRequest.getHeaders(TokenMetadataHeaderHandler.DEFAULT_HEADER_PREFIX + "test").length);
    }

    /**
     * Verify that metadata items configured for inclusion in outbound requests as headers are indeed included iff the
     * metadata item actually exists for the client_id, and that no other metadata items manifest as headers except the
     * default userName metadata.
     *
     * @throws Exception
     */
    @Test
    public void testIncludeConfiguredMetatadataAsHeadersWithConfigurablePrefix() throws Exception {
        final String customHeaderPrefix = "Test-Header-Prefix-";
        final String client_id = "4606f5e1-9d02-41a1-9071-26a9e74e4ab9";
        final String clientSecret =
                "TkRZd05tWTFaVEV0T1dRd01pMDBNV0V4TFRrd056RXRNalpoT1dVM05HVTBZV0k1OjpNV1JrWkRBMk1tVXRPV0ppTnkwMFkyWTVMVGxqTUdJdFpqWXhZalV5WTJGa1lURmw";

        final String metadata_email = "user@host.com";
        final String metadata_userName = "someuser";
        final String metadata_category = "test category";
        final String metadata_arbitrary = "somevalue";

        Map<String, String> metaMap = new HashMap<>();
        metaMap.put("email", metadata_email);
        metaMap.put("userName", metadata_userName);
        metaMap.put("category", metadata_category);
        metaMap.put("arbitrary", metadata_arbitrary);
        metaMap.put("enabled", "true");

        HttpUriRequest outboundRequest =
                testUtils.doTest(metaMap, "email,arbitrary", customHeaderPrefix, CommonTokenConstants.CLIENT_CREDENTIALS, client_id, clientSecret);

        assertEquals("Unexpected number of headers in the outbound request",3, outboundRequest.getAllHeaders().length);
        assertEquals("userName is not configured as metadata which should result in an outbound header, but it is included by default.",
                1, outboundRequest.getHeaders(customHeaderPrefix + "userName").length);
        assertEquals("email is configured as metadata which should result in an outbound header.",
                1, outboundRequest.getHeaders(customHeaderPrefix + "email").length);
        assertEquals("Even though the test metadata is configured, there should be no header since there is no actual such metadata.",
                0,outboundRequest.getHeaders(customHeaderPrefix + "test").length);
        assertEquals("Since there is metadata by this name, and it is configured to be conveyed as a header, it should be present.",
                1, outboundRequest.getHeaders(customHeaderPrefix + "arbitrary").length);
    }

}
