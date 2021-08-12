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
package org.apache.knox.gateway.ha.dispatch;

import static org.easymock.EasyMock.capture;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.ha.provider.HaDescriptor;
import org.apache.knox.gateway.ha.provider.HaProvider;
import org.apache.knox.gateway.ha.provider.HaServletContextListener;
import org.apache.knox.gateway.ha.provider.impl.DefaultHaProvider;
import org.apache.knox.gateway.ha.provider.impl.HaDescriptorFactory;
import org.apache.knox.gateway.servlet.SynchronousServletOutputStreamAdapter;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Assert;
import org.junit.Test;

public class ConfigurableHADispatchTest {

  /**
   * Test whether the dispatch url is correctly used in case where loadbalancing is enabled
   * and sticky session is enabled making sure we dispatch requests based on the HA Provider logic and
   * not based on URL rewrite logic.
   *
   * @throws Exception
   */
  @Test
  public void testHADispatchURL() throws Exception {
    String serviceName = "HIVE";
    HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, "true", "true", null, null));
    HaProvider provider = new DefaultHaProvider(descriptor);
    URI uri1 = new URI("http://host1.valid");
    URI uri2 = new URI("http://host2.valid");
    URI uri3 = new URI("http://host3.valid");
    ArrayList<String> urlList = new ArrayList<>();
    urlList.add(uri1.toString());
    urlList.add(uri2.toString());
    urlList.add(uri3.toString());
    provider.addHaService(serviceName, urlList);


    HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(inboundRequest.getRequestURL()).andReturn(new StringBuffer(provider.getActiveURL(serviceName))).anyTimes();
    EasyMock.replay(inboundRequest);

    ConfigurableHADispatch dispatch = new ConfigurableHADispatch();
    dispatch.setHaProvider(provider);
    dispatch.setServiceRole(serviceName);
    dispatch.init();

    /* make sure the dispatch URL is always active URL */
    Assert.assertEquals(provider.getActiveURL(serviceName), dispatch.getDispatchUrl(inboundRequest).toString());
  }

  /**
   * This tests ensure that in case where HA is configured.
   * the host the the request is dispatched is the same host for
   * which HA cookie is set.
   *
   * @throws Exception
   */
  @Test
  public void testSetCookieHeader() throws Exception {
    String serviceName = "HIVE";
    HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, "true", "true", null, null));
    HaProvider provider = new DefaultHaProvider(descriptor);
    URI uri1 = new URI( "http://host1.valid" );
    URI uri2 = new URI( "http://host2.valid" );
    ArrayList<String> urlList = new ArrayList<>();
    urlList.add(uri1.toString());
    urlList.add(uri2.toString());
    provider.addHaService(serviceName, urlList);
    FilterConfig filterConfig = EasyMock.createNiceMock(FilterConfig.class);
    ServletContext servletContext = EasyMock.createNiceMock(ServletContext.class);

    EasyMock.expect(filterConfig.getServletContext()).andReturn(servletContext).anyTimes();
    EasyMock.expect(servletContext.getAttribute(HaServletContextListener.PROVIDER_ATTRIBUTE_NAME)).andReturn(provider).anyTimes();

    BasicHttpParams params = new BasicHttpParams();

    HttpUriRequest outboundRequest = EasyMock.createNiceMock(HttpRequestBase.class);
    EasyMock.expect(outboundRequest.getMethod()).andReturn( "GET" ).anyTimes();
    EasyMock.expect(outboundRequest.getURI()).andReturn( uri1  ).anyTimes();
    EasyMock.expect(outboundRequest.getParams()).andReturn( params ).anyTimes();

    /* dispatched url is the active HA url */
    String activeURL = provider.getActiveURL(serviceName);

    /* backend request */
    HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(inboundRequest.getRequestURL()).andReturn( new StringBuffer(activeURL)).once();
    EasyMock.expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(0)).once();
    EasyMock.expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(1)).once();

    /* backend response */
    CloseableHttpResponse inboundResponse = EasyMock.createNiceMock(CloseableHttpResponse.class);
    final StatusLine statusLine = EasyMock.createNiceMock(StatusLine.class);
    final HttpEntity entity = EasyMock.createNiceMock(HttpEntity.class);
    final Header header = EasyMock.createNiceMock(Header.class);
    final ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    final GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    final ByteArrayInputStream backendResponse = new ByteArrayInputStream("knox-backend".getBytes(
            StandardCharsets.UTF_8));


    EasyMock.expect(inboundResponse.getStatusLine()).andReturn(statusLine).anyTimes();
    EasyMock.expect(statusLine.getStatusCode()).andReturn(HttpStatus.SC_OK).anyTimes();
    EasyMock.expect(inboundResponse.getEntity()).andReturn(entity).anyTimes();
    EasyMock.expect(inboundResponse.getAllHeaders()).andReturn(new Header[0]).anyTimes();
    EasyMock.expect(inboundRequest.getServletContext()).andReturn(context).anyTimes();
    EasyMock.expect(entity.getContent()).andReturn(backendResponse).anyTimes();
    EasyMock.expect(entity.getContentType()).andReturn(header).anyTimes();
    EasyMock.expect(header.getElements()).andReturn(new HeaderElement[]{}).anyTimes();
    EasyMock.expect(entity.getContentLength()).andReturn(4L).anyTimes();
    EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(config).anyTimes();

    Capture<Cookie> captureCookieValue = EasyMock.newCapture();
    HttpServletResponse outboundResponse = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.expect(outboundResponse.getOutputStream()).andAnswer( new IAnswer<SynchronousServletOutputStreamAdapter>() {
      @Override
      public SynchronousServletOutputStreamAdapter answer() {
        return new SynchronousServletOutputStreamAdapter() {
          @Override
          public void write( int b ) throws IOException {
            /* do nothing */
          }
        };
      }
    }).once();

    outboundResponse.addCookie(capture(captureCookieValue));

    CloseableHttpClient mockHttpClient = EasyMock.createNiceMock(CloseableHttpClient.class);
    EasyMock.expect(mockHttpClient.execute(outboundRequest)).andReturn(inboundResponse).anyTimes();

    EasyMock.replay(filterConfig, servletContext, outboundRequest, inboundRequest,
            outboundResponse, mockHttpClient, inboundResponse,
            statusLine, entity, header, context, config);


    Assert.assertEquals(uri1.toString(), provider.getActiveURL(serviceName));
    ConfigurableHADispatch dispatch = new ConfigurableHADispatch();
    dispatch.setHttpClient(mockHttpClient);
    dispatch.setHaProvider(provider);
    dispatch.setServiceRole(serviceName);
    dispatch.init();
    try {
      dispatch.executeRequestWrapper(outboundRequest, inboundRequest, outboundResponse);
    } catch (IOException e) {
      //this is expected after the failover limit is reached
    }
    /* make sure the url is ladbalanced */
    Assert.assertEquals(uri2.toString(), provider.getActiveURL(serviceName));
    /* make sure the HA backend URL hash in set-cookie is for active URL (which was in the dispatch request) */
    Assert.assertEquals(DigestUtils.sha256Hex(activeURL), captureCookieValue.getValue().getValue());
  }

}
