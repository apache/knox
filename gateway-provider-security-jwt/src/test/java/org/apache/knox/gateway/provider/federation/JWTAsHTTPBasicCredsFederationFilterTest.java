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

import org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter;
import org.junit.Test;
import org.easymock.EasyMock;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import com.nimbusds.jwt.SignedJWT;
import javax.servlet.http.HttpServletRequest;
import org.junit.Before;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;

public class JWTAsHTTPBasicCredsFederationFilterTest extends AbstractJWTFilterTest
{
    @Before
    public void setUp() {
      handler = new TestJWTFederationFilter();
      ((TestJWTFederationFilter) handler).setTokenService(new TestJWTokenAuthority(publicKey));
    }

    @Override
    protected void setTokenOnRequest(final HttpServletRequest request, final SignedJWT jwt) {
        final String token = "Basic " + Base64.getEncoder().encodeToString(("Token:" + jwt.serialize()).getBytes(StandardCharsets.UTF_8));
        EasyMock.expect((Object)request.getHeader("Authorization")).andReturn((Object)token);
    }

    @Override
    protected void setGarbledTokenOnRequest(final HttpServletRequest request, final SignedJWT jwt) {
        final String token = "Basic " + Base64.getEncoder().encodeToString(("Token: ljm" + jwt.serialize()).getBytes(StandardCharsets.UTF_8));
        EasyMock.expect((Object)request.getHeader("Authorization")).andReturn((Object)token);
    }

    @Override
    protected String getAudienceProperty() {
        return "knox.token.audiences";
    }

    private static class TestJWTFederationFilter extends JWTFederationFilter implements TokenVerificationCounter {
      private int verifiedCount;

      void setTokenService(JWTokenAuthority ts) {
        authority = ts;
      }

      @Override
      protected void recordSignatureVerification(String tokenId) {
        super.recordSignatureVerification(tokenId);
        verifiedCount++;
      }

      @Override
      public int getVerificationCount() {
        return verifiedCount;
      }
    }

    @Override
    protected String getVerificationPemProperty() {
        return "knox.token.verification.pem";
    }

    @Test
    public void doTest() {
    }
}

