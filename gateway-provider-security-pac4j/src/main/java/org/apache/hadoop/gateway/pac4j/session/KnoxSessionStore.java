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

import org.apache.hadoop.gateway.services.security.token.JWTokenAuthority;
import org.apache.hadoop.gateway.util.Urls;
import org.pac4j.core.context.Cookie;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.util.JavaSerializationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.Collection;

public class KnoxSessionStore implements SessionStore {

    private final static Logger logger = LoggerFactory.getLogger(KnoxSessionStore.class);

    private final static String PAC4J_SESSION_PREFIX = "pac4j.session.";

    private final JWTokenAuthority authority;

    private final JavaSerializationHelper javaSerializationHelper;

    public KnoxSessionStore(final JWTokenAuthority authority) {
        this.authority = authority;
        javaSerializationHelper = new JavaSerializationHelper();
    }

    public String getOrCreateSessionId(WebContext context) {
        return null;
    }

    private Object getCookieObject(final WebContext context, final String key) {
        final Collection<Cookie> cookies = context.getRequestCookies();
        for (final Cookie cookie : cookies) {
            final String name = cookie.getName();
            if (name.equals(PAC4J_SESSION_PREFIX + key)) {
                final String value = cookie.getValue();
                if (value != null) {
                    return javaSerializationHelper.unserializeFromBase64(value);
                }
            }
        }
        return null;
    }

    public Object get(WebContext context, String key) {
        final Object value = getCookieObject(context, key);
        logger.debug("Get from session: {} = {}", key, value);
        return value;
    }

    public void set(WebContext context, String key, Object value) {
        logger.debug("Save in session: {} = {}", key, value);
        final Cookie cookie = new Cookie(PAC4J_SESSION_PREFIX + key, javaSerializationHelper.serializeToBase64((Serializable) value));
        try {
            cookie.setDomain(Urls.getDomainName(context.getFullRequestURL()));
        } catch (final URISyntaxException e) {
            throw new RuntimeException(e);
        }
        context.addResponseCookie(cookie);
    }
}
