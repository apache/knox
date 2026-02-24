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


import org.apache.commons.lang3.tuple.Pair;
import org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter;
import org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter.TokenType;
import org.apache.knox.gateway.provider.federation.jwt.filter.SignatureVerificationCache;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


public class OAuthFlowsFederationFilterTest extends TokenIDAsHTTPBasicCredsFederationFilterTest {
    @Override
    protected void setTokenOnRequest(HttpServletRequest request, String authUsername, String authPassword) {
        EasyMock.expect((Object)request.getHeader("Authorization")).andReturn("");
        EasyMock.expect((Object)request.getContentType()).andReturn("application/x-www-form-urlencoded").anyTimes();
        ensureClientCredentials(request, authPassword, false);
    }

    private void ensureClientCredentials(final HttpServletRequest request, final String clientSecret) {
        ensureClientCredentials(request, clientSecret, true);
    }

    private void ensureClientCredentials(final HttpServletRequest request, final String clientSecret, final boolean excludeQueryString) {
        EasyMock.expect(request.getParameter(JWTFederationFilter.GRANT_TYPE)).andReturn(JWTFederationFilter.CLIENT_CREDENTIALS).anyTimes();
        EasyMock.expect(request.getParameter(JWTFederationFilter.CLIENT_SECRET)).andReturn(clientSecret).anyTimes();
        if (excludeQueryString) {
            EasyMock.expect(request.getQueryString()).andReturn(null).anyTimes();
        }
    }

    @Test
    public void testGetWireTokenUsingClientCredentialsFlow() throws Exception {
      final String clientId = "client-id-12345";
      final String passcode = "WTJ4cFpXNTBMV2xrTFRFeU16UTE6OlkyeHBaVzUwTFhObFkzSmxkQzB4TWpNME5RPT0=";

      final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
      ensureClientCredentials(request, passcode);
      EasyMock.expect(request.getHeader("Authorization")).andReturn(null).anyTimes();
      EasyMock.expect(request.getParameter(JWTFederationFilter.CLIENT_ID)).andReturn(clientId).anyTimes();
      EasyMock.replay(request);
      handler.init(new TestFilterConfig(getProperties()));
      final Pair<TokenType, String> wireToken = ((TestJWTFederationFilter) handler).getWireToken(request);

      EasyMock.verify(request);

      assertNotNull(wireToken);
      assertEquals(TokenType.Passcode, wireToken.getLeft());
      assertEquals(passcode, wireToken.getRight());
    }

    @Test
    public void testGetWireTokenUsingClientCredentialsBasicAuth() throws Exception {
        final String clientId = "client-id-12345";
        final String passcode = "WTJ4cFpXNTBMV2xrTFRFeU16UTE6OlkyeHBaVzUwTFhObFkzSmxkQzB4TWpNME5RPT0=";
        final String basicCredentials = Base64.getEncoder()
                .encodeToString((clientId + ":" + passcode).getBytes(StandardCharsets.UTF_8));

        final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
        EasyMock.expect(request.getHeader("Authorization")).andReturn("Basic " + basicCredentials).anyTimes();
        EasyMock.expect(request.getParameter(JWTFederationFilter.GRANT_TYPE)).andReturn(JWTFederationFilter.CLIENT_CREDENTIALS).anyTimes();
        EasyMock.replay(request);

        handler.init(new TestFilterConfig(getProperties()));
        final Pair<TokenType, String> wireToken = ((TestJWTFederationFilter) handler).getWireToken(request);

        EasyMock.verify(request);

        assertNotNull(wireToken);
        assertEquals(TokenType.Passcode, wireToken.getLeft());
        assertEquals(passcode, wireToken.getRight());
    }

    @Test
    public void testVerifyClientCredentialsFlow() throws Exception {
        final String topologyName = "jwt-topology";
        final String tokenId = "4e0c548b-6568-4061-a3dc-62908087650a";
        final String passcode = "0138aaed-ca2a-47f1-8ed8-e0c397596f95";
        final String passcodeToken = "TkdVd1l6VTBPR0l0TmpVMk9DMDBNRFl4TFdFelpHTXROakk1TURnd09EYzJOVEJoOjpNREV6T0dGaFpXUXRZMkV5WVMwME4yWXhMVGhsWkRndFpUQmpNemszTlRrMlpqazE=";

        final Pair<TokenStateService, TokenMetadata> tokenServices = createMockTokenStateService(tokenId, passcodeToken);
        final FilterConfig filterConfig = initFilter(tokenServices.getLeft());

        final HttpServletRequest request = createMockRequestForClientCredentials(tokenId);
        final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
        EasyMock.replay(tokenServices.getLeft(), tokenServices.getRight(), request, response);

        SignatureVerificationCache.getInstance(topologyName, filterConfig).recordSignatureVerification(passcode);

        final TestFilterChain chain = new TestFilterChain();
        handler.doFilter(request, response, chain);

        EasyMock.verify(response);
        Assert.assertTrue(chain.doFilterCalled);
        Assert.assertNotNull(chain.subject);
    }

    @Test(expected = SecurityException.class)
    public void testFailedVerifyClientCredentialsFlow() throws Exception {
        final String topologyName = "jwt-topology";
        final String tokenId = "4e0c548b-6568-4061-a3dc-62908087650a";
        final String passcode = "0138aaed-ca2a-47f1-8ed8-e0c397596f95";
        final String passcodeToken = "TkdVd1l6VTBPR0l0TmpVMk9DMDBNRFl4TFdFelpHTXROakk1TURnd09EYzJOVEJoOjpNREV6T0dGaFpXUXRZMkV5WVMwME4yWXhMVGhsWkRndFpUQmpNemszTlRrMlpqazE=";

        final Pair<TokenStateService, TokenMetadata> tokenServices = createMockTokenStateService(tokenId, passcodeToken);
        final FilterConfig filterConfig = initFilter(tokenServices.getLeft());

        final HttpServletRequest request = createMockRequestForClientCredentials(tokenId + "invalidating_string");

        final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                JWTFederationFilter.MISMATCHING_CLIENT_ID_AND_CLIENT_SECRET);
        EasyMock.expectLastCall().once();
        EasyMock.replay(tokenServices.getLeft(), tokenServices.getRight(), request, response);

        SignatureVerificationCache.getInstance(topologyName, filterConfig).recordSignatureVerification(passcode);

        final TestFilterChain chain = new TestFilterChain();
        handler.doFilter(request, response, chain);

        EasyMock.verify(response);
        Assert.assertFalse(chain.doFilterCalled);
        Assert.assertNull(chain.subject);
    }

    @Test(expected = SecurityException.class)
    public void shouldFailIfClientSecretIsPassedInQueryParams() throws Exception {
      final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
      final String queryString = "test=test&client_secret=sup3r5ecreT&otherTest=otherTestQueryParam";
      EasyMock.expect(request.getQueryString()).andReturn(queryString).anyTimes();
      EasyMock.replay(request);

      handler.init(new TestFilterConfig(getProperties()));
      ((TestJWTFederationFilter) handler).getWireToken(request);
    }

    @Test(expected = SecurityException.class)
    public void testInvalidClientSecret() throws Exception {
        final String passcode = "sUpers3cret!";

        final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
        ensureClientCredentials(request, passcode);
        EasyMock.expect(request.getHeader("Authorization")).andReturn(null).anyTimes();
        EasyMock.replay(request);
        handler.init(new TestFilterConfig(getProperties()));
        ((TestJWTFederationFilter) handler).getWireToken(request);

        EasyMock.verify(request);
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

    @Override
    @Test
    public void testInvalidPasscodeForJWT() throws Exception {
    }

    @Override
    @Test
    public void testUnableToParseJWT() throws Exception {
    }

    @Test
    public void testGetWireTokenUsingRefreshTokenFlow() throws Exception {
      final String refreshToken = "WTJ4cFpXNTBMV2xrTFRFeU16UTE6OlkyeHBaVzUwTFhObFkzSmxkQzB4TWpNME5RPT0=";
      testGetWireTokenWithGrant(JWTFederationFilter.REFRESH_TOKEN, JWTFederationFilter.REFRESH_TOKEN_PARAM, refreshToken);
    }

    @Test
    public void testGetWireTokenUsingTokenExchangeFlow() throws Exception {
      final String subjectToken = "WTJ4cFpXNTBMV2xrTFRFeU16UTE2OlkyeHBaVzUwTFhObFkzSmxkQzB4TWpNME5RPT0=";
      testGetWireTokenWithGrant(JWTFederationFilter.TOKEN_EXCHANGE, JWTFederationFilter.SUBJECT_TOKEN, subjectToken);
    }

    @Test
    public void testVerifyRefreshTokenFlow() throws Exception {
        final String tokenId = "4e0c548b-6568-4061-a3dc-62908087650b";
        final String passcode = "0138aaed-ca2a-47f1-8ed8-e0c397596f96";
        final String passcodeToken = "TkdVd1l6VTBPR0l0TmpVMk9DMDBNRFl4TFdFelpHTXROakk1TURnd09EYzJOVEJpOjpNREV6T0dGaFpXUXRZMkV5WVMwME4yWXhMVGhsWkRndFpUQmpNemszTlRrMlpqazI=";
        testVerifyTokenWithGrant(tokenId, passcode, passcodeToken, JWTFederationFilter.REFRESH_TOKEN, JWTFederationFilter.REFRESH_TOKEN_PARAM);
    }

    @Test
    public void testVerifyTokenExchangeFlow() throws Exception {
        final String tokenId = "4e0c548b-6568-4061-a3dc-62908087650c";
        final String passcode = "0138aaed-ca2a-47f1-8ed8-e0c397596f97";
        final String passcodeToken = "TkdVd1l6VTBPR0l0TmpVMk9DMDBNRFl4TFdFelpHTXROakk1TURnd09EYzJOVEJqOjpNREV6T0dGaFpXUXRZMkV5WVMwME4yWXhMVGhsWkRndFpUQmpNemszTlRrMlpqazM=";
        testVerifyTokenWithGrant(tokenId, passcode, passcodeToken, JWTFederationFilter.TOKEN_EXCHANGE, JWTFederationFilter.SUBJECT_TOKEN);
    }

    private Pair<TokenStateService, TokenMetadata> createMockTokenStateService(String tokenId, String passcodeToken) throws UnknownTokenException {
        final TokenStateService tokenStateService = EasyMock.createNiceMock(TokenStateService.class);
        EasyMock.expect(tokenStateService.getTokenExpiration(tokenId)).andReturn(Long.MAX_VALUE).anyTimes();

        final TokenMetadata tokenMetadata = EasyMock.createNiceMock(TokenMetadata.class);
        EasyMock.expect(tokenMetadata.isEnabled()).andReturn(true).anyTimes();
        EasyMock.expect(tokenMetadata.getPasscode()).andReturn(passcodeToken).anyTimes();
        EasyMock.expect(tokenStateService.getTokenMetadata(EasyMock.anyString())).andReturn(tokenMetadata).anyTimes();
        return Pair.of(tokenStateService, tokenMetadata);
    }

    private FilterConfig initFilter(TokenStateService tokenStateService) throws Exception {
        final Properties filterConfigProps = getProperties();
        filterConfigProps.put(TokenStateService.CONFIG_SERVER_MANAGED, Boolean.toString(true));
        filterConfigProps.put(TestFilterConfig.TOPOLOGY_NAME_PROP, "jwt-topology");
        final FilterConfig filterConfig = new TestFilterConfig(filterConfigProps, tokenStateService);
        handler.init(filterConfig);
        return filterConfig;
    }

    private HttpServletRequest createMockRequestForClientCredentials(String clientId) {
        final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
        final String clientCredentials = "TkdVd1l6VTBPR0l0TmpVMk9DMDBNRFl4TFdFelpHTXROakk1TURnd09EYzJOVEJoOjpNREV6T0dGaFpXUXRZMkV5WVMwME4yWXhMVGhsWkRndFpUQmpNemszTlRrMlpqazE=";
        EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
        ensureClientCredentials(request, clientCredentials);
        EasyMock.expect(request.getParameter("client_id")).andReturn(clientId).anyTimes();
        return request;
    }

    private void testGetWireTokenWithGrant(String grantType, String tokenParam, String tokenValue) throws Exception {
          final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
          EasyMock.expect(request.getHeader("Authorization")).andReturn(null).anyTimes();
          EasyMock.expect(request.getQueryString()).andReturn(null).anyTimes();
          EasyMock.expect(request.getParameter(JWTFederationFilter.GRANT_TYPE)).andReturn(grantType).anyTimes();
          EasyMock.expect(request.getParameter(tokenParam)).andReturn(tokenValue).anyTimes();
          EasyMock.replay(request);

          handler.init(new TestFilterConfig(getProperties()));
          final Pair<TokenType, String> wireToken = ((TestJWTFederationFilter) handler).getWireToken(request);

          EasyMock.verify(request);

          assertNotNull(wireToken);
          assertEquals(TokenType.Passcode, wireToken.getLeft());
          assertEquals(tokenValue, wireToken.getRight());
    }

    private void testVerifyTokenWithGrant(String tokenId, String passcode, String passcodeToken, String grantType, String tokenParam) throws Exception {
        final Pair<TokenStateService, TokenMetadata> tokenServices = createMockTokenStateService(tokenId, passcodeToken);
        final TokenStateService tokenStateService = tokenServices.getLeft();
        final TokenMetadata tokenMetadata = tokenServices.getRight();

        final FilterConfig filterConfig = initFilter(tokenStateService);

        final HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
        EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
        EasyMock.expect(request.getHeader("Authorization")).andReturn(null).anyTimes();
        EasyMock.expect(request.getParameter(JWTFederationFilter.GRANT_TYPE)).andReturn(grantType).anyTimes();
        EasyMock.expect(request.getParameter(tokenParam)).andReturn(passcodeToken).anyTimes();
        EasyMock.expect(request.getQueryString()).andReturn(null).anyTimes();

        final HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
        EasyMock.replay(tokenStateService, tokenMetadata, request, response);

        SignatureVerificationCache.getInstance("jwt-topology", filterConfig).recordSignatureVerification(passcode);

        final TestFilterChain chain = new TestFilterChain();
        handler.doFilter(request, response, chain);

        EasyMock.verify(response);
        Assert.assertTrue(chain.doFilterCalled);
        Assert.assertNotNull(chain.subject);
    }
}
