/**
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
package org.apache.knox.gateway.filter.rewrite.impl;

import static org.junit.Assert.assertEquals;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteProcessor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletContextListener;
import org.apache.knox.gateway.util.urltemplate.Template;
import org.easymock.EasyMock;
import org.junit.Test;

public class UrlRewriteRequestTest {
  @Test
  public void testResolve() throws Exception {
    
    UrlRewriteProcessor rewriter = EasyMock.createNiceMock( UrlRewriteProcessor.class );

    ServletContext context = EasyMock.createNiceMock( ServletContext.class );
    EasyMock.expect( context.getServletContextName() ).andReturn( "test-cluster-name" ).anyTimes();
    EasyMock.expect( context.getInitParameter( "test-init-param-name" ) ).andReturn( "test-init-param-value" ).anyTimes();
    EasyMock.expect( context.getAttribute( UrlRewriteServletContextListener.PROCESSOR_ATTRIBUTE_NAME ) ).andReturn( rewriter ).anyTimes();

    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect( config.getInitParameter( "test-filter-init-param-name" ) ).andReturn( "test-filter-init-param-value" ).anyTimes();
    EasyMock.expect( config.getServletContext() ).andReturn( context ).anyTimes();

    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getScheme()).andReturn("https").anyTimes();
    EasyMock.expect( request.getServerName()).andReturn("targethost.com").anyTimes();
    EasyMock.expect( request.getServerPort()).andReturn(80).anyTimes();
    EasyMock.expect( request.getRequestURI()).andReturn("/").anyTimes();
    EasyMock.expect( request.getQueryString()).andReturn(null).anyTimes();
    EasyMock.expect( request.getHeader("Host")).andReturn("sourcehost.com").anyTimes();
    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
//    EasyMock.replay( rewriter, context, config, request, response );
    EasyMock.replay( rewriter, context, config, request, response );

    // instantiate UrlRewriteRequest so that we can use it as a Template factory for targetUrl
    UrlRewriteRequest rewriteRequest = new UrlRewriteRequest(config, request);
    // emulate the getTargetUrl by using the sourceUrl as the targetUrl when
    // it doesn't exist
    Template target = rewriteRequest.getSourceUrl();

    // reset the mock so that we can set the targetUrl as a request attribute for deriving
    // host header. Also set the servername to the sourcehost which would be the knox host
    // make sure that Host header is returned as the target host instead.
    EasyMock.reset(request);
    EasyMock.expect( request.getScheme()).andReturn("https").anyTimes();
    EasyMock.expect( request.getServerName()).andReturn("sourcehost.com").anyTimes();
    EasyMock.expect( request.getServerPort()).andReturn(80).anyTimes();
    EasyMock.expect( request.getRequestURI()).andReturn("/").anyTimes();
    EasyMock.expect( request.getQueryString()).andReturn(null).anyTimes();
    EasyMock.expect( request.getHeader("Host")).andReturn("sourcehost.com").anyTimes();
    EasyMock.expect( request.getAttribute(AbstractGatewayFilter.TARGET_REQUEST_URL_ATTRIBUTE_NAME))
      .andReturn(target).anyTimes();
    EasyMock.replay(request);

    String hostHeader = rewriteRequest.getHeader("Host");

    assertEquals(hostHeader, "targethost.com");
  }
}
