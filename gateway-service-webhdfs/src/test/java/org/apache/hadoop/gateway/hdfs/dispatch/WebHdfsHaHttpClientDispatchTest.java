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
package org.apache.hadoop.gateway.hdfs.dispatch;

import org.apache.hadoop.gateway.dispatch.AppCookieManager;
import org.apache.hadoop.gateway.ha.provider.HaDescriptor;
import org.apache.hadoop.gateway.ha.provider.HaProvider;
import org.apache.hadoop.gateway.ha.provider.HaServletContextListener;
import org.apache.hadoop.gateway.ha.provider.impl.DefaultHaDescriptor;
import org.apache.hadoop.gateway.ha.provider.impl.DefaultHaProvider;
import org.apache.hadoop.gateway.ha.provider.impl.DefaultHaServiceConfig;
import org.apache.hadoop.gateway.ha.provider.impl.HaDescriptorFactory;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Assert;
import org.junit.Test;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class WebHdfsHaHttpClientDispatchTest {

  private class InstrumentedWebHdfsHaHttpClientDispatch extends WebHdfsHaHttpClientDispatch {

    public InstrumentedWebHdfsHaHttpClientDispatch() throws ServletException {
    }

    public AppCookieManager getAppCookieManager() {
      return appCookieManager;
    }

  }

   @Test
   public void testInitCallsSuperInit() throws Exception {
     DefaultHaDescriptor haDescriptor = new DefaultHaDescriptor();
     haDescriptor.addServiceConfig( new DefaultHaServiceConfig( "test-role" ) );
     HaProvider haProvider = new DefaultHaProvider( haDescriptor );
     ServletContext context = EasyMock.createNiceMock(ServletContext.class);
     EasyMock.expect(context.getAttribute(HaServletContextListener.PROVIDER_ATTRIBUTE_NAME)).andReturn(haProvider).anyTimes();
     FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
     EasyMock.expect(config.getServletContext()).andReturn(context).anyTimes();
     EasyMock.expect(config.getInitParameter(EasyMock.anyObject(String.class))).andReturn(null).anyTimes();
     InstrumentedWebHdfsHaHttpClientDispatch dispatch = new InstrumentedWebHdfsHaHttpClientDispatch();
     EasyMock.replay(context,config);

     dispatch.init();

     assertThat( dispatch.getAppCookieManager(), notNullValue() );
   }

   @Test
   public void testConnectivityFailover() throws Exception {
      String serviceName = "WEBHDFS";
      HaDescriptor descriptor = HaDescriptorFactory.createDescriptor();
      descriptor.addServiceConfig(HaDescriptorFactory.createServiceConfig(serviceName, "true", "1", "1000", "2", "1000"));
      HaProvider provider = new DefaultHaProvider(descriptor);
      URI uri1 = new URI( "http://unreachable-host" );
      URI uri2 = new URI( "http://reachable-host" );
      ArrayList<String> urlList = new ArrayList<String>();
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
      EasyMock.expect(outboundResponse.getOutputStream()).andAnswer( new IAnswer<ServletOutputStream>() {
         @Override
         public ServletOutputStream answer() throws Throwable {
            return new ServletOutputStream() {
               @Override
               public void write( int b ) throws IOException {
                  throw new IOException( "unreachable-host" );
               }
            };
         }
      }).once();
      EasyMock.replay(filterConfig, servletContext, outboundRequest, inboundRequest, outboundResponse);
      Assert.assertEquals(uri1.toString(), provider.getActiveURL(serviceName));
      WebHdfsHaHttpClientDispatch dispatch = new WebHdfsHaHttpClientDispatch();
      dispatch.setHttpClient(new DefaultHttpClient());
      dispatch.setHaProvider(provider);
      dispatch.init();
      long startTime = System.currentTimeMillis();
      try {
         dispatch.executeRequest(outboundRequest, inboundRequest, outboundResponse);
      } catch (IOException e) {
        //this is expected after the failover limit is reached
      }
      long elapsedTime = System.currentTimeMillis() - startTime;
      Assert.assertEquals(uri2.toString(), provider.getActiveURL(serviceName));
      //test to make sure the sleep took place
      Assert.assertTrue(elapsedTime > 1000);
   }
}
