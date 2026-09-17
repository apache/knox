/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.service.knoxidf;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.easymock.EasyMock;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TokenResourceExpiresInTest {

    private static final String EXPIRES_IN = "expires_in";
    private static final String ISSUED_TOKEN_TYPE = "issued_token_type";
    private static final String ISSUED_TOKEN_TYPE_JWT_VALUE = "urn:ietf:params:oauth:token-type:jwt";
    private static final long ONE_HOUR_SECONDS = 3600L;
    private static final long ONE_HOUR_MS = 3_600_000L;
    private static final String TOKEN_ID = "11111111-2222-3333-4444-555555555555";

    static final class TestableTokenResource extends TokenResource {
        private long lifetimeSeconds;

        void configure(final long lifetimeSeconds, final HttpServletRequest request) throws Exception {
            this.lifetimeSeconds = lifetimeSeconds;
            this.request = request;
            setParentContext(EasyMock.createNiceMock(ServletContext.class));
        }

        @Override
        protected long getTokenLifetimeInSeconds() {
            return lifetimeSeconds;
        }

        Map<String, Object> responseMap(final JWT token, final long expires) throws TokenServiceException {
            return buildResponseMap(token, expires).map;
        }

        private void setParentContext(final ServletContext context) throws Exception {
            final Field field = org.apache.knox.gateway.service.knoxtoken.TokenResource.class.getDeclaredField("context");
            field.setAccessible(true);
            field.set(this, context);
        }
    }

    private static HttpServletRequest clientCredentialsRequest() {
        final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
        EasyMock.expect(request.getParameter("grant_type")).andReturn("client_credentials").anyTimes();
        EasyMock.replay(request);
        return request;
    }

    private static JWT jwt() {
        final JWT token = EasyMock.createNiceMock(JWT.class);
        EasyMock.expect(token.getClaim(JWTToken.KNOX_ID_CLAIM)).andReturn(TOKEN_ID).anyTimes();
        EasyMock.replay(token);
        return token;
    }

    @Test
    public void testExpiresInIsRelativeSecondsNotAbsoluteEpochMs() throws Exception {
        final TestableTokenResource resource = new TestableTokenResource();
        resource.configure(ONE_HOUR_SECONDS, clientCredentialsRequest());
        final long absoluteExpiry = System.currentTimeMillis() + ONE_HOUR_MS;

        final Object expiresIn = resource.responseMap(jwt(), absoluteExpiry).get(EXPIRES_IN);

        assertEquals("expires_in must be the relative lifetime in seconds, not epoch-ms.",
                ONE_HOUR_SECONDS, expiresIn);
        assertNotEquals("expires_in must not be the absolute epoch-ms expiry.", absoluteExpiry, expiresIn);
        assertTrue("expires_in must be a small relative value, not an epoch-ms timestamp.",
                ((Number) expiresIn).longValue() < absoluteExpiry / 1000);
    }

    @Test
    public void testIssuedTokenTypeIsAdvertised() throws Exception {
        final TestableTokenResource resource = new TestableTokenResource();
        resource.configure(ONE_HOUR_SECONDS, clientCredentialsRequest());

        final Object issuedTokenType =
                resource.responseMap(jwt(), System.currentTimeMillis() + ONE_HOUR_MS).get(ISSUED_TOKEN_TYPE);

        assertEquals("RFC 8693 §2.2.1 requires the issued_token_type of the minted JWT.",
                ISSUED_TOKEN_TYPE_JWT_VALUE, issuedTokenType);
    }

    @Test
    public void testExpiresInIsOmittedForUnlimitedLifetime() throws Exception {
        final TestableTokenResource resource = new TestableTokenResource();
        resource.configure(-1L, clientCredentialsRequest());

        final Map<String, Object> map = resource.responseMap(jwt(), System.currentTimeMillis() + ONE_HOUR_MS);

        assertFalse("expires_in is OPTIONAL (RFC 8693 §2.2.1) and must be omitted for an unlimited-lifetime token.",
                map.containsKey(EXPIRES_IN));
    }
}
