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

import com.jayway.jsonassert.JsonAssert;
import org.apache.knox.gateway.filter.AbstractGatewayFilter;
import org.apache.knox.gateway.filter.rewrite.api.FrontendFunctionDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletContextListener;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletFilter;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.registry.ServiceRegistry;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.test.TestUtils;
import org.apache.knox.test.log.NoOpLogger;
import org.apache.knox.test.mock.MockInteraction;
import org.apache.knox.test.mock.MockServlet;
import org.apache.http.auth.BasicUserPrincipal;
import org.easymock.EasyMock;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletTester;
import org.eclipse.jetty.util.ArrayQueue;
import org.eclipse.jetty.util.Attributes;
import org.eclipse.jetty.util.AttributesMap;
import org.eclipse.jetty.util.log.Log;
import org.hamcrest.core.Is;
import org.junit.Test;

import javax.security.auth.Subject;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;

public class FrontendFunctionProcessorTest {

  private ServletTester server;
  private HttpTester.Request request;
  private HttpTester.Response response;
  private ArrayQueue<MockInteraction> interactions;
  private MockInteraction interaction;

  @SuppressWarnings("rawtypes")
  @Test
  public void testServiceLoader() throws Exception {
    ServiceLoader loader = ServiceLoader.load( UrlRewriteFunctionProcessor.class );
    Iterator iterator = loader.iterator();
    assertThat( "Service iterator empty.", iterator.hasNext() );
    while( iterator.hasNext() ) {
      Object object = iterator.next();
      if( object instanceof FrontendFunctionProcessor ) {
        return;
      }
    }
    fail( "Failed to find " + FrontendFunctionProcessor.class.getName() + " via service loader." );
  }

  @Test
  public void testName() throws Exception {
    FrontendFunctionProcessor processor = new FrontendFunctionProcessor();
    assertThat( processor.name(), is( "frontend" ) );
  }

  @Test
  public void testNullHandling() throws Exception {
    UrlRewriteEnvironment badEnv = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    UrlRewriteEnvironment goodEnv = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    EasyMock.expect( goodEnv.getAttribute(FrontendFunctionDescriptor.FRONTEND_URI_ATTRIBUTE) ).andReturn( new URI( "http://mock-host:80/mock-root/mock-topo" ) ).anyTimes();
    EasyMock.replay( badEnv,goodEnv );

    FrontendFunctionProcessor processor = new FrontendFunctionProcessor();
    try {
      processor.initialize( null, null );
    } catch ( IllegalArgumentException e ) {
      assertThat( e.getMessage(), containsString( "environment" ) );
    }
    try {
      processor.initialize( badEnv, null );
    } catch ( IllegalArgumentException e ) {
      assertThat( e.getMessage(), containsString( "org.apache.hadoop.knox.frontend.context.uri" ) );
    }
    processor.initialize( goodEnv, null );
    processor.resolve( null, null );
  }

  public void setUp( String username, Map<String, String> initParams, Attributes attributes ) throws Exception {
    ServiceRegistry mockServiceRegistry = EasyMock.createNiceMock( ServiceRegistry.class );
    EasyMock.expect( mockServiceRegistry.lookupServiceURL( "test-cluster", "NAMENODE" ) ).andReturn( "test-nn-scheme://test-nn-host:411" ).anyTimes();
    EasyMock.expect( mockServiceRegistry.lookupServiceURL( "test-cluster", "JOBTRACKER" ) ).andReturn( "test-jt-scheme://test-jt-host:511" ).anyTimes();

    GatewayServices mockGatewayServices = EasyMock.createNiceMock( GatewayServices.class );
    EasyMock.expect( mockGatewayServices.getService(GatewayServices.SERVICE_REGISTRY_SERVICE) ).andReturn( mockServiceRegistry ).anyTimes();

    EasyMock.replay( mockServiceRegistry, mockGatewayServices );

    String descriptorUrl = TestUtils.getResourceUrl( FrontendFunctionProcessorTest.class, "rewrite.xml" ).toExternalForm();

    Log.setLog( new NoOpLogger() );

    server = new ServletTester();
    server.setContextPath( "/" );
    server.getContext().addEventListener( new UrlRewriteServletContextListener() );
    server.getContext().setInitParameter(
        UrlRewriteServletContextListener.DESCRIPTOR_LOCATION_INIT_PARAM_NAME, descriptorUrl );

    if( attributes != null ) {
      server.getContext().setAttributes( attributes );
    }
    server.getContext().setAttribute( GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE, "test-cluster" );
    server.getContext().setAttribute( GatewayServices.GATEWAY_SERVICES_ATTRIBUTE, mockGatewayServices );

    FilterHolder setupFilter = server.addFilter( SetupFilter.class, "/*", EnumSet.of( DispatcherType.REQUEST ) );
    setupFilter.setFilter( new SetupFilter( username ) );
    FilterHolder rewriteFilter = server.addFilter( UrlRewriteServletFilter.class, "/*", EnumSet.of( DispatcherType.REQUEST ) );
    if( initParams != null ) {
      for( Map.Entry<String,String> entry : initParams.entrySet() ) {
        rewriteFilter.setInitParameter( entry.getKey(), entry.getValue() );
      }
    }
    rewriteFilter.setFilter( new UrlRewriteServletFilter() );

    interactions = new ArrayQueue<>();

    ServletHolder servlet = server.addServlet( MockServlet.class, "/" );
    servlet.setServlet( new MockServlet( "mock-servlet", interactions ) );

    server.start();

    interaction = new MockInteraction();
    request = HttpTester.newRequest();
    response = null;
  }

  @Test
  public void testFrontendFunctionsOnJsonRequestBody() throws Exception {
    Map<String,String> initParams = new HashMap<>();
    initParams.put( "response.body", "test-filter" );
    setUp( "test-user", initParams, null );

    String input = TestUtils.getResourceString( FrontendFunctionProcessorTest.class, "test-input-body.json", "UTF-8" );

    interaction.expect()
        .method( "GET" )
        .requestUrl( "http://test-host:42/test-path" );
    interaction.respond()
        .status( 200 )
        .contentType( "application/json" )
        .characterEncoding( "UTF-8" )
        .content( input, Charset.forName( "UTF-8" ) );
    interactions.add( interaction );
    request.setMethod( "GET" );
    request.setURI( "/test-path" );
    //request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "test-host:42" );

    response = TestUtils.execute( server, request );

    assertThat( response.getStatus(), Is.is( 200 ) );

    String json = response.getContent();

    // Note: The Jetty ServletTester/HttpTester doesn't return very good values.
    JsonAssert.with( json ).assertThat( "$.url", anyOf( is( "http://localhost:0" ), is( "http://0.0.0.0:0" ) ) );
    JsonAssert.with( json ).assertThat( "$.scheme", is( "http" ) );
    JsonAssert.with( json ).assertThat( "$.host", anyOf( is( "localhost" ), is( "0.0.0.0" ) ) );
    JsonAssert.with( json ).assertThat( "$.port", is( "0" ) );
    JsonAssert.with( json ).assertThat( "$.addr", anyOf( is( "localhost:0" ), is( "0.0.0.0:0" ) ) );
    JsonAssert.with( json ).assertThat( "$.address", anyOf( is( "localhost:0" ), is( "0.0.0.0:0" ) ) );
    JsonAssert.with( json ).assertThat( "$.path", is( "" ) );
    JsonAssert.with( json ).assertThat( "$.topology", is( "test-cluster" ) );
  }

  @Test
  public void testFrontendFunctionsWithFrontendUriConfigOnJsonRequestBody() throws Exception {

    // This hooks up the filter in rewrite.xml in this class' test resource directory.
    Map<String,String> initParams = new HashMap<>();
    initParams.put( "response.body", "test-filter" );

    // This simulates having gateway.frontend.uri in gateway-site.xml
    Attributes attributes = new AttributesMap(  );
    attributes.setAttribute( FrontendFunctionDescriptor.FRONTEND_URI_ATTRIBUTE, new URI( "mock-frontend-scheme://mock-frontend-host:777/mock-frontend-path" ) );

    setUp( "test-user", initParams, attributes );

    String input = TestUtils.getResourceString( FrontendFunctionProcessorTest.class, "test-input-body.json", "UTF-8" );

    interaction.expect()
        .method( "GET" )
        .requestUrl( "http://test-host:42/test-path" );
    interaction.respond()
        .status( 200 )
        .contentType( "application/json" )
        .characterEncoding( "UTF-8" )
        .content( input, Charset.forName( "UTF-8" ) );
    interactions.add( interaction );
    request.setMethod( "GET" );
    request.setURI( "/test-path" );
    //request.setVersion( "HTTP/1.1" );
    request.setHeader( "Host", "test-host:42" );

    response = TestUtils.execute( server, request );

    assertThat( response.getStatus(), Is.is( 200 ) );

    String json = response.getContent();

    // Note: The Jetty ServletTester/HttpTester doesn't return very good values.
    JsonAssert.with( json ).assertThat( "$.url", is( "mock-frontend-scheme://mock-frontend-host:777/mock-frontend-path" ) );
    JsonAssert.with( json ).assertThat( "$.scheme", is( "mock-frontend-scheme" ) );
    JsonAssert.with( json ).assertThat( "$.host", is( "mock-frontend-host" ) );
    JsonAssert.with( json ).assertThat( "$.port", is( "777" ) );
    JsonAssert.with( json ).assertThat( "$.addr", is( "mock-frontend-host:777" ) );
    JsonAssert.with( json ).assertThat( "$.address", is( "mock-frontend-host:777" ) );
    JsonAssert.with( json ).assertThat( "$.path", is( "/mock-frontend-path" ) );
  }

  private static class SetupFilter implements Filter {
    private Subject subject;

    public SetupFilter( String userName ) {
      subject = new Subject();
      subject.getPrincipals().add( new BasicUserPrincipal( userName ) );
    }

    @Override
    public void init( FilterConfig filterConfig ) throws ServletException {
    }

    @Override
    public void doFilter( final ServletRequest request, final ServletResponse response, final FilterChain chain ) throws IOException, ServletException {
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
            Parser.parseLiteral( sourceUrl.toString() ) );
      } catch( URISyntaxException e ) {
        throw new ServletException( e );
      }
      try {
        Subject.doAs( subject, new PrivilegedExceptionAction<Void>() {
          @Override
          public Void run() throws Exception {
            chain.doFilter( request, response );
            return null;
          }
        } );
      } catch( PrivilegedActionException e ) {
        throw new ServletException( e );
      }
    }

    @Override
    public void destroy() {
    }
  }

}
