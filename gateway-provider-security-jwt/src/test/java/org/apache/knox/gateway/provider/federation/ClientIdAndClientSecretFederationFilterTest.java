/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.provider.federation;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter;
import org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter.TokenType;
import org.apache.knox.test.mock.MockServletInputStream;
import org.easymock.EasyMock;
import org.junit.Test;


public class ClientIdAndClientSecretFederationFilterTest extends TokenIDAsHTTPBasicCredsFederationFilterTest {
    @Override
    protected void setTokenOnRequest(HttpServletRequest request, String authUsername, String authPassword) {
        EasyMock.expect((Object)request.getHeader("Authorization")).andReturn("");
        try {
          EasyMock.expect(request.getInputStream()).andAnswer(() -> produceServletInputStream(authPassword)).atLeastOnce();
        } catch (IOException e) {
          throw new RuntimeException("Error while setting up expectation for getting client credentials from request body", e);
        }
    }

    private ServletInputStream produceServletInputStream(String clientSecret) {
      final String requestBody = JWTFederationFilter.GRANT_TYPE + "=" + JWTFederationFilter.CLIENT_CREDENTIALS + "&" + JWTFederationFilter.CLIENT_SECRET + "="
          + clientSecret;
      final InputStream inputStream = IOUtils.toInputStream(requestBody, StandardCharsets.UTF_8);
      return new MockServletInputStream(inputStream);
    }

    @Test
    public void testGetWireTokenUsingClientCredentialsFlow() throws Exception {
      final String clientSecret = "sup3r5ecreT!";
      final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
      EasyMock.expect(request.getInputStream()).andAnswer(() -> produceServletInputStream(clientSecret)).atLeastOnce();
      EasyMock.replay(request);

      handler.init(new TestFilterConfig(getProperties()));
      final Pair<TokenType, String> wireToken = ((TestJWTFederationFilter) handler).getWireToken(request);

      EasyMock.verify(request);

      assertNotNull(wireToken);
      assertEquals(TokenType.Passcode, wireToken.getLeft());
      assertEquals(clientSecret, wireToken.getRight());
    }

    @Test(expected = SecurityException.class)
    public void shouldFailIfClientSecretIsPassedInQueryParams() throws Exception {
      final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
      EasyMock.expect((Object)request.getParameter("client_secret")).andReturn("sup3r5ecreT!");
      EasyMock.replay(request);

      handler.init(new TestFilterConfig(getProperties()));
      ((TestJWTFederationFilter) handler).getWireToken(request);
    }

    @Override
    @Test
    public void testInvalidUsername() throws Exception {
        // there is no way to specify an invalid username for
        // client credentials flow or at least no meaningful way
        // to do so for our implementation. The client id is
        // actually encoded in the client secret and that is used
        // for the actual authentication with passcodes.
    }

    @Override
    @Test
    public void testInvalidJWTForPasscode() throws Exception {
        // there is no way to specify an invalid username for
        // client credentials flow or at least no meaningful way
        // to do so for our implementation. The username is actually
        // set by the JWTProvider when determining that the request
        // is a client credentials flow.
    }
}
