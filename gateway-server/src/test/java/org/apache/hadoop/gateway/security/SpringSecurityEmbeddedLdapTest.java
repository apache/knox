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
package org.apache.hadoop.gateway.security;

import org.apache.hadoop.test.category.ManualTests;
import org.apache.hadoop.test.category.MediumTests;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.filter.DelegatingFilterProxy;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.EnumSet;

import static com.jayway.restassured.RestAssured.given;
import static org.hamcrest.core.IsEqual.equalTo;

@Category( { ManualTests.class, MediumTests.class } )
public class SpringSecurityEmbeddedLdapTest {

  private static Logger log = LoggerFactory.getLogger( SpringSecurityEmbeddedLdapTest.class );

  private Server jetty;
  
  @BeforeClass
  public static void setupSuite() throws Exception{
  }

  @AfterClass
  public static void cleanupSuite() throws Exception {
  }

  @Before
  public void setupTest() throws Exception {
    ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
    context.setContextPath( "/" );
    
    context.setInitParameter("contextConfigLocation", "classpath:app-context-security.xml" );
    context.addEventListener( new ContextLoaderListener() );

    // Add root Spring Security Filter, all remaining filters and filter chains are determined by the config in app-context-security.xml.
    FilterHolder filterHolder = new FilterHolder( new DelegatingFilterProxy("springSecurityFilterChain") );
    ServletHolder servletHolder = new ServletHolder( new MockServlet() );

    EnumSet<DispatcherType> types = EnumSet.allOf( DispatcherType.class );
    context.addFilter( filterHolder, "/*", types );
    context.addFilter( new FilterHolder( new TestFilter() ), "/*", types );
    context.addServlet( servletHolder, "/*" );

    jetty = new Server( findFreePort() );
    jetty.setHandler( context );
    jetty.start();
  }

  @After
  public void cleanupTest() throws Exception {
    jetty.stop();
    jetty.join();
  }

  @Test
  public void testSpringSecurity() throws Exception {
    String url = "http://localhost:" + jetty.getConnectors()[0].getPort() + "/";

    given()
        .expect().statusCode( 401 )
        .when().get( url );

    given()
        .auth().basic( "allowedUser", "password" )
        .expect().body( equalTo( "<html>Hello!</html>" ) )
        .when().get( url );

    given()
        .auth().basic( "deniedUser","invalid-password")
        .expect().statusCode( 401 )
        .when().get( url );

    given()
        .auth().basic( "invalidUser", "password" )
        .expect().statusCode( 401 )
        .when().get( url );

  }

  private static class TestFilter implements Filter {
    public void init( FilterConfig filterConfig ) throws ServletException {}
    public void destroy() {}
    
    public void doFilter( ServletRequest request, ServletResponse response, FilterChain chain ) throws IOException, ServletException {
      log.info( "PRINCIPAL: " + SecurityContextHolder.getContext().getAuthentication().getPrincipal() );
      chain.doFilter( request, response );
    }
  }

  private static class MockServlet extends HttpServlet {
    @Override
    protected void doGet( HttpServletRequest req, HttpServletResponse resp ) throws ServletException, IOException {
      resp.setContentType( "text/html" );
      resp.getWriter().write( "<html>Hello!</html>" );
    }
  }

  private static int findFreePort() throws IOException {
    ServerSocket socket = new ServerSocket(0);
    int port = socket.getLocalPort();
    socket.close();
    return port;
  }

}
