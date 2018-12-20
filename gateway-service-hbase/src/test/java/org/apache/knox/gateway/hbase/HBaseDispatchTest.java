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
package org.apache.knox.gateway.hbase;

import org.apache.knox.gateway.dispatch.Dispatch;
import org.apache.knox.test.TestUtils;
import org.apache.knox.test.category.FastTests;
import org.apache.knox.test.category.UnitTests;
import org.easymock.EasyMock;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.servlet.http.HttpServletRequest;
import java.net.URI;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Category( { UnitTests.class, FastTests.class } )
public class HBaseDispatchTest {

  @SuppressWarnings("deprecation")
  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testGetDispatchUrl() throws Exception {
    HttpServletRequest request;
    Dispatch dispatch;
    String path;
    String query;
    URI uri;

    dispatch = new HBaseDispatch();

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

    // KNOX-709: HBase request URLs must not be URL encoded
    path = "http://test-host:42/test%2Cpath";
    request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getRequestURI() ).andReturn( path ).anyTimes();
    EasyMock.expect( request.getRequestURL() ).andReturn( new StringBuffer( path ) ).anyTimes();
    EasyMock.expect( request.getQueryString() ).andReturn( null ).anyTimes();
    EasyMock.replay( request );
    uri = dispatch.getDispatchUrl( request );
    assertThat( uri.toASCIIString(), is( "http://test-host:42/test,path" ) );

    // KNOX-709: HBase request URLs must not be URL encoded
    path = "http://test-host:42/test%2Cpath";
    query = "test%26name=test%3Dvalue";
    request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getRequestURI() ).andReturn( path ).anyTimes();
    EasyMock.expect( request.getRequestURL() ).andReturn( new StringBuffer( path ) ).anyTimes();
    EasyMock.expect( request.getQueryString() ).andReturn( query ).anyTimes();
    EasyMock.replay( request );
    uri = dispatch.getDispatchUrl( request );
    assertThat( uri.toASCIIString(), is( "http://test-host:42/test,path?test%26name=test%3Dvalue" ) );
  }
}