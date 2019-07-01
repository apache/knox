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
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.shell.KnoxSession;
import org.junit.Test;

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
}