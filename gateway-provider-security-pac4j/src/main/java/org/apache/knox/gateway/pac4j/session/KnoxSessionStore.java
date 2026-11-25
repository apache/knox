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
package org.apache.knox.gateway.pac4j.session;

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter;
import org.apache.knox.gateway.util.SetCookieHeader;
import org.apache.knox.gateway.util.Urls;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pac4j.core.context.WebContextHelper;
import org.pac4j.core.context.Cookie;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.util.serializer.JavaSerializer;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.jee.context.JEEContext;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_COOKIE_SAMESITE;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_CUSTOM_ATTRIBUTES;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_CUSTOM_ATTRIBUTES_DEFAULT;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_GROUPS;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_GROUPS_DEFAULT;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS_DEFAULT;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_ROLES;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_ROLES_DEFAULT;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_COOKIE_MAX_AGE;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_SECURE_COOKIE;

/**
 * Specific session store where data are saved into cookies (and not in memory).
 * Each data is encrypted and base64 encoded before being saved as a cookie (for security reasons).
 *
 * @since 0.8.0
 */
public class KnoxSessionStore implements SessionStore {

    private static final Logger logger = LogManager.getLogger(KnoxSessionStore.class);

    public static final String PAC4J_PASSWORD = "pac4j.password";

    public static final String PAC4J_SESSION_PREFIX = "pac4j.session.";

    private static final String KEY_SALT_PREFIX = "knox-pac4j-session:";

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";

    private static final String DEFAULT_PBE_ALGORITHM = "PBKDF2WithHmacSHA256";

    private static final int DEFAULT_ITERATION_COUNT = 65536;

    private static final int DEFAULT_KEY_LENGTH = 256;

    private static final int NONCE_LENGTH = 12;

    private static final int TAG_LENGTH_BITS = 128;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JavaSerializer javaSerializer;

    private final SecretKey secretKey;

    private final String domainSuffix;

    final Map<String, String> sessionStoreConfigs;

    public KnoxSessionStore(final char[] password,
        final GatewayConfig gatewayConfig,
        final String clusterName,
        final String domainSuffix,
        final Map<String, String> sessionStoreConfigs) {
        this.javaSerializer = new JavaSerializer();
        this.secretKey = deriveKey(password, gatewayConfig, clusterName);
        this.domainSuffix = domainSuffix;
        this.sessionStoreConfigs = sessionStoreConfigs == null ? new HashMap<>() : sessionStoreConfigs;
    }

    private static SecretKey deriveKey(final char[] password, final GatewayConfig gatewayConfig, final String clusterName) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("A pac4j session password is required");
        }
        if (clusterName == null) {
            throw new IllegalArgumentException("A cluster name is required");
        }
        String pbeAlgorithm = DEFAULT_PBE_ALGORITHM;
        int iterationCount = DEFAULT_ITERATION_COUNT;
        int keyLength = DEFAULT_KEY_LENGTH;
        if (gatewayConfig != null) {
            if (StringUtils.isNotBlank(gatewayConfig.getPBEAlgorithm())) {
                pbeAlgorithm = gatewayConfig.getPBEAlgorithm();
            }
            if (StringUtils.isNotBlank(gatewayConfig.getIterationCount())) {
                iterationCount = Integer.parseInt(gatewayConfig.getIterationCount().trim());
            }
            if (StringUtils.isNotBlank(gatewayConfig.getKeyLength())) {
                keyLength = Integer.parseInt(gatewayConfig.getKeyLength().trim());
            }
        }
        if (keyLength != 128 && keyLength != 192 && keyLength != 256) {
            throw new IllegalArgumentException("Unsupported AES key length for pac4j session cookies: " + keyLength);
        }
        if (iterationCount <= 0) {
            throw new IllegalArgumentException("Invalid iteration count for pac4j session cookies: " + iterationCount);
        }
        final byte[] salt = (KEY_SALT_PREFIX + clusterName).getBytes(StandardCharsets.UTF_8);
        final PBEKeySpec spec = new PBEKeySpec(password, salt, iterationCount, keyLength);
        try {
            final byte[] encoded = SecretKeyFactory.getInstance(pbeAlgorithm).generateSecret(spec).getEncoded();
            return new SecretKeySpec(encoded, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Unable to derive the pac4j session cookie key", e);
        } finally {
            spec.clearPassword();
        }
    }

    @Override
    public Optional<String> getSessionId(WebContext context, boolean createSession) {
        return Optional.empty();
    }

    private Object uncompressDecryptBase64(final String cookieName, final String v) {
        if (v == null || v.isEmpty()) {
            return null;
        }
        try {
            final byte[] bytes = Base64.getUrlDecoder().decode(v);
            if (bytes.length < NONCE_LENGTH + TAG_LENGTH_BITS / 8) {
                return null;
            }
            final Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, bytes, 0, NONCE_LENGTH));
            cipher.updateAAD(cookieName.getBytes(StandardCharsets.UTF_8));
            final byte[] clear = cipher.doFinal(bytes, NONCE_LENGTH, bytes.length - NONCE_LENGTH);
            return javaSerializer.deserializeFromBytes(unCompress(clear));
        } catch (Exception e) {
            logger.debug("Ignoring invalid pac4j session cookie {}: {}", cookieName, e.getClass().getName());
            return null;
        }
    }

    @Override
    public Optional<Object> get(WebContext context, String key) {
        final String cookieName = PAC4J_SESSION_PREFIX + key;
        final Cookie cookie = WebContextHelper.getCookie(context, cookieName);
        Object value = null;
        if (cookie != null) {
            value = uncompressDecryptBase64(cookieName, cookie.getValue());
        }
        logger.debug("Get from session: {} = {}", key, value);
        return Optional.ofNullable(value);
    }

    private String compressEncryptBase64(final String cookieName, final Object o) {
        if (o == null || o.equals("")
            || (o instanceof Map<?,?> && ((Map<?,?>)o).isEmpty())) {
            return null;
        } else {
            byte[] bytes = javaSerializer.serializeToBytes(o);

            /* compress the data  */
            try {
                bytes = compress(bytes);

                if (bytes.length > 3000) {
                    logger.warn("Cookie too big, it might not be properly set");
                }

            } catch (final IOException e) {
                throw new TechnicalException(e);
            }

            try {
                final byte[] nonce = new byte[NONCE_LENGTH];
                SECURE_RANDOM.nextBytes(nonce);
                final Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
                cipher.updateAAD(cookieName.getBytes(StandardCharsets.UTF_8));
                final byte[] encrypted = cipher.doFinal(bytes);
                final byte[] out = ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
                return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
            } catch (final GeneralSecurityException e) {
                throw new TechnicalException(e);
            }
        }
    }

    @Override
    public void set(WebContext context, String key, Object value) {
        Object profile = value;
        SetCookieHeader setCookieHeader;

        if (value == null) {
            setCookieHeader = new SetCookieHeader(PAC4J_SESSION_PREFIX + key, null);
        } else {
            if (key.contentEquals(Pac4jConstants.USER_PROFILES)) {
                /* trim the profile object */
                profile = clearUserProfile(value);
            }
            logger.debug("Save in session: {} = {}", key, profile);
            setCookieHeader = new SetCookieHeader(PAC4J_SESSION_PREFIX + key, compressEncryptBase64(PAC4J_SESSION_PREFIX + key, profile));
        }
        try {
            String domain = Urls
                .getDomainName(context.getFullRequestURL(), this.domainSuffix);
            if (domain == null) {
                domain = context.getServerName();
            }
            setCookieHeader.setDomain(domain);
        } catch (final Exception e) {
            throw new TechnicalException(e);
        }
        final String secureCookieSettings = sessionStoreConfigs.get(PAC4J_SESSION_STORE_SECURE_COOKIE);
        final boolean secureFlag = secureCookieSettings == null ? WebContextHelper.isHttpsOrSecure(context) : Boolean.parseBoolean(secureCookieSettings);
        setCookieHeader.setSecure(secureFlag);
        if (WebContextHelper.isHttpsOrSecure(context)) {
            setCookieHeader.setHttpOnly(true);
        }

        /*
         *  set the correct path for setting pac4j profile cookie.
         *  This is because, Pac4jDispatcherFilter.PAC4J_CALLBACK_PARAMETER in the path
         *  indicates callback when ? cannot be used.
         */
        if (context.getPath() != null && context.getPath()
            .contains(Pac4jDispatcherFilter.PAC4J_CALLBACK_PARAMETER)) {

            final String[] parts = ((JEEContext) context).getNativeRequest().getRequestURI()
                .split(
                    "websso"+ Pac4jDispatcherFilter.URL_PATH_SEPARATOR + Pac4jDispatcherFilter.PAC4J_CALLBACK_PARAMETER);
            setCookieHeader.setPath(parts[0]);
        }

        /* Set cookie max age */
        if(sessionStoreConfigs != null && sessionStoreConfigs.containsKey(PAC4J_COOKIE_MAX_AGE)) {
            setCookieHeader.setMaxAge(Integer.parseInt(sessionStoreConfigs.get(PAC4J_COOKIE_MAX_AGE)));
        }

        if(sessionStoreConfigs != null && sessionStoreConfigs.containsKey(PAC4J_COOKIE_SAMESITE)) {
            setCookieHeader.setSameSite(sessionStoreConfigs.get(PAC4J_COOKIE_SAMESITE));
        }

        ((JEEContext) context).getNativeResponse().addHeader("Set-Cookie", setCookieHeader.toString());
    }

    /**
     * A function used to compress the data using GZIP
     * @param data data to be compressed
     * @return gziped data
     * @since 1.1.0
     */
    private static byte[] compress(final byte[] data) throws IOException {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream(data.length)) {
            try(GZIPOutputStream gzip = new GZIPOutputStream(byteStream)) {
                gzip.write(data);
            }
            return byteStream.toByteArray();
        }
    }

    /**
     * Decompress the data compressed using gzip
     *
     * @param data data to be decompressed
     * @return uncompressed data
     * @throws IOException exception if can't decompress
     * @since 1.1.0
     */
    private static byte[] unCompress(final byte[] data) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            GZIPInputStream gzip = new GZIPInputStream(inputStream)) {
            return gzip.readAllBytes();
        }
    }

    /**
     * Keep only the fields that are needed for Pac4J.
     * Used to reduce the cookie size.
     * @param value profile object
     * @return trimmed profile object
     * @since 1.3.0
     */
    private Object clearUserProfile(final Object value) {
        if(value instanceof Map<?,?>) {
            final Map<String, CommonProfile> profiles = (Map<String, CommonProfile>) value;
            profiles.forEach((name, profile) -> profile.removeLoginData());

            if(sessionStoreConfigs != null) {
                if(sessionStoreConfigs
                        .getOrDefault(PAC4J_SESSION_STORE_EXCLUDE_GROUPS, PAC4J_SESSION_STORE_EXCLUDE_GROUPS_DEFAULT)
                        .equalsIgnoreCase("true")) {
                    profiles.forEach((name, profile) -> profile.removeAttribute("groups"));
                }
                if(sessionStoreConfigs
                        .getOrDefault(PAC4J_SESSION_STORE_EXCLUDE_ROLES, PAC4J_SESSION_STORE_EXCLUDE_ROLES_DEFAULT)
                        .equalsIgnoreCase("true")) {
                    profiles.forEach((name, profile) -> profile.removeAttribute("roles"));
                }
                if(sessionStoreConfigs
                        .getOrDefault(PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS, PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS_DEFAULT)
                        .equalsIgnoreCase("true")) {
                    profiles.forEach((name, profile) -> profile.removeAttribute("permissions"));
                }
              if(!StringUtils.isBlank(sessionStoreConfigs
                      .getOrDefault(PAC4J_SESSION_STORE_EXCLUDE_CUSTOM_ATTRIBUTES, PAC4J_SESSION_STORE_EXCLUDE_CUSTOM_ATTRIBUTES_DEFAULT))) {
                final String customAttributes = sessionStoreConfigs
                        .getOrDefault(PAC4J_SESSION_STORE_EXCLUDE_CUSTOM_ATTRIBUTES, PAC4J_SESSION_STORE_EXCLUDE_CUSTOM_ATTRIBUTES_DEFAULT);
                /* splits the string based on: zero or more whitespace, a literal comma, zero or more whitespace */
                final List<String> attr = Arrays.asList(customAttributes.split("\\s*,\\s*"));
                attr.forEach(a -> profiles.forEach((name, profile) -> profile.removeAttribute(a)));
              }
            }

            return profiles;
        } else {
            final CommonProfile profile = (CommonProfile) value;
            profile.removeLoginData();
            return profile;
        }
    }

    @Override
    public Optional<SessionStore> buildFromTrackableSession(WebContext arg0, Object arg1) {
        return Optional.empty();
    }

    @Override
    public boolean destroySession(WebContext arg0) {
        return false;
    }

    @Override
    public Optional<Object> getTrackableSession(WebContext arg0) {
        return Optional.empty();
    }

    @Override
    public boolean renewSession(final WebContext context) {
        return false;
    }
}
