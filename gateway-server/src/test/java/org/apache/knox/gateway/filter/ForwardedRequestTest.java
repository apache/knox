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
package org.apache.knox.gateway.filter;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import java.util.Locale;

public class ForwardedRequestTest {
  @Test
  public void testForwardedRequestNoContextDefaultPort() {
    String scheme = "http";
    String host = "localhost";
    String context = "/abc";
    String requestURL = String.format(Locale.ROOT, "%s://%s%s", scheme, host, context);

    HttpServletRequest httpServletRequest = EasyMock.mock(HttpServletRequest.class);
    EasyMock.expect(httpServletRequest.getScheme()).andReturn(scheme);
    EasyMock.expect(httpServletRequest.getServerName()).andReturn(host);
    EasyMock.expect(httpServletRequest.getServerPort()).andReturn(-1);
    EasyMock.expect(httpServletRequest.getRequestURI()).andReturn(context).anyTimes();
    EasyMock.expect(httpServletRequest.getRequestURL()).andReturn(new StringBuffer(requestURL));
    EasyMock.replay(httpServletRequest);

    String contextPath = "";
    RequestUpdateHandler.ForwardedRequest forwardedRequest =
        new RequestUpdateHandler.ForwardedRequest(httpServletRequest, contextPath);
    Assert.assertEquals(httpServletRequest.getRequestURL().toString(),
        forwardedRequest.getRequestURL().toString());
    Assert.assertEquals(httpServletRequest.getRequestURI(), forwardedRequest.getRequestURI());
    Assert.assertEquals(contextPath, forwardedRequest.getContextPath());
  }

  @Test
  public void testForwardedRequestNoContext() {
    String scheme = "http";
    String host = "localhost";
    int port = 8443;
    String context = "/abc";
    String requestURL = String.format(Locale.ROOT, "%s://%s:%s%s", scheme, host, port, context);

    HttpServletRequest httpServletRequest = EasyMock.mock(HttpServletRequest.class);
    EasyMock.expect(httpServletRequest.getScheme()).andReturn(scheme);
    EasyMock.expect(httpServletRequest.getServerName()).andReturn(host);
    EasyMock.expect(httpServletRequest.getServerPort()).andReturn(port).anyTimes();
    EasyMock.expect(httpServletRequest.getRequestURI()).andReturn(context).anyTimes();
    EasyMock.expect(httpServletRequest.getRequestURL()).andReturn(new StringBuffer(requestURL));
    EasyMock.replay(httpServletRequest);

    String contextPath = "";
    RequestUpdateHandler.ForwardedRequest forwardedRequest =
        new RequestUpdateHandler.ForwardedRequest(httpServletRequest, contextPath);
    Assert.assertEquals(httpServletRequest.getRequestURL().toString(),
        forwardedRequest.getRequestURL().toString());
    Assert.assertEquals(contextPath, forwardedRequest.getContextPath());
  }

  @Test
  public void testForwardedRequestWithContextDefaultPort() {
    String scheme = "http";
    String host = "localhost";
    String context = "/abc";
    String requestURL = String.format(Locale.ROOT, "%s://%s%s", scheme, host, context);

    HttpServletRequest httpServletRequest = EasyMock.mock(HttpServletRequest.class);
    EasyMock.expect(httpServletRequest.getScheme()).andReturn(scheme);
    EasyMock.expect(httpServletRequest.getServerName()).andReturn(host);
    EasyMock.expect(httpServletRequest.getServerPort()).andReturn(-1);
    EasyMock.expect(httpServletRequest.getRequestURI()).andReturn(context).anyTimes();
    EasyMock.expect(httpServletRequest.getRequestURL()).andReturn(new StringBuffer(requestURL));
    EasyMock.replay(httpServletRequest);

    String contextPath = "/mycontext";
    RequestUpdateHandler.ForwardedRequest forwardedRequest =
        new RequestUpdateHandler.ForwardedRequest(httpServletRequest, contextPath);
    Assert.assertEquals(
        String.format(Locale.ROOT, "%s://%s%s", scheme, host, contextPath + context),
        forwardedRequest.getRequestURL().toString());
    Assert.assertEquals(contextPath + context, forwardedRequest.getRequestURI());
    Assert.assertEquals(contextPath, forwardedRequest.getContextPath());
  }

  @Test
  public void testForwardedRequestWithContext() {
    String scheme = "http";
    String host = "localhost";
    int port = 8443;
    String context = "/abc";
    String requestURL = String.format(Locale.ROOT, "%s://%s:%s%s", scheme, host, port, context);

    HttpServletRequest httpServletRequest = EasyMock.mock(HttpServletRequest.class);
    EasyMock.expect(httpServletRequest.getScheme()).andReturn(scheme);
    EasyMock.expect(httpServletRequest.getServerName()).andReturn(host);
    EasyMock.expect(httpServletRequest.getServerPort()).andReturn(port).anyTimes();
    EasyMock.expect(httpServletRequest.getRequestURI()).andReturn(context).anyTimes();
    EasyMock.expect(httpServletRequest.getRequestURL()).andReturn(new StringBuffer(requestURL));
    EasyMock.replay(httpServletRequest);

    String contextPath = "/mycontext";
    RequestUpdateHandler.ForwardedRequest forwardedRequest =
        new RequestUpdateHandler.ForwardedRequest(httpServletRequest, contextPath);
    Assert.assertEquals(
        String.format(Locale.ROOT, "%s://%s:%s%s", scheme, host, port, contextPath + context),
        forwardedRequest.getRequestURL().toString());
    Assert.assertEquals(contextPath + context, forwardedRequest.getRequestURI());
    Assert.assertEquals(contextPath, forwardedRequest.getContextPath());
  }
}
