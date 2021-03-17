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

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.HttpClients;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.ha.provider.HaDescriptor;
import org.apache.knox.gateway.ha.provider.HaProvider;
import org.apache.knox.gateway.ha.provider.HaServletContextListener;
import org.apache.knox.gateway.ha.provider.impl.DefaultHaProvider;
import org.apache.knox.gateway.ha.provider.impl.HaDescriptorFactory;
import org.apache.knox.gateway.servlet.SynchronousServletOutputStreamAdapter;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.params.BasicHttpParams;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Assert;
import org.junit.Test;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultHaDispatchTest {

  @Test
  public void testConnectivityFailover() throws Exception {
    String serviceName = "OOZIE";
    HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, null, null, null, null));
    HaProvider provider = new DefaultHaProvider(descriptor);
    URI uri1 = new URI( "http://unreachable-host.invalid" );
    URI uri2 = new URI( "http://reachable-host.invalid" );
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

    HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(inboundRequest.getRequestURL()).andReturn( new StringBuffer(uri2.toString()) ).once();
    EasyMock.expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(0)).once();
    EasyMock.expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(1)).once();

    HttpServletResponse outboundResponse = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.expect(outboundResponse.getOutputStream()).andAnswer( new IAnswer<SynchronousServletOutputStreamAdapter>() {
      @Override
      public SynchronousServletOutputStreamAdapter answer() {
        return new SynchronousServletOutputStreamAdapter() {
          @Override
          public void write( int b ) throws IOException {
            throw new IOException( "unreachable-host.invalid" );
          }
        };
      }
    }).once();
    EasyMock.replay(filterConfig, servletContext, outboundRequest, inboundRequest, outboundResponse);
    Assert.assertEquals(uri1.toString(), provider.getActiveURL(serviceName));
    ConfigurableHADispatch dispatch = new ConfigurableHADispatch();
    HttpClientBuilder builder = HttpClientBuilder.create();
    CloseableHttpClient client = builder.build();
    dispatch.setHttpClient(client);
    dispatch.setHaProvider(provider);
    dispatch.setServiceRole(serviceName);
    dispatch.init();
    long startTime = System.currentTimeMillis();
    try {
      dispatch.executeRequestWrapper(outboundRequest, inboundRequest, outboundResponse);
    } catch (IOException e) {
      //this is expected after the failover limit is reached
    }
    long elapsedTime = System.currentTimeMillis() - startTime;
    Assert.assertEquals(uri2.toString(), provider.getActiveURL(serviceName));
    //test to make sure the sleep took place
    Assert.assertTrue(elapsedTime > 1000);
  }

  /**
   * Test failover when loadbalancing=false, sticky=true, nofallback=true.
   * should failover.
   * @throws Exception
   */
  @Test
  public void testNoLoadbalancingStickyFailoverNoFallback() throws Exception {
    String serviceName = "OOZIE";
    HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, null, "true", null, "true"));
    HaProvider provider = new DefaultHaProvider(descriptor);
    URI uri1 = new URI( "http://unreachable-host.invalid" );
    URI uri2 = new URI( "http://reachable-host.invalid" );
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

    HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(inboundRequest.getRequestURL()).andReturn( new StringBuffer(uri2.toString()) ).once();
    EasyMock.expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(0)).once();
    EasyMock.expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(1)).once();

    HttpServletResponse outboundResponse = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.expect(outboundResponse.getOutputStream()).andAnswer( new IAnswer<SynchronousServletOutputStreamAdapter>() {
      @Override
      public SynchronousServletOutputStreamAdapter answer() {
        return new SynchronousServletOutputStreamAdapter() {
          @Override
          public void write( int b ) throws IOException {
            throw new IOException( "unreachable-host.invalid" );
          }
        };
      }
    }).once();
    EasyMock.replay(filterConfig, servletContext, outboundRequest, inboundRequest, outboundResponse);
    Assert.assertEquals(uri1.toString(), provider.getActiveURL(serviceName));
    ConfigurableHADispatch dispatch = new ConfigurableHADispatch();
    HttpClientBuilder builder = HttpClientBuilder.create();
    CloseableHttpClient client = builder.build();
    dispatch.setHttpClient(client);
    dispatch.setHaProvider(provider);
    dispatch.setServiceRole(serviceName);
    dispatch.init();
    try {
      dispatch.executeRequestWrapper(outboundRequest, inboundRequest, outboundResponse);
    } catch (IOException e) {
      //this is expected after the failover limit is reached
    }
    /* since fallback happens */
    Assert.assertEquals(uri2.toString(), provider.getActiveURL(serviceName));
  }

  /**
   * This is a negative test for noFallback flag
   * When sticky session is disabled noFallback should not have any effect
   * i.e. request should failover.
   * @throws Exception
   */
  @Test
  public void testNoFallbackWhenStickyDisabled() throws Exception {
    String serviceName = "OOZIE";
    HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, null, null, null, "true"));
    HaProvider provider = new DefaultHaProvider(descriptor);
    URI uri1 = new URI( "http://unreachable-host.invalid" );
    URI uri2 = new URI( "http://reachable-host.invalid" );
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

    HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(inboundRequest.getRequestURL()).andReturn( new StringBuffer(uri2.toString()) ).once();
    EasyMock.expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(0)).once();
    EasyMock.expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(1)).once();

    HttpServletResponse outboundResponse = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.expect(outboundResponse.getOutputStream()).andAnswer( new IAnswer<SynchronousServletOutputStreamAdapter>() {
      @Override
      public SynchronousServletOutputStreamAdapter answer() {
        return new SynchronousServletOutputStreamAdapter() {
          @Override
          public void write( int b ) throws IOException {
            throw new IOException( "unreachable-host.invalid" );
          }
        };
      }
    }).once();
    EasyMock.replay(filterConfig, servletContext, outboundRequest, inboundRequest, outboundResponse);
    Assert.assertEquals(uri1.toString(), provider.getActiveURL(serviceName));
    ConfigurableHADispatch dispatch = new ConfigurableHADispatch();
    HttpClientBuilder builder = HttpClientBuilder.create();
    CloseableHttpClient client = builder.build();
    dispatch.setHttpClient(client);
    dispatch.setHaProvider(provider);
    dispatch.setServiceRole(serviceName);
    dispatch.init();
    long startTime = System.currentTimeMillis();
    try {
      dispatch.executeRequestWrapper(outboundRequest, inboundRequest, outboundResponse);
    } catch (IOException e) {
      //this is expected after the failover limit is reached
    }
    long elapsedTime = System.currentTimeMillis() - startTime;
    Assert.assertEquals(uri2.toString(), provider.getActiveURL(serviceName));
    //test to make sure the sleep took place
    Assert.assertTrue(elapsedTime > 1000);
  }

  /**
   * Test the case where loadbalancing is off and sticky session is on
   * Expected behavior: When loadbalncing is off sticky sessions on is
   * that there should be no url loadbalancing
   * @throws Exception
   */
  @Test
  public void testLoadbalancingOffStickyOn() throws Exception {
    String serviceName = "OOZIE";
    HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, null, "true", null, null));
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

    /* backend request */
    HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(inboundRequest.getRequestURL()).andReturn( new StringBuffer(uri2.toString()) ).once();
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
    /* make sure the url is not ladbalanced since fallback did not happen */
    Assert.assertEquals(uri1.toString(), provider.getActiveURL(serviceName));
  }

  /**
   * Test the case where loadbalancing is on
   * Expected behavior: When loadbalncing is on then urls should loadbalance
   * @throws Exception
   */
  @Test
  public void testLoadbalancingOn() throws Exception {
    String serviceName = "OOZIE";
    HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, "true", null, null, null));
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

    /* backend request */
    HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(inboundRequest.getRequestURL()).andReturn( new StringBuffer(uri2.toString()) ).once();
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
  }

  /**
   * Test the case where loadbalancing is on and sticky session is on
   * Expected behavior: When loadbalncing is on and sticky session
   * is on = urls should loadbalance with sticky session
   * @throws Exception
   */
  @Test
  public void testLoadbalancingOnStickyOn() throws Exception {
    String serviceName = "OOZIE";
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

    /* backend request */
    HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(inboundRequest.getRequestURL()).andReturn( new StringBuffer(uri2.toString()) ).once();
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
    /* make sure the url is loadbalanced */
    Assert.assertEquals(uri2.toString(), provider.getActiveURL(serviceName));
  }

  /**
   * Test the case where sticky session is on (and loadbalancing is on)
   * Expected behavior: When
   * @throws Exception
   */
  @Test
  public void testStickyOn() throws Exception {
    String serviceName = "OOZIE";
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
    EasyMock.expect(outboundRequest.getURI()).andReturn( uri2  ).anyTimes();
    EasyMock.expect(outboundRequest.getParams()).andReturn( params ).anyTimes();

    /* backend request with cookie for url2 */
    //http://host2.valid = 59973e253ae20de796c6ef413608ec1c80fca24310a4cbdecc0ff97aeea55745
    Cookie[] cookie = new Cookie[] { new Cookie("KNOX_BACKEND-OOZIE","59973e253ae20de796c6ef413608ec1c80fca24310a4cbdecc0ff97aeea55745")};
    HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(inboundRequest.getRequestURL()).andReturn( new StringBuffer(uri2.toString()) ).once();
    EasyMock.expect(inboundRequest.getCookies()).andReturn( cookie ).anyTimes();
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
    /* sticky session is on do not loadbalance */
    Assert.assertEquals(uri1.toString(), provider.getActiveURL(serviceName));
  }


  @Test
  public void testConnectivityActive() throws Exception {
    String serviceName = "OOZIE";
    HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, null, "true", null, null));
    HaProvider provider = new DefaultHaProvider(descriptor);
    URI uri1 = new URI( "http://unreachable-host" );
    URI uri2 = new URI( "http://reachable-host" );
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

    HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(inboundRequest.getRequestURL()).andReturn( new StringBuffer(uri2.toString()) ).once();
    EasyMock.expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(0)).once();
    EasyMock.expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(1)).once();

    HttpServletResponse outboundResponse = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.expect(outboundResponse.getOutputStream()).andAnswer( new IAnswer<SynchronousServletOutputStreamAdapter>() {
      @Override
      public SynchronousServletOutputStreamAdapter answer() {
        return new SynchronousServletOutputStreamAdapter() {
          @Override
          public void write( int b ) throws IOException {
            throw new IOException( "unreachable-host" );
          }
        };
      }
    }).once();
    EasyMock.replay(filterConfig, servletContext, outboundRequest, inboundRequest, outboundResponse);
    Assert.assertEquals(uri1.toString(), provider.getActiveURL(serviceName));
    DefaultHaDispatch dispatch = new DefaultHaDispatch();
    dispatch.setHttpClient(HttpClients.createDefault());
    dispatch.setHaProvider(provider);
    dispatch.setServiceRole(serviceName);
    dispatch.init();
  }

  /**
   * test a case where a service might want to mark a host failed without retrying
   * so the next request that comes goes to the other HA host.
   * This can be achieved by using maxFailoverAttempsValue=0
   * @throws Exception
   */
  @Test
  public void testMarkedFailedWithoutRetry() throws Exception {
    String serviceName = "OOZIE";
    HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(serviceName, "true", "0", "1000", null, null, null, null, null, null));
    HaProvider provider = new DefaultHaProvider(descriptor);
    URI uri1 = new URI( "http://unreachable-host.invalid" );
    URI uri2 = new URI( "http://reachable-host.invalid" );
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

    HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(inboundRequest.getRequestURL()).andReturn( new StringBuffer(uri2.toString()) ).once();
    EasyMock.expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(0)).once();
    EasyMock.expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(1)).once();

    HttpServletResponse outboundResponse = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.expect(outboundResponse.getOutputStream()).andAnswer( new IAnswer<SynchronousServletOutputStreamAdapter>() {
      @Override
      public SynchronousServletOutputStreamAdapter answer() {
        return new SynchronousServletOutputStreamAdapter() {
          @Override
          public void write( int b ) throws IOException {
            throw new IOException( "unreachable-host.invalid" );
          }
        };
      }
    }).once();
    EasyMock.replay(filterConfig, servletContext, outboundRequest, inboundRequest, outboundResponse);
    Assert.assertEquals(uri1.toString(), provider.getActiveURL(serviceName));
    DefaultHaDispatch dispatch = new DefaultHaDispatch();
    HttpClientBuilder builder = HttpClientBuilder.create();
    CloseableHttpClient client = builder.build();
    dispatch.setHttpClient(client);
    dispatch.setHaProvider(provider);
    dispatch.setServiceRole(serviceName);
    dispatch.init();
    try {
      dispatch.executeRequest(outboundRequest, inboundRequest, outboundResponse);
    } catch (IOException e) {
      //this is expected after the failover limit is reached
    }
    /* make sure active url list got updated */
    Assert.assertEquals(uri2.toString(), provider.getActiveURL(serviceName));
  }

}
