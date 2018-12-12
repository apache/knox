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
package org.apache.knox.gateway.inboundurl.impl;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteProcessor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRuleDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptorFactory;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletContextListener;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletFilter;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriter;
import org.apache.knox.gateway.filter.rewrite.ext.UrlRewriteActionRewriteDescriptorExt;
import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteContextImpl;
import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteResponse;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Resolver;
import org.apache.knox.gateway.util.urltemplate.Template;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class InboundUrlFunctionProcessorTest {

  @Test
  public void testServiceLoader() throws Exception {
    ServiceLoader loader = ServiceLoader.load( UrlRewriteFunctionProcessor.class );
    Iterator iterator = loader.iterator();
    assertThat( "Service iterator empty.", iterator.hasNext() );
    while( iterator.hasNext() ) {
      Object object = iterator.next();
      if( object instanceof InboundUrlFunctionProcessor ) {
        return;
      }
    }
    fail( "Failed to find " + InboundUrlFunctionProcessor.class.getName() + " via service loader." );
  }

  @Test
  public void testServiceResolve() throws Exception {
    ServiceLoader loader = ServiceLoader.load( UrlRewriteFunctionProcessor.class );
    Iterator iterator = loader.iterator();
    assertThat( "Service iterator empty.", iterator.hasNext() );
    InboundUrlFunctionProcessor proc = null;
    while( iterator.hasNext() ) {
      Object object = iterator.next();
      if( object instanceof InboundUrlFunctionProcessor ) {
        proc = (InboundUrlFunctionProcessor) object ;
      }
    }
    if( proc == null ) {
      fail("Failed to find " + InboundUrlFunctionProcessor.class.getName() + " via service loader.");
    }

    Map<String,UrlRewriteFunctionProcessor> functions = new HashMap<>();
    UrlRewriteEnvironment environment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    UrlRewriter.Direction direction = UrlRewriter.Direction.OUT;

    List<String> parameters = Collections.singletonList("host");

    Template template = Parser.parseLiteral( "https://localhost:8443/gateway/default/datanode/?host=http://foo:50075" );
    UrlRewriteContextImpl ctx = new UrlRewriteContextImpl( environment, this.getRewriteResponse(), functions, direction, template );

    List<String> result = proc.resolve(ctx, parameters);
    assertThat( result.get(0), is( "http://foo:50075" ) );
  }

  private UrlRewriteResponse getRewriteResponse() throws Exception {
    UrlRewriteProcessor rewriter = EasyMock.createNiceMock( UrlRewriteProcessor.class );
    EasyMock.expect( rewriter.getConfig() ).andReturn( null ).anyTimes();

    ServletContext context = EasyMock.createNiceMock( ServletContext.class );
    EasyMock.expect( context.getAttribute( UrlRewriteServletContextListener.PROCESSOR_ATTRIBUTE_NAME ) ).andReturn( rewriter ).anyTimes();

    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect( config.getInitParameter( UrlRewriteServletFilter.RESPONSE_BODY_FILTER_PARAM ) ).andReturn( "test-filter" ).anyTimes();
    EasyMock.expect( config.getServletContext() ).andReturn( context ).anyTimes();

    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getParameterValues("host" ) ).andReturn( new String[]{"http://foo:50075"}  ).anyTimes();

    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );

    EasyMock.replay( rewriter, context, config, request, response );

    return new UrlRewriteResponse( config, request, response );
  }

  @Test
  public void testQueryParam() throws Exception {
    GatewayServices gatewayServices = EasyMock.createNiceMock( GatewayServices.class );
    UrlRewriteEnvironment environment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    EasyMock.expect( environment.getAttribute( GatewayServices.GATEWAY_SERVICES_ATTRIBUTE ) ).andReturn( gatewayServices ).anyTimes();
    EasyMock.expect( environment.resolve( "cluster.name" ) ).andReturn(Collections.singletonList("test-cluster-name")).anyTimes();

    Resolver resolver = EasyMock.createNiceMock( Resolver.class );
    EasyMock.expect( resolver.resolve( "query.param.host" ) ).andReturn( Collections.singletonList( "http://foo:50075" ) ).anyTimes();
    EasyMock.replay( gatewayServices, environment, resolver );

    UrlRewriteRulesDescriptor descriptor = UrlRewriteRulesDescriptorFactory.create();
    UrlRewriteRuleDescriptor rule = descriptor.addRule( "test-location" );
    rule.pattern( "{*}://{*}:{*}/{**}/?{**}" );
    UrlRewriteActionRewriteDescriptorExt rewrite = rule.addStep( "rewrite" );
    rewrite.template( "{$inboundurl[host]}" );
    UrlRewriteProcessor rewriter = new UrlRewriteProcessor();
    rewriter.initialize( environment, descriptor );
    Template input = Parser.parseLiteral( "https://localhost:8443/gateway/default/datanode/?host=http://foo:50075" );
    Template output = rewriter.rewrite( resolver, input, UrlRewriter.Direction.OUT, "test-location" );
    assertThat( output.toString(), is( "http://foo:50075" ) );
  }
}
