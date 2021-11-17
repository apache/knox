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

import com.jayway.jsonassert.JsonAssert;
import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.test.TestUtils;
import org.apache.knox.test.mock.MockInteraction;
import org.apache.knox.test.mock.MockServlet;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletTester;
import org.junit.After;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.Is.is;
import static org.xmlmatchers.XmlMatchers.hasXPath;
import static org.xmlmatchers.transform.XmlConverters.the;

public class UrlRewriteServletFilterTest {
  private ServletTester server;
  private HttpTester.Request request;
  private HttpTester.Response response;
  private Queue<MockInteraction> interactions;
  private MockInteraction interaction;

  private static URL getTestResource( String name ) {
    name = UrlRewriteServletFilterTest.class.getName().replaceAll( "\\.", "/" ) + "/" + name;
    return ClassLoader.getSystemResource( name );
  }

  private void testSetUp(Map<String,String> initParams ) throws Exception {
    String descriptorUrl = getTestResource( "rewrite.xml" ).toExternalForm();

    server = new ServletTester();
    server.setContextPath( "/" );
    server.getContext().addEventListener( new UrlRewriteServletContextListener() );
    server.getContext().setInitParameter(
        UrlRewriteServletContextListener.DESCRIPTOR_LOCATION_INIT_PARAM_NAME, descriptorUrl );

    FilterHolder setupFilter = server.addFilter( SetupFilter.class, "/*", EnumSet.of( DispatcherType.REQUEST ) );
    setupFilter.setFilter( new SetupFilter() );
    FilterHolder rewriteFilter = server.addFilter( UrlRewriteServletFilter.class, "/*", EnumSet.of( DispatcherType.REQUEST ) );
    if( initParams != null ) {
      for( Map.Entry<String,String> entry : initParams.entrySet() ) {
        rewriteFilter.setInitParameter( entry.getKey(), entry.getValue() );
      }
    }
    rewriteFilter.setFilter( new UrlRewriteServletFilter() );

    interactions = new ArrayDeque<>();

    ServletHolder servlet = server.addServlet( MockServlet.class, "/" );
    servlet.setServlet( new MockServlet( "mock-servlet", interactions ) );

    server.start();

    interaction = new MockInteraction();
    request = HttpTester.newRequest();
    response = null;
  }

  @After
  public void tearDown() throws Exception {
    if( server != null ) {
      server.stop();
    }
  }

  @Test
  public void testInboundRequestUrlRewrite() throws Exception {
    testSetUp( null );
    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "GET" )
        .requestUrl( "http://mock-host:1/test-output-path-1" );
    interaction.respond().status( 200 ).content( "test-response-content".getBytes(StandardCharsets.UTF_8) );
    interactions.add( interaction );
    // Create the client request.
    request.setMethod( "GET" );
    request.setURI( "/test-input-path" );
    //request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:1" );
    // Execute the request.
    response = TestUtils.execute( server, request );

    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );
    assertThat( response.getContent(), is( "test-response-content" ) );
  }

  @Test
  public void testInboundHeaderRewrite() throws Exception {
    testSetUp( null );
    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "GET" )
        .requestUrl( "http://mock-host:1/test-output-path-1" )
        .header( "Location", "http://mock-host:1/test-output-path-1" );
    interaction.respond()
        .status( 200 );
    interactions.add( interaction );
    // Create the client request.
    request.setMethod( "GET" );
    request.setURI( "/test-input-path" );
    //request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:1" );
    request.setHeader( "Location", "http://mock-host:1/test-input-path" );
    // Execute the request.
    response = TestUtils.execute( server, request );

    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );
  }

  @Test
  public void testOutboundHeaderRewrite() throws Exception {
    testSetUp( null );
    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "GET" )
        .requestUrl( "http://mock-host:1/test-output-path-1" );
    interaction.respond()
        .status( 201 )
        .header( "Location", "http://mock-host:1/test-input-path" );
    interactions.add( interaction );
    // Create the client request.
    request.setMethod( "GET" );
    request.setURI( "/test-input-path" );
    //request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:1" );
    // Execute the request.
    response = TestUtils.execute( server, request );

    // Test the results.
    assertThat( response.getStatus(), is( 201 ) );
    assertThat( response.get( HttpHeader.LOCATION ), is( "http://mock-host:1/test-output-path-1" ) );
  }

//  @Ignore( "Need to figure out how to handle cookies since domain and path are separate." )
//  @Test
//  public void testRequestCookieRewrite() throws Exception {
//    setUpAndReturnOriginalAppenders( null );
//    // Setup the server side request/response interaction.
//    interaction.expect()
//        .method( "GET" )
//        .requestUrl( "http://mock-host:1/test-output-path-1" )
//        .header( "Cookie", "cookie-name=cookie-value; Domain=docs.foo.com; Path=/accounts; Expires=Wed, 13-Jan-2021 22:23:01 GMT; Secure; HttpOnly" );
//    interaction.respond()
//        .status( 201 );
//    interactions.add( interaction );
//    // Create the client request.
//    request.setMethod( "GET" );
//    request.setURI( "/test-input-path" );
//    //request.setVersion( "HTTP/1.1" );
//    request.setHeader( "Host", "mock-host:1" );
//    request.setHeader( "Cookie", "cookie-name=cookie-value; Domain=docs.foo.com; Path=/accounts; Expires=Wed, 13-Jan-2021 22:23:01 GMT; Secure; HttpOnly" );
//
//    // Execute the request.
//    response = TestUtils.execute( server, request );
//
//    // Test the results.
//    assertThat( response.getStatus(), is( 201 ) );
//    fail( "TODO" );
//  }

//  @Ignore( "Need to figure out how to handle cookies since domain and path are separate." )
//  @Test
//  public void testResponseCookieRewrite() throws Exception {
//    setUpAndReturnOriginalAppenders( null );
//    // Setup the server side request/response interaction.
//    interaction.expect()
//        .method( "GET" )
//        .requestUrl( "http://mock-host:1/test-output-path-1" );
//    interaction.respond()
//        .status( 200 )
//        .header( "Set-Cookie", "cookie-name=cookie-value; Domain=docs.foo.com; Path=/accounts; Expires=Wed, 13-Jan-2021 22:23:01 GMT; Secure; HttpOnly" );
//    interactions.add( interaction );
//    // Create the client request.
//    request.setMethod( "GET" );
//    request.setURI( "/test-input-path" );
//    //request.setVersion( "HTTP/1.1" );
//    request.setHeader( "Host", "mock-host:1" );
//
//    // Execute the request.
//    response = TestUtils.execute( server, request );
//
//    // Test the results.
//    assertThat( response.getStatus(), is( 200 ) );
//    assertThat( response.get( HttpHeader.SET_COOKIE ), is( "TODO" ) );
//    fail( "TODO" );
//  }

  // If no rewrite rule is defined for inbound request skip rewriting JSON Body.
  @Test
  public void testInboundJsonBodyRewrite() throws Exception {
    testSetUp( null );

    String inputJson = "{\"url\":\"http://mock-host:1/test-input-path\"}";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "PUT" )
        .requestUrl( "http://mock-host:1/test-output-path-1" )
        // Make sure nothing changed in the payload since no rule for payload was specified
        .content( inputJson, StandardCharsets.UTF_8 );
    interaction.respond()
        .status( 200 );
    interactions.add( interaction );
    request.setMethod( "PUT" );
    request.setURI( "/test-input-path" );
    //request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:1" );
    request.setHeader( "Content-Type", "application/json; charset=UTF-8" );
    request.setContent( inputJson );

    // Execute the request.
    response = TestUtils.execute( server, request );
    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );
  }

  @Test
  public void testInboundXmlBodyRewrite() throws Exception {
    testSetUp( null );
    String input = "<root attribute=\"http://mock-host:1/test-input-path\">http://mock-host:1/test-input-path</root>";
    String output;
    if(System.getProperty("java.vendor").contains("IBM")){
      output = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><root attribute=\"http://mock-host:1/test-output-path-1\">http://mock-host:1/test-output-path-1</root>";
    }else {
      output = "<?xml version=\"1.0\" standalone=\"no\"?><root attribute=\"http://mock-host:1/test-output-path-1\">http://mock-host:1/test-output-path-1</root>";
    }
    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "PUT" )
        .requestUrl( "http://mock-host:1/test-output-path-1" )
        .content( output, StandardCharsets.UTF_8 );
    interaction.respond()
        .status( 200 );
    interactions.add( interaction );
    request.setMethod( "PUT" );
    request.setURI( "/test-input-path" );
    //request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:1" );
    request.setHeader( "Content-Type", "application/xml; charset=UTF-8" );
    request.setContent( input );

    // Execute the request.
    response = TestUtils.execute( server, request );

    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );
  }

  // MatcherAssert.assertThat( XmlConverters.the( outputHtml ), XmlMatchers.hasXPath( "/html" ) );
  @Test
  public void testOutboundJsonBodyRewrite() throws Exception {
    testSetUp( null );

    String input = "{\"url\":\"http://mock-host:1/test-input-path\"}";
    String expect = "{\"url\":\"http://mock-host:1/test-output-path-1\"}";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "PUT" )
        .requestUrl( "http://mock-host:1/test-output-path-1" );
    interaction.respond()
        .status( 200 )
        .contentType( "application/json" )
        .content( input, StandardCharsets.UTF_8 );
    interactions.add( interaction );
    request.setMethod( "PUT" );
    request.setURI( "/test-input-path" );
    //request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:1" );

    // Execute the request.
    response = TestUtils.execute( server, request );

    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );
    assertThat( response.getContent(), is( expect ) );
  }

  @Test
  public void testOutboundHtmlBodyRewrite() throws Exception {
    testSetUp( null );

    String input = "<html><head></head><body><a href=\"http://mock-host:1/test-input-path\">link text</a></body></html>";
    String output = "<html><head></head><body><a href=\"http://mock-host:1/test-output-path-1\">link text</a></body></html>";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "PUT" )
        .requestUrl( "http://mock-host:1/test-output-path-1" )
        .content( output, StandardCharsets.UTF_8 );
    interaction.respond()
        .status( 200 );
    interactions.add( interaction );
    request.setMethod( "PUT" );
    request.setURI( "/test-input-path" );
    //request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:1" );
    request.setHeader( "Content-Type", "application/html; charset=UTF-8" );
    request.setContent( input );

    // Execute the request.
    response = TestUtils.execute( server, request );

    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );
  }

  @Test
  public void testInboundHtmlFormRewrite() throws Exception {
    testSetUp( null );

    String input = "Name=Jonathan+Doe&Age=23&Formula=a+%2B+b+%3D%3D+13%25%21&url=http%3A%2F%2Fmock-host%3A1%2Ftest-input-path";
    String expect = "Name=Jonathan+Doe&Age=23&Formula=a+%2B+b+%3D%3D+13%25%21&url=http%3A%2F%2Fmock-host%3A1%2Ftest-output-path-1";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "PUT" )
        .requestUrl( "http://mock-host:1/test-output-path-1" )
        .content( expect, StandardCharsets.UTF_8 );
    interaction.respond()
        .status( 200 );
    interactions.add( interaction );
    request.setMethod( "PUT" );
    request.setURI( "/test-input-path" );
    //request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:1" );
    request.setHeader( "Content-Type", "application/x-www-form-urlencoded; charset=UTF-8" );
    request.setContent( input );

    // Execute the request.
    response = TestUtils.execute( server, request );

    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );
  }

  @Test
  public void testRequestUrlRewriteWithFilterInitParam() throws Exception {
    Map<String,String> initParams = new HashMap<>();
    initParams.put( "request.url", "test-rule-2" );
    testSetUp( initParams );

    String input = "<root/>";
    String expect = "<root/>";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "PUT" )
        .requestUrl( "http://mock-host:42/test-output-path-2" )
        .contentType( "text/xml" )
        .characterEncoding( StandardCharsets.UTF_8.name() )
        .content( expect, StandardCharsets.UTF_8 );
    interaction.respond()
        .status( 200 );
    interactions.add( interaction );
    request.setMethod( "PUT" );
    request.setURI( "/test-input-path" );
    //request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:42" );
    request.setHeader( "Content-Type", "text/xml; charset=UTF-8" );
    request.setContent( input );

    // Execute the request.
    response = TestUtils.execute( server, request );

    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );
  }

  @Test
  public void testRequestHeaderRewriteWithFilterInitParam() throws Exception {
    Map<String,String> initParams = new HashMap<>();
    initParams.put( "request.headers", "test-filter-2" );
    testSetUp( initParams );

    String input = "<root/>";
    String expect = "<root/>";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "PUT" )
        .requestUrl( "http://mock-host:42/test-output-path-1" )
        .contentType( "text/xml" )
        .characterEncoding( StandardCharsets.UTF_8.name() )
        .content( expect, StandardCharsets.UTF_8 )
        .header( "Location", "http://mock-host:42/test-output-path-2" );
    interaction.respond()
        .status( 200 );
    interactions.add( interaction );
    request.setMethod( "PUT" );
    request.setURI( "/test-input-path" );
    //request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:42" );
    request.setHeader( "Location", "http://mock-host:42/test-input-path-1" );
    request.setHeader( "Content-Type", "text/xml; charset=UTF-8" );
    request.setContent( input );

    // Execute the request.
    response = TestUtils.execute( server, request );

    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );
  }

//  @Ignore( "Not Implemented Yet" )
//  @Test
//  public void testRequestCookieRewriteWithFilterInitParam() {
//    fail( "TODO" );
//  }

  // Example test case where inbound rule is specified to rewrite request body.
  @Test
  public void testRequestJsonBodyRewriteWithFilterInitParam() throws Exception {
    Map<String,String> initParams = new HashMap<>();
    //initParams.put( "url, "" );
    initParams.put( "request.body", "test-filter-2" );
    //initParams.put( "response", "" );
    testSetUp( initParams );

    String inputJson = "{\"url\":\"http://mock-host:42/test-input-path-1\"}";
    String expectJson = "{\"url\":\"http://mock-host:42/test-output-path-2\"}";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "PUT" )
        .requestUrl( "http://mock-host:42/test-output-path-1" )
        .contentType( "application/json" )
        .content( expectJson, StandardCharsets.UTF_8 );
    interaction.respond()
        .status( 200 );
    interactions.add( interaction );
    request.setMethod( "PUT" );
    request.setURI( "/test-input-path" );
    //request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:42" );
    request.setHeader( "Content-Type", "application/json; charset=UTF-8" );
    request.setContent( inputJson );

    // Execute the request.
    response = TestUtils.execute( server, request );

    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );
  }

  @Test
  public void testRequestXmlBodyRewriteWithFilterInitParam() throws Exception {
    Map<String,String> initParams = new HashMap<>();
    initParams.put( "request.body", "test-filter-2" );
    testSetUp( initParams );

    String input = "<root url='http://mock-host:42/test-input-path-1'><url>http://mock-host:42/test-input-path-1</url></root>";
    String expect = "<root url='http://mock-host:42/test-output-path-2'><url>http://mock-host:42/test-output-path-2</url></root>";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "PUT" )
        .requestUrl( "http://mock-host:42/test-output-path-1" )
        .contentType( "text/xml" )
        .characterEncoding( StandardCharsets.UTF_8.name() )
        .content( expect, StandardCharsets.UTF_8 );
    interaction.respond()
        .status( 200 );
    interactions.add( interaction );
    request.setMethod( "PUT" );
    request.setURI( "/test-input-path" );
    //request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:42" );
    request.setHeader( "Content-Type", "text/xml; charset=UTF-8" );
    request.setContent( input );

    // Execute the request.
    response = TestUtils.execute( server, request );

    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );
  }

  @Test
  public void testRequestXmlBodyRewriteWithFilterInitParamForInvalidFilterConfig() throws Exception {
    Map<String,String> initParams = new HashMap<>();
    initParams.put( "request.body", "test-filter-3" );
    testSetUp( initParams );

    String input = "<root url='http://mock-host:42/test-input-path-1'><url>http://mock-host:42/test-input-path-2</url></root>";
    String expect = "<root url='http://mock-host:42/test-input-path-2'><url>http://mock-host:42/test-input-path-2</url></root>";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "PUT" )
        .requestUrl( "http://mock-host:42/test-output-path-1" )
        .contentType( "text/xml" )
        .characterEncoding( StandardCharsets.UTF_8.name() )
        .content( expect, StandardCharsets.UTF_8 );
    interaction.respond()
        .status( 200 );
    interactions.add( interaction );
    request.setMethod( "PUT" );
    request.setURI( "/test-input-path" );
    //request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:42" );
    request.setHeader( "Content-Type", "text/xml; charset=UTF-8" );
    request.setContent( input );

    // Execute the request.
    response = TestUtils.execute( server, request );

    // Test the results.
    assertThat( response.getStatus(), is( 500 ) );
  }

  @Test
  public void testRequestFormBodyRewriteWithFilterInitParam() throws Exception {
    Map<String,String> initParams = new HashMap<>();
    initParams.put( "request.body", "test-filter-2" );
    testSetUp( initParams );

    String input = "Name=Jonathan+Doe&Age=23&Formula=a+%2B+b+%3D%3D+13%25%21&url=http%3A%2F%2Fmock-host%3A1%2Ftest-input-path";
    String expect = "Name=Jonathan+Doe&Age=23&Formula=a+%2B+b+%3D%3D+13%25%21&url=http%3A%2F%2Fmock-host%3A1%2Ftest-output-path-2";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "PUT" )
        .requestUrl( "http://mock-host:1/test-output-path-1" )
        .content( expect, StandardCharsets.UTF_8 )
        .characterEncoding( StandardCharsets.UTF_8.name() );
    interaction.respond()
        .status( 200 );
    interactions.add( interaction );
    request.setMethod( "PUT" );
    request.setURI( "/test-input-path" );
    //request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:1" );
    request.setHeader( "Content-Type", "application/x-www-form-urlencoded; charset=UTF-8" );
    request.setContent( input );

    // Execute the request.
    response = TestUtils.execute( server, request );

    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );
  }

  @Test
  public void testResponseHeaderRewriteWithFilterInitParam() throws Exception {
    Map<String,String> initParams = new HashMap<>();
    initParams.put( "response.headers", "test-filter-2" );
    testSetUp( initParams );

    String output = "<root url='http://mock-host:42/test-input-path-2'><url>http://mock-host:42/test-input-path-3</url></root>";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "GET" )
        .requestUrl( "http://mock-host:42/test-output-path-1" );
    interaction.respond()
        .content( output, StandardCharsets.UTF_8 )
        .contentType( "text/xml" )
        .header( "Location", "http://mock-host:42/test-input-path-4" )
        .status( 307 );
    interactions.add( interaction );
    request.setMethod( "GET" );
    request.setURI( "/test-input-path-1" );
    //request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:42" );

    // Execute the request.
    response = TestUtils.execute( server, request );

    // Test the results.
    assertThat( response.getStatus(), is( 307 ) );
    assertThat( response.get( HttpHeader.LOCATION ), is( "http://mock-host:42/test-output-path-2" ) );

    String actual = response.getContent();

    assertThat( the( actual ), hasXPath( "/root/@url", equalTo( "http://mock-host:42/test-output-path-1" ) ) );
    assertThat( the( actual ), hasXPath( "/root/url/text()", equalTo( "http://mock-host:42/test-output-path-1" ) ) );
  }

//  @Ignore( "Not Implemented Yet" )
//  @Test
//  public void testResponseCookieRewriteWithFilterInitParam() {
//    fail( "TODO" );
//  }

  @Test
  public void testResponseJsonBodyRewriteWithFilterInitParam() throws Exception {
    Map<String,String> initParams = new HashMap<>();
    //initParams.put( "url, "" );
    initParams.put( "response.body", "test-filter-2" );
    //initParams.put( "response", "" );
    testSetUp( initParams );

    String responseJson = "{\"url\":\"http://mock-host:42/test-input-path-1\"}";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "GET" )
        .requestUrl( "http://mock-host:42/test-output-path-1" );
    interaction.respond()
        .contentType( "application/json" )
        .content( responseJson, StandardCharsets.UTF_8 )
        .status( 200 );
    interactions.add( interaction );
    request.setMethod( "GET" );
    request.setURI( "/test-input-path" );
    //request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "mock-host:42" );
    request.setHeader( "Content-Type", "application/json; charset=UTF-8" );
    request.setContent( responseJson );

    // Execute the request.
    response = TestUtils.execute( server, request );

    assertThat( response.getStatus(), is( 200 ) );
    JsonAssert.with( response.getContent() ).assertThat( "$.url", is( "http://mock-host:42/test-output-path-2" ) );
  }

  @Test
  public void testResponseHtmlBodyRewriteWithFilterInitParam() throws Exception {
    Map<String,String> initParams = new HashMap<>();
    //initParams.put( "url, "" );
    initParams.put( "response.body", "test-filter-4" );
    //initParams.put( "response", "" );
    testSetUp( initParams );

    String responseHtml = "<!DOCTYPE html>\n" +
        "<html>\n" +
        "  <head>\n" +
        "    <meta charset=\"UTF-8\">\n" +
        "    <link rel=\"stylesheet\" href=\"pretty.css\">\n" +
        "    <script src=\"script.js\"></script>\n" +
        "  </head>\n" +
        "  <body>\n" +
        "  </body>\n" +
        "</html>";
    String rewrittenResponseHtml = "<!DOCTYPE html>\n" +
        "<html>\n" +
        "  <head>\n" +
        "    <meta charset=\"UTF-8\">\n" +
        "    <link rel=\"stylesheet\" href=\"http://someotherhost/stylesheets/pretty.css\">\n" +
        "    <script src=\"script.js\"></script>\n" +
        "  </head>\n" +
        "  <body>\n" +
        "  </body>\n" +
        "</html>";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "GET" )
        .requestUrl( "http://mock-host:42/test-output-path-1" );
    interaction.respond()
        .contentType( "application/html" )
        .content( responseHtml, StandardCharsets.UTF_8 )
        .status( 200 );
    interactions.add( interaction );
    request.setMethod( "GET" );
    request.setURI( "/test-input-path" );
    request.setHeader( "Host", "mock-host:42" );
    request.setHeader( "Content-Type", "application/html" );

    // Execute the request.
    response = TestUtils.execute( server, request );

    assertThat( response.getStatus(), is( 200 ) );
    String content = response.getContent();
    assertThat(content, is(rewrittenResponseHtml));
  }

  @Test
  public void testResponseXmlBodyRewriteWithFilterInitParam() throws Exception {
    Map<String,String> initParams = new HashMap<>();
    initParams.put( "response.body", "test-filter-2" );
    testSetUp( initParams );

    String output = "<root url='http://mock-host:42/test-input-path-1'><url>http://mock-host:42/test-input-path-1</url></root>";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "GET" )
        .requestUrl( "http://mock-host:42/test-output-path-1" );
    interaction.respond()
        .content( output, StandardCharsets.UTF_8 )
        .contentType( "text/xml" )
        .status( 200 );
    interactions.add( interaction );
    request.setMethod( "GET" );
    request.setURI( "/test-input-path" );
    request.setVersion( "HTTP/1.0" );
    request.setHeader( "Host", "mock-host:42" );

    // Execute the request.
    response = TestUtils.execute( server, request );

    // Test the results.
    assertThat( response.getStatus(), is( 200 ) );

    String actual = response.getContent();

    assertThat( the( actual ), hasXPath( "/root/@url", equalTo( "http://mock-host:42/test-output-path-2" ) ) );
    assertThat( the( actual ), hasXPath( "/root/url/text()", equalTo( "http://mock-host:42/test-output-path-2" ) ) );
  }

  @Test
  public void testResponseHtmlBodyRewriteCSSImport() throws Exception {
    Map<String,String> initParams = new HashMap<>();
    //initParams.put( "url, "" );
    initParams.put( "response.body", "test-filter-5" );
    //initParams.put( "response", "" );
    testSetUp( initParams );

    String responseHtml = "<html>" +
                          "  <head>" +
                          "    <style type=\"text/css\">@import \"pretty.css\";</style>" +
                          "  </head>" +
                          "</html>";
    String responseHtmlOne = "<html>" +
                          "  <head>" +
                          "    <style type=\"text/css\">@import \"http://0.0.0.0:0/stylesheets/pretty.css\";</style>" +
                          "  </head>" +
                          "</html>";
    String responseHtmlTwo = "<html>" +
                          "  <head>" +
                          "    <style type=\"text/css\">@import \"http://localhost:0/stylesheets/pretty.css\";</style>" +
                          "  </head>" +
                          "</html>";

    // Setup the server side request/response interaction.
    interaction.expect()
               .method( "GET" )
               .requestUrl( "http://mock-host:42/test-output-path-1" );
    interaction.respond()
               .contentType( "application/html" )
               .content( responseHtml, StandardCharsets.UTF_8 )
               .status( 200 );
    interactions.add( interaction );
    request.setMethod( "GET" );
    request.setURI( "/test-input-path" );
    request.setHeader( "Host", "mock-host:42" );
    request.setHeader( "Content-Type", "application/html" );

    // Execute the request.
    response = TestUtils.execute( server, request );

    assertThat( response.getStatus(), is( 200 ) );
    String content = response.getContent();
//    assertThat( the( content ), hasXPath( "//style/text()", equalTo( "@import \\\"http://0.0.0.0:0/stylesheets/pretty.css\\\";" ) ) );
    assertThat(content, anyOf( is(responseHtmlOne), is(responseHtmlTwo)));
  }

  /*
   * Test the prefix function
   * @see KNOX-994
   * @since 0.14.0
   */
  @Test
  public void testResponseHtmlBodyRewritePrefixFunctionTestPrefix() throws Exception {

    Map<String,String> initParams = new HashMap<>();
    testSetUp( initParams );

    String responseHtml = "<html><div src=\"'components/navbar/navbar.html?v=1496201914075\"></div></html>";
    String responseHtmlOne = "<html><div src=\"'http://0.0.0.0:0/zeppelin/components/navbar/navbar.html?v=1496201914075\"></div></html>";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "GET" )
        .requestUrl( "http://mock-host:42/test-output-path-1" );
    interaction.respond()
        .contentType( "application/html" )
        .content( responseHtml, StandardCharsets.UTF_8 )
        .status( 200 );
    interactions.add( interaction );
    request.setMethod( "GET" );
    request.setURI( "/test-input-path" );
    request.setHeader( "Host", "mock-host:42" );
    request.setHeader( "Content-Type", "application/html" );

    // Execute the request.
    response = TestUtils.execute( server, request );

    assertThat( response.getStatus(), is( 200 ) );
    String content = response.getContent();

    assertThat(content,  is(responseHtmlOne));

  }

  /*
   * Test the postfix function
   * @since 1.1.0
   * KNOX-1305
   */
  @Test
  public void testFunctionTestPostfix() throws Exception {

    Map<String,String> initParams = new HashMap<>();
    initParams.put( "response.headers", "test-filter-6" );
    testSetUp( initParams );

    String locationHeader = "https://localhost:8443/gateway/knoxsso/api/v1/websso?originalUrl=http://localhost:20070/index.html&doAs=anonymous";
    String responseHtml = "<html>Hello There !</html>";
    String expectedLocationHeader = "https://localhost:8443/gateway/knoxsso/api/v1/websso?originalUrl=http%3A%2F%2F0.0.0.0%3A0%2Fsparkhistory%2F";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "GET" )
        .requestUrl( "http://mock-host:42/test-output-path-1" );
    interaction.respond()
        .contentType( "application/html" )
        .header("Location", locationHeader )
        .content( responseHtml, StandardCharsets.UTF_8 )
        .status( 200 );
    interactions.add( interaction );
    request.setMethod( "GET" );
    request.setURI( "/test-input-path" );
    request.setHeader( "Host", "mock-host:42" );
    request.setHeader( "Content-Type", "application/html" );

    // Execute the request.
    response = TestUtils.execute( server, request );

    assertThat( response.getStatus(), is( 200 ) );
    String content = response.get("Location");

    assertThat(content,  is(expectedLocationHeader));
  }

  /*
   * Test the infix function
   * @since 1.1.0
   * KNOX-1305
   */
  @Test
  public void testFunctionTestInfix() throws Exception {

    Map<String,String> initParams = new HashMap<>();
    initParams.put( "response.headers", "test-filter-6" );
    testSetUp( initParams );

    String customHeader = "https://localhost:8443/gateway/sandbox/?query=http://localhost:20070";
    String responseHtml = "<html>Hello There !</html>";
    String expectedCustomHeader = "https://localhost:8443/gateway/sandbox/?query=%27http%3A%2F%2F0.0.0.0%3A0%2Fsparkhistory%2F%27";

    // Setup the server side request/response interaction.
    interaction.expect()
        .method( "GET" )
        .requestUrl( "http://mock-host:42/test-output-path-1" );
    interaction.respond()
        .contentType( "application/html" )
        .header("CustomHeader", customHeader )
        .content( responseHtml, StandardCharsets.UTF_8 )
        .status( 200 );
    interactions.add( interaction );
    request.setMethod( "GET" );
    request.setURI( "/test-input-path" );
    request.setHeader( "Host", "mock-host:42" );
    request.setHeader( "Content-Type", "application/html" );

    // Execute the request.
    response = TestUtils.execute( server, request );

    assertThat( response.getStatus(), is( 200 ) );
    String content = response.get("CustomHeader");

    assertThat(content,  is(expectedCustomHeader));
  }

  /*
   * See KNOX-791
   */
  @Test
  public void testResponseHtmlAttributeEscaping() throws Exception {
    final Map<String, String> initParams = new HashMap<>();
    initParams.put("response.body", "test-filter-4");
    testSetUp(initParams);

    final String responseHtml = "<!DOCTYPE html>\n" + "<html>\n" + "  <head>\n"
        + "    <meta charset=\"UTF-8\">\n"
        + "    <link rel=\"stylesheet\" href=\"pretty.css\">\n"
        + "    <script escaped-data=\"&lt;&gt;\" src=\"script.js\"></script>\n"
        + "  </head>\n" + "  <body>\n" + "  </body>\n" + "</html>";
    final String rewrittenResponseHtml = "<!DOCTYPE html>\n" + "<html>\n"
        + "  <head>\n" + "    <meta charset=\"UTF-8\">\n"
        + "    <link rel=\"stylesheet\" href=\"http://someotherhost/stylesheets/pretty.css\">\n"
        + "    <script escaped-data=\"&lt;&gt;\" src=\"script.js\"></script>\n"
        + "  </head>\n" + "  <body>\n" + "  </body>\n" + "</html>";

    // Setup the server side request/response interaction.
    interaction.expect().method("GET")
        .requestUrl("http://mock-host:42/test-output-path-1");
    interaction.respond().contentType("application/html")
        .content(responseHtml, StandardCharsets.UTF_8).status(200);
    interactions.add(interaction);
    request.setMethod("GET");
    request.setURI("/test-input-path");
    request.setHeader("Host", "mock-host:42");
    request.setHeader("Content-Type", "application/html");

    // Execute the request.
    response = TestUtils.execute(server, request);

    assertThat(response.getStatus(), is(200));
    String content = response.getContent();
    assertThat(content, is(rewrittenResponseHtml));
  }

  private static class SetupFilter implements Filter {
    @Override
    public void init( FilterConfig filterConfig ) {
    }

    @Override
    public void doFilter( ServletRequest request, ServletResponse response, FilterChain chain ) throws IOException, ServletException {
      HttpServletRequest httpRequest = ((HttpServletRequest)request);
      StringBuffer sourceUrl = httpRequest.getRequestURL();
      String queryString = httpRequest.getQueryString();
      if( queryString != null ) {
        sourceUrl.append('?').append( queryString );
      }
      try {
        request.setAttribute(
            AbstractGatewayFilter.SOURCE_REQUEST_URL_ATTRIBUTE_NAME,
            Parser.parseLiteral( sourceUrl.toString() ) );
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
