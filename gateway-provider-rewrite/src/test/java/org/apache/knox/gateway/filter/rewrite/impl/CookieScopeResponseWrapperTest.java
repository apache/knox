/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.filter.rewrite.impl;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletResponse;

public class CookieScopeResponseWrapperTest {

  private HttpServletResponse mock;

  private Capture<String> captureKey;

  private Capture<String> captureValue;

  @Before
  public void init(){
    mock = EasyMock.createNiceMock(HttpServletResponse.class);
    captureKey = Capture.newInstance();
    captureValue = Capture.newInstance();
    mock.addHeader( EasyMock.capture(captureKey), EasyMock.capture(captureValue));
    EasyMock.replay(mock);
  }

  @Test
  public void testNoPath() {
    CookieScopeResponseWrapper underTest = new CookieScopeResponseWrapper(mock, "gw");
    underTest.addHeader("Set-Cookie", "SESSIONID=jn0zexg59r1jo1n66hd7tg5anl;HttpOnly;");

    Assert.assertEquals("Set-Cookie", captureKey.getValue());
    Assert.assertEquals("SESSIONID=jn0zexg59r1jo1n66hd7tg5anl;HttpOnly; Path=/gw/;", captureValue.getValue());
  }

  @Test
  public void testRootPath() {
    CookieScopeResponseWrapper underTest = new CookieScopeResponseWrapper(mock, "gw");
    underTest.addHeader("Set-Cookie", "SESSIONID=jn0zexg59r1jo1n66hd7tg5anl; Path=/; HttpOnly;");

    Assert.assertEquals("Set-Cookie", captureKey.getValue());
    Assert.assertEquals("SESSIONID=jn0zexg59r1jo1n66hd7tg5anl; Path=/gw/; HttpOnly;", captureValue.getValue());
  }

  @Test
  public void testRootLowerCasePath() {
    CookieScopeResponseWrapper underTest = new CookieScopeResponseWrapper(mock, "gw");
    underTest.addHeader("Set-Cookie", "SESSIONID=jn0zexg59r1jo1n66hd7tg5anl; path=/; HttpOnly;");

    Assert.assertEquals("Set-Cookie", captureKey.getValue());
    Assert.assertEquals("SESSIONID=jn0zexg59r1jo1n66hd7tg5anl; Path=/gw/; HttpOnly;", captureValue.getValue());
  }

  @Test
  public void testMultiSegmentPath() {
    CookieScopeResponseWrapper underTest = new CookieScopeResponseWrapper(mock, "some/path");
    underTest.addHeader("Set-Cookie", "SESSIONID=jn0zexg59r1jo1n66hd7tg5anl; Path=/; HttpOnly;");

    Assert.assertEquals("Set-Cookie", captureKey.getValue());
    Assert.assertEquals("SESSIONID=jn0zexg59r1jo1n66hd7tg5anl; Path=/some/path/; HttpOnly;", captureValue.getValue());
  }

  @Test
  public void testAlreadyScopedPath() {
    CookieScopeResponseWrapper underTest = new CookieScopeResponseWrapper(mock, "some/path");
    underTest.addHeader("Set-Cookie", "SESSIONID=jn0zexg59r1jo1n66hd7tg5anl; Path=/already-scoped/; HttpOnly;");

    Assert.assertEquals("Set-Cookie", captureKey.getValue());
    Assert.assertEquals("SESSIONID=jn0zexg59r1jo1n66hd7tg5anl; Path=/some/path/already-scoped/; HttpOnly;", captureValue.getValue());
  }

  @Test
  public void testCaseSensitive() {
    CookieScopeResponseWrapper underTest = new CookieScopeResponseWrapper(mock, "some/path");
    underTest.addHeader("set-cookie", "SESSIONID=jn0zexg59r1jo1n66hd7tg5anl; Path=/not-touched/; HttpOnly;");

    Assert.assertEquals("set-cookie", captureKey.getValue());
    Assert.assertEquals("SESSIONID=jn0zexg59r1jo1n66hd7tg5anl; Path=/not-touched/; HttpOnly;", captureValue.getValue());
  }

  @Test
  public void testWithPathAndTopologyName() {
    CookieScopeResponseWrapper underTest = new CookieScopeResponseWrapper(mock, "some/path", "dp-proxy");
    underTest.addHeader("Set-Cookie", "SESSIONID=jn0zexg59r1jo1n66hd7tg5anl; Path=/; HttpOnly;");

    Assert.assertEquals("Set-Cookie", captureKey.getValue());
    Assert.assertEquals("SESSIONID=jn0zexg59r1jo1n66hd7tg5anl; Path=/some/path/dp-proxy/; HttpOnly;", captureValue.getValue());
  }

  @Test
  public void gatewayPathIsInvalid() {
      CookieScopeResponseWrapper underTest = new CookieScopeResponseWrapper(mock, "/", "dp-proxy");
      underTest.addHeader("Set-Cookie", "SESSIONID=jn0zexg59r1jo1n66hd7tg5anl; Path=/; HttpOnly;");

      Assert.assertEquals("Set-Cookie", captureKey.getValue());
      Assert.assertEquals("SESSIONID=jn0zexg59r1jo1n66hd7tg5anl; Path=/dp-proxy/; HttpOnly;", captureValue.getValue());
  }

  @Test
  public void topologyNameIsInvalid() {
      CookieScopeResponseWrapper underTest = new CookieScopeResponseWrapper(mock, "some/path", "");
      underTest.addHeader("Set-Cookie", "SESSIONID=jn0zexg59r1jo1n66hd7tg5anl; Path=/; HttpOnly;");

      Assert.assertEquals("Set-Cookie", captureKey.getValue());
      Assert.assertEquals("SESSIONID=jn0zexg59r1jo1n66hd7tg5anl; Path=/some/path/; HttpOnly;", captureValue.getValue());
  }
}
