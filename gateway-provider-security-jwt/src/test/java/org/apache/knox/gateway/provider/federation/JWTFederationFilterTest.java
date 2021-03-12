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

import com.nimbusds.jwt.SignedJWT;
import org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter;
import org.apache.knox.gateway.services.security.token.JWTokenAuthority;
import org.easymock.EasyMock;
import org.junit.Before;

import javax.servlet.http.HttpServletRequest;

public class JWTFederationFilterTest extends AbstractJWTFilterTest {
  @Before
  public void setUp() {
    handler = new TestJWTFederationFilter();
    ((TestJWTFederationFilter) handler).setTokenService(new TestJWTokenAuthority(publicKey));
  }

  @Override
  protected void setTokenOnRequest(HttpServletRequest request, SignedJWT jwt) {
    String token = "Bearer " + jwt.serialize();
    EasyMock.expect(request.getHeader("Authorization")).andReturn(token);
  }

  @Override
  protected void setGarbledTokenOnRequest(HttpServletRequest request, SignedJWT jwt) {
    String token = "Bearer " + "ljm" + jwt.serialize();
    EasyMock.expect(request.getHeader("Authorization")).andReturn(token);
  }

  @Override
  protected String getAudienceProperty() {
    return TestJWTFederationFilter.KNOX_TOKEN_AUDIENCES;
  }

  private static class TestJWTFederationFilter extends JWTFederationFilter implements TokenVerificationCounter {
    private int verificationCount;

    void setTokenService(final JWTokenAuthority ts) {
      authority = ts;
    }

    @Override
    protected void recordSignatureVerification(final String tokenId) {
      super.recordSignatureVerification(tokenId);
      verificationCount++;
    }

    @Override
    public int getVerificationCount() {
      return verificationCount;
    }
  }

  @Override
  protected String getVerificationPemProperty() {
    return TestJWTFederationFilter.TOKEN_VERIFICATION_PEM;
  }

}
