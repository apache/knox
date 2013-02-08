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
package org.apache.hadoop.gateway.filter.rewrite.ext;

import org.apache.hadoop.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.hadoop.gateway.util.urltemplate.Parser;
import org.apache.hadoop.gateway.util.urltemplate.Template;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Test;
import sun.misc.BASE64Encoder;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class UrlRewriteActionQueryDecodeProcessorTest {

  @Test
  public void testSimpleQueryDecode() throws Exception {
    BASE64Encoder encoder = new BASE64Encoder();
    String encQuery = encoder.encode( "test-query".getBytes("utf-8" ) );
    encQuery = encQuery.replaceAll( "\\=", "" );
    String inString = "http://host:0/root/path?_=" + encQuery;
    Template inTemplate = Parser.parse( inString );

    UrlRewriteContext context = EasyMock.createNiceMock( UrlRewriteContext.class );
    EasyMock.expect( context.getCurrentUrl() ).andReturn( inTemplate );
    Capture<Template> outTemplate = new Capture<Template>();
    context.setCurrentUrl( EasyMock.capture( outTemplate ) );
    EasyMock.replay( context );

    UrlRewriteActionQueryDecodeDescriptor descriptor = new UrlRewriteActionQueryDecodeDescriptor();
    UrlRewriteActionQueryDecodeProcessor processor = new UrlRewriteActionQueryDecodeProcessor();
    processor.initialize( descriptor );
    processor.process( context );

    String outActual = outTemplate.getValue().toString();
    assertThat( outActual, is( "http://host:0/root/path?test-query" ) );
  }

  @Test
  public void testDecodeQueryWithNonEncodedParams() throws Exception {
    BASE64Encoder encoder = new BASE64Encoder();
    String inQuery = "test-query=test-value";
    String encQuery = encoder.encode( inQuery.getBytes( "utf-8" ) );
    encQuery = encQuery.replaceAll( "\\=", "" );
    String inString = "http://host:0/root/path?_=" + encQuery + "&clear-param=clear-value";
    Template inTemplate = Parser.parse( inString );

    UrlRewriteContext context = EasyMock.createNiceMock( UrlRewriteContext.class );
    EasyMock.expect( context.getCurrentUrl() ).andReturn( inTemplate );
    Capture<Template> outTemplate = new Capture<Template>();
    context.setCurrentUrl( EasyMock.capture( outTemplate ) );
    EasyMock.replay( context );

    UrlRewriteActionQueryDecodeDescriptor descriptor = new UrlRewriteActionQueryDecodeDescriptor();
    UrlRewriteActionQueryDecodeProcessor processor = new UrlRewriteActionQueryDecodeProcessor();
    processor.initialize( descriptor );
    processor.process( context );

    String outActual = outTemplate.getValue().toString();
    assertThat( outActual, containsString( "http://host:0/root/path?" ) );
    assertThat( outActual, containsString( "test-query=test-value" ) );
    assertThat( outActual, containsString( "clear-param=clear-value" ) );
    assertThat( outActual, not( containsString( encQuery ) ) );
  }


}
