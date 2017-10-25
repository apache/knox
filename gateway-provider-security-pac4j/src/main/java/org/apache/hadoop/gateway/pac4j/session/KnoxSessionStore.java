/**
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
package org.apache.hadoop.gateway.pac4j.session;

import org.apache.commons.codec.binary.Base64;
import org.apache.hadoop.gateway.services.security.CryptoService;
import org.apache.hadoop.gateway.services.security.EncryptionResult;
import org.apache.hadoop.gateway.util.Urls;
import org.pac4j.core.context.ContextHelper;
import org.pac4j.core.context.Cookie;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.core.util.JavaSerializationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Map;

/**
 * Specific session store where data are saved into cookies (and not in memory).
 * Each data is encrypted and base64 encoded before being saved as a cookie (for security reasons).
 *
 * @since 0.8.0
 */
public class KnoxSessionStore implements SessionStore {

    private static final Logger logger = LoggerFactory.getLogger(KnoxSessionStore.class);

    public static final String PAC4J_PASSWORD = "pac4j.password";

    public static final String PAC4J_SESSION_PREFIX = "pac4j.session.";

    private final JavaSerializationHelper javaSerializationHelper;

    private final CryptoService cryptoService;

    private final String clusterName;

    private final String domainSuffix;

    public KnoxSessionStore(final CryptoService cryptoService, final String clusterName, final String domainSuffix) {
        javaSerializationHelper = new JavaSerializationHelper();
        this.cryptoService = cryptoService;
        this.clusterName = clusterName;
        this.domainSuffix = domainSuffix;
    }

    public String getOrCreateSessionId(WebContext context) {
        return null;
    }

    private Serializable decryptBase64(final String v) {
        if (v != null && v.length() > 0) {
            byte[] bytes = Base64.decodeBase64(v);
            EncryptionResult result = EncryptionResult.fromByteArray(bytes);
            byte[] clear = cryptoService.decryptForCluster(this.clusterName,
                    PAC4J_PASSWORD,
                    result.cipher,
                    result.iv,
                    result.salt);
            if (clear != null) {
                return javaSerializationHelper.unserializeFromBytes(clear);
            }
        }
        return null;
    }

    public Object get(WebContext context, String key) {
        final Cookie cookie = ContextHelper.getCookie(context, PAC4J_SESSION_PREFIX + key);
        Object value = null;
        if (cookie != null) {
            value = decryptBase64(cookie.getValue());
        }
        logger.debug("Get from session: {} = {}", key, value);
        return value;
    }

    private String encryptBase64(final Object o) {
        if (o == null || o.equals("")
            || (o instanceof Map<?,?> && ((Map<?,?>)o).isEmpty())) {
            return null;
        } else {
            final byte[] bytes = javaSerializationHelper.serializeToBytes((Serializable) o);
            EncryptionResult result = cryptoService.encryptForCluster(this.clusterName, PAC4J_PASSWORD, bytes);
            return Base64.encodeBase64String(result.toByteAray());
        }
    }

    public void set(WebContext context, String key, Object value) {
        logger.debug("Save in session: {} = {}", key, value);
        final Cookie cookie = new Cookie(PAC4J_SESSION_PREFIX + key, encryptBase64(value));
        try {
            String domain = Urls.getDomainName(context.getFullRequestURL(), this.domainSuffix);
            if (domain == null) {
                domain = context.getServerName();
            }
            cookie.setDomain(domain);
        } catch (final Exception e) {
            throw new TechnicalException(e);
        }
        cookie.setHttpOnly(true);
        cookie.setSecure(ContextHelper.isHttpsOrSecure(context));
        context.addResponseCookie(cookie);
    }

    @Override
    public SessionStore buildFromTrackableSession(WebContext arg0, Object arg1) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean destroySession(WebContext arg0) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Object getTrackableSession(WebContext arg0) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean renewSession(WebContext arg0) {
        // TODO Auto-generated method stub
        return false;
    }
}
