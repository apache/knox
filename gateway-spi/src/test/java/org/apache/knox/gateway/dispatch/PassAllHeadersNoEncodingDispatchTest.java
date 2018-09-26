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
package org.apache.knox.gateway.dispatch;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.net.URI;
import javax.servlet.http.HttpServletRequest;

import org.apache.knox.test.TestUtils;
import org.easymock.EasyMock;
import org.junit.Test;

public class PassAllHeadersNoEncodingDispatchTest {
  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testGetDispatchUrl() throws Exception {
    HttpServletRequest request;
    Dispatch dispatch;
    String path;
    String query;
    URI uri;

    dispatch = new PassAllHeadersNoEncodingDispatch();

    path = "http://test-host:42/test-path";
    request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getRequestURI() ).andReturn( path ).anyTimes();
    EasyMock.expect( request.getRequestURL() ).andReturn( new StringBuffer( path ) ).anyTimes();
    EasyMock.expect( request.getQueryString() ).andReturn( null ).anyTimes();
    EasyMock.replay( request );
    uri = dispatch.getDispatchUrl( request );
    assertThat( uri.toASCIIString(), is( "http://test-host:42/test-path" ) );

    path = "http://test-host:42/test,path";
    request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getRequestURI() ).andReturn( path ).anyTimes();
    EasyMock.expect( request.getRequestURL() ).andReturn( new StringBuffer( path ) ).anyTimes();
    EasyMock.expect( request.getQueryString() ).andReturn( null ).anyTimes();
    EasyMock.replay( request );
    uri = dispatch.getDispatchUrl( request );
    assertThat( uri.toASCIIString(), is( "http://test-host:42/test,path" ) );

    // encoding in the patch remains
    path = "http://test-host:42/test%2Cpath";
    request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getRequestURI() ).andReturn( path ).anyTimes();
    EasyMock.expect( request.getRequestURL() ).andReturn( new StringBuffer( path ) ).anyTimes();
    EasyMock.expect( request.getQueryString() ).andReturn( null ).anyTimes();
    EasyMock.replay( request );
    uri = dispatch.getDispatchUrl( request );
    assertThat( uri.toASCIIString(), is( "http://test-host:42/test%2Cpath" ) );

    // encoding in query string is removed
    path = "http://test-host:42/test%2Cpath";
    query = "test%26name=test%3Dvalue";
    request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getRequestURI() ).andReturn( path ).anyTimes();
    EasyMock.expect( request.getRequestURL() ).andReturn( new StringBuffer( path ) ).anyTimes();
    EasyMock.expect( request.getQueryString() ).andReturn( query ).anyTimes();
    EasyMock.replay( request );
    uri = dispatch.getDispatchUrl( request );
    assertThat( uri.toASCIIString(), is( "http://test-host:42/test%2Cpath?test&name=test=value" ) );

    // double quotes removed
    path = "https://test-host:42/api/v1/views/TEZ/versions/0.7.0.2.6.2.0-205/instances/TEZ_CLUSTER_INSTANCE/resources/atsproxy/ws/v1/timeline/TEZ_DAG_ID";
    query = "limit=9007199254740991&primaryFilter=applicationId:%22application_1518808140659_0007%22&_=1519053586839";
    request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getRequestURI() ).andReturn( path ).anyTimes();
    EasyMock.expect( request.getRequestURL() ).andReturn( new StringBuffer( path ) ).anyTimes();
    EasyMock.expect( request.getQueryString() ).andReturn( query ).anyTimes();
    EasyMock.replay( request );
    uri = dispatch.getDispatchUrl( request );
    assertThat( uri.toASCIIString(), is( "https://test-host:42/api/v1/views/TEZ/versions/0.7.0.2.6.2.0-205/instances/TEZ_CLUSTER_INSTANCE/resources/atsproxy/ws/v1/timeline/TEZ_DAG_ID?limit=9007199254740991&primaryFilter=applicationId:%22application_1518808140659_0007%22&_=1519053586839" ) );
  }
}
