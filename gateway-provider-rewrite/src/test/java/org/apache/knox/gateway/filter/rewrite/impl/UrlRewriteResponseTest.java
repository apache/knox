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
package org.apache.knox.gateway.filter.rewrite.impl;

import org.apache.commons.io.IOUtils;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteProcessor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletContextListener;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletFilter;
import org.easymock.EasyMock;
import org.junit.Test;

import javax.activation.MimeTypeParseException;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsCollectionContaining.hasItems;

public class UrlRewriteResponseTest {

  @Test
  public void testResolve() throws Exception {

    UrlRewriteProcessor rewriter = EasyMock.createNiceMock( UrlRewriteProcessor.class );

    ServletContext context = EasyMock.createNiceMock( ServletContext.class );
    EasyMock.expect( context.getServletContextName() ).andReturn( "test-cluster-name" ).anyTimes();
    EasyMock.expect( context.getInitParameter( "test-init-param-name" ) ).andReturn( "test-init-param-value" ).anyTimes();
    EasyMock.expect( context.getAttribute( UrlRewriteServletContextListener.PROCESSOR_ATTRIBUTE_NAME ) ).andReturn( rewriter ).anyTimes();

    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect( config.getInitParameter( "test-filter-init-param-name" ) ).andReturn( "test-filter-init-param-value" ).anyTimes();
    EasyMock.expect( config.getServletContext() ).andReturn( context ).anyTimes();

    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );

    EasyMock.replay( rewriter, context, config, request, response );

    UrlRewriteResponse rewriteResponse = new UrlRewriteResponse( config, request, response );

    List<String> names = rewriteResponse.resolve( "test-filter-init-param-name" );
    assertThat( names.size(), is( 1 ) );
    assertThat( names.get( 0 ), is( "test-filter-init-param-value" ) );
  }

  @Test
  public void testResolveGatewayParams() throws Exception {

    UrlRewriteProcessor rewriter = EasyMock.createNiceMock( UrlRewriteProcessor.class );

    ServletContext context = EasyMock.createNiceMock( ServletContext.class );
    EasyMock.expect( context.getAttribute( UrlRewriteServletContextListener.PROCESSOR_ATTRIBUTE_NAME ) ).andReturn( rewriter ).anyTimes();

    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect( config.getServletContext() ).andReturn( context ).anyTimes();

    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getScheme() ).andReturn( "mock-scheme" ).anyTimes();
    EasyMock.expect( request.getLocalName() ).andReturn( "mock-host" ).anyTimes();
    EasyMock.expect( request.getLocalPort() ).andReturn( 42 ).anyTimes();
    EasyMock.expect( request.getContextPath() ).andReturn( "/mock-path" ).anyTimes();
    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );

    EasyMock.replay( rewriter, context, config, request, response );

    UrlRewriteResponse rewriteResponse = new UrlRewriteResponse( config, request, response );

    List<String> url = rewriteResponse.resolve( "gateway.url" );
    assertThat( url, hasItems( new String[]{ "mock-scheme://mock-host:42/mock-path" } ) );

    List<String> scheme = rewriteResponse.resolve( "gateway.scheme" );
    assertThat( scheme, hasItems( new String[]{ "mock-scheme" } ) );

    List<String> host = rewriteResponse.resolve( "gateway.host" );
    assertThat( host, hasItems( new String[]{ "mock-host" } ) );

    List<String> port = rewriteResponse.resolve( "gateway.port" );
    assertThat( port, hasItems( new String[]{ "42" } ) );

    List<String> addr = rewriteResponse.resolve( "gateway.addr" );
    assertThat( addr, hasItems( new String[]{ "mock-host:42" } ) );

    List<String> address = rewriteResponse.resolve( "gateway.addr" );
    assertThat( address, hasItems( new String[]{ "mock-host:42" } ) );

    List<String> path = rewriteResponse.resolve( "gateway.path" );
    assertThat( path, hasItems( new String[]{ "/mock-path" } ) );
  }

  @Test
  public void testStreamResponse() throws IOException, MimeTypeParseException {
    UrlRewriteProcessor rewriter = EasyMock.createNiceMock( UrlRewriteProcessor.class );
    EasyMock.expect( rewriter.getConfig() ).andReturn( null ).anyTimes();

    ServletContext context = EasyMock.createNiceMock( ServletContext.class );
    EasyMock.expect( context.getAttribute( UrlRewriteServletContextListener.PROCESSOR_ATTRIBUTE_NAME ) ).andReturn( rewriter ).anyTimes();

    FilterConfig config = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect( config.getInitParameter( UrlRewriteServletFilter.RESPONSE_BODY_FILTER_PARAM ) ).andReturn( "test-filter" ).anyTimes();
    EasyMock.expect( config.getServletContext() ).andReturn( context ).anyTimes();

    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );

    EasyMock.replay( rewriter, context, config, request, response );

    UrlRewriteResponse rewriteResponse = new UrlRewriteResponse( config, request, response );

    String content = "content to test gzip streaming";
    testStreamResponseGzip ( content, rewriteResponse, false );
    testStreamResponseGzip ( content, rewriteResponse, true );
  }

  private void testStreamResponseGzip( String content, UrlRewriteResponse rewriteResponse , boolean isGzip ) throws IOException {
    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File inputFile = new File( targetDir, "input.test" );
    File outputFile = new File( targetDir, "output.test" );
    OutputStream outStream = null, output = null;
    InputStream inStream = null, input = null;
    try {
      outStream = isGzip ? new GZIPOutputStream( new FileOutputStream( inputFile ) ) : new FileOutputStream( inputFile );
      outStream.write( content.getBytes(StandardCharsets.UTF_8) );
      outStream.close();

      input = new FileInputStream( inputFile );
      output = new FileOutputStream( outputFile );
      rewriteResponse.streamResponse( input, output );

      inStream = isGzip ? new GZIPInputStream( new FileInputStream( outputFile ) ) : new FileInputStream( outputFile );
      assertThat( String.valueOf( IOUtils.toCharArray( inStream, StandardCharsets.UTF_8 ) ), is( content ) );
    } finally {
      if ( inStream != null ) {
        inStream.close();
      }
      if ( input != null ) {
        input.close();
      }
      inputFile.delete();
      outputFile.delete();
    }
  }
}
