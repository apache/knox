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
package org.apache.knox.gateway.provider.federation;

import org.apache.knox.gateway.provider.federation.jwt.filter.AbstractJWTFilter;
import org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter;
import org.easymock.EasyMock;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;

import com.nimbusds.jwt.SignedJWT;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.fail;

public class JWTAsHTTPBasicCredsFederationFilterTest extends AbstractJWTFilterTest {

    @Before
    public void setUp() {
      handler = new TestJWTFederationFilter();
      ((TestJWTFederationFilter) handler).setTokenService(new TestJWTokenAuthority(publicKey));
    }

    @Override
    protected String getAudienceProperty() {
        return TestJWTFederationFilter.KNOX_TOKEN_AUDIENCES;
    }

    @Override
    protected String getVerificationPemProperty() {
        return TestJWTFederationFilter.TOKEN_VERIFICATION_PEM;
    }

    @Override
    protected void setTokenOnRequest(final HttpServletRequest request, final SignedJWT jwt) {
        setTokenOnRequest(request, TestJWTFederationFilter.TOKEN, jwt.serialize());
    }

    @Override
    protected void setGarbledTokenOnRequest(final HttpServletRequest request, final SignedJWT jwt) {
        setTokenOnRequest(request, TestJWTFederationFilter.TOKEN, "ljm" + jwt.serialize());
    }

    /**
     * Bind the specified JWT to the specified request, and apply the specified authUsername as the HTTP Basic
     * username in the Authorization header value.
     *
     * @param request      The request to which the JWT should be bound.
     * @param authUsername The HTTP Basic auth username to apply in the Authorization header value.
     * @param authPassword The HTTP Basic auth password to apply in the Authorization header value.
     */
    protected void setTokenOnRequest(final HttpServletRequest request,
                                     final String             authUsername,
                                     final String             authPassword) {
        final byte[] basicAuth = (authUsername + ":" + authPassword).getBytes(StandardCharsets.UTF_8);
        final String authHeaderValue = "Basic " + Base64.getEncoder().encodeToString(basicAuth);
        EasyMock.expect((Object)request.getHeader("Authorization")).andReturn(authHeaderValue);
    }

    @Test
    public void testAlternativeCaseUsername() throws Exception {
        try {
            Properties props = getProperties();
            handler.init(new TestFilterConfig(props));

            SignedJWT jwt = getJWT(AbstractJWTFilter.JWT_DEFAULT_ISSUER, "bob",
                                   new Date(new Date().getTime() + 5000), privateKey);

            HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
            setTokenOnRequest(request, JWTFederationFilter.TOKEN.toLowerCase(Locale.ROOT), jwt.serialize());

            EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
            EasyMock.expect(request.getQueryString()).andReturn(null);
            HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
            EasyMock.expect(response.encodeRedirectURL(SERVICE_URL)).andReturn(SERVICE_URL);
            EasyMock.expect(response.getOutputStream()).andAnswer(DummyServletOutputStream::new).anyTimes();
            EasyMock.replay(request, response);

            TestFilterChain chain = new TestFilterChain();
            handler.doFilter(request, response, chain);
            Assert.assertTrue("doFilterCalled should be true.", chain.doFilterCalled );
        } catch (ServletException se) {
            fail("Should NOT have thrown a ServletException.");
        }
    }

}

