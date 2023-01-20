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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter;
import org.apache.knox.gateway.services.security.CryptoService;
import org.apache.knox.gateway.services.security.EncryptionResult;
import org.apache.knox.gateway.util.Urls;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pac4j.core.context.ContextHelper;
import org.pac4j.core.context.Cookie;
import org.pac4j.core.context.JEEContext;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.util.JavaSerializationHelper;
import org.pac4j.core.util.Pac4jConstants;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_CUSTOM_ATTRIBUTES;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_CUSTOM_ATTRIBUTES_DEFAULT;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_GROUPS;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_GROUPS_DEFAULT;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_PERMISSIONS_DEFAULT;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_ROLES;
import static org.apache.knox.gateway.pac4j.filter.Pac4jDispatcherFilter.PAC4J_SESSION_STORE_EXCLUDE_ROLES_DEFAULT;

/**
 * Specific session store where data are saved into cookies (and not in memory).
 * Each data is encrypted and base64 encoded before being saved as a cookie (for security reasons).
 *
 * @since 0.8.0
 */
public class KnoxSessionStore<C extends WebContext> implements SessionStore<C> {

    private static final Logger logger = LogManager.getLogger(KnoxSessionStore.class);

    public static final String PAC4J_PASSWORD = "pac4j.password";

    public static final String PAC4J_SESSION_PREFIX = "pac4j.session.";

    private final JavaSerializationHelper javaSerializationHelper;

    private final CryptoService cryptoService;

    private final String clusterName;

    private final String domainSuffix;

    final Map<String, String> sessionStoreConfigs;

    public KnoxSessionStore(final CryptoService cryptoService, final String clusterName, final String domainSuffix) {
        this(cryptoService, clusterName, domainSuffix, new HashMap());
    }

    public KnoxSessionStore(final CryptoService cryptoService,
        final String clusterName,
        final String domainSuffix,
        final Map<String, String> sessionStoreConfigs) {
        javaSerializationHelper = new JavaSerializationHelper();
        this.cryptoService = cryptoService;
        this.clusterName = clusterName;
        this.domainSuffix = domainSuffix;
        this.sessionStoreConfigs = sessionStoreConfigs;
    }


    @Override
    public String getOrCreateSessionId(WebContext context) {
        return null;
    }

    private Serializable uncompressDecryptBase64(final String v) {
        if (v != null && !v.isEmpty()) {
            byte[] bytes = Base64.decodeBase64(v);
            EncryptionResult result = EncryptionResult.fromByteArray(bytes);
            byte[] clear = cryptoService.decryptForCluster(this.clusterName,
                PAC4J_PASSWORD,
                result.cipher,
                result.iv,
                result.salt);
            if (clear != null) {
                try {
                    return javaSerializationHelper.deserializeFromBytes(unCompress(clear));
                } catch (IOException e) {
                    throw new TechnicalException(e);
                }
            }
        }
        return null;
    }

    @Override
    public Optional<Object> get(WebContext context, String key) {
        final Cookie cookie = ContextHelper.getCookie(context, PAC4J_SESSION_PREFIX + key);
        Object value = null;
        if (cookie != null) {
            value = uncompressDecryptBase64(cookie.getValue());
        }
        logger.debug("Get from session: {} = {}", key, value);
        return Optional.ofNullable(value);
    }

    private String compressEncryptBase64(final Object o) {
        if (o == null || o.equals("")
            || (o instanceof Map<?,?> && ((Map<?,?>)o).isEmpty())) {
            return null;
        } else {
            byte[] bytes = javaSerializationHelper.serializeToBytes((Serializable) o);

            /* compress the data  */
            try {
                bytes = compress(bytes);

                if(bytes.length > 3000) {
                    logger.warn("Cookie too big, it might not be properly set");
                }

            } catch (final IOException e) {
                throw new TechnicalException(e);
            }

            EncryptionResult result = cryptoService.encryptForCluster(this.clusterName, PAC4J_PASSWORD, bytes);
            return Base64.encodeBase64String(result.toByteAray());
        }
    }

    @Override
    public void set(WebContext context, String key, Object value) {
        Object profile = value;
        Cookie cookie;

        if (value == null) {
            cookie = new Cookie(PAC4J_SESSION_PREFIX + key, null);
        } else {
            if (key.contentEquals(Pac4jConstants.USER_PROFILES)) {
                /* trim the profile object */
                profile = clearUserProfile(value);
            }
            logger.debug("Save in session: {} = {}", key, profile);
            cookie = new Cookie(PAC4J_SESSION_PREFIX + key,
                compressEncryptBase64(profile));
        }
        try {
            String domain = Urls
                .getDomainName(context.getFullRequestURL(), this.domainSuffix);
            if (domain == null) {
                domain = context.getServerName();
            }
            cookie.setDomain(domain);
        } catch (final Exception e) {
            throw new TechnicalException(e);
        }
        cookie.setHttpOnly(true);
        cookie.setSecure(ContextHelper.isHttpsOrSecure(context));

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

            cookie.setPath(parts[0]);

        }
        context.addResponseCookie(cookie);
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
            return IOUtils.toByteArray(gzip);
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
    public Optional<SessionStore<C>> buildFromTrackableSession(WebContext arg0, Object arg1) {
        return Optional.empty();
    }

    @Override
    public boolean destroySession(WebContext arg0) {
        return false;
    }

    @Override
    public Optional getTrackableSession(WebContext arg0) {
        return Optional.empty();
    }

    @Override
    public boolean renewSession(final WebContext context) {
        return false;
    }
}
