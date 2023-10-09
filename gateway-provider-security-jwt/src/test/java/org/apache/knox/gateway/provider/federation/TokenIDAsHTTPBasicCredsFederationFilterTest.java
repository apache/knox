/*
 *
 *  * Licensed to the Apache Software Foundation (ASF) under one or more
 *  * contributor license agreements. See the NOTICE file distributed with this
 *  * work for additional information regarding copyright ownership. The ASF
 *  * licenses this file to you under the Apache License, Version 2.0 (the
 *  * "License"); you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  * License for the specific language governing permissions and limitations under
 *  * the License.
 *
 */
package org.apache.knox.gateway.provider.federation;

import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.provider.federation.jwt.filter.AbstractJWTFilter;
import org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.token.KnoxToken;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.services.security.token.impl.TokenMAC;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

import com.nimbusds.jwt.SignedJWT;

@SuppressWarnings({"PMD.JUnit4TestShouldUseBeforeAnnotation", "PMD.JUnit4TestShouldUseTestAnnotation"})
public class TokenIDAsHTTPBasicCredsFederationFilterTest extends JWTAsHTTPBasicCredsFederationFilterTest {

    TestTokenStateService tss;
    TokenMAC tokenMAC;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        tss = new TestTokenStateService();
        ((TestJWTFederationFilter) handler).setTokenStateService(tss);
        tokenMAC = new TokenMAC(HmacAlgorithms.HMAC_SHA_256.getName(), "sPj8FCgQhCEi6G18kBfpswxYSki33plbelGLs0hMSbk".toCharArray());
        ((TestJWTFederationFilter) handler).setTokenMac(tokenMAC);
    }

    @Override
    protected void setTokenOnRequest(final HttpServletRequest request, final SignedJWT jwt) {
      try {
        final long issueTime = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5);
        final String subject = (String) jwt.getJWTClaimsSet().getClaim(JWTToken.SUBJECT);
        final String passcode = (String) jwt.getJWTClaimsSet().getClaims().get(PASSCODE_CLAIM);
        addTokenState(jwt, issueTime, subject, passcode);
        setTokenOnRequest(request, TestJWTFederationFilter.PASSCODE, generatePasscodeField(getTokenId(jwt), passcode));
      } catch(ParseException e) {
        Assert.fail(e.getMessage());
      }
    }

    private String generatePasscodeField(String tokenId, String passcode) {
      final String base64TokenIdPasscode = Base64.encodeBase64String(tokenId.getBytes(StandardCharsets.UTF_8)) + "::" + Base64.encodeBase64String(passcode.getBytes(StandardCharsets.UTF_8));
      return Base64.encodeBase64String(base64TokenIdPasscode.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Bind the specified JWT to the specified request, and apply the specified authUsername as the HTTP Basic
     * username in the Authorization header value.
     *
     * @param request      The request to which the JWT should be bound.
     * @param jwt          The JWT
     * @param authUsername The HTTP Basic auth username to apply in the Authorization header value.
     */
    protected void setTokenOnRequest(final HttpServletRequest request,
                                     final SignedJWT          jwt,
                                     final String             authUsername) {
      try {
        final long issueTime = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5);
        final String subject = (String) jwt.getJWTClaimsSet().getClaim(JWTToken.SUBJECT);
        final String passcode = (String) jwt.getJWTClaimsSet().getClaims().get(PASSCODE_CLAIM);
        addTokenState(jwt, issueTime, subject, passcode);
        setTokenOnRequest(request, authUsername, generatePasscodeField(getTokenId(jwt), passcode));
      } catch(ParseException e) {
        Assert.fail(e.getMessage());
      }
    }

    @Override
    protected void setGarbledTokenOnRequest(final HttpServletRequest request, final SignedJWT jwt) {
        setTokenOnRequest(request, TestJWTFederationFilter.PASSCODE, "junk" + getTokenId(jwt));
    }

    private String getTokenId(final SignedJWT jwt) {
        String tokenId = null;
        try {
             tokenId = (String) jwt.getJWTClaimsSet().getClaim(JWTToken.KNOX_ID_CLAIM);
        } catch (ParseException e) {
            //
        }
        return tokenId;
    }

    private void addTokenState(final SignedJWT jwt, long issueTime, String subject, String passcode) {
        try {
            JWTToken token = new JWTToken(jwt.serialize());
            tss.addToken(token, issueTime);

            final TokenMetadata metadata = new TokenMetadata(subject);
            metadata.setPasscode(tokenMAC.hash(TokenUtils.getTokenId(token), issueTime, subject, passcode));
            tss.addMetadata(TokenUtils.getTokenId(token), metadata);
        } catch (ParseException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Override
    public void testAlternativeCaseUsername() throws Exception {
        try {
            Properties props = getProperties();
            handler.init(new TestFilterConfig(props));

            SignedJWT jwt = getJWT(AbstractJWTFilter.JWT_DEFAULT_ISSUER, "bob",
                    new Date(new Date().getTime() + 5000), privateKey);

            HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
            setTokenOnRequest(request, jwt, JWTFederationFilter.PASSCODE.toLowerCase(Locale.ROOT));

            EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
            EasyMock.expect(request.getPathInfo()).andReturn("resource").anyTimes();
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

    @Test
    public void testInvalidUsername() throws Exception {
        try {
            Properties props = getProperties();
            handler.init(new TestFilterConfig(props));

            SignedJWT jwt = getJWT(AbstractJWTFilter.JWT_DEFAULT_ISSUER, "bob",
                                   new Date(new Date().getTime() + 5000), privateKey);

            HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
            setTokenOnRequest(request, jwt, "InvalidBasicAuthUsername");

            EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
            EasyMock.expect(request.getPathInfo()).andReturn("resource").anyTimes();
            EasyMock.expect(request.getQueryString()).andReturn(null);
            HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
            EasyMock.expect(response.encodeRedirectURL(SERVICE_URL)).andReturn(SERVICE_URL);
            EasyMock.expect(response.getOutputStream()).andAnswer(DummyServletOutputStream::new).anyTimes();
            EasyMock.replay(request, response);

            TestFilterChain chain = new TestFilterChain();
            handler.doFilter(request, response, chain);
            Assert.assertFalse("doFilterCalled should be false.", chain.doFilterCalled );
        } catch (ServletException se) {
            fail("Should NOT have thrown a ServletException.");
        }
    }

    /**
     * Negative test, specifying the Basic auth username as the one used for JWTs while specifying
     * the passcode as the password.
     */
    @Test
    public void testInvalidJWTForPasscode() throws Exception {
        try {
            Properties props = getProperties();
            handler.init(new TestFilterConfig(props));

            SignedJWT jwt = getJWT(AbstractJWTFilter.JWT_DEFAULT_ISSUER, "bob",
                            new Date(new Date().getTime() + 5000), privateKey);

            HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
            setTokenOnRequest(request, jwt, JWTFederationFilter.TOKEN);

            EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
            EasyMock.expect(request.getPathInfo()).andReturn("resource").anyTimes();
            EasyMock.expect(request.getQueryString()).andReturn(null);
            HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
            EasyMock.expect(response.encodeRedirectURL(SERVICE_URL)).andReturn(SERVICE_URL);
            EasyMock.expect(response.getOutputStream()).andAnswer(DummyServletOutputStream::new).anyTimes();
            EasyMock.replay(request, response);

            TestFilterChain chain = new TestFilterChain();
            handler.doFilter(request, response, chain);
            Assert.assertFalse("doFilterCalled should be false.", chain.doFilterCalled );
        } catch (ServletException se) {
            fail("Should NOT have thrown a ServletException.");
        }
    }

    /**
     * Negative test, specifying the Basic auth username as the one used for passcodes while specifying the JWT
     * as the password.
     */
    @Test
    public void testInvalidPasscodeForJWT() throws Exception {
        try {
            Properties props = getProperties();
            handler.init(new TestFilterConfig(props));

            SignedJWT jwt = getJWT(AbstractJWTFilter.JWT_DEFAULT_ISSUER, "bob",
                                   new Date(new Date().getTime() + 5000), privateKey);

            HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
            setTokenOnRequest(request, JWTFederationFilter.PASSCODE, jwt.serialize());

            EasyMock.expect(request.getRequestURL()).andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
            EasyMock.expect(request.getPathInfo()).andReturn("resource").anyTimes();
            EasyMock.expect(request.getQueryString()).andReturn(null);
            HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
            EasyMock.expect(response.encodeRedirectURL(SERVICE_URL)).andReturn(SERVICE_URL);
            EasyMock.expect(response.getOutputStream()).andAnswer(DummyServletOutputStream::new).anyTimes();
            EasyMock.replay(request, response);

            TestFilterChain chain = new TestFilterChain();
            handler.doFilter(request, response, chain);
            Assert.assertFalse("doFilterCalled should be false.", chain.doFilterCalled );
            Assert.assertNull("Subject should be null since authentication should have failed.", chain.subject);
        } catch (ServletException se) {
            fail("Should NOT have thrown a ServletException.");
        }
    }

    @Override
    public void testJWTWithoutKnoxUUIDClaim() throws Exception {
        // Override to disable N/A test
    }

    @Override
    public void testExpiredJWTWithoutKnoxUUIDClaim() throws Exception {
        // Override to disable N/A test
    }

    @Override
    public void testInvalidAudienceJWT() throws Exception {
        // Override to disable N/A test
    }

    @Override
    public void testNoTokenAudience() throws Exception {
        // Override to disable N/A test
    }

    @Override
    public void testNoAudienceConfigured() throws Exception {
        // Override to disable N/A test
    }

    @Override
    public void testEmptyAudienceConfigured() throws Exception {
        // Override to disable N/A test
    }

    @Override
    public void testValidAudienceJWT() throws Exception {
        // Override to disable N/A test
    }

    @Override
    public void testValidAudienceJWTWhitespace() throws Exception {
        // Override to disable N/A test
    }

    @Override
    public void testValidIssuerViaConfig() throws Exception {
        // Override to disable N/A test
    }

    @Override
    public void testInvalidIssuer() throws Exception {
        // Override to disable N/A test
    }

    @Override
    public void testFailedSignatureValidationJWT() throws Exception {
        // Override to disable N/A test
    }

    @Override
    public void testInvalidVerificationPEM() throws Exception {
        // Override to disable N/A test
    }

    @Override
    public void testRS512SignatureAlgorithm() throws Exception {
        // Override to disable N/A test
    }

    @Override
    public void testInvalidSignatureAlgorithm() throws Exception {
        // Override to disable N/A test
    }

    @Override
    public void testValidVerificationPEM() throws Exception {
        // Override to disable N/A test
    }

    @Override
    public void testNotBeforeJWT() throws Exception {
        // Override to disable N/A test
    }

    @Override
    public void testExpiredTokensEvictedFromSignatureVerificationCache() throws Exception {
        // Override to disable N/A test
    }

    @Override
    public void testVerificationOptimization_NoTokenID() throws Exception {
        // Override to disable N/A test
    }

    /**
     * Very basic TokenStateService implementation for these tests only
     */
    private static class TestTokenStateService implements TokenStateService {

        private final Map<String, Long> tokenIssueTimes = new ConcurrentHashMap<>();
        private final Map<String, Long> tokenExpirations = new ConcurrentHashMap<>();
        private final Map<String, TokenMetadata> tokenMetadata = new ConcurrentHashMap<>();

        @Override
        public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
        }

        @Override
        public void start() throws ServiceLifecycleException {
        }

        @Override
        public void stop() throws ServiceLifecycleException {
        }

        @Override
        public long getDefaultRenewInterval() {
            return TimeUnit.DAYS.toMillis(1);
        }

        @Override
        public long getDefaultMaxLifetimeDuration() {
            return TimeUnit.DAYS.toMillis(7);
        }

        @Override
        public void addToken(JWTToken token, long issueTime) {
            String expiration = token.getExpires();
            if (expiration == null || expiration.isEmpty()) {
                expiration = "0";
            }
            addToken(TokenUtils.getTokenId(token), issueTime, Long.parseLong(expiration));
        }

        private void addToken(String tokenId, long expiration) {
            tokenExpirations.put(tokenId, expiration);
        }

        @Override
        public void addToken(String tokenId, long issueTime, long expiration) {
            addToken(tokenId, expiration);
            tokenIssueTimes.put(tokenId, issueTime);
        }

        @Override
        public void addToken(String tokenId, long issueTime, long expiration, long maxLifetimeDuration) {
            addToken(tokenId, expiration);
            tokenIssueTimes.put(tokenId, issueTime);
        }

        @Override
        public long getTokenIssueTime(String tokenId) throws UnknownTokenException {
        return tokenIssueTimes.getOrDefault(tokenId, 0L);
        }

        @Override
        public boolean isExpired(JWTToken token) throws UnknownTokenException {
            Long expiration;
            String tokenId = TokenUtils.getTokenId(token);
            if (tokenId != null) {
                expiration = tokenExpirations.get(tokenId);
            } else {
                expiration = Long.parseLong(token.getExpires());
            }

            return Instant.ofEpochMilli(expiration).isBefore(Instant.now());
        }

        @Override
        public void revokeToken(JWTToken token) throws UnknownTokenException {

        }

        @Override
        public void revokeToken(String tokenId) throws UnknownTokenException {

        }

        @Override
        public long renewToken(JWTToken token) throws UnknownTokenException {
            return 0;
        }

        @Override
        public long renewToken(JWTToken token, long renewInterval) throws UnknownTokenException {
            return 0;
        }

        @Override
        public long renewToken(String tokenId) throws UnknownTokenException {
            return 0;
        }

        @Override
        public long renewToken(String tokenId, long renewInterval) throws UnknownTokenException {
            return 0;
        }

        @Override
        public long getTokenExpiration(JWT token) throws UnknownTokenException {
            return getTokenExpiration(TokenUtils.getTokenId(token));
        }

        @Override
        public long getTokenExpiration(String tokenId) throws UnknownTokenException {
            if (!tokenExpirations.containsKey(tokenId)) {
                throw new UnknownTokenException(tokenId);
            }
            return tokenExpirations.get(tokenId);
        }

        @Override
        public long getTokenExpiration(String tokenId, boolean validate) throws UnknownTokenException {
            return getTokenExpiration(tokenId);
        }

        @Override
        public void addMetadata(String tokenId, TokenMetadata metadata) {
            tokenMetadata.put(tokenId, metadata);
        }

        @Override
        public TokenMetadata getTokenMetadata(String tokenId) throws UnknownTokenException {
            if (!tokenMetadata.containsKey(tokenId)) {
                throw new UnknownTokenException(tokenId);
            }
            return tokenMetadata.get(tokenId);
        }

        @Override
        public Collection<KnoxToken> getAllTokens() {
           return fetchTokens(null, false);
        }

        @Override
        public Collection<KnoxToken> getTokens(String userName) {
          return fetchTokens(userName, false);
        }

        @Override
        public Collection<KnoxToken> getDoAsTokens(String createdBy) {
          return fetchTokens(createdBy, true);
        }

        private Collection<KnoxToken> fetchTokens(String userName, boolean createdBy) {
          final Collection<KnoxToken> tokens = new TreeSet<>();
          final Predicate<Map.Entry<String, TokenMetadata>> filterPredicate;
          if (userName == null) {
            filterPredicate = entry -> true;
          } else {
            if (createdBy) {
              filterPredicate = entry -> userName.equals(entry.getValue().getCreatedBy());
            } else {
              filterPredicate = entry -> userName.equals(entry.getValue().getUserName());
            }
          }
          tokenMetadata.entrySet().stream().filter(filterPredicate).forEach(metadata -> {
            String tokenId = metadata.getKey();
            try {
              tokens.add(new KnoxToken(tokenId, getTokenIssueTime(tokenId), getTokenExpiration(tokenId), 0L, metadata.getValue()));
            } catch (UnknownTokenException e) {
              // NOP
            }
          });
          return tokens;
        }
    }
}
