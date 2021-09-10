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
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("PMD.TestClassWithoutTestCases")
public class JWTFederationFilterTest extends AbstractJWTFilterTest {
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
  protected void setTokenOnRequest(HttpServletRequest request, SignedJWT jwt) {
    String token = TestJWTFederationFilter.BEARER + " " + jwt.serialize();
    EasyMock.expect(request.getHeader("Authorization")).andReturn(token);
  }

  @Override
  protected void setGarbledTokenOnRequest(HttpServletRequest request, SignedJWT jwt) {
    String token = TestJWTFederationFilter.BEARER + " ljm" + jwt.serialize();
    EasyMock.expect(request.getHeader("Authorization")).andReturn(token);
  }

  @Test
  public void testMissingTokenValue() throws Exception {
    handler.init(new TestFilterConfig(getProperties()));

    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
    EasyMock.expect(request.getHeader("Authorization")).andReturn("Basic VG9rZW46");
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    EasyMock.expectLastCall().once();
    EasyMock.replay(request, response);

    TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    EasyMock.verify(response);
  }
}
