/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.knox.gateway.shirorealm;

import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.credential.CredentialsMatcher;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KnoxPamRealmHashRoundTripTest {

  /** Exposes the protected createAuthenticationInfo for testing. */
  private static class TestableRealm extends KnoxPamRealm {
    AuthenticationInfo build(AuthenticationToken token) {
      return createAuthenticationInfo(token, token.getPrincipal());
    }
  }

  @Test
  public void correctPasswordMatchesUnderShiro221() {
    TestableRealm realm = new TestableRealm();
    UsernamePasswordToken stored = new UsernamePasswordToken("alice", "s3cr3t");
    AuthenticationInfo info = realm.build(stored);

    CredentialsMatcher matcher = realm.getCredentialsMatcher();
    UsernamePasswordToken submittedGood = new UsernamePasswordToken("alice", "s3cr3t");
    assertTrue("correct password must match", matcher.doCredentialsMatch(submittedGood, info));
  }

  @Test
  public void wrongPasswordIsRejectedUnderShiro221() {
    TestableRealm realm = new TestableRealm();
    UsernamePasswordToken stored = new UsernamePasswordToken("alice", "s3cr3t");
    AuthenticationInfo info = realm.build(stored);

    CredentialsMatcher matcher = realm.getCredentialsMatcher();
    UsernamePasswordToken submittedBad = new UsernamePasswordToken("alice", "wrong");
    assertFalse("wrong password must not match", matcher.doCredentialsMatch(submittedBad, info));
  }
}
