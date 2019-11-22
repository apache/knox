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
package org.apache.knox.gateway.shell;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createMockBuilder;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.apache.commons.lang3.StringUtils;
import org.easymock.IMockBuilder;
import org.junit.Test;

import java.net.URISyntaxException;

public class AbstractRequestTest {
  @Test
  public void testBuildUrlWithNoDoAs() throws Exception {
    testBuildURL(false, null);
  }

  @Test
  public void testBuildUrlWithNullDoAs() throws Exception {
    testBuildURL(true, null);
  }

  @Test
  public void testBuildUrlWithEmptyDoAs() throws Exception {
    testBuildURL(true, "");
  }

  @Test
  public void testBuildUrlWithDoAs() throws Exception {
    testBuildURL(true, "userA");
  }

  private void testBuildURL(boolean setDoAsUser, String doAsUser) throws URISyntaxException {
    KnoxSession knoxSession = createMock(KnoxSession.class);
    expect(knoxSession.base()).andReturn("http://localhost/base").atLeastOnce();
    replay(knoxSession);

    IMockBuilder<AbstractRequest> builder = createMockBuilder(AbstractRequest.class);

    if (setDoAsUser) {
      builder.withConstructor(KnoxSession.class, String.class);
      builder.withArgs(knoxSession, doAsUser);
    } else {
      builder.withConstructor(KnoxSession.class);
      builder.withArgs(knoxSession);
    }

    AbstractRequest<?> request = builder.createMock();
    replay(request);

    if (setDoAsUser) {
      assertEquals(doAsUser, request.getDoAsUser());
    } else {
      assertNull(request.getDoAsUser());
    }

    if (setDoAsUser && StringUtils.isNotEmpty(doAsUser)) {
      assertEquals("http://localhost/base/test?doAs=" + doAsUser, request.uri("/test").toString());
    } else {
      assertEquals("http://localhost/base/test", request.uri("/test").toString());
    }

    assertSame(knoxSession, request.getSession());

    verify(knoxSession, request);
  }
}
