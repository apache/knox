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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.provider.federation.jwt.JWTMessages;

import javax.servlet.FilterConfig;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A shared record of tokens for which the signature has been verified.
 */
public class SignatureVerificationCache {

    public static final String TOKENS_VERIFIED_CACHE_MAX = "tokens.verified.cache.max";
    private static final int   TOKENS_VERIFIED_CACHE_MAX_DEFAULT = 250;

    static final String DEFAULT_CACHE_ID = "default-cache";

    static JWTMessages log = MessagesFactory.get( JWTMessages.class );

    private static final ConcurrentHashMap<String, SignatureVerificationCache> instances = new ConcurrentHashMap<>();

    private Cache<String, Boolean> verifiedTokens;

    /**
     * Caches are topology-specific because the configuration is defined at the provider level.
     *
     * @param topology The topology for which the cache is being requested, or null if the default is sufficient.
     * @param config   The FilterConfig associated with the calling provider.
     *
     * @return A SignatureVerificationCache for the specified topology, or the default one if no topology is specified.
     */
    @SuppressWarnings("PMD.SingletonClassReturningNewInstance")
    public static SignatureVerificationCache getInstance(final String topology, final FilterConfig config) {
        String cacheId = topology != null ? topology : DEFAULT_CACHE_ID;
        return instances.computeIfAbsent(cacheId, c -> initializeCacheForTopology(cacheId, config));
    }

    private static SignatureVerificationCache initializeCacheForTopology(final String topology, final FilterConfig config) {
        SignatureVerificationCache cache = new SignatureVerificationCache(config);
        log.initializedSignatureVerificationCache(topology);
        return cache;
    }

    private SignatureVerificationCache(final FilterConfig config) {
        initializeVerifiedTokensCache(config);
    }

    /**
     * Initialize the cache for token verification records.
     *
     * @param config The configuration of the provider employing this cache.
     */
    private void initializeVerifiedTokensCache(final FilterConfig config) {
        int maxCacheSize = TOKENS_VERIFIED_CACHE_MAX_DEFAULT;

        String configValue = config.getInitParameter(TOKENS_VERIFIED_CACHE_MAX);
        if (configValue != null && !configValue.isEmpty()) {
            try {
                maxCacheSize = Integer.parseInt(configValue);
            } catch (NumberFormatException e) {
                log.invalidVerificationCacheMaxConfiguration(configValue);
            }
        }

        verifiedTokens = Caffeine.newBuilder().maximumSize(maxCacheSize).build();
    }

    /**
     * Determine if the specified token's signature has previously been successfully verified.
     *
     * @param token A serialized JWT or Passcode token.
     *
     * @return true, if the specified token has been previously verified; Otherwise, false.
     */
    public boolean hasSignatureBeenVerified(final String token) {
        return (verifiedTokens.getIfPresent(token) != null);
    }

    /**
     * Record a successful token signature verification.
     *
     * @param token A serialized JWT or Passcode token for which the signature has been successfully verified.
     */
    public void recordSignatureVerification(final String token) {
        verifiedTokens.put(token, true);
    }

    /**
     * Explicitly evict the signature verification record from the cache if it exists.
     *
     * @param token The serialized JWT or Passcode token for which the associated signature verification record should be evicted.
     */
    public void removeSignatureVerificationRecord(final String token) {
         verifiedTokens.asMap().remove(token);
    }

    /**
     * @return The size of the cache.
     */
    public long getSize() {
        return verifiedTokens.estimatedSize();
    }

    /**
     * Remove any entries which should be evicted from the cache.
     */
    public void performMaintenance() {
        verifiedTokens.cleanUp();
    }

    /**
     * Clear the contents of the cache.
     */
    public void clear() {
        verifiedTokens.asMap().clear();
    }
}
