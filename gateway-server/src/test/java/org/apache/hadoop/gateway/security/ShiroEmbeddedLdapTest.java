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

import com.jayway.restassured.RestAssured;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.hadoop.gateway.security.ldap.SimpleLdapDirectoryServer;
import org.apache.hadoop.test.category.ManualTests;
import org.apache.hadoop.test.category.MediumTests;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.web.env.EnvironmentLoaderListener;
import org.apache.shiro.web.servlet.ShiroFilter;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.EnumSet;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.config.ConnectionConfig.connectionConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.hamcrest.core.IsEqual.equalTo;

// Derrived from this thread
// http://shiro-user.582556.n2.nabble.com/Integration-of-Shiro-with-Embedded-Jetty-td7519712.html
@Category( { ManualTests.class, MediumTests.class } )
public class ShiroEmbeddedLdapTest {

  private static Logger log = LoggerFactory.getLogger( ShiroEmbeddedLdapTest.class );

  private static SimpleLdapDirectoryServer ldap;

  private Server jetty;

  @BeforeClass
  public static void setupSuite() throws Exception{
    RestAssured.config = newConfig().connectionConfig(connectionConfig().closeIdleConnectionsAfterEachResponse());
    int port = findFreePort();
    TcpTransport transport = new TcpTransport( port );
    ldap = new SimpleLdapDirectoryServer(
        "dc=hadoop,dc=apache,dc=org",
        new File( ClassLoader.getSystemResource( "users.ldif" ).toURI() ),
        transport );
    ldap.start();
  }

  @AfterClass
  public static void cleanupSuite() throws Exception {
    ldap.stop( true );
  }

  @Before
  public void setupTest() throws Exception {
    ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
    context.setContextPath( "/" );
    context.addEventListener( new EnvironmentLoaderListener() );

    // Add root ShiroFilter, all remaining filters and filter chains are defined in shiro.ini's [urls] section.
    FilterHolder filterHolder = new FilterHolder( new ShiroFilter() );
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
  public void testShiro() throws Exception {
    String url = "http://localhost:" + jetty.getURI().getPort() + "/";

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
      org.apache.shiro.subject.Subject ss = SecurityUtils.getSubject();
      log.info( "PRINCIPAL: " + ss.getPrincipal() );
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
