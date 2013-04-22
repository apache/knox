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

import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.params.BasicHttpParams;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class HttpClientDispatchTest {

  // Make sure Hadoop cluster topology isn't exposed to client when there is a connectivity issue.
  @Test
  public void testJiraKnox58() throws URISyntaxException {

    URI uri = new URI( "http://unreachable-host" );
    BasicHttpParams params = new BasicHttpParams();

    HttpUriRequest outboundRequest = EasyMock.createNiceMock( HttpUriRequest.class );
    EasyMock.expect( outboundRequest.getMethod() ).andReturn( "GET" ).anyTimes();
    EasyMock.expect( outboundRequest.getURI() ).andReturn( uri  ).anyTimes();
    EasyMock.expect( outboundRequest.getParams() ).andReturn( params ).anyTimes();

    HttpServletRequest inboundRequest = EasyMock.createNiceMock( HttpServletRequest.class );

    HttpServletResponse outboundResponse = EasyMock.createNiceMock( HttpServletResponse.class );

    EasyMock.replay( outboundRequest, inboundRequest, outboundResponse );

    HttpClientDispatch dispatch = new HttpClientDispatch();
    try {
      dispatch.executeRequest( outboundRequest, inboundRequest, outboundResponse );
      fail( "Should have thrown IOException" );
    } catch( IOException e ) {
      assertThat( e.getMessage(), not( containsString( "unreachable-host" ) ) );
      assertThat( e, not( instanceOf( UnknownHostException.class ) ) ) ;
      assertThat( "Message needs meaningful content.", e.getMessage().trim().length(), greaterThan( 12 ) );
    }
  }

}
