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
package org.apache.hadoop.gateway.filter.rewrite.api;

import org.apache.hadoop.gateway.filter.AbstractGatewayFilter;
import org.apache.hadoop.gateway.util.urltemplate.Parser;
import org.apache.hadoop.test.mock.MockInteraction;
import org.apache.hadoop.test.mock.MockServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.testing.HttpTester;
import org.eclipse.jetty.testing.ServletTester;
import org.eclipse.jetty.util.ArrayQueue;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.EnumSet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;

public class UrlRewriteServletFilterTest {

  private ServletTester server;
  private HttpTester request;
  private HttpTester response;
  private ArrayQueue<MockInteraction> interactions;
  private MockInteraction interaction;

  private static URL getTestResource( String name ) {
    name = UrlRewriteServletFilterTest.class.getName().replaceAll( "\\.", "/" ) + "/" + name;
    URL url = ClassLoader.getSystemResource( name );
    return url;
  }

  @Before
  public void setUp() throws Exception {
    String descriptorUrl = getTestResource( "rewrite.xml" ).toExternalForm();

    server = new ServletTester();
    server.setContextPath( "/" );
    server.getContext().addEventListener( new UrlRewriteServletContextListener() );
    server.getContext().setInitParameter(
        UrlRewriteServletContextListener.DESCRIPTOR_LOCATION_INIT_PARAM_NAME, descriptorUrl );

    FilterHolder setupFilter = server.addFilter( SetupFilter.class, "/*", EnumSet.of( DispatcherType.REQUEST ) );
    setupFilter.setFilter( new SetupFilter() );
    FilterHolder rewriteFilter = server.addFilter( UrlRewriteServletFilter.class, "/*", EnumSet.of( DispatcherType.REQUEST ) );
    rewriteFilter.setFilter( new UrlRewriteServletFilter() );

    interactions = new ArrayQueue<MockInteraction>();

    ServletHolder servlet = server.addServlet( MockServlet.class, "/" );
    servlet.setServlet( new MockServlet( "mock-servlet", interactions ) );

    server.start();

    interaction = new MockInteraction();
    request = new HttpTester();
    response = new HttpTester();
  }

  @After
  public void tearDown() throws Exception {
    server.stop();
  }

  @Test
  public void testInboundRequestUrlRewrite() throws Exception {
    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "GET" )
        .requestURL( "http://mock-host:1/test-output-path" );
    interaction.respond().status( 200 ).content( "test-response-content".getBytes() );
    interactions.add( interaction );
    // Create the client request.
    request.setMethod( "GET" );
    request.setURI( "/test-input-path" );
    request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:1" );
    // Execute the request.
    response.parse( server.getResponses( request.generate() ) );

    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );
    assertThat( response.getContent(), is( "test-response-content" ) );
  }

  @Test
  public void testInboundHeaderRewrite() throws Exception {
    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "GET" )
        .requestURL( "http://mock-host:1/test-output-path" )
        .header( "Location", "http://mock-host:1/test-output-path" );
    interaction.respond()
        .status( 200 );
    interactions.add( interaction );
    // Create the client request.
    request.setMethod( "GET" );
    request.setURI( "/test-input-path" );
    request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:1" );
    request.setHeader( "Location", "http://mock-host:1/test-input-path" );
    // Execute the request.
    response.parse( server.getResponses( request.generate() ) );

    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );
  }

  @Test
  public void testOutboundHeaderRewrite() throws Exception {
    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "GET" )
        .requestURL( "http://mock-host:1/test-output-path" );
    interaction.respond()
        .status( 201 )
        .header( "Location", "http://mock-host:1/test-input-path" );
    interactions.add( interaction );
    // Create the client request.
    request.setMethod( "GET" );
    request.setURI( "/test-input-path" );
    request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:1" );
    // Execute the request.
    response.parse( server.getResponses( request.generate() ) );

    // Test the results.
    assertThat( response.getStatus(), is( 201 ) );
    assertThat( response.getHeader( "Location" ), is( "http://mock-host:1/test-output-path" ) );
  }

  @Ignore( "Need to figure out how to handle cookies since domain and path are separate." )
  @Test
  public void testInboundCookieRewrite() throws Exception {
    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "GET" )
        .requestURL( "http://mock-host:1/test-output-path" )
        .header( "Cookie", "cookie-name=cookie-value; Domain=docs.foo.com; Path=/accounts; Expires=Wed, 13-Jan-2021 22:23:01 GMT; Secure; HttpOnly" );
    interaction.respond()
        .status( 201 );
    interactions.add( interaction );
    // Create the client request.
    request.setMethod( "GET" );
    request.setURI( "/test-input-path" );
    request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:1" );
    request.addHeader( "Cookie", "cookie-name=cookie-value; Domain=docs.foo.com; Path=/accounts; Expires=Wed, 13-Jan-2021 22:23:01 GMT; Secure; HttpOnly" );

    // Execute the request.
    response.parse( server.getResponses( request.generate() ) );

    // Test the results.
    assertThat( response.getStatus(), is( 201 ) );
    fail( "TODO" );
  }

  @Ignore( "Need to figure out how to handle cookies since domain and path are separate." )
  @Test
  public void testOutboundCookieRewrite() throws Exception {
    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "GET" )
        .requestURL( "http://mock-host:1/test-output-path" );
    interaction.respond()
        .status( 200 )
        .header( "Set-Cookie", "cookie-name=cookie-value; Domain=docs.foo.com; Path=/accounts; Expires=Wed, 13-Jan-2021 22:23:01 GMT; Secure; HttpOnly" );
    interactions.add( interaction );
    // Create the client request.
    request.setMethod( "GET" );
    request.setURI( "/test-input-path" );
    request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:1" );

    // Execute the request.
    response.parse( server.getResponses( request.generate() ) );

    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );
    assertThat( response.getHeader( "Set-Cookie" ), is( "TODO" ) );
    fail( "TODO" );
  }

  @Test
  public void testInboundJsonBodyRewrite() throws Exception {
    String inputJson = "{\"url\":\"http://mock-host:1/test-input-path\"}";
    String outputJson = "{\"url\":\"http://mock-host:1/test-output-path\"}";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "PUT" )
        .requestURL( "http://mock-host:1/test-output-path" )
        .content( outputJson, Charset.forName( "UTF-8" ) );
    interaction.respond()
        .status( 200 );
    interactions.add( interaction );
    request.setMethod( "PUT" );
    request.setURI( "/test-input-path" );
    request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:1" );
    request.setContentType( "application/json; charset=UTF-8" );
    request.setContent( inputJson );

    // Execute the request.
    response.parse( server.getResponses( request.generate() ) );

    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );
  }

  @Test
  public void testOutboundJsonBodyRewrite() throws Exception {
    String inputJson = "{\"url\":\"http://mock-host:1/test-input-path\"}";
    String outputJson = "{\"url\":\"http://mock-host:1/test-output-path\"}";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "PUT" )
        .requestURL( "http://mock-host:1/test-output-path" );
    interaction.respond()
        .status( 200 )
        .contentType( "application/json" )
        .content( inputJson, Charset.forName( "UTF-8" ) );
    interactions.add( interaction );
    request.setMethod( "PUT" );
    request.setURI( "/test-input-path" );
    request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:1" );

    // Execute the request.
    response.parse( server.getResponses( request.generate() ) );

    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );
    assertThat( response.getContent(), is( outputJson ) );
  }

  @Test
  public void testInboundXmlBodyRewrite() throws Exception {
    String input = "<root attribute=\"http://mock-host:1/test-input-path\">http://mock-host:1/test-input-path</root>";
    String output = "<root attribute=\"http://mock-host:1/test-output-path\">http://mock-host:1/test-output-path</root>";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "PUT" )
        .requestURL( "http://mock-host:1/test-output-path" )
        .content( output, Charset.forName( "UTF-8" ) );
    interaction.respond()
        .status( 200 );
    interactions.add( interaction );
    request.setMethod( "PUT" );
    request.setURI( "/test-input-path" );
    request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:1" );
    request.setContentType( "application/xml; charset=UTF-8" );
    request.setContent( input );

    // Execute the request.
    response.parse( server.getResponses( request.generate() ) );

    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );
  }

  // MatcherAssert.assertThat( XmlConverters.the( outputHtml ), XmlMatchers.hasXPath( "/html" ) );
  @Test
  public void testOutboundXmlBodyRewrite() throws Exception {
    String input = "{\"url\":\"http://mock-host:1/test-input-path\"}";
    String expect = "{\"url\":\"http://mock-host:1/test-output-path\"}";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "PUT" )
        .requestURL( "http://mock-host:1/test-output-path" );
    interaction.respond()
        .status( 200 )
        .contentType( "application/json" )
        .content( input, Charset.forName( "UTF-8" ) );
    interactions.add( interaction );
    request.setMethod( "PUT" );
    request.setURI( "/test-input-path" );
    request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:1" );

    // Execute the request.
    response.parse( server.getResponses( request.generate() ) );

    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );
    assertThat( response.getContent(), is( expect ) );
  }

  @Test
  public void testOutboundHtmlBodyRewrite() throws Exception {

    String input = "<html><head></head><body><a href=\"http://mock-host:1/test-input-path\">link text</a></body></html>";
    String output = "<html><head></head><body><a href=\"http://mock-host:1/test-output-path\">link text</a></body></html>";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "PUT" )
        .requestURL( "http://mock-host:1/test-output-path" )
        .content( output, Charset.forName( "UTF-8" ) );
    interaction.respond()
        .status( 200 );
    interactions.add( interaction );
    request.setMethod( "PUT" );
    request.setURI( "/test-input-path" );
    request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:1" );
    request.setContentType( "application/xml; charset=UTF-8" );
    request.setContent( input );

    // Execute the request.
    response.parse( server.getResponses( request.generate() ) );

    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );
  }

  @Test
  public void testInboundHtmlFormRewrite() throws Exception {
    String input = "Name=Jonathan+Doe&Age=23&Formula=a+%2B+b+%3D%3D+13%25%21&url=http%3A%2F%2Fmock-host%3A1%2Ftest-input-path";
    String expect = "Name=Jonathan+Doe&Age=23&Formula=a+%2B+b+%3D%3D+13%25%21&url=http%3A%2F%2Fmock-host%3A1%2Ftest-output-path";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "PUT" )
        .requestURL( "http://mock-host:1/test-output-path" )
        .content( expect, Charset.forName( "UTF-8" ) );
    interaction.respond()
        .status( 200 );
    interactions.add( interaction );
    request.setMethod( "PUT" );
    request.setURI( "/test-input-path" );
    request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:1" );
    request.setContentType( "application/x-www-form-urlencoded; charset=UTF-8" );
    request.setContent( input );

    // Execute the request.
    response.parse( server.getResponses( request.generate() ) );

    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );
  }

  private static class SetupFilter implements Filter {
    @Override
    public void init( FilterConfig filterConfig ) throws ServletException {
    }

    @Override
    public void doFilter( ServletRequest request, ServletResponse response, FilterChain chain ) throws IOException, ServletException {
      HttpServletRequest httpRequest = ((HttpServletRequest)request);
      StringBuffer sourceUrl = httpRequest.getRequestURL();
      String queryString = httpRequest.getQueryString();
      if( queryString != null ) {
        sourceUrl.append( "?" );
        sourceUrl.append( queryString );
      }
      try {
        request.setAttribute(
            AbstractGatewayFilter.SOURCE_REQUEST_URL_ATTRIBUTE_NAME,
            Parser.parse( sourceUrl.toString() ) );
      } catch( URISyntaxException e ) {
        throw new ServletException( e );
      }
      chain.doFilter( request, response );
    }

    @Override
    public void destroy() {
    }
  }

}
