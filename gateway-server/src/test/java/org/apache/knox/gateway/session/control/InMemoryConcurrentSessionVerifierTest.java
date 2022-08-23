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
package org.apache.knox.gateway.session.control;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.services.security.impl.DefaultKeystoreService;
import org.apache.knox.gateway.services.security.token.JWTokenAttributes;
import org.apache.knox.gateway.services.security.token.JWTokenAttributesBuilder;
import org.apache.knox.gateway.services.security.token.TokenServiceException;
import org.apache.knox.gateway.services.security.token.impl.JWT;
import org.apache.knox.gateway.services.token.impl.DefaultTokenAuthorityService;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class InMemoryConcurrentSessionVerifierTest {
  private InMemoryConcurrentSessionVerifier verifier;
  private Map<String, String> options;
  private DefaultTokenAuthorityService tokenAuthority;
  private JWTokenAttributes jwtAttributesForAdmin;
  private JWTokenAttributes jwtAttributesForTom;
  private JWTokenAttributes expiredJwtAttributesForTom;
  private JWTokenAttributes expiredJwtAttributesForAdmin;
  private JWT adminToken1;
  private JWT adminToken2;
  private JWT adminToken3;
  private JWT adminToken4;
  private JWT adminToken5;
  private JWT adminToken6;
  private JWT tomToken1;
  private JWT tomToken2;
  private JWT tomToken3;
  private JWT tomToken4;
  private JWT tomToken5;
  private JWT tomToken6;

  @Before
  public void setUp() throws AliasServiceException, IOException, ServiceLifecycleException {
    verifier = new InMemoryConcurrentSessionVerifier();
    options = Collections.emptyMap();

    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }
    EasyMock.expect(config.getGatewaySecurityDir()).andReturn(basedir + "/target/test-classes").anyTimes();
    EasyMock.expect(config.getGatewayKeystoreDir()).andReturn(basedir + "/target/test-classes/keystores").anyTimes();
    EasyMock.expect(config.getSigningKeystoreName()).andReturn("server-keystore.jks").anyTimes();
    EasyMock.expect(config.getSigningKeystorePath()).andReturn(basedir + "/target/test-classes/keystores/server-keystore.jks").anyTimes();
    EasyMock.expect(config.getSigningKeystorePasswordAlias()).andReturn(GatewayConfig.DEFAULT_SIGNING_KEYSTORE_PASSWORD_ALIAS).anyTimes();
    EasyMock.expect(config.getSigningKeyPassphraseAlias()).andReturn(GatewayConfig.DEFAULT_SIGNING_KEY_PASSPHRASE_ALIAS).anyTimes();
    EasyMock.expect(config.getSigningKeystoreType()).andReturn("jks").anyTimes();
    EasyMock.expect(config.getSigningKeyAlias()).andReturn("server").anyTimes();
    EasyMock.expect(config.getCredentialStoreType()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_TYPE).anyTimes();
    EasyMock.expect(config.getCredentialStoreAlgorithm()).andReturn(GatewayConfig.DEFAULT_CREDENTIAL_STORE_ALG).anyTimes();

    MasterService ms = EasyMock.createNiceMock(MasterService.class);
    EasyMock.expect(ms.getMasterSecret()).andReturn("horton".toCharArray());

    AliasService as = EasyMock.createNiceMock(AliasService.class);
    EasyMock.expect(as.getSigningKeyPassphrase()).andReturn("horton".toCharArray()).anyTimes();

    EasyMock.replay(config, ms, as);

    DefaultKeystoreService ks = new DefaultKeystoreService();
    ks.setMasterService(ms);
    ks.init(config, new HashMap<>());

    tokenAuthority = new DefaultTokenAuthorityService();
    tokenAuthority.setAliasService(as);
    tokenAuthority.setKeystoreService(ks);
    tokenAuthority.init(config, new HashMap<>());
    tokenAuthority.start();

    jwtAttributesForAdmin = makeJwtAttribute("admin", false);
    jwtAttributesForTom = makeJwtAttribute("tom", false);
    expiredJwtAttributesForAdmin = makeJwtAttribute("admin", true);
    expiredJwtAttributesForTom = makeJwtAttribute("tom", true);

    try {
      adminToken1 = tokenAuthority.issueToken(jwtAttributesForAdmin);
      adminToken2 = tokenAuthority.issueToken(jwtAttributesForAdmin);
      adminToken3 = tokenAuthority.issueToken(jwtAttributesForAdmin);
      adminToken4 = tokenAuthority.issueToken(jwtAttributesForAdmin);
      adminToken5 = tokenAuthority.issueToken(jwtAttributesForAdmin);
      adminToken6 = tokenAuthority.issueToken(jwtAttributesForAdmin);
      tomToken1 = tokenAuthority.issueToken(jwtAttributesForTom);
      tomToken2 = tokenAuthority.issueToken(jwtAttributesForTom);
      tomToken3 = tokenAuthority.issueToken(jwtAttributesForTom);
      tomToken4 = tokenAuthority.issueToken(jwtAttributesForTom);
      tomToken5 = tokenAuthority.issueToken(jwtAttributesForTom);
      tomToken6 = tokenAuthority.issueToken(jwtAttributesForTom);
    } catch (TokenServiceException ignored) {
    }
  }

  private GatewayConfig mockConfig(Set<String> unlimitedUsers, Set<String> privilegedUsers, int privilegedUsersLimit, int nonPrivilegedUsersLimit) {
    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.getSessionVerificationPrivilegedUsers()).andReturn(privilegedUsers);
    EasyMock.expect(config.getSessionVerificationUnlimitedUsers()).andReturn(unlimitedUsers);
    EasyMock.expect(config.getPrivilegedUsersConcurrentSessionLimit()).andReturn(privilegedUsersLimit);
    EasyMock.expect(config.getNonPrivilegedUsersConcurrentSessionLimit()).andReturn(nonPrivilegedUsersLimit);
    EasyMock.replay(config);
    return config;
  }

  private JWTokenAttributes makeJwtAttribute(String username, boolean expired) {
    long expiryTime = expired ? System.currentTimeMillis() - 1000 : -1;
    return new JWTokenAttributesBuilder()
            .setIssuer("KNOXSSO")
            .setUserName(username)
            .setAudiences(new ArrayList<>())
            .setAlgorithm("RS256")
            .setExpires(expiryTime)
            .setSigningKeystoreName(null)
            .setSigningKeystoreAlias(null)
            .setSigningKeystorePassphrase(null)
            .build();
  }

  /**
   * The goal for this test is to prove that if the user is configured for the unlimited group then
   * neither of the limits apply to him, he can have unlimited sessions.
   */
  @Test
  public void testUserIsUnlimitedAndCanBeLoggedInUnlimitedTimes() throws ServiceLifecycleException {
    GatewayConfig config = mockConfig(new HashSet<>(Arrays.asList("admin")), Collections.emptySet(), 3, 2);
    verifier.init(config, options);

    Assert.assertTrue(verifier.verifySessionForUser("admin", adminToken1));
    Assert.assertTrue(verifier.verifySessionForUser("admin", adminToken2));
    Assert.assertTrue(verifier.verifySessionForUser("admin", adminToken3));
    Assert.assertTrue(verifier.verifySessionForUser("admin", adminToken4));
  }

  @Test
  public void testUserIsPrivileged() throws ServiceLifecycleException {
    GatewayConfig config = mockConfig(Collections.emptySet(), new HashSet<>(Arrays.asList("admin")), 3, 2);
    verifier.init(config, options);

    Assert.assertTrue(verifier.verifySessionForUser("admin", adminToken1));
    Assert.assertTrue(verifier.verifySessionForUser("admin", adminToken2));
    Assert.assertTrue(verifier.verifySessionForUser("admin", adminToken3));
    Assert.assertFalse(verifier.verifySessionForUser("admin", adminToken4));
    verifier.sessionEndedForUser("admin", adminToken1.toString());
    Assert.assertTrue(verifier.verifySessionForUser("admin", adminToken5));
    Assert.assertFalse(verifier.verifySessionForUser("admin", adminToken6));
  }

  @Test
  public void testUserIsNotPrivileged() throws ServiceLifecycleException {
    GatewayConfig config = mockConfig(Collections.emptySet(), Collections.emptySet(), 3, 2);
    verifier.init(config, options);

    Assert.assertTrue(verifier.verifySessionForUser("tom", tomToken1));
    Assert.assertTrue(verifier.verifySessionForUser("tom", tomToken2));
    Assert.assertFalse(verifier.verifySessionForUser("tom", tomToken3));
    Assert.assertFalse(verifier.verifySessionForUser("tom", tomToken4));
    verifier.sessionEndedForUser("tom", tomToken1.toString());
    Assert.assertTrue(verifier.verifySessionForUser("tom", tomToken5));
    Assert.assertFalse(verifier.verifySessionForUser("tom", tomToken6));
  }

  @Test
  public void testPrivilegedLimitIsZero() throws ServiceLifecycleException {
    GatewayConfig config = mockConfig(Collections.emptySet(), new HashSet<>(Arrays.asList("tom")), 0, 2);
    verifier.init(config, options);

    Assert.assertFalse(verifier.verifySessionForUser("tom", tomToken1));
  }

  @Test
  public void testNonPrivilegedLimitIsZero() throws ServiceLifecycleException {
    GatewayConfig config = mockConfig(Collections.emptySet(), Collections.emptySet(), 3, 0);
    verifier.init(config, options);

    Assert.assertFalse(verifier.verifySessionForUser("tom", tomToken1));
  }

  @Test
  public void testSessionsDoNotGoToNegative() throws ServiceLifecycleException {
    GatewayConfig config = mockConfig(Collections.emptySet(), new HashSet<>(Arrays.asList("admin")), 2, 2);
    verifier.init(config, options);

    Assert.assertEquals(0, verifier.countValidTokensForUser("admin"));
    verifier.verifySessionForUser("admin", adminToken1);
    Assert.assertEquals(1, verifier.countValidTokensForUser("admin"));
    verifier.sessionEndedForUser("admin", adminToken1.toString());
    Assert.assertEquals(0, verifier.countValidTokensForUser("admin"));
    verifier.sessionEndedForUser("admin", adminToken1.toString());
    Assert.assertEquals(0, verifier.countValidTokensForUser("admin"));
    verifier.verifySessionForUser("admin", adminToken2);
    Assert.assertEquals(1, verifier.countValidTokensForUser("admin"));

    Assert.assertEquals(0, verifier.countValidTokensForUser("tom"));
    verifier.verifySessionForUser("tom", tomToken1);
    Assert.assertEquals(1, verifier.countValidTokensForUser("tom"));
    verifier.sessionEndedForUser("tom", tomToken1.toString());
    Assert.assertEquals(0, verifier.countValidTokensForUser("tom"));
    verifier.sessionEndedForUser("tom", tomToken1.toString());
    Assert.assertEquals(0, verifier.countValidTokensForUser("tom"));
    verifier.verifySessionForUser("tom", tomToken2);
    Assert.assertEquals(1, verifier.countValidTokensForUser("tom"));
  }

  @Test
  public void testNegativeLimitMeansUnlimited() throws ServiceLifecycleException {
    GatewayConfig config = mockConfig(Collections.emptySet(), new HashSet<>(Arrays.asList("admin")), -2, -2);
    verifier.init(config, options);

    for (int i = 0; i < 10; i++) {
      try {
        JWT token = tokenAuthority.issueToken(jwtAttributesForAdmin);
        Assert.assertTrue(verifier.verifySessionForUser("admin", token));
        token = tokenAuthority.issueToken(jwtAttributesForTom);
        Assert.assertTrue(verifier.verifySessionForUser("tom", token));
      } catch (TokenServiceException ignored) {
      }
    }
  }

  @Test
  public void testExpiredTokensAreNotCounted() throws ServiceLifecycleException, TokenServiceException, InterruptedException {
    GatewayConfig config = mockConfig(Collections.emptySet(), new HashSet<>(Arrays.asList("admin")), 3, 3);
    verifier.init(config, options);

    verifier.verifySessionForUser("tom", tomToken1);
    Assert.assertEquals(1, verifier.countValidTokensForUser("tom"));
    JWT expiredTomToken = tokenAuthority.issueToken(expiredJwtAttributesForTom);
    verifier.verifySessionForUser("tom", expiredTomToken);
    Assert.assertEquals(1, verifier.countValidTokensForUser("tom"));
    expiredTomToken = tokenAuthority.issueToken(expiredJwtAttributesForTom);
    verifier.verifySessionForUser("tom", expiredTomToken);
    Assert.assertEquals(1, verifier.countValidTokensForUser("tom"));

    verifier.verifySessionForUser("admin", adminToken1);
    Assert.assertEquals(1, verifier.countValidTokensForUser("admin"));
    JWT expiredAdminToken = tokenAuthority.issueToken(expiredJwtAttributesForAdmin);
    verifier.verifySessionForUser("admin", expiredAdminToken);
    Assert.assertEquals(1, verifier.countValidTokensForUser("admin"));
    expiredAdminToken = tokenAuthority.issueToken(expiredJwtAttributesForAdmin);
    verifier.verifySessionForUser("admin", expiredAdminToken);
    Assert.assertEquals(1, verifier.countValidTokensForUser("admin"));
  }

  @Test
  public void testBackgroundThreadRemoveExpiredTokens() throws ServiceLifecycleException, TokenServiceException, InterruptedException {
    GatewayConfig config = mockConfig(Collections.emptySet(), new HashSet<>(Arrays.asList("admin")), 3, 3);
    verifier.init(config, options);

    verifier.verifySessionForUser("admin", adminToken1);
    verifier.verifySessionForUser("admin", adminToken2);
    JWT expiredAdminToken = tokenAuthority.issueToken(expiredJwtAttributesForAdmin);
    verifier.verifySessionForUser("admin", expiredAdminToken);
    Assert.assertEquals(3, verifier.getTokenCountForUser("admin").intValue());
    verifier.removeExpiredTokens();
    Assert.assertEquals(2, verifier.getTokenCountForUser("admin").intValue());

    verifier.verifySessionForUser("tom", tomToken1);
    verifier.verifySessionForUser("tom", tomToken2);
    JWT expiredTomToken = tokenAuthority.issueToken(expiredJwtAttributesForTom);
    verifier.verifySessionForUser("tom", expiredTomToken);
    Assert.assertEquals(3, verifier.getTokenCountForUser("tom").intValue());
    verifier.removeExpiredTokens();
    Assert.assertEquals(2, verifier.getTokenCountForUser("tom").intValue());
  }

  @SuppressWarnings("PMD.DoNotUseThreads")
  @Test
  public void testPrivilegedLoginLogoutStress() throws ServiceLifecycleException, InterruptedException {
    GatewayConfig config = mockConfig(Collections.emptySet(), new HashSet<>(Arrays.asList("admin")), 256, 256);
    verifier.init(config, options);

    ExecutorService executor = Executors.newFixedThreadPool(128);
    BlockingQueue<JWT> tokenStorage = new ArrayBlockingQueue<>(256);
    CyclicBarrier barrier = new CyclicBarrier(128);

    Runnable privilegedLogin = () -> {
      JWT token;
      try {
        token = tokenAuthority.issueToken(jwtAttributesForAdmin);
        tokenStorage.add(token);
        barrier.await();
      } catch (InterruptedException | BrokenBarrierException | TokenServiceException e) {
        throw new RuntimeException(e);
      }
      verifier.verifySessionForUser("admin", token);
    };

    for (int i = 0; i < 128; i++) {
      executor.submit(privilegedLogin);
    }
    Thread.sleep(1000);
    Assert.assertEquals(128, verifier.countValidTokensForUser("admin"));

    Runnable privilegedLogout = () -> {
      JWT token;
      try {
        token = tokenStorage.take();
        barrier.await();
      } catch (InterruptedException | BrokenBarrierException e) {
        throw new RuntimeException(e);
      }
      verifier.sessionEndedForUser("admin", String.valueOf(token));
    };

    for (int i = 0; i < 64; i++) {
      executor.submit(privilegedLogin);
    }
    for (int i = 0; i < 64; i++) {
      executor.submit(privilegedLogout);
    }
    Thread.sleep(1000);
    Assert.assertEquals(128, verifier.countValidTokensForUser("admin"));

    for (int i = 0; i < 128; i++) {
      executor.submit(privilegedLogout);
    }
    Thread.sleep(1000);
    Assert.assertEquals(0, verifier.countValidTokensForUser("admin"));

    config = mockConfig(Collections.emptySet(), new HashSet<>(Arrays.asList("admin")), 10, 10);
    verifier.init(config, options);
    tokenStorage.clear();

    for (int i = 0; i < 128; i++) {
      executor.submit(privilegedLogin);
    }
    Thread.sleep(1000);
    Assert.assertEquals(10, verifier.countValidTokensForUser("admin"));
  }

  @SuppressWarnings("PMD.DoNotUseThreads")
  @Test
  public void testNonPrivilegedLoginLogoutStress() throws ServiceLifecycleException, InterruptedException {
    GatewayConfig config = mockConfig(Collections.emptySet(), Collections.emptySet(), 256, 256);
    verifier.init(config, options);

    ExecutorService executor = Executors.newFixedThreadPool(128);
    BlockingQueue<JWT> tokenStorage = new ArrayBlockingQueue<>(256);
    CyclicBarrier barrier = new CyclicBarrier(128);

    Runnable nonPrivilegedLogin = () -> {
      JWT token;
      try {
        token = tokenAuthority.issueToken(jwtAttributesForTom);
        tokenStorage.add(token);
        barrier.await();
      } catch (InterruptedException | BrokenBarrierException | TokenServiceException e) {
        throw new RuntimeException(e);
      }
      verifier.verifySessionForUser("tom", token);
    };

    for (int i = 0; i < 128; i++) {
      executor.submit(nonPrivilegedLogin);
    }
    Thread.sleep(1000);
    Assert.assertEquals(128, verifier.countValidTokensForUser("tom"));

    Runnable nonPrivilegedLogout = () -> {
      JWT token;
      try {
        token = tokenStorage.take();
        barrier.await();
      } catch (InterruptedException | BrokenBarrierException e) {
        throw new RuntimeException(e);
      }
      verifier.sessionEndedForUser("tom", String.valueOf(token));
    };

    for (int i = 0; i < 64; i++) {
      executor.submit(nonPrivilegedLogin);
    }
    for (int i = 0; i < 64; i++) {
      executor.submit(nonPrivilegedLogout);
    }
    Thread.sleep(1000);
    Assert.assertEquals(128, verifier.countValidTokensForUser("tom"));

    for (int i = 0; i < 128; i++) {
      executor.submit(nonPrivilegedLogout);
    }
    Thread.sleep(1000);
    Assert.assertEquals(0, verifier.countValidTokensForUser("tom"));

    config = mockConfig(Collections.emptySet(), Collections.emptySet(), 10, 10);
    verifier.init(config, options);
    tokenStorage.clear();

    for (int i = 0; i < 128; i++) {
      executor.submit(nonPrivilegedLogin);
    }
    Thread.sleep(1000);
    Assert.assertEquals(10, verifier.countValidTokensForUser("tom"));
  }
}


