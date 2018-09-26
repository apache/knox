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
package org.apache.knox.gateway.hostmap.impl;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteProcessor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRuleDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptorFactory;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriter;
import org.apache.knox.gateway.filter.rewrite.ext.UrlRewriteActionRewriteDescriptorExt;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.hostmap.HostMapper;
import org.apache.knox.gateway.services.hostmap.HostMapperService;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Resolver;
import org.apache.knox.gateway.util.urltemplate.Template;
import org.apache.knox.test.TestUtils;
import org.easymock.EasyMock;
import org.junit.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ServiceLoader;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class HostmapFunctionProcessorTest {

  @Test
  public void testServiceLoader() throws Exception {
    ServiceLoader loader = ServiceLoader.load( UrlRewriteFunctionProcessor.class );
    Iterator iterator = loader.iterator();
    assertThat( "Service iterator empty.", iterator.hasNext() );
    while( iterator.hasNext() ) {
      Object object = iterator.next();
      if( object instanceof HostmapFunctionProcessor ) {
        return;
      }
    }
    fail( "Failed to find " + HostmapFunctionProcessor.class.getName() + " via service loader." );
  }

  @Test
  public void testBasicUseCase() throws Exception {
    URL configUrl = TestUtils.getResourceUrl( this.getClass(), "hostmap.txt" );

    HostMapper hm = EasyMock.createNiceMock(HostMapper.class);
    EasyMock.expect( hm.resolveInboundHostName("test-inbound-host")).andReturn( "test-inbound-rewritten-host" ).anyTimes();
    
    HostMapperService hms = EasyMock.createNiceMock( HostMapperService.class );

    GatewayServices gatewayServices = EasyMock.createNiceMock( GatewayServices.class );
    EasyMock.expect( gatewayServices.getService( GatewayServices.HOST_MAPPING_SERVICE ) ).andReturn( hms ).anyTimes();


    UrlRewriteEnvironment environment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    EasyMock.expect( environment.getAttribute( GatewayServices.GATEWAY_SERVICES_ATTRIBUTE ) ).andReturn( gatewayServices ).anyTimes();    
    EasyMock.expect( environment.resolve( "cluster.name" ) ).andReturn( Arrays.asList( "test-cluster-name" ) ).anyTimes();
    EasyMock.expect( environment.getResource( "/WEB-INF/hostmap.txt" ) ).andReturn( configUrl ).anyTimes();
    Resolver resolver = EasyMock.createNiceMock( Resolver.class );
    EasyMock.expect( resolver.resolve( "host" ) ).andReturn( Arrays.asList( "test-inbound-host" ) ).anyTimes();
    EasyMock.replay( gatewayServices, hm, hms, environment, resolver );

    UrlRewriteRulesDescriptor descriptor = UrlRewriteRulesDescriptorFactory.create();
    UrlRewriteRuleDescriptor rule = descriptor.addRule( "test-rule" );
    rule.pattern( "{*}://{host}:{*}/{**}?{**}" );
    UrlRewriteActionRewriteDescriptorExt rewrite = rule.addStep( "rewrite" );
    rewrite.template( "{*}://{$hostmap(host)}:{*}/{**}?{**}" );

    UrlRewriteProcessor rewriter = new UrlRewriteProcessor();
    rewriter.initialize( environment, descriptor );

    Template input = Parser.parseLiteral( "test-scheme://test-inbound-host:42/test-path/test-file?test-name=test-value" );
    Template output = rewriter.rewrite( resolver, input, UrlRewriter.Direction.IN, null );
    //System.out.println( output );
    assertThat( output, notNullValue() );
    assertThat( output.getHost().getFirstValue().getPattern(), is( "test-inbound-rewritten-host" ) );
  }

  @Test
  public void testHdfsUseCase() throws Exception {
    URL configUrl = TestUtils.getResourceUrl( this.getClass(), "hdfs-hostmap.txt" );

    UrlRewriteEnvironment environment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    EasyMock.expect( environment.getResource( "/WEB-INF/hostmap.txt" ) ).andReturn( configUrl ).anyTimes();
    Resolver resolver = EasyMock.createNiceMock( Resolver.class );
    EasyMock.expect( resolver.resolve( "host" ) ).andReturn( Arrays.asList( "test-internal-host" ) ).anyTimes();
    EasyMock.replay( environment, resolver );

    UrlRewriteRulesDescriptor descriptor = UrlRewriteRulesDescriptorFactory.create();
    UrlRewriteRuleDescriptor rule = descriptor.addRule( "test-rule" );
    rule.pattern( "{*}://{host}:{*}/{**}?{**}" );
    UrlRewriteActionRewriteDescriptorExt rewrite = rule.addStep( "rewrite" );
    rewrite.template( "{*}://test-static-host:{*}/{**}?server={$hostmap(host)}&{**}" );

    UrlRewriteProcessor rewriter = new UrlRewriteProcessor();
    rewriter.initialize( environment, descriptor );

    Template input = Parser.parseLiteral(
        "test-scheme://test-external-host:42/test-path/test-file?test-name-1=test-value-1&test-name-2=test-value-2" );
    Template output = rewriter.rewrite( resolver, input, UrlRewriter.Direction.OUT, "test-rule" );
    //System.out.println( output );
    assertThat( output, notNullValue() );
    assertThat( output.getHost().getFirstValue().getPattern(), is( "test-static-host" ) );
    assertThat( output.getQuery().get( "server" ).getFirstValue().getPattern(), is( "test-external-host" ) );
    assertThat( output.getQuery().get( "server" ).getValues().size(), is( 1 ) );
    assertThat( output.getQuery().get( "test-name-1" ).getFirstValue().getPattern(), is( "test-value-1" ) );
    assertThat( output.getQuery().get( "test-name-1" ).getValues().size(), is( 1 ) );
    assertThat( output.getQuery().get( "test-name-2" ).getFirstValue().getPattern(), is( "test-value-2" ) );
    assertThat( output.getQuery().get( "test-name-2" ).getValues().size(), is( 1 ) );
    assertThat( output.getQuery().size(), is( 3 ) );
  }

  @Test
  public void testQueryToPathRewriteWithFunction() throws Exception {
    URL configUrl = TestUtils.getResourceUrl( this.getClass(), "hdfs-hostmap.txt" );

    UrlRewriteEnvironment environment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    EasyMock.expect( environment.getResource( "/WEB-INF/hostmap.txt" ) ).andReturn( configUrl ).anyTimes();
    Resolver resolver = EasyMock.createNiceMock( Resolver.class );
    EasyMock.expect( resolver.resolve( "host" ) ).andReturn( Arrays.asList( "test-internal-host" ) ).anyTimes();
    EasyMock.replay( environment, resolver );

    UrlRewriteRulesDescriptor descriptor = UrlRewriteRulesDescriptorFactory.create();
    UrlRewriteRuleDescriptor rule = descriptor.addRule( "test-rule" );
    rule.pattern( "{*}://{host}:{*}/{**}?{qp1}&{qp2}&{**}" );
    UrlRewriteActionRewriteDescriptorExt rewrite = rule.addStep( "rewrite" );
    rewrite.template( "{*}://test-static-host:{*}/{qp1}/{qp2}/{**}?server={$hostmap(host)}&{**}" );

    UrlRewriteProcessor rewriter = new UrlRewriteProcessor();
    rewriter.initialize( environment, descriptor );

    Template input = Parser.parseLiteral(
        "test-scheme://test-external-host:42/test-path/test-file?qp1=qp1-val&qp2=qp2-val&test-name-1=test-value-1&test-name-2=test-value-2" );
    Template output = rewriter.rewrite( resolver, input, UrlRewriter.Direction.OUT, "test-rule" );
    //System.out.println( output );
    assertThat( output, notNullValue() );
    assertThat( output.getHost().getFirstValue().getPattern(), is( "test-static-host" ) );
    assertThat( output.getQuery().get( "server" ).getFirstValue().getPattern(), is( "test-external-host" ) );
    assertThat( output.getQuery().get( "server" ).getValues().size(), is( 1 ) );
    assertThat( output.getQuery().get( "test-name-1" ).getFirstValue().getPattern(), is( "test-value-1" ) );
    assertThat( output.getQuery().get( "test-name-1" ).getValues().size(), is( 1 ) );
    assertThat( output.getQuery().get( "test-name-2" ).getFirstValue().getPattern(), is( "test-value-2" ) );
    assertThat( output.getQuery().get( "test-name-2" ).getValues().size(), is( 1 ) );
    assertThat( output.getQuery().size(), is( 3 ) );
  }

  @Test
  public void testUnmappedUseCase() throws Exception {
    URL configUrl = TestUtils.getResourceUrl( this.getClass(), "hostmap.txt" );

    UrlRewriteEnvironment environment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    EasyMock.expect( environment.getResource( "/WEB-INF/hostmap.txt" ) ).andReturn( configUrl ).anyTimes();
    Resolver resolver = EasyMock.createNiceMock( Resolver.class );
    EasyMock.expect( resolver.resolve( "host" ) ).andReturn( Arrays.asList( "test-inbound-unmapped-host" ) ).anyTimes();
    EasyMock.replay( environment, resolver );

    UrlRewriteRulesDescriptor descriptor = UrlRewriteRulesDescriptorFactory.create();
    UrlRewriteRuleDescriptor rule = descriptor.addRule( "test-rule" );
    rule.pattern( "{*}://{host}:{*}/{**}?{**}" );
    UrlRewriteActionRewriteDescriptorExt rewrite = rule.addStep( "rewrite" );
    rewrite.template( "{*}://{$hostmap(host)}:{*}/{**}?{**}" );

    UrlRewriteProcessor rewriter = new UrlRewriteProcessor();
    rewriter.initialize( environment, descriptor );

    Template input = Parser.parseLiteral(
        "test-scheme://test-inbound-unmapped-host:42/test-path/test-file?test-name-1=test-value-1&test-name-2=test-value-2" );
    Template output = rewriter.rewrite( resolver, input, UrlRewriter.Direction.IN, null );
    //System.out.println( output );
    assertThat( output, notNullValue() );
    assertThat( output.getHost().getFirstValue().getPattern(), is( "test-inbound-unmapped-host" ) );
  }

  @Test
  public void testInvalidFunctionNameUseCase() throws Exception {
    URL configUrl = TestUtils.getResourceUrl( this.getClass(), "hostmap.txt" );

    UrlRewriteEnvironment environment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    EasyMock.expect( environment.getResource( "/WEB-INF/hostmap.txt" ) ).andReturn( configUrl ).anyTimes();
    Resolver resolver = EasyMock.createNiceMock( Resolver.class );
    EasyMock.expect( resolver.resolve( "host" ) ).andReturn( Arrays.asList( "test-inbound-host" ) ).anyTimes();
    EasyMock.replay( environment, resolver );

    UrlRewriteRulesDescriptor descriptor = UrlRewriteRulesDescriptorFactory.create();
    UrlRewriteRuleDescriptor rule = descriptor.addRule( "test-rule" );
    rule.pattern( "{*}://{host}:{*}/{**}?{**}" );
    UrlRewriteActionRewriteDescriptorExt rewrite = rule.addStep( "rewrite" );
    rewrite.template( "{*}://{$invalid-function(host)}:{*}/{**}?{**}" );

    UrlRewriteProcessor rewriter = new UrlRewriteProcessor();
    rewriter.initialize( environment, descriptor );

    Template input = Parser.parseLiteral( "test-scheme://test-inbound-host:42/test-path/test-file?test-name=test-value" );
    Template output = rewriter.rewrite( resolver, input, UrlRewriter.Direction.IN, null );
    //System.out.println( output );
    assertThat( output, notNullValue() );
    assertThat( output.getHost().getFirstValue().getPattern(), is( "$invalid-function(host)" ) );
  }

  @Test
  public void testInvalidFunctionNameAndEmptyHostmapUseCase() throws Exception {
    URL configUrl = TestUtils.getResourceUrl( this.getClass(), "empty-hostmap.txt" );

    UrlRewriteEnvironment environment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    EasyMock.expect( environment.getResource( "/WEB-INF/hostmap.txt" ) ).andReturn( configUrl ).anyTimes();
    Resolver resolver = EasyMock.createNiceMock( Resolver.class );
    EasyMock.expect( resolver.resolve( "host" ) ).andReturn( Arrays.asList( "test-inbound-host" ) ).anyTimes();
    EasyMock.replay( environment, resolver );

    UrlRewriteRulesDescriptor descriptor = UrlRewriteRulesDescriptorFactory.create();
    UrlRewriteRuleDescriptor rule = descriptor.addRule( "test-rule" );
    rule.pattern( "{*}://{host}:{*}/{**}?{**}" );
    UrlRewriteActionRewriteDescriptorExt rewrite = rule.addStep( "rewrite" );
    rewrite.template( "{*}://{$invalid-function(host)}:{*}/{**}?{**}" );

    UrlRewriteProcessor rewriter = new UrlRewriteProcessor();
    rewriter.initialize( environment, descriptor );

    Template input = Parser.parseLiteral( "test-scheme://test-inbound-host:42/test-path/test-file?test-name=test-value" );
    Template output = rewriter.rewrite( resolver, input, UrlRewriter.Direction.IN, null );
    //System.out.println( output );
    assertThat( output, notNullValue() );
    assertThat( output.getHost().getFirstValue().getPattern(), is( "$invalid-function(host)" ) );
  }

  @Test
  public void testEmptyHostmapUseCase() throws Exception {
    URL configUrl = TestUtils.getResourceUrl( this.getClass(), "empty-hostmap.txt" );

    UrlRewriteEnvironment environment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    EasyMock.expect( environment.getResource( "/WEB-INF/hostmap.txt" ) ).andReturn( configUrl ).anyTimes();
    Resolver resolver = EasyMock.createNiceMock( Resolver.class );
    EasyMock.expect( resolver.resolve( "host" ) ).andReturn( Arrays.asList( "test-inbound-host" ) ).anyTimes();
    EasyMock.replay( environment, resolver );

    UrlRewriteRulesDescriptor descriptor = UrlRewriteRulesDescriptorFactory.create();
    UrlRewriteRuleDescriptor rule = descriptor.addRule( "test-rule" );
    rule.pattern( "{*}://{host}:{*}/{**}?{**}" );
    UrlRewriteActionRewriteDescriptorExt rewrite = rule.addStep( "rewrite" );
    rewrite.template( "{*}://{$hostmap(host)}:{*}/{**}?{**}" );

    UrlRewriteProcessor rewriter = new UrlRewriteProcessor();
    rewriter.initialize( environment, descriptor );

    Template input = Parser.parseLiteral( "test-scheme://test-inbound-host:42/test-path/test-file?test-name=test-value" );
    Template output = rewriter.rewrite( resolver, input, UrlRewriter.Direction.IN, null );
    //System.out.println( output );
    assertThat( output, notNullValue() );
    assertThat( output.getHost().getFirstValue().getPattern(), is( "test-inbound-host" ) );
  }

}
