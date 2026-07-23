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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.ha.provider.HaDescriptor;
import org.apache.knox.gateway.ha.provider.HaProvider;
import org.apache.knox.gateway.ha.provider.HaServletContextListener;
import org.apache.knox.gateway.ha.provider.impl.DefaultHaProvider;
import org.apache.knox.gateway.ha.provider.impl.HaDescriptorFactory;
import org.apache.knox.gateway.servlet.SynchronousServletOutputStreamAdapter;
import org.easymock.Capture;
import org.easymock.CaptureType;
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
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, "true", "true", null, null, null));
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
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, "true", "true", null, null,null));
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

  /**
   * Test that a failure while copying an already-received backend response to the client
   * (e.g. a parse/rewrite error thrown from writeOutboundResponse) does NOT trigger a failover.
   * The backend has already served the request, so replaying it against another node would be wrong.
   */
  @Test
  public void testNoFailoverWhenResponseCopyFails() throws Exception {
    String serviceName = "OOZIE";
    HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", null, null, null, null, null, null, null));
    HaProvider provider = new DefaultHaProvider(descriptor);
    URI uri1 = new URI( "http://host1.valid" );
    URI uri2 = new URI( "http://host2.valid" );
    ArrayList<String> urlList = new ArrayList<>();
    urlList.add(uri1.toString());
    urlList.add(uri2.toString());
    provider.addHaService(serviceName, urlList);

    BasicHttpParams params = new BasicHttpParams();

    HttpUriRequest outboundRequest = EasyMock.createNiceMock(HttpRequestBase.class);
    EasyMock.expect(outboundRequest.getMethod()).andReturn( "GET" ).anyTimes();
    EasyMock.expect(outboundRequest.getURI()).andReturn( uri1 ).anyTimes();
    EasyMock.expect(outboundRequest.getParams()).andReturn( params ).anyTimes();

    HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(inboundRequest.getRequestURL()).andReturn( new StringBuffer(uri1.toString()) ).anyTimes();
    EasyMock.expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(0)).anyTimes();

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
            throw new IOException( "response copy failed" );
          }
        };
      }
    }).once();

    CloseableHttpClient mockHttpClient = EasyMock.createNiceMock(CloseableHttpClient.class);
    EasyMock.expect(mockHttpClient.execute(outboundRequest)).andReturn(inboundResponse).once();

    EasyMock.replay(outboundRequest, inboundRequest, outboundResponse, mockHttpClient, inboundResponse,
            statusLine, entity, header, context, config);

    ConfigurableHADispatch dispatch = new ConfigurableHADispatch();
    dispatch.setHttpClient(mockHttpClient);
    dispatch.setHaProvider(provider);
    dispatch.setServiceRole(serviceName);
    dispatch.init();

    try {
      dispatch.executeRequestWrapper(outboundRequest, inboundRequest, outboundResponse);
      Assert.fail("Expected the response-copy IOException to propagate");
    } catch (IOException e) {
      Assert.assertEquals("response copy failed", e.getMessage());
    }

    EasyMock.verify(mockHttpClient);
    Assert.assertEquals(uri1.toString(), provider.getActiveURL(serviceName));
  }

  @Test
  public void testConcurrentRequestsDoNotShareBackendDuringSlowCall() throws Exception {
    String serviceName = "HIVE";
    HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(
        serviceName, "true", "1", "1000", null, null, "true", "true", null, null, null));
    HaProvider provider = new DefaultHaProvider(descriptor);
    URI uri1 = new URI("http://host1.valid");
    URI uri2 = new URI("http://host2.valid");
    ArrayList<String> urlList = new ArrayList<>();
    urlList.add(uri1.toString());
    urlList.add(uri2.toString());
    provider.addHaService(serviceName, urlList);
    final HttpGet outboundRequest1 = new HttpGet(uri1);
    final HttpGet outboundRequest2 = new HttpGet(uri1);

    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(config).anyTimes();

    HttpServletRequest inboundRequest1 = EasyMock.createNiceMock(HttpServletRequest.class);
    HttpServletRequest inboundRequest2 = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(inboundRequest1.getServletContext()).andReturn(context).anyTimes();
    EasyMock.expect(inboundRequest2.getServletContext()).andReturn(context).anyTimes();
    EasyMock.expect(inboundRequest1.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(0)).anyTimes();
    EasyMock.expect(inboundRequest2.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(0)).anyTimes();

    CloseableHttpResponse inboundResponse = EasyMock.createNiceMock(CloseableHttpResponse.class);
    StatusLine statusLine = EasyMock.createNiceMock(StatusLine.class);
    HttpEntity entity = EasyMock.createNiceMock(HttpEntity.class);
    Header header = EasyMock.createNiceMock(Header.class);
    EasyMock.expect(inboundResponse.getStatusLine()).andReturn(statusLine).anyTimes();
    EasyMock.expect(statusLine.getStatusCode()).andReturn(HttpStatus.SC_OK).anyTimes();
    EasyMock.expect(inboundResponse.getEntity()).andReturn(entity).anyTimes();
    EasyMock.expect(inboundResponse.getAllHeaders()).andReturn(new Header[0]).anyTimes();
    EasyMock.expect(entity.getContent()).andAnswer(() ->
        new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8))).anyTimes();
    EasyMock.expect(entity.getContentType()).andReturn(header).anyTimes();
    EasyMock.expect(header.getElements()).andReturn(new HeaderElement[]{}).anyTimes();
    EasyMock.expect(entity.getContentLength()).andReturn(1L).anyTimes();

    HttpServletResponse outboundResponse1 = EasyMock.createNiceMock(HttpServletResponse.class);
    HttpServletResponse outboundResponse2 = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.expect(outboundResponse1.getOutputStream()).andAnswer(
        () -> new SynchronousServletOutputStreamAdapter() {
          @Override public void write(int b) { /* do nothing */ }
        }).anyTimes();
    EasyMock.expect(outboundResponse2.getOutputStream()).andAnswer(
        () -> new SynchronousServletOutputStreamAdapter() {
          @Override public void write(int b) { /* do nothing */ }
        }).anyTimes();

    final CountDownLatch firstCallEntered = new CountDownLatch(1);
    final CountDownLatch releaseFirstCall = new CountDownLatch(1);
    CloseableHttpClient httpClient = new CloseableHttpClient() {
      @Override
      protected CloseableHttpResponse doExecute(HttpHost target, HttpRequest request, HttpContext context) throws IOException {
        if (request == outboundRequest1) {
          firstCallEntered.countDown();
          try {
            if (!releaseFirstCall.await(5, TimeUnit.SECONDS)) {
              throw new IOException("Slow call was not released in time");
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
          }
        }
        return inboundResponse;
      }
      @Override public void close() { /* no-op */ }
      @Override public HttpParams getParams() { return new BasicHttpParams(); }
      @Override public ClientConnectionManager getConnectionManager() { return null; }
    };

    EasyMock.replay(inboundRequest1, inboundRequest2, context, config,
        inboundResponse, statusLine, entity, header,
        outboundResponse1, outboundResponse2);

    Assert.assertEquals(uri1.toString(), provider.getActiveURL(serviceName));

    ConfigurableHADispatch dispatch = new ConfigurableHADispatch();
    dispatch.setHttpClient(httpClient);
    dispatch.setHaProvider(provider);
    dispatch.setServiceRole(serviceName);
    dispatch.init();
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      Future<?> slowRequest = pool.submit(() -> {
        try {
          dispatch.executeRequestWrapper(outboundRequest1, inboundRequest1, outboundResponse1);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });

      Assert.assertTrue("Slow request did not reach the HTTP call in time",
          firstCallEntered.await(5, TimeUnit.SECONDS));

      dispatch.executeRequestWrapper(outboundRequest2, inboundRequest2, outboundResponse2);

      Assert.assertNotEquals(
          "Concurrent second request landed on the same backend as the slow one — race not fixed",
          outboundRequest1.getURI().getHost(),
          outboundRequest2.getURI().getHost());
      Assert.assertEquals(uri1.getHost(), outboundRequest1.getURI().getHost());
      Assert.assertEquals(uri2.getHost(), outboundRequest2.getURI().getHost());

      releaseFirstCall.countDown();
      slowRequest.get(5, TimeUnit.SECONDS);
    } finally {
      releaseFirstCall.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  public void testSetBackendUriCarriesPickedBackendBasePath() throws Exception {
    String serviceName = "HIVE";
    HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(
        serviceName, "true", "1", "0", null, null, "true", "false", null, null, null));
    HaProvider provider = new DefaultHaProvider(descriptor);
    ArrayList<String> urlList = new ArrayList<>();
    urlList.add("http://host1.valid:8443/cliservice");
    urlList.add("http://host2.valid:8443/cliservice2");
    provider.addHaService(serviceName, urlList);

    ConfigurableHADispatch dispatch = new ConfigurableHADispatch();
    dispatch.setHaProvider(provider);
    dispatch.setServiceRole(serviceName);
    dispatch.init();

    HttpGet outboundRequest = new HttpGet(new URI("http://host1.valid:8443/cliservice/query?op=EXECUTE"));
    provider.makeNextActiveURLAvailable(serviceName);

    HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.replay(inboundRequest);

    dispatch.setBackendUri(outboundRequest, inboundRequest, false);

    Assert.assertEquals("Picked backend's base path must replace the peeked backend's",
        new URI("http://host2.valid:8443/cliservice2/query?op=EXECUTE"), outboundRequest.getURI());
  }

  @Test
  public void testSetBackendUriCarriesPickedBackendBasePath_EmptyFirst() throws Exception {
    String serviceName = "HIVE";
    HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(
            serviceName, "true", "1", "0", null, null, "true", "false", null, null, null));
    HaProvider provider = new DefaultHaProvider(descriptor);
    ArrayList<String> urlList = new ArrayList<>();
    urlList.add("http://host1.valid:8443");
    urlList.add("http://host2.valid:8443/cliservice2");
    provider.addHaService(serviceName, urlList);

    ConfigurableHADispatch dispatch = new ConfigurableHADispatch();
    dispatch.setHaProvider(provider);
    dispatch.setServiceRole(serviceName);
    dispatch.init();

    HttpGet outboundRequest = new HttpGet(new URI("http://host1.valid:8443"));
    provider.makeNextActiveURLAvailable(serviceName);

    HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.replay(inboundRequest);

    dispatch.setBackendUri(outboundRequest, inboundRequest, false);

    Assert.assertEquals("Picked backend's base path must replace the peeked backend's",
            new URI("http://host2.valid:8443/cliservice2"), outboundRequest.getURI());
  }

  /*
   * markEndpointFailed stores the FULL failed request URI (path and query included) in the
   * dispatch-level activeURL. The user-agent-disabled branch of setBackendUri routes against
   * activeURL via updateBackendURL, which interprets its argument's path as the backend's
   * base path — so a stale full request URI must not leak its path into later requests.
   */
  @Test
  public void testUserAgentDisabledRequestAfterFailoverKeepsRequestPath() throws Exception {
    String serviceName = "HIVE";
    HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(
        serviceName, "true", "1", "0", null, null, "true", "true", null, null, "agentX"));
    HaProvider provider = new DefaultHaProvider(descriptor);
    ArrayList<String> urlList = new ArrayList<>();
    urlList.add("http://host1.valid");
    urlList.add("http://host2.valid");
    provider.addHaService(serviceName, urlList);

    ConfigurableHADispatch dispatch = new ConfigurableHADispatch();
    dispatch.setHaProvider(provider);
    dispatch.setServiceRole(serviceName);
    dispatch.init();

    /* a failover on any request stores the full failed request URI in activeURL */
    HttpGet failedRequest = new HttpGet(new URI("http://host1.valid/svc/path?op=EXECUTE"));
    HttpServletRequest failoverInbound = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.replay(failoverInbound);
    dispatch.markEndpointFailed(failedRequest, failoverInbound);
    Assert.assertEquals("http://host1.valid/svc/path?op=EXECUTE", dispatch.getActiveURL().get());

    /* a later request from a user agent configured to bypass load balancing */
    HttpGet outboundRequest = new HttpGet(new URI("http://host2.valid/other?op=LIST"));
    HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(inboundRequest.getHeader("User-Agent")).andReturn("agentX").anyTimes();
    EasyMock.replay(inboundRequest);
    Assert.assertTrue(dispatch.isUserAgentDisabled(inboundRequest));

    dispatch.setBackendUri(outboundRequest, inboundRequest, true);

    Assert.assertEquals("Stale activeURL's request path leaked into the outbound URI as a base path",
        "/other", outboundRequest.getURI().getPath());
    Assert.assertEquals(new URI("http://host1.valid/other?op=LIST"), outboundRequest.getURI());
  }

  /*
   * With three backends and maxFailoverAttempts=2, a request whose first two attempts both throw
   * a connection error must walk the whole rotation host1 -> host2 -> host3 and succeed on the
   * third, never revisiting a host it already tried. This exercises the real selection path end
   * to end: setBackendUri's pick-and-advance for the first attempt, and markFailedURL rotating
   * the dead backend to the tail so the re-run rewriter (simulated here via getRequestURL, which
   * mirrors the $serviceUrl function peeking provider.getActiveURL) targets the next healthy one.
   */
  @Test
  public void testRequestRotatesAcrossBackendsOnMultipleFailovers() throws Exception {
    final String serviceName = "HIVE";
    HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(
        serviceName, "true", "2", "0", null, null, "true", "true", null, null, null));
    final HaProvider provider = new DefaultHaProvider(descriptor);
    URI uri1 = new URI("http://host1.valid");
    URI uri2 = new URI("http://host2.valid");
    URI uri3 = new URI("http://host3.valid");
    ArrayList<String> urlList = new ArrayList<>();
    urlList.add(uri1.toString());
    urlList.add(uri2.toString());
    urlList.add(uri3.toString());
    provider.addHaService(serviceName, urlList);

    /* a real request object so setBackendUri / prepareForFailover actually mutate the URI */
    final HttpGet outboundRequest = new HttpGet(uri1);

    /*
     * Stand in for the URL-rewrite filter: on each (re)dispatch the rewriter's $serviceUrl
     * function re-peeks the provider's current active URL. prepareForFailover nulls the cached
     * target so getDispatchUrl -> getRequestURL is what re-targets the failed-over request.
     */
    HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(inboundRequest.getRequestURL())
        .andAnswer(() -> new StringBuffer(provider.getActiveURL(serviceName))).anyTimes();
    EasyMock.expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(0)).once();
    EasyMock.expect(inboundRequest.getAttribute("dispatch.ha.failover.counter")).andReturn(new AtomicInteger(1)).once();

    /* backend response for the successful third attempt */
    CloseableHttpResponse inboundResponse = EasyMock.createNiceMock(CloseableHttpResponse.class);
    StatusLine statusLine = EasyMock.createNiceMock(StatusLine.class);
    HttpEntity entity = EasyMock.createNiceMock(HttpEntity.class);
    Header header = EasyMock.createNiceMock(Header.class);
    ServletContext context = EasyMock.createNiceMock(ServletContext.class);
    GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);

    EasyMock.expect(inboundResponse.getStatusLine()).andReturn(statusLine).anyTimes();
    EasyMock.expect(statusLine.getStatusCode()).andReturn(HttpStatus.SC_OK).anyTimes();
    EasyMock.expect(inboundResponse.getEntity()).andReturn(entity).anyTimes();
    EasyMock.expect(inboundResponse.getAllHeaders()).andReturn(new Header[0]).anyTimes();
    EasyMock.expect(inboundRequest.getServletContext()).andReturn(context).anyTimes();
    EasyMock.expect(entity.getContent())
        .andAnswer(() -> new ByteArrayInputStream("knox-backend".getBytes(StandardCharsets.UTF_8))).anyTimes();
    EasyMock.expect(entity.getContentType()).andReturn(header).anyTimes();
    EasyMock.expect(header.getElements()).andReturn(new HeaderElement[]{}).anyTimes();
    EasyMock.expect(entity.getContentLength()).andReturn(4L).anyTimes();
    EasyMock.expect(context.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(config).anyTimes();

    HttpServletResponse outboundResponse = EasyMock.createNiceMock(HttpServletResponse.class);
    Capture<Integer> statusCodeCapture = EasyMock.newCapture(CaptureType.FIRST);
    outboundResponse.setStatus(EasyMock.captureInt(statusCodeCapture));
    EasyMock.expectLastCall().anyTimes();
    EasyMock.expect(outboundResponse.getOutputStream())
        .andAnswer((IAnswer<SynchronousServletOutputStreamAdapter>) () -> new SynchronousServletOutputStreamAdapter() {
          @Override
          public void write(int b) { /* do nothing */ }
        }).anyTimes();

    /* record the backend each attempt actually targeted; fail the first two, succeed on the third */
    final List<String> attemptedHosts = new ArrayList<>();
    CloseableHttpClient httpClient = new CloseableHttpClient() {
      @Override
      protected CloseableHttpResponse doExecute(HttpHost target, HttpRequest request, HttpContext ctx) throws IOException {
        attemptedHosts.add(outboundRequest.getURI().getHost());
        if (attemptedHosts.size() <= 2) {
          throw new IOException("unreachable-host");
        }
        return inboundResponse;
      }
      @Override public void close() { /* no-op */ }
      @Override public HttpParams getParams() { return new BasicHttpParams(); }
      @Override public ClientConnectionManager getConnectionManager() { return null; }
    };

    EasyMock.replay(inboundRequest, outboundResponse, inboundResponse,
        statusLine, entity, header, context, config);

    Assert.assertEquals(uri1.toString(), provider.getActiveURL(serviceName));
    ConfigurableHADispatch dispatch = new ConfigurableHADispatch();
    dispatch.setHttpClient(httpClient);
    dispatch.setHaProvider(provider);
    dispatch.setServiceRole(serviceName);
    dispatch.init();

    dispatch.executeRequestWrapper(outboundRequest, inboundRequest, outboundResponse);

    /* both counter reads were consumed => exactly two failovers happened */
    EasyMock.verify(inboundRequest);
    Assert.assertEquals("Request must walk the full rotation without revisiting a backend.",
        Arrays.asList(uri1.getHost(), uri2.getHost(), uri3.getHost()), attemptedHosts);
    Assert.assertEquals("The finally-successful backend must be the outbound target.",
        uri3.getHost(), outboundRequest.getURI().getHost());
    Assert.assertEquals("Each attempt advances the rotation, so after serving on the last backend the head is the next one.",
        uri1.toString(), provider.getActiveURL(serviceName));
    Assert.assertEquals("Expected the request to succeed after failing over twice.",
        HttpStatus.SC_OK, statusCodeCapture.getValue().intValue());
  }

  /*
   * Failover-path counterpart to the initial-selection reconciliation: if the URL queue is
   * rotated (by a concurrent request) between the rewrite's getActiveURL peek and our
   * getActiveURLAndAdvance in prepareForFailover, updateBackendURL must re-target the outbound
   * request at the backend we actually picked, carrying THAT backend's base path — not the
   * stale peeked one. Here the rewrite peeks host1 (/cliservice) but the head has already
   * advanced to host2 (/cliservice2) by the time we pick.
   */
  @Test
  public void testPrepareForFailoverReTargetsWhenQueueRotatedBetweenPeekAndAdvance() throws Exception {
    String serviceName = "HIVE";
    HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
    descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(
        serviceName, "true", "1", "0", null, null, "true", "false", null, null, null));
    HaProvider provider = new DefaultHaProvider(descriptor);
    ArrayList<String> urlList = new ArrayList<>();
    urlList.add("http://host1.valid:8443/cliservice");
    urlList.add("http://host2.valid:8443/cliservice2");
    provider.addHaService(serviceName, urlList);

    ConfigurableHADispatch dispatch = new ConfigurableHADispatch();
    dispatch.setHaProvider(provider);
    dispatch.setServiceRole(serviceName);
    dispatch.init();

    /* the outbound URI as the rewrite produced it after peeking host1 (host1 base path + tail) */
    HttpGet outboundRequest = new HttpGet(new URI("http://host1.valid:8443/cliservice/query?op=EXECUTE"));
    HttpServletRequest inboundRequest = EasyMock.createNiceMock(HttpServletRequest.class);
    /* getDispatchUrl re-derives the peeked target from these on the retry */
    EasyMock.expect(inboundRequest.getRequestURL())
        .andReturn(new StringBuffer("http://host1.valid:8443/cliservice/query")).anyTimes();
    EasyMock.expect(inboundRequest.getQueryString()).andReturn("op=EXECUTE").anyTimes();
    EasyMock.replay(inboundRequest);

    /* a concurrent request advances the head host1 -> host2 between the peek and our advance */
    provider.makeNextActiveURLAvailable(serviceName);
    Assert.assertEquals("http://host2.valid:8443/cliservice2", provider.getActiveURL(serviceName));

    dispatch.prepareForFailover(outboundRequest, inboundRequest);

    Assert.assertEquals("Retried request must target the advanced pick with its own base path, not the peeked backend's",
        new URI("http://host2.valid:8443/cliservice2/query?op=EXECUTE"), outboundRequest.getURI());
    /* selection advanced the queue past the picked backend */
    Assert.assertEquals("http://host1.valid:8443/cliservice", provider.getActiveURL(serviceName));
  }
}
