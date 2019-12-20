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
package org.apache.knox.gateway.util;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

public class CookieUtilsTest {
  @Test
  public void testNoCookies() {
    HttpServletRequest httpServletRequest = EasyMock.createMock(HttpServletRequest.class);
    EasyMock.expect(httpServletRequest.getCookies()).andReturn(null);
    EasyMock.replay(httpServletRequest);

    Assert.assertTrue(CookieUtils.getCookiesForName(httpServletRequest, "any").isEmpty());
  }

  @Test
  public void testNoCookiesByName() {
    Cookie[] cookies = new Cookie[] {
        new Cookie("one", "1"),
        new Cookie("two", "2")
    };

    HttpServletRequest httpServletRequest = EasyMock.createMock(HttpServletRequest.class);
    EasyMock.expect(httpServletRequest.getCookies()).andReturn(cookies);
    EasyMock.replay(httpServletRequest);

    Assert.assertTrue(CookieUtils.getCookiesForName(httpServletRequest, "noMatch").isEmpty());
  }

  @Test
  public void testCookiesByName() {
    Cookie one = new Cookie("one", "1");
    Cookie two = new Cookie("two", "2");
    Cookie onea = new Cookie("one", "1a");
    Cookie[] expectedCookies = new Cookie[] { one, two, onea };

    HttpServletRequest httpServletRequest = EasyMock.createMock(HttpServletRequest.class);
    EasyMock.expect(httpServletRequest.getCookies()).andReturn(expectedCookies);
    EasyMock.replay(httpServletRequest);

    List<Cookie> actualCookies = CookieUtils.getCookiesForName(httpServletRequest, "one");
    Assert.assertEquals(2, actualCookies.size());
    Assert.assertEquals(one, actualCookies.get(0));
    Assert.assertEquals(onea, actualCookies.get(1));
  }
}
