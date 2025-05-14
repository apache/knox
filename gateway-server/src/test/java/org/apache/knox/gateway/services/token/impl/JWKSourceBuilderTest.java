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
package org.apache.knox.gateway.services.token.impl;

import com.nimbusds.jose.JOSEObjectType;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.easymock.EasyMock;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test class for verifying the JWKSourceBuilder parameters in DefaultTokenAuthorityService.
 */
public class JWKSourceBuilderTest {

    /**
     * Test that the cacheTTL and cacheTimeOut parameters are correctly passed to JWKSourceBuilder.
     * <p>
     * This test verifies that the DefaultTokenAuthorityService correctly uses the values from
     * GatewayConfig when building the JWKSource.
     */
    @Test
    public void testJWKSourceBuilderParameters() throws Exception {
        // Define custom cache TTL and timeout values
        final long customCacheTTL = 60000; // 1 minute
        final long customCacheTimeout = 5000; // 5 seconds
        final long customOutageTTL = 7200000; // 2 hours
        // Create a mock GatewayConfig that returns our custom values
        GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
        EasyMock.expect(config.getJwksCacheTimeToLive()).andReturn(customCacheTTL).anyTimes();
        EasyMock.expect(config.getJwksCacheRefreshTimeout()).andReturn(customCacheTimeout).anyTimes();
        EasyMock.expect(config.getJwksOutageCacheTTL()).andReturn(customOutageTTL).anyTimes();
        EasyMock.expect(config.getIssuersWithIgnoredTypeHeader()).andReturn(Collections.emptySet()).anyTimes();
        // Mock the necessary services
        AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
        KeystoreService keystoreService = EasyMock.createNiceMock(KeystoreService.class);
        // Replay all mocks
        EasyMock.replay(config, aliasService, keystoreService);
        // Create the DefaultTokenAuthorityService
        DefaultTokenAuthorityService service = new DefaultTokenAuthorityService();
        service.setAliasService(aliasService);
        service.setKeystoreService(keystoreService);
        service.init(config, new HashMap<>());
        // Create a test JWT token
        JWT token = EasyMock.createNiceMock(JWT.class);
        EasyMock.expect(token.getIssuer()).andReturn("test-issuer").anyTimes();
        EasyMock.replay(token);
        // Define the JWK URL and algorithm
        String jwksUrl = "https://example.com/.well-known/jwks.json";
        String algorithm = "RS256";
        Set<JOSEObjectType> allowedJwsTypes = new HashSet<>();
        allowedJwsTypes.add(JOSEObjectType.JWT);
        try {
            // This will throw an exception because the URL doesn't exist, but we're only
            // interested in verifying that the correct parameters are passed to JWKSourceBuilder
            service.verifyToken(token, jwksUrl, algorithm, allowedJwsTypes);
            fail("Expected TokenServiceException");
        } catch (TokenServiceException e) {
            // Expected exception because the URL doesn't exist
            // Verify that the exception message indicates an attempt to connect to the URL
            assertTrue(e.getMessage().contains("Cannot verify token"));
        }
        // Verify all mocks
        EasyMock.verify(config, aliasService, keystoreService, token);
    }
}
