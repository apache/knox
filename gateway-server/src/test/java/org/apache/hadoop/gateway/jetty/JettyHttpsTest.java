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
package org.apache.hadoop.gateway.jetty;

import org.apache.hadoop.test.category.ManualTests;
import org.apache.hadoop.test.category.MediumTests;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.BasicClientConnectionManager;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ssl.SslConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

@Category( { ManualTests.class, MediumTests.class } )
public class JettyHttpsTest {

  Logger log = LoggerFactory.getLogger( JettyHttpsTest.class );

  private Server jetty;

  @BeforeClass
  public static void setupSuite() {
    //System.setProperty( "java.protocol.handler.pkgs", "com.sun.net.ssl.internal.www.protocol" );
    //System.setProperty( "javax.net.debug", "ssl" );
  }

  @Before
  public void setupTest() throws Exception {

    SslContextFactory sslContext = new SslContextFactory( true );
    sslContext.setCertAlias( "server" );
    sslContext.setKeyStorePath( "target/test-classes/server-keystore.jks" );
    sslContext.setKeyStorePassword( "horton" );
    sslContext.setKeyManagerPassword( "horton" );
    sslContext.setTrustStore( "target/test-classes/server-truststore.jks" );
    sslContext.setTrustStorePassword( "horton" );
    sslContext.setNeedClientAuth( false );
    sslContext.setTrustAll( true );
    SslConnector sslConnector = new SslSelectChannelConnector( sslContext );

    ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
    context.setContextPath( "/" );
    ServletHolder servletHolder = new ServletHolder( new MockServlet() );
    context.addServlet( servletHolder, "/*" );

    jetty = new Server();
    jetty.addConnector( sslConnector );
    jetty.setHandler( context );
    jetty.start();
  }

  @After
  public void cleanupTest() throws Exception {
    jetty.stop();
    jetty.join();
  }

  @Test
  public void testHttps() throws Exception {
    int port = jetty.getConnectors()[ 0 ].getLocalPort();
    String url = "https://localhost:" + port + "/";

    System.out.println( "Jetty HTTPS listenting on port " + port + ". Press any key to continue." );
    System.in.read();

    SSLContext ctx = SSLContext.getInstance( "TLS" );
    KeyManager[] keyManagers = createKeyManagers( "jks", "target/test-classes/client-keystore.jks", "horton" );
    TrustManager[] trustManagers = createTrustManagers( "jks", "target/test-classes/client-truststore.jks", "horton" );
    ctx.init( keyManagers, trustManagers, new SecureRandom() );

    SSLSocketFactory socketFactory = new SSLSocketFactory( ctx, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER );

    SchemeRegistry schemes = new SchemeRegistry();
    schemes.register( new Scheme( "https", port, socketFactory ) );
    ClientConnectionManager cm = new BasicClientConnectionManager( schemes );

    HttpClient client = new DefaultHttpClient( cm );

    HttpGet get = new HttpGet( url );
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    client.execute( get ).getEntity().writeTo( buffer );
    assertThat( buffer.toString(), equalTo( "<html>Hello!</html>" ) );
  }

  private static KeyManager[] createKeyManagers( String keyStoreType, String keyStorePath, String keyStorePassword ) throws Exception {
    KeyStore keyStore = loadKeyStore( keyStoreType, keyStorePath, keyStorePassword );
    KeyManagerFactory kmf = KeyManagerFactory.getInstance( KeyManagerFactory.getDefaultAlgorithm() );
    kmf.init( keyStore, keyStorePassword.toCharArray() );
    return kmf.getKeyManagers();
  }

  private static TrustManager[] createTrustManagers( String trustStoreType, String trustStorePath, String trustStorePassword ) throws Exception {
    KeyStore trustStore = loadKeyStore( trustStoreType, trustStorePath, trustStorePassword );
    TrustManagerFactory tmf = TrustManagerFactory.getInstance( TrustManagerFactory.getDefaultAlgorithm() );
    tmf.init( trustStore );
    return tmf.getTrustManagers();
  }

  private static KeyStore loadKeyStore( String type, String path, String password ) throws IOException, NoSuchAlgorithmException, CertificateException, KeyStoreException {
    KeyStore keyStore = KeyStore.getInstance( type );
    InputStream keystoreInput = new FileInputStream( path );
    keyStore.load( keystoreInput, password.toCharArray() );
    return keyStore;
  }

private static class MockServlet extends HttpServlet {
  @Override
  protected void doGet( HttpServletRequest req, HttpServletResponse resp ) throws ServletException, IOException {
    resp.setContentType( "text/html" );
    resp.getWriter().write( "<html>Hello!</html>" );
  }
}

}
