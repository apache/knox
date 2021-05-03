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
package org.apache.knox.gateway.provider.federation.jwt.filter;

import com.nimbusds.jwt.SignedJWT;
import org.apache.knox.gateway.provider.federation.TestFilterConfig;
import org.junit.BeforeClass;
import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SignatureVerificationCacheTest {

    private static RSAPrivateKey privateKey;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair KPair = kpg.generateKeyPair();
        privateKey = (RSAPrivateKey) KPair.getPrivate();
    }

    /**
     * Verify that the default SignatureVerificationCache instance is returned when no topology is specified.
     */
    @Test
    public void testSignatureVerificationCacheWithoutTopology() throws Exception {
        SignatureVerificationCache cache = SignatureVerificationCache.getInstance(null, new TestFilterConfig());
        assertNotNull(cache);
        SignatureVerificationCache defaultCache =
            SignatureVerificationCache.getInstance(SignatureVerificationCache.DEFAULT_CACHE_ID, new TestFilterConfig());
        assertNotNull(defaultCache);
        assertEquals("Expected the default cache when no topology is specified.", defaultCache, cache);
    }

    /**
     * Verify that the topology-specific SignatureVerificationCache instance is returned when a topology is specified.
     */
    @Test
    public void testSignatureVerificationCacheForTopology() throws Exception {
        final String topologyName = "test-topology-explicit";
        final Properties filterProps = new Properties();
        filterProps.setProperty(TestFilterConfig.TOPOLOGY_NAME_PROP, topologyName);
        final TestFilterConfig filterConfig = new TestFilterConfig(filterProps);

        SignatureVerificationCache ref1 = SignatureVerificationCache.getInstance(topologyName, filterConfig);
        assertNotNull(ref1);

        SignatureVerificationCache ref2 = SignatureVerificationCache.getInstance(topologyName, filterConfig);
        assertNotNull(ref2);
        assertEquals("Expected the same cache when the same topology is explicitly specified.", ref1, ref2);

        SignatureVerificationCache ref3 = SignatureVerificationCache.getInstance(topologyName + "-2", filterConfig);
        assertNotNull(ref3);
        assertNotEquals("Expected a different cache when a different topology is explicitly specified.", ref2, ref3);
    }

    @Test
    public void testSignatureVerificationCacheLifecycle() throws Exception {
        final String topologyName = "test-topology-lifecycle";
        final Properties filterProps = new Properties();
        filterProps.setProperty(TestFilterConfig.TOPOLOGY_NAME_PROP, topologyName);
        final TestFilterConfig filterConfig = new TestFilterConfig(filterProps);

        SignatureVerificationCache cache = SignatureVerificationCache.getInstance(topologyName, filterConfig);
        assertNotNull(cache);

        // Create a JWT
        SignedJWT jwt = createTestJWT();
        String serializedJWT = jwt.serialize();

        // Verify that there is not yet any signature verification record for this JWT
        assertFalse("JWT signature verification should NOT have been recored yet.",
                    cache.hasSignatureBeenVerified(serializedJWT));

        // Record the signature verification for this JWT
        cache.recordSignatureVerification(serializedJWT);
        assertTrue("JWT signature verification should have been recored yet.",
                    cache.hasSignatureBeenVerified(serializedJWT));

        // Explicitly remove the signature verification record for this JWT
        cache.removeSignatureVerificationRecord(serializedJWT);
        assertFalse("JWT signature verification record should no longer be in the cache.",
                    cache.hasSignatureBeenVerified(serializedJWT));
    }

    @Test
    public void testSignatureVerificationCacheClear() throws Exception {
        final int jwtCount = 5;
        final String topologyName = "test-topology-clear";
        final Properties filterProps = new Properties();
        filterProps.setProperty(TestFilterConfig.TOPOLOGY_NAME_PROP, topologyName);
        final TestFilterConfig filterConfig = new TestFilterConfig(filterProps);

        SignatureVerificationCache cache = SignatureVerificationCache.getInstance(topologyName, filterConfig);
        assertNotNull(cache);

        // Create test JWTs
        List<SignedJWT> testJWTs = new ArrayList<>();
        List<String> serializedJWTs = new ArrayList<>();
        for (int i = 0 ; i < jwtCount ; i++) {
            testJWTs.add(createTestJWT());
            serializedJWTs.add(testJWTs.get(i).serialize());
        }

        // Verify that there is not yet any signature verification record for the test JWTs
        assertEquals("There should not yet be any records in the cache.", 0, cache.getSize());

        // Record the signature verification for the test JWTs
        for (int i = 0 ; i < jwtCount ; i++) {
            cache.recordSignatureVerification(serializedJWTs.get(i));
        }
        assertEquals("Unexpected cache size.", jwtCount, cache.getSize());

        // Explicitly remove all signature verification records from the cache
        cache.clear();
        assertEquals("Cache should be empty after clear() is invoked.", 0, cache.getSize());

        // Verify that there is no longer any signature verification record for the test JWTs
        for (int i = 0 ; i < jwtCount ; i++) {
            assertFalse("JWT signature verification record should no longer be in the cache.",
                        cache.hasSignatureBeenVerified(serializedJWTs.get(i)));
        }
    }

    private SignedJWT createTestJWT() throws Exception {
        return JWTTestUtils.getJWT(AbstractJWTFilter.JWT_DEFAULT_ISSUER,
                                   "alice",
                                   new Date(System.currentTimeMillis() + 5000),
                                   privateKey);
    }

}
