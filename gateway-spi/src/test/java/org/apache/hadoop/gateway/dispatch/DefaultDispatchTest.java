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
package org.apache.hadoop.gateway.dispatch;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Test;

public class DefaultDispatchTest {

  // Make sure Hadoop cluster topology isn't exposed to client when there is a connectivity issue.
  @Test
  public void testJiraKnox58() throws URISyntaxException, IOException {

    URI uri = new URI( "http://unreachable-host" );
    BasicHttpParams params = new BasicHttpParams();

    HttpUriRequest outboundRequest = EasyMock.createNiceMock( HttpUriRequest.class );
    EasyMock.expect( outboundRequest.getMethod() ).andReturn( "GET" ).anyTimes();
    EasyMock.expect( outboundRequest.getURI() ).andReturn( uri  ).anyTimes();
    EasyMock.expect( outboundRequest.getParams() ).andReturn( params ).anyTimes();

    HttpServletRequest inboundRequest = EasyMock.createNiceMock( HttpServletRequest.class );

    HttpServletResponse outboundResponse = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.expect( outboundResponse.getOutputStream() ).andAnswer( new IAnswer<ServletOutputStream>() {
      @Override
      public ServletOutputStream answer() throws Throwable {
        return new ServletOutputStream() {
          @Override
          public void write( int b ) throws IOException {
            throw new IOException( "unreachable-host" );
          }
        };
      }
    });

    EasyMock.replay( outboundRequest, inboundRequest, outboundResponse );

    DefaultDispatch dispatch = new DefaultDispatch();
    dispatch.setHttpClient(new DefaultHttpClient());
    try {
      dispatch.executeRequest( outboundRequest, inboundRequest, outboundResponse );
      fail( "Should have thrown IOException" );
    } catch( IOException e ) {
      assertThat( e.getMessage(), not( containsString( "unreachable-host" ) ) );
      assertThat( e, not( instanceOf( UnknownHostException.class ) ) ) ;
      assertThat( "Message needs meaningful content.", e.getMessage().trim().length(), greaterThan( 12 ) );
    }
  }

  @Test
  public void testCallToSecureClusterWithDelegationToken() throws URISyntaxException, IOException {
    System.setProperty(GatewayConfig.HADOOP_KERBEROS_SECURED, "true");
    DefaultDispatch defaultDispatch = new DefaultDispatch();
    ServletInputStream inputStream = EasyMock.createNiceMock( ServletInputStream.class );
    HttpServletRequest inboundRequest = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect(inboundRequest.getQueryString()).andReturn( "delegation=123").anyTimes();
    EasyMock.expect(inboundRequest.getInputStream()).andReturn( inputStream).anyTimes();
    EasyMock.replay( inboundRequest );
    HttpEntity httpEntity = defaultDispatch.createRequestEntity(inboundRequest);
    System.setProperty(GatewayConfig.HADOOP_KERBEROS_SECURED, "false");
    assertFalse("buffering in the presence of delegation token", 
        (httpEntity instanceof CappedBufferHttpEntity));
  }

}
