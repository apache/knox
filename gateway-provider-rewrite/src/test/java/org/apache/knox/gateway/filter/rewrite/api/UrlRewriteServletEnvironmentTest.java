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
package org.apache.knox.gateway.filter.rewrite.api;

import org.easymock.EasyMock;
import org.junit.Test;

import javax.servlet.ServletContext;

import java.net.URL;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

public class UrlRewriteServletEnvironmentTest {

  @Test
  public void testGetResource() throws Exception {
    ServletContext context = EasyMock.createNiceMock( ServletContext.class );
    EasyMock.expect( context.getResource( "test-resource-name" ) ).andReturn( new URL( "http:/test-resource-value" ) ).anyTimes();
    EasyMock.replay( context );
    UrlRewriteServletEnvironment env = new UrlRewriteServletEnvironment( context );
    assertThat( env.getResource( "test-resource-name" ), is( new URL( "http:/test-resource-value" ) ) );
  }

  @Test
  public void testGetAttribute() throws Exception {
    ServletContext context = EasyMock.createNiceMock( ServletContext.class );
    EasyMock.expect(  context.getAttribute( "test-attribute-name" ) ).andReturn( "test-attribute-value" ).anyTimes();
    EasyMock.replay( context );
    UrlRewriteServletEnvironment env = new UrlRewriteServletEnvironment( context );
    assertThat( (String)env.getAttribute( "test-attribute-name" ), is( "test-attribute-value" ) );
  }

  @Test
  public void testResolve() throws Exception {
    ServletContext context = EasyMock.createNiceMock( ServletContext.class );
    EasyMock.expect( context.getInitParameter( "test-parameter-name" ) ).andReturn( "test-parameter-value" );
    EasyMock.replay( context );
    UrlRewriteServletEnvironment env = new UrlRewriteServletEnvironment( context );
    assertThat( env.resolve( "test-parameter-name" ), contains( "test-parameter-value" ) );
  }

}
