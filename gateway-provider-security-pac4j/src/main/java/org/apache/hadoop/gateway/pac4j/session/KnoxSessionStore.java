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
import org.pac4j.core.context.Cookie;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.core.util.JavaSerializationHelper;

import java.util.Collection;
import java.util.HashMap;

public class KnoxSessionStore implements SessionStore {

    private final static String PAC4J_SESSION = "pac4j-session-jwt";

    private final JWTokenAuthority authority;

    private final JavaSerializationHelper javaSerializationHelper;

    public KnoxSessionStore(final JWTokenAuthority authority) {
        this.authority = authority;
        javaSerializationHelper = new JavaSerializationHelper();
    }

    public String getOrCreateSessionId(WebContext context) {
        return PAC4J_SESSION;
    }

    private HashMap<String, Object> getCookie(final WebContext context) {
        final Collection<Cookie> cookies = context.getRequestCookies();
        for (final Cookie cookie : cookies) {
            final String name = cookie.getName();
            if (name.equals(PAC4J_SESSION)) {
                final String value = cookie.getValue();
                if (value != null) {
                    return (HashMap<String, Object>) javaSerializationHelper.unserializeFromBase64(value);
                }
            }
        }
        return new HashMap<>();
    }

    public Object get(WebContext context, String key) {
        return getCookie(context).get(key);
    }

    public void set(WebContext context, String key, Object value) {
        final HashMap<String, Object> data = getCookie(context);
        data.put(key, value);
        context.addResponseCookie(new Cookie(PAC4J_SESSION, javaSerializationHelper.serializeToBase64(data)));
    }
}
