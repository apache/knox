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
package org.apache.knox.gateway;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.junit.Test;

public class GatewayForwardingServletTest {
  
  @Test
  public void testRedirectDefaults() throws ServletException, IOException {
    IMocksControl mockControl = EasyMock.createControl();
    ServletConfig config = (ServletConfig)mockControl.createMock(ServletConfig.class);
    ServletContext context = (ServletContext)mockControl.createMock(ServletContext.class);
    HttpServletRequest request = (HttpServletRequest)mockControl.createMock(HttpServletRequest.class);
    HttpServletResponse response = (HttpServletResponse)mockControl.createMock(HttpServletResponse.class);
    RequestDispatcher dispatcher = (RequestDispatcher)mockControl.createMock(RequestDispatcher.class);
    // setup expectations
    EasyMock.expect(config.getServletName()).andStubReturn("default");
    EasyMock.expect(config.getServletContext()).andStubReturn(context);
    EasyMock.expect(config.getInitParameter("redirectTo")).andReturn("/gateway/sandbox");
    EasyMock.expect(request.getMethod()).andReturn("GET").anyTimes();
    EasyMock.expect(request.getPathInfo()).andReturn("/webhdfs/v1/tmp").anyTimes();
    EasyMock.expect(request.getQueryString()).andReturn("op=LISTSTATUS");
    EasyMock.expect(response.getStatus()).andReturn(200).anyTimes();
    EasyMock.expect(context.getContext("/gateway/sandbox")).andReturn(context);
    EasyMock.expect(context.getRequestDispatcher("/webhdfs/v1/tmp?op=LISTSTATUS")).andReturn(dispatcher);
    dispatcher.forward(request, response);
    EasyMock.expectLastCall().once();
    // logging
    context.log((String)EasyMock.anyObject());
    EasyMock.expectLastCall().anyTimes();
    // run the test
    mockControl.replay();
    GatewayForwardingServlet servlet = new GatewayForwardingServlet();
    servlet.init(config);
    servlet.service(request, response);
    mockControl.verify();
  }

}
