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
package org.apache.knox.gateway.filter.rewrite.impl;

import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.knox.gateway.dispatch.InputStreamEntity;
import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteProcessor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRuleDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptorFactory;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletContextListener;
import org.apache.knox.gateway.filter.rewrite.ext.UrlRewriteActionRewriteDescriptorExt;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.util.urltemplate.Template;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.servlet.FilterConfig;
import javax.servlet.ReadListener;
import javax.servlet.ServletContext;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

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

  @Test
  public void testEmptyPayload() throws Exception {

    /* copy results */
    final ByteArrayOutputStream results = new ByteArrayOutputStream();
    final ByteArrayInputStream bai = new ByteArrayInputStream(
        "".getBytes(StandardCharsets.UTF_8));

    final ServletInputStream payload = new ServletInputStream() {

      @Override
      public int read() throws IOException {
        return bai.read();
      }

      @Override
      public int available() throws IOException {
        return bai.available();
      }

      @Override
      public boolean isFinished() {
        return false;
      }

      @Override
      public boolean isReady() {
        return false;
      }

      @Override
      public void setReadListener(ReadListener readListener) {

      }
    };

    UrlRewriteProcessor rewriter = EasyMock
        .createNiceMock(UrlRewriteProcessor.class);

    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getServletContextName())
        .andReturn("test-cluster-name").anyTimes();
    EasyMock.expect(context.getInitParameter("test-init-param-name"))
        .andReturn("test-init-param-value").anyTimes();
    EasyMock.expect(context.getAttribute(
        UrlRewriteServletContextListener.PROCESSOR_ATTRIBUTE_NAME))
        .andReturn(rewriter).anyTimes();

    FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(config.getInitParameter("test-filter-init-param-name"))
        .andReturn("test-filter-init-param-value").anyTimes();
    EasyMock.expect(config.getServletContext()).andReturn(context).anyTimes();

    HttpServletRequest request = EasyMock
        .createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getScheme()).andReturn("https").anyTimes();
    EasyMock.expect(request.getServerName()).andReturn("targethost.com")
        .anyTimes();
    EasyMock.expect(request.getServerPort()).andReturn(80).anyTimes();
    EasyMock.expect(request.getRequestURI()).andReturn("/").anyTimes();
    EasyMock.expect(request.getQueryString()).andReturn(null).anyTimes();
    EasyMock.expect(request.getHeader("Host")).andReturn("sourcehost.com")
        .anyTimes();

    EasyMock.expect(request.getMethod()).andReturn("POST").anyTimes();
    EasyMock.expect(request.getContentType())
        .andReturn("application/xml").anyTimes();
    EasyMock.expect(request.getInputStream()).andReturn(payload).anyTimes();
    EasyMock.expect(request.getContentLength()).andReturn(-1).anyTimes();

    HttpServletResponse response = EasyMock
        .createNiceMock(HttpServletResponse.class);
    //    EasyMock.replay( rewriter, context, config, request, response );
    EasyMock.replay(rewriter, context, config, request, response);

    // instantiate UrlRewriteRequest so that we can use it as a Template factory for targetUrl
    UrlRewriteRequest rewriteRequest = new UrlRewriteRequest(config, request);

    ServletInputStream inputStream = rewriteRequest.getInputStream();
    HttpEntity entity = new InputStreamEntity(inputStream,
        request.getContentLength(), ContentType.parse("application/xml"));
    entity.writeTo(results);

  }

  /*
   * Test the case where a request has
   * Content-Type:text/xml and Content-Encoding:gzip
   */
  @Test
  public void testContentEncoding() throws Exception {
    /* copy results */
    final ByteArrayOutputStream results = new ByteArrayOutputStream();

    final InputStream input = Files.newInputStream(
        Paths.get(ClassLoader.getSystemResource("KNOX-1412.xml.gz").toURI()));
    final ServletInputStream payload = new ServletInputStream() {

      @Override
      public int read() throws IOException {
        return input.read();
      }

      @Override
      public boolean isFinished() {
        return false;
      }

      @Override
      public boolean isReady() {
        return false;
      }

      @Override
      public void setReadListener(ReadListener readListener) {

      }
    };

    GatewayServices gatewayServices = EasyMock
        .createNiceMock(GatewayServices.class);
    UrlRewriteEnvironment environment = EasyMock
        .createNiceMock(UrlRewriteEnvironment.class);
    EasyMock.expect(
        environment.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE))
        .andReturn(gatewayServices).anyTimes();
    EasyMock.expect(
        environment.getAttribute("org.apache.knox.gateway.frontend.uri"))
        .andReturn(new URI("https://test-location")).anyTimes();
    EasyMock.expect(environment.resolve("cluster.name"))
        .andReturn(Collections.singletonList("test-cluster-name")).anyTimes();

    EasyMock.replay(gatewayServices, environment);

    UrlRewriteRulesDescriptor descriptor = UrlRewriteRulesDescriptorFactory
        .create();
    UrlRewriteRuleDescriptor rule = descriptor.addRule("test-location");
    rule.pattern("{*}://{*}:{*}/{**}/?{**}");
    UrlRewriteActionRewriteDescriptorExt rewrite = rule.addStep("rewrite");
    rewrite.template("{$inboundurl[host]}");
    UrlRewriteProcessor rewriter = new UrlRewriteProcessor();
    rewriter.initialize(environment, descriptor);

    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    EasyMock.expect(context.getServletContextName())
        .andReturn("test-cluster-name").anyTimes();
    EasyMock.expect(context.getInitParameter("test-init-param-name"))
        .andReturn("test-init-param-value").anyTimes();
    EasyMock.expect(context.getAttribute(
        UrlRewriteServletContextListener.PROCESSOR_ATTRIBUTE_NAME))
        .andReturn(rewriter).anyTimes();

    FilterConfig config = EasyMock.createNiceMock(FilterConfig.class);
    EasyMock.expect(config.getInitParameter("test-filter-init-param-name"))
        .andReturn("test-filter-init-param-value").anyTimes();
    EasyMock.expect(config.getServletContext()).andReturn(context).anyTimes();

    /* Request wih Content-Type:text/xml and Content-Encoding:gzip */
    HttpServletRequest request1 = EasyMock
        .createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request1.getScheme()).andReturn("https").anyTimes();
    EasyMock.expect(request1.getServerName()).andReturn("targethost.com")
        .anyTimes();
    EasyMock.expect(request1.getServerPort()).andReturn(80).anyTimes();
    EasyMock.expect(request1.getRequestURI()).andReturn("/").anyTimes();
    EasyMock.expect(request1.getQueryString()).andReturn(null).anyTimes();
    EasyMock.expect(request1.getInputStream()).andReturn(payload).anyTimes();
    EasyMock.expect(request1.getContentLength()).andReturn(input.available())
        .anyTimes();
    EasyMock.expect(request1.getContentType()).andReturn("text/xml").anyTimes();
    EasyMock.expect(request1.getHeader("Content-Encoding")).andReturn("gzip")
        .anyTimes();
    EasyMock.expect(request1.getHeader("Host")).andReturn("sourcehost.com")
        .anyTimes();

    /* Request wih Content-Type:application/gzip and Content-Encoding:gzip */
    HttpServletRequest request2 = EasyMock
        .createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request2.getScheme()).andReturn("https").anyTimes();
    EasyMock.expect(request2.getServerName()).andReturn("targethost.com")
        .anyTimes();
    EasyMock.expect(request2.getServerPort()).andReturn(80).anyTimes();
    EasyMock.expect(request2.getRequestURI()).andReturn("/").anyTimes();
    EasyMock.expect(request2.getQueryString()).andReturn(null).anyTimes();
    EasyMock.expect(request2.getInputStream()).andReturn(payload).anyTimes();
    EasyMock.expect(request2.getContentLength()).andReturn(input.available())
        .anyTimes();
    EasyMock.expect(request2.getContentType()).andReturn("application/gzip")
        .anyTimes();
    EasyMock.expect(request2.getHeader("Content-Encoding")).andReturn("gzip")
        .anyTimes();
    EasyMock.expect(request2.getHeader("Host")).andReturn("sourcehost.com")
        .anyTimes();

    /* Request wih Content-Type:application/gzip no content encoding */
    HttpServletRequest request3 = EasyMock
        .createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request3.getScheme()).andReturn("https").anyTimes();
    EasyMock.expect(request3.getServerName()).andReturn("targethost.com")
        .anyTimes();
    EasyMock.expect(request3.getServerPort()).andReturn(80).anyTimes();
    EasyMock.expect(request3.getRequestURI()).andReturn("/").anyTimes();
    EasyMock.expect(request3.getQueryString()).andReturn(null).anyTimes();
    EasyMock.expect(request3.getInputStream()).andReturn(payload).anyTimes();
    EasyMock.expect(request3.getContentLength()).andReturn(input.available())
        .anyTimes();
    EasyMock.expect(request3.getContentType()).andReturn("application/gzip")
        .anyTimes();
    EasyMock.expect(request3.getHeader("Host")).andReturn("sourcehost.com")
        .anyTimes();

    HttpServletResponse response = EasyMock
        .createNiceMock(HttpServletResponse.class);

    EasyMock.replay(context, config, response, request1, request2, request3);

    /* make sure the following exception is not thrown
     * java.lang.RuntimeException: com.ctc.wstx.exc.WstxUnexpectedCharException:
     * Illegal character ((CTRL-CHAR, code 31))
     */

    /* Test for condition where Content-Type:text/xml and Content-Encoding:gzip */
    UrlRewriteRequest rewriteRequest = new UrlRewriteRequest(config, request1);
    ServletInputStream inputStream = rewriteRequest.getInputStream();
    HttpEntity entity = new InputStreamEntity(inputStream,
        request1.getContentLength(), ContentType.parse("text/xml"));
    entity.writeTo(results);

    /* Test for condition where Content-Type:application/gzip and Content-Encoding:gzip */
    rewriteRequest = new UrlRewriteRequest(config, request2);
    inputStream = rewriteRequest.getInputStream();
    entity = new InputStreamEntity(inputStream, request1.getContentLength(),
        ContentType.parse("application/gzip"));
    entity.writeTo(results);

    /* Test for condition where Content-Type:application/gzip no content encoding */
    rewriteRequest = new UrlRewriteRequest(config, request3);
    inputStream = rewriteRequest.getInputStream();
    entity = new InputStreamEntity(inputStream, request1.getContentLength(),
        ContentType.parse("application/gzip"));
    entity.writeTo(results);

  }

}
