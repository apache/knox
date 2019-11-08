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
package org.apache.knox.gateway.shell.knox.token;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.util.EntityUtils;
import org.apache.knox.gateway.shell.KnoxSession;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.Callable;

public class TokenTest {

  @Test
  public void testTokenWithNoDoAs() {
    testToken(false, null);
  }

  @Test
  public void testTokenWithNullDoAs() {
    testToken(true, null);
  }

  @Test
  public void testTokenWithEmptyDoAs() {
    testToken(true, "");
  }

  @Test
  public void testTokenWithDoAs() {
    testToken(true, "userA");
  }


  @Test
  public void testTokenRenewalWithNoDoAs() throws Exception {
    testRenewToken(false, null);
  }

  @Test
  public void testTokenRenewalWithNullDoAs() throws Exception {
    testRenewToken(true, null);
  }

  @Test
  public void testTokenRenewalWithEmptyDoAs() throws Exception {
    testRenewToken(true, "");
  }

  @Test
  public void testTokenRenewalWithDoAs() throws Exception {
    testRenewToken(true, "userA");
  }

  @Test
  public void testTokenRevocationWithNoDoAs() throws Exception {
    testRevokeToken(false, null);
  }

  @Test
  public void testTokenRecationWithNullDoAs() throws Exception {
    testRevokeToken(true, null);
  }

  @Test
  public void testTokenRevocationWithEmptyDoAs() throws Exception {
    testRevokeToken(true, "");
  }

  @Test
  public void testTokenRevocationWithDoAs() throws Exception {
    testRevokeToken(true, "userA");
  }


  private void testToken(boolean setDoAsUser, String doAsUser) {
    KnoxSession knoxSession = createMock(KnoxSession.class);
    expect(knoxSession.base()).andReturn("http://localhost/base").atLeastOnce();
    replay(knoxSession);

    Get.Request request = (setDoAsUser)
        ? Token.get(knoxSession, doAsUser)
        : Token.get(knoxSession);

    if (setDoAsUser) {
      assertEquals(doAsUser, request.getDoAsUser());
    } else {
      assertNull(request.getDoAsUser());
    }

    if (setDoAsUser && StringUtils.isNotEmpty(doAsUser)) {
      assertEquals("http://localhost/base/knoxtoken/api/v1/token?doAs=" + doAsUser, request.getRequestURI().toString());
    } else {
      assertEquals("http://localhost/base/knoxtoken/api/v1/token", request.getRequestURI().toString());
    }

    assertSame(knoxSession, request.getSession());

    verify(knoxSession);
  }

  private void testRenewToken(boolean setDoAsUser, String doAsUser) throws Exception {
    final String testToken = "RENEW+ABCDEFG123456";

    final KnoxSession knoxSession = createMockKnoxSession();

    Renew.Request request = (setDoAsUser)
                              ? Token.renew(knoxSession, testToken, doAsUser)
                              : Token.renew(knoxSession, testToken);

    if (setDoAsUser) {
      assertEquals(doAsUser, request.getDoAsUser());
    } else {
      assertNull(request.getDoAsUser());
    }

    testTokenLifecyle(request, testToken);

    assertSame(knoxSession, request.getSession());
    verify(knoxSession);
  }


  private void testRevokeToken(boolean setDoAsUser, String doAsUser) throws Exception {
    final String testToken = "REVOKE+ABCDEFG123456";

    final KnoxSession knoxSession = createMockKnoxSession();

    Revoke.Request request = (setDoAsUser)
                                ? Token.revoke(knoxSession, testToken, doAsUser)
                                : Token.revoke(knoxSession, testToken);

    if (setDoAsUser) {
      assertEquals(doAsUser, request.getDoAsUser());
    } else {
      assertNull(request.getDoAsUser());
    }

    testTokenLifecyle(request, testToken);

    assertSame(knoxSession, request.getSession());
    verify(knoxSession);
  }

  private KnoxSession createMockKnoxSession() throws Exception {
    KnoxSession knoxSession = createMock(KnoxSession.class);
    expect(knoxSession.base()).andReturn("http://localhost/base").atLeastOnce();
    expect(knoxSession.getHeaders()).andReturn(Collections.emptyMap()).atLeastOnce();
    expect(knoxSession.executeNow(isA(HttpRequest.class))).andReturn(null).atLeastOnce();
    replay(knoxSession);
    return knoxSession;
  }

  private void testTokenLifecyle(AbstractTokenLifecycleRequest request, final String testToken) throws Exception {

    final String expectedEndpointPath =
                    request.getSession().base()+ "/knoxtoken/api/v1/token/" + request.getOperation();

    String doAsUser = request.getDoAsUser();
    if (doAsUser != null && doAsUser.isEmpty()) {
      doAsUser = null;
    }

    assertEquals(expectedEndpointPath + (doAsUser != null ? ("?doAs=" + doAsUser) : ""),
                 request.getRequestURI().toString());

    assertEquals(testToken, request.getToken());

    Callable<TokenLifecycleResponse> callable = request.callable();
    try {
      callable.call();
    } catch (Exception e) {
      // expected
    }

    HttpEntity entity = request.getRequest().getEntity();
    assertNotNull("Missing expected POST data.", entity);
    String postData = null;
    try {
      postData = EntityUtils.toString(entity);
    } catch (IOException e) {
      e.printStackTrace();
    }
    assertEquals(testToken, postData);
  }

}