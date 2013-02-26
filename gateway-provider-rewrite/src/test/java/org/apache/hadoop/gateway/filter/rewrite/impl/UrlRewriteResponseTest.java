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
package org.apache.hadoop.gateway.filter.rewrite.impl;

import org.easymock.EasyMock;
import org.junit.Test;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class UrlRewriteResponseTest {

  @Test
  public void testResolve() throws Exception {

    ServletContext context = EasyMock.createNiceMock( ServletContext.class );
    EasyMock.expect( context.getServletContextName() ).andReturn( "test-cluster-name" ).anyTimes();

    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect( config.getServletContext() ).andReturn( context ).anyTimes();

    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );

    EasyMock.replay( context, config, request, response );

    UrlRewriteResponse reponse = new UrlRewriteResponse( config, request, response );

    List<String> names = reponse.resolve( "cluster.name" );
    assertThat( names.size(), is( 1 ) );
    assertThat( names.get( 0 ), is( "test-cluster-name" ) );
  }

}
