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
package org.apache.hadoop.gateway.util;

import org.apache.hadoop.test.FastTests;
import org.apache.hadoop.test.UnitTests;
import org.easymock.EasyMock;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

@Category( { UnitTests.class, FastTests.class })
public class UrlRewriterTest {

  @Test
  public void testRewriteUrlWithHttpServletRequestAndFilterConfig() throws Exception {
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getParameter( "request-param-name" ) ).andReturn( "request-param-value" ).anyTimes();
    EasyMock.replay( request );

    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect( config.getInitParameter( "filter-param-name" ) ).andReturn( "filter-param-value" ).anyTimes();
    EasyMock.replay( config );

    String sourceInput, sourcePattern, targetPattern, actualOutput, expectedOutput;

    sourceInput = "http://some-host:0/some-path";
    sourcePattern = "**";
    targetPattern = "should-not-change";
    actualOutput = UrlRewriter.rewriteUrl( sourceInput, sourcePattern, targetPattern, request, config );
    assertThat( actualOutput, equalTo( targetPattern ) );

    sourceInput = "http://some-host:0/some-path";
    sourcePattern = "**";
    targetPattern = "{0}";
    actualOutput = UrlRewriter.rewriteUrl( sourceInput, sourcePattern, targetPattern, request, config );
    assertThat( actualOutput, equalTo( sourceInput ) );

    sourceInput = "http://some-host:0/pathA/pathB/pathC";
    sourcePattern = "*/pathA/*/*";
    targetPattern = "http://some-other-host/{2}/{1}";
    actualOutput = UrlRewriter.rewriteUrl( sourceInput, sourcePattern, targetPattern, request, config );
    assertThat( actualOutput, equalTo( "http://some-other-host/pathC/pathB" ) );

    sourceInput = "http://some-host:0/some-path";
    sourcePattern = "**";
    targetPattern = "{filter-param-name}";
    actualOutput = UrlRewriter.rewriteUrl( sourceInput, sourcePattern, targetPattern, request, config );
    assertThat( actualOutput, equalTo( "filter-param-value" ) );

    sourceInput = "http://some-host:0/some-path";
    sourcePattern = "**";
    targetPattern = "{request-param-name}";
    actualOutput = UrlRewriter.rewriteUrl( sourceInput, sourcePattern, targetPattern, request, config );
    assertThat( actualOutput, equalTo( "request-param-value" ) );

    sourceInput = "http://some-host:0/some-path";
    sourcePattern = "**";
    targetPattern = "http://some-other-host/{filter-param-name}/{request-param-name}";
    actualOutput = UrlRewriter.rewriteUrl( sourceInput, sourcePattern, targetPattern, request, config );
    assertThat( actualOutput, equalTo( "http://some-other-host/filter-param-value/request-param-value" ) );

    sourceInput = "http://some-host:0/pathA/pathB/pathC";
    sourcePattern = "*/pathA/*/*";
    targetPattern = "http://some-other-host/{2}/{1}/{filter-param-name}/{request-param-name}";
    actualOutput = UrlRewriter.rewriteUrl( sourceInput, sourcePattern, targetPattern, request, config );
    assertThat( actualOutput, equalTo( "http://some-other-host/pathC/pathB/filter-param-value/request-param-value" ) );

    sourceInput = "/namenode/api/v1/test";
    sourcePattern = "/namenode/api/v1/**";
    targetPattern = "http://{filter-param-name}/webhdfs/v1/{0}";
    expectedOutput = "http://filter-param-value/webhdfs/v1/test";
    actualOutput = UrlRewriter.rewriteUrl( sourceInput, sourcePattern, targetPattern, request, config );
    assertThat( actualOutput, equalTo( expectedOutput ) );
  }

}
