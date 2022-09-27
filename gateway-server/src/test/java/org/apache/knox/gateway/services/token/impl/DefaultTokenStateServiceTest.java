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
package org.apache.knox.gateway.services.token.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.token.JWTokenAttributesBuilder;
import org.apache.knox.gateway.services.security.token.TokenMetadata;
import org.apache.knox.gateway.services.security.token.TokenStateService;
import org.apache.knox.gateway.services.security.token.TokenUtils;
import org.apache.knox.gateway.services.security.token.UnknownTokenException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.apache.knox.gateway.util.Tokens;
import org.easymock.EasyMock;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;

public class DefaultTokenStateServiceTest {

  private static RSAPrivateKey privateKey;

  @Rule
  public final TemporaryFolder testFolder = new TemporaryFolder();

  private Path gatewaySecurityDir;

  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);

    KeyPair kp = kpg.genKeyPair();
    privateKey = (RSAPrivateKey) kp.getPrivate();
  }

  @Test
  public void testGetExpiration() throws Exception {
    final JWTToken token = createMockToken(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60));
    final TokenStateService tss = createTokenStateService();

    addToken(tss, token, System.currentTimeMillis());
    long expiration = tss.getTokenExpiration(TokenUtils.getTokenId(token));
    assertEquals(token.getExpiresDate().getTime(), expiration);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetExpiration_NullToken() throws Exception {
    // Expecting an IllegalArgumentException because the token is null
    createTokenStateService().getTokenExpiration((String) null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetExpiration_EmptyToken() throws Exception {
    // Expecting an IllegalArgumentException because the token is empty
    createTokenStateService().getTokenExpiration("");
  }

  @Test(expected = UnknownTokenException.class)
  public void testGetExpiration_InvalidToken() throws Exception {
    final JWTToken token = createMockToken(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60));

    // Expecting an UnknownTokenException because the token is not known to the TokenStateService
    createTokenStateService().getTokenExpiration(TokenUtils.getTokenId(token));
  }

  @Test(expected = UnknownTokenException.class)
  public void testGetExpiration_InvalidToken_WithoutValidation() throws Exception {
    final JWTToken token = createMockToken(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60));

    // Expecting an UnknownTokenException because the token is not known to the TokenStateService
    createTokenStateService().getTokenExpiration(TokenUtils.getTokenId(token), false);
  }

  @Test(expected = UnknownTokenException.class)
  public void testGetMetadata_InvalidToken() throws Exception {
    final JWTToken token = createMockToken(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60));

    // Expecting an UnknownTokenException because the token is not known to the TokenStateService
    createTokenStateService().getTokenMetadata(TokenUtils.getTokenId(token));
  }

  @Test
  public void testGetExpiration_AfterRenewal() throws Exception {
    final JWTToken token = createMockToken(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60));
    final TokenStateService tss = createTokenStateService();

    addToken(tss, token, System.currentTimeMillis());
    long expiration = tss.getTokenExpiration(TokenUtils.getTokenId(token));
    assertEquals(token.getExpiresDate().getTime(), expiration);

    long newExpiration = tss.renewToken(token);
    assertTrue(newExpiration > token.getExpiresDate().getTime());
    assertTrue(tss.getTokenExpiration(TokenUtils.getTokenId(token)) > token.getExpiresDate().getTime());
  }

  @Test
  public void testIsExpired_Negative() throws Exception {
    final JWTToken token = createMockToken(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60));
    final TokenStateService tss = createTokenStateService();

    addToken(tss, token, System.currentTimeMillis());
    assertFalse(tss.isExpired(token));
  }

  @Test
  public void testIsExpired_Positive() throws Exception {
    final JWTToken token = createMockToken(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(60));
    final TokenStateService tss = createTokenStateService();

    addToken(tss, token, System.currentTimeMillis());
    assertTrue(tss.isExpired(token));
  }

  @Test(expected = UnknownTokenException.class)
  public void testIsExpired_Revoked() throws Exception {
    final JWTToken token = createMockToken(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60));
    final TokenStateService tss = createTokenStateService();

    addToken(tss, token, System.currentTimeMillis());
    assertFalse("Expected the token to be valid.", tss.isExpired(token));

    tss.revokeToken(token);
    tss.isExpired(token);
  }

  @Test
  public void testRenewal() throws Exception {
    final JWTToken token = createMockToken(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(60));
    final TokenStateService tss = createTokenStateService();

    // Add the expired token
    addToken(tss, token, System.currentTimeMillis());
    assertTrue("Expected the token to have expired.", tss.isExpired(token));

    tss.renewToken(token);
    assertFalse("Expected the token to have been renewed.", tss.isExpired(token));
  }

  @Test
  public void testRenewalBeyondMaxLifetime() throws Exception {
    long maxLifetimeDuration = TimeUnit.SECONDS.toMillis(5);
    long issueTime = System.currentTimeMillis();
    long expiration = issueTime + maxLifetimeDuration;
    final JWTToken token = createMockToken(expiration);
    final TokenStateService tss = createTokenStateService();

    // Add the token with a short maximum lifetime
    tss.addToken(TokenUtils.getTokenId(token), issueTime, expiration, maxLifetimeDuration);

    try {
      // Attempt to renew the token for the default interval, which should exceed the specified short maximum lifetime
      // for this token.
      tss.renewToken(token);
      fail("Token renewal should have been disallowed because the maximum lifetime will have been exceeded.");
    } catch (IllegalArgumentException e) {
      assertEquals("The renewal limit for the token has been exceeded", e.getMessage());
    }
  }

  @Test
  public void testNegativeTokenEviction() throws Exception {
    final JWTToken token = createMockToken(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(60));
    final TokenStateService tss = createTokenStateService();

    final long evictionInterval = TimeUnit.SECONDS.toMillis(3);
    final long maxTokenLifetime = TimeUnit.MINUTES.toMillis(2);

    // Add the expired token
    addToken(tss,
            token.getClaim(JWTToken.KNOX_ID_CLAIM),
            System.currentTimeMillis(),
            token.getExpiresDate().getTime(),
            maxTokenLifetime);
    assertTrue("Expected the token to have expired.", tss.isExpired(token));

    // Sleep to allow the eviction evaluation to be performed prior to the maximum token lifetime
    Thread.sleep(evictionInterval + (evictionInterval / 2));

    // Renewal should succeed because there is sufficient time until expiration + grace period is exceeded
    tss.renewToken(token, TimeUnit.SECONDS.toMillis(10));
    assertFalse("Expected the token to have been renewed.", tss.isExpired(token));
  }

  @Test
  public void testTokenEviction() throws Exception {
    final JWTToken token = createMockToken(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(60));
    final TokenStateService tss = createTokenStateService();

    final long evictionInterval = TimeUnit.SECONDS.toMillis(3);
    final long maxTokenLifetime = evictionInterval * 3;

    try {
      tss.start();
      // Add the expired token
      addToken(tss,
               token.getClaim(JWTToken.KNOX_ID_CLAIM),
               System.currentTimeMillis(),
               token.getExpiresDate().getTime(),
               maxTokenLifetime);
      assertTrue("Expected the token to have expired.", tss.isExpired(token));

      // Sleep to allow the eviction evaluation to be performed
      Thread.sleep(evictionInterval + (evictionInterval / 2));

      // Expect the renew call to fail since the token should have been evicted
      final UnknownTokenException e = assertThrows(UnknownTokenException.class, () -> tss.renewToken(token));
      assertEquals("Unknown token: " + Tokens.getTokenIDDisplayText(TokenUtils.getTokenId(token)), e.getMessage());
    } finally {
      tss.stop();
    }
  }

  @Test
  public void testTokenPermissiveness() throws Exception {
    final long expiry = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(300);
    final JWT token = getJWTToken(expiry);
    TokenStateService tss = new DefaultTokenStateService();
    try {
      tss.init(createMockGatewayConfig(true), Collections.emptyMap());
    } catch (ServiceLifecycleException e) {
      fail("Error creating TokenStateService: " + e.getMessage());
    }
    assertEquals(TimeUnit.MILLISECONDS.toSeconds(expiry),
        TimeUnit.MILLISECONDS.toSeconds(tss.getTokenExpiration(token)));
  }

  @Test(expected = UnknownTokenException.class)
  public void testTokenPermissivenessNoExpiry() throws Exception {
    final JWT token = getJWTToken(-1L);
    TokenStateService tss = new DefaultTokenStateService();
    try {
      tss.init(createMockGatewayConfig(true), Collections.emptyMap());
    } catch (ServiceLifecycleException e) {
      fail("Error creating TokenStateService: " + e.getMessage());
    }

    tss.getTokenExpiration(token);
  }

  @SuppressWarnings("PMD.JUnitUseExpected")
  @Test
  public void testAddTokenMetadata() throws Exception {
    final JWT token = getJWTToken(System.currentTimeMillis());
    final String tokenId = token.getClaim(JWTToken.KNOX_ID_CLAIM);
    final TokenStateService tss = new DefaultTokenStateService();
    tss.addToken((JWTToken) token, System.currentTimeMillis());
    try {
      tss.getTokenMetadata(tokenId);
      fail("Expected exception since there is no metadata for the token ID.");
    } catch (UnknownTokenException e) {
      // Expected
    }

    final String userName = "testUser";
    tss.addMetadata(token.getClaim(JWTToken.KNOX_ID_CLAIM), new TokenMetadata(userName));
    assertNotNull(tss.getTokenMetadata(tokenId));
    assertEquals(tss.getTokenMetadata(tokenId).getUserName(), userName);
    assertNull(tss.getTokenMetadata(tokenId).getComment());

    final String comment = "this is my test comment";
    tss.addMetadata(token.getClaim(JWTToken.KNOX_ID_CLAIM), new TokenMetadata(userName, comment, true));
    assertNotNull(tss.getTokenMetadata(tokenId));
    assertEquals(tss.getTokenMetadata(tokenId).getComment(), comment);
    assertTrue(tss.getTokenMetadata(tokenId).isEnabled());

    final String passcode = "myPasscode";
    final TokenMetadata metadata = new TokenMetadata(userName, comment, true);
    metadata.setPasscode(passcode);
    tss.addMetadata(token.getClaim(JWTToken.KNOX_ID_CLAIM), metadata);
    assertNotNull(tss.getTokenMetadata(tokenId));
    assertEquals(tss.getTokenMetadata(tokenId).getPasscode(), passcode);
  }

  protected static JWTToken createMockToken(final long expiration) {
    return createMockToken("abcD1234eFGHIJKLmnoPQRSTUVwXYz", expiration);
  }

  protected static JWTToken createMockToken(final String payload, final long expiration) {
    UUID tokenUID = UUID.randomUUID();
    JWTToken token = EasyMock.createNiceMock(JWTToken.class);
    EasyMock.expect(token.getPayload()).andReturn(payload).anyTimes();
    EasyMock.expect(token.getClaim(JWTToken.KNOX_ID_CLAIM)).andReturn(String.valueOf(tokenUID)).anyTimes();
    EasyMock.expect(token.getExpiresDate()).andReturn(new Date(expiration)).anyTimes();
    EasyMock.replay(token);
    return token;
  }

  protected GatewayConfig createMockGatewayConfig(boolean tokenPermissiveness) throws Exception {
    return createMockGatewayConfig(tokenPermissiveness, getGatewaySecurityDir(), getTokenStatePersistenceInterval());
  }

  protected GatewayConfig createMockGatewayConfig(boolean tokenPermissiveness,
                                                         final String securityDir,
                                                         long statePersistenceInterval) {
    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    /* configure token eviction time to be 2 secs for test */
    EasyMock.expect(config.getKnoxTokenEvictionInterval()).andReturn(2L).anyTimes();
    EasyMock.expect(config.getKnoxTokenEvictionGracePeriod()).andReturn(0L).anyTimes();
    EasyMock.expect(config.isKnoxTokenPermissiveValidationEnabled()).andReturn(tokenPermissiveness).anyTimes();
    EasyMock.expect(config.getKnoxTokenStateAliasPersistenceInterval()).andReturn(statePersistenceInterval).anyTimes();
    EasyMock.expect(config.getGatewaySecurityDir()).andReturn(securityDir).anyTimes();
    EasyMock.replay(config);
    return config;
  }

  protected void initTokenStateService(TokenStateService tss) throws Exception {
    try {
      tss.init(createMockGatewayConfig(false), Collections.emptyMap());
    } catch (ServiceLifecycleException e) {
      fail("Error creating TokenStateService: " + e.getMessage());
    }
  }

  protected long getTokenStatePersistenceInterval() {
    return TimeUnit.SECONDS.toMillis(15);
  }

  protected String getGatewaySecurityDir() throws IOException {
    if (gatewaySecurityDir == null) {
      gatewaySecurityDir = testFolder.newFolder().toPath();
      Files.createDirectories(gatewaySecurityDir);
    }
    return gatewaySecurityDir.toString();
  }

  protected TokenStateService createTokenStateService() throws Exception {
    TokenStateService tss = new DefaultTokenStateService();
    initTokenStateService(tss);
    return tss;
  }

  /* create a test JWT token */
  protected JWT getJWTToken(final long expiry) {
    JWT token = new JWTToken(new JWTokenAttributesBuilder().setExpires(expiry).setAlgorithm("RS256").build());
    // Sign the token
    JWSSigner signer = new RSASSASigner(privateKey);
    token.sign(signer);
    return token;
  }

  protected void addToken(TokenStateService tss, JWTToken token, long issueTime) {
    tss.addToken(token, issueTime);
  }

  protected void addToken(TokenStateService tss, String tokenId, long issueTime, long expiration, long maxLifetime) {
    tss.addToken(tokenId, issueTime, expiration, maxLifetime);
  }
}
