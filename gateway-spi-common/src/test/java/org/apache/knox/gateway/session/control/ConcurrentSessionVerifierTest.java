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

import org.apache.knox.gateway.config.GatewayConfig;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ConcurrentSessionVerifierTest {

  private ConcurrentSessionVerifier verifier;

  @Before
  public void setUp() {
    verifier = ConcurrentSessionVerifier.getInstance();
  }

  private GatewayConfig mockConfig(Set<String> privilegedUsers, Set<String> nonPrivilegedUsers, int privilegedUsersLimit, int nonPrivilegedUsersLimit) {
    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(config.getPrivilegedUsers()).andReturn(privilegedUsers);
    EasyMock.expect(config.getNonPrivilegedUsers()).andReturn(nonPrivilegedUsers);
    EasyMock.expect(config.getPrivilegedUsersConcurrentSessionLimit()).andReturn(privilegedUsersLimit);
    EasyMock.expect(config.getNonPrivilegedUsersConcurrentSessionLimit()).andReturn(nonPrivilegedUsersLimit);
    EasyMock.replay(config);
    return config;
  }


  @Test
  public void userIsInNeitherOfTheGroups() {
    GatewayConfig config = mockConfig(new HashSet<>(Arrays.asList("admin")), new HashSet<>(Arrays.asList("tom", "guest")), 3, 2);
    verifier.init(config);
    for (int i = 0; i < 4; i++) {
      Assert.assertTrue(verifier.verifySessionForUser("sam"));
    }
  }

  @Test
  public void userIsInBothOfTheGroups() {
    GatewayConfig config = mockConfig(new HashSet<>(Arrays.asList("admin", "tom")), new HashSet<>(Arrays.asList("tom", "guest")), 3, 2);
    verifier.init(config);

    Assert.assertTrue(verifier.verifySessionForUser("tom"));
    Assert.assertTrue(verifier.verifySessionForUser("tom"));
    Assert.assertFalse(verifier.verifySessionForUser("tom"));

    config = mockConfig(new HashSet<>(Arrays.asList("admin", "tom")), new HashSet<>(Arrays.asList("tom", "guest")), 3, 4);
    verifier.init(config);

    Assert.assertTrue(verifier.verifySessionForUser("tom"));
    Assert.assertTrue(verifier.verifySessionForUser("tom"));
    Assert.assertTrue(verifier.verifySessionForUser("tom"));
    Assert.assertFalse(verifier.verifySessionForUser("tom"));
  }

  @Test
  public void userIsPrivileged() {
    GatewayConfig config = mockConfig(new HashSet<>(Arrays.asList("admin")), new HashSet<>(Arrays.asList("tom", "guest")), 3, 2);
    verifier.init(config);

    Assert.assertTrue(verifier.verifySessionForUser("admin"));
    Assert.assertTrue(verifier.verifySessionForUser("admin"));
    Assert.assertTrue(verifier.verifySessionForUser("admin"));
    Assert.assertFalse(verifier.verifySessionForUser("admin"));
    Assert.assertFalse(verifier.verifySessionForUser("admin"));
    verifier.sessionEndedForUser("admin");
    Assert.assertTrue(verifier.verifySessionForUser("admin"));
    Assert.assertFalse(verifier.verifySessionForUser("admin"));
    Assert.assertFalse(verifier.verifySessionForUser("admin"));
  }

  @Test
  public void userIsNotPrivileged() {
    GatewayConfig config = mockConfig(new HashSet<>(Arrays.asList("admin")), new HashSet<>(Arrays.asList("tom", "guest")), 3, 2);
    verifier.init(config);

    Assert.assertTrue(verifier.verifySessionForUser("tom"));
    Assert.assertTrue(verifier.verifySessionForUser("tom"));
    Assert.assertFalse(verifier.verifySessionForUser("tom"));
    Assert.assertFalse(verifier.verifySessionForUser("tom"));
    verifier.sessionEndedForUser("tom");
    Assert.assertTrue(verifier.verifySessionForUser("tom"));
    Assert.assertFalse(verifier.verifySessionForUser("tom"));
    Assert.assertFalse(verifier.verifySessionForUser("tom"));
  }

  @Test
  public void privilegedLimitIsZero() {
    GatewayConfig config = mockConfig(new HashSet<>(Arrays.asList("admin")), new HashSet<>(Arrays.asList("tom", "guest")), 0, 2);
    verifier.init(config);

    Assert.assertFalse(verifier.verifySessionForUser("admin"));
  }

  @Test
  public void nonPrivilegedLimitIsZero() {
    GatewayConfig config = mockConfig(new HashSet<>(Arrays.asList("admin")), new HashSet<>(Arrays.asList("tom", "guest")), 3, 0);
    verifier.init(config);

    Assert.assertFalse(verifier.verifySessionForUser("tom"));
  }

  @Test
  public void sessionsDoNotGoToNegative() {
    GatewayConfig config = mockConfig(new HashSet<>(Arrays.asList("admin")), new HashSet<>(Arrays.asList("tom", "guest")), 2, 2);
    verifier.init(config);

    Assert.assertNull(verifier.getUserConcurrentSessionCount("admin"));
    verifier.verifySessionForUser("admin");
    Assert.assertEquals(1, verifier.getUserConcurrentSessionCount("admin").intValue());
    verifier.sessionEndedForUser("admin");
    Assert.assertNull(verifier.getUserConcurrentSessionCount("admin"));
    verifier.sessionEndedForUser("admin");
    Assert.assertNull(verifier.getUserConcurrentSessionCount("admin"));
    verifier.verifySessionForUser("admin");
    Assert.assertEquals(1, verifier.getUserConcurrentSessionCount("admin").intValue());

    Assert.assertNull(verifier.getUserConcurrentSessionCount("tom"));
    verifier.verifySessionForUser("tom");
    Assert.assertEquals(1, verifier.getUserConcurrentSessionCount("tom").intValue());
    verifier.sessionEndedForUser("tom");
    Assert.assertNull(verifier.getUserConcurrentSessionCount("tom"));
    verifier.sessionEndedForUser("tom");
    Assert.assertNull(verifier.getUserConcurrentSessionCount("tom"));
    verifier.verifySessionForUser("tom");
    Assert.assertEquals(1, verifier.getUserConcurrentSessionCount("tom").intValue());
  }

  @Test
  public void negativeLimitMeansUnlimited() {
    GatewayConfig config = mockConfig(new HashSet<>(Arrays.asList("admin")), new HashSet<>(Arrays.asList("tom", "guest")), -2, -2);
    verifier.init(config);

    for (int i = 0; i < 10; i++) {
      Assert.assertTrue(verifier.verifySessionForUser("admin"));
      Assert.assertTrue(verifier.verifySessionForUser("tom"));
    }
  }
}

