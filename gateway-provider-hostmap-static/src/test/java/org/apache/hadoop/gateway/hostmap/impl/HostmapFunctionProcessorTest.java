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
package org.apache.hadoop.gateway.hostmap.impl;

import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteProcessor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRuleDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRulesDescriptorFactory;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriter;
import org.apache.hadoop.gateway.filter.rewrite.ext.UrlRewriteActionRewriteDescriptorExt;
import org.apache.hadoop.gateway.util.urltemplate.Parser;
import org.apache.hadoop.gateway.util.urltemplate.Resolver;
import org.apache.hadoop.gateway.util.urltemplate.Template;
import org.apache.hadoop.test.TestUtils;
import org.easymock.EasyMock;
import org.junit.Test;

import java.net.URL;
import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class HostmapFunctionProcessorTest {

  @Test
  public void testBasicUseCase() throws Exception {
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
    rewrite.template( "{*}://{$hostmap(host)}:{*}/{**}?{**}" );

    UrlRewriteProcessor rewriter = new UrlRewriteProcessor();
    rewriter.initialize( environment, descriptor );

    Template input = Parser.parse( "test-scheme://test-inbound-host:42/test-path/test-file?test-name=test-value" );
    Template output = rewriter.rewrite( resolver, input, UrlRewriter.Direction.IN );
    System.out.println( output );
    assertThat( output, notNullValue() );
    assertThat( output.getHost().getFirstValue().getPattern(), is( "test-inbound-rewritten-host" ) );
  }

}
