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
import org.apache.shiro.authc.UsernamePasswordToken;
import org.junit.Test;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class KnoxPamRealmTest {
  @Test
  public void setService() {
    KnoxPamRealm realm = new KnoxPamRealm();
    realm.setService("knox-pam-os-service");
    //assertEquals(realm.getService(), "knox-pam-os-service");
  }

  @Test
  public void testDoGetAuthenticationInfo() {
    KnoxPamRealm realm = new KnoxPamRealm();
    realm.setService("sshd");  // pam settings being used: /etc/pam.d/sshd

    // use environment variables and skip the test if not set.
    String pamuser = System.getenv("PAMUSER");
    String pampass = System.getenv("PAMPASS");
    assumeTrue(pamuser != null);
    assumeTrue(pampass != null);

    // mock shiro auth token
    UsernamePasswordToken authToken = createMock(UsernamePasswordToken.class);
    expect(authToken.getUsername()).andReturn(pamuser);
    expect(authToken.getPassword()).andReturn(pampass.toCharArray());
    expect(authToken.getCredentials()).andReturn(pampass);
    replay(authToken);

    // login
    AuthenticationInfo authInfo = realm.doGetAuthenticationInfo(authToken);

    // verify success
    assertTrue(authInfo.getCredentials() != null);
  }

  public static void main(String[] args) throws Exception {
    KnoxPamRealmTest pamTest = new KnoxPamRealmTest();
    pamTest.testDoGetAuthenticationInfo();
  }
}
