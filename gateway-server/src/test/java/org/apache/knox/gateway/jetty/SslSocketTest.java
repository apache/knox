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
package org.apache.knox.gateway.jetty;

import org.apache.http.HttpVersion;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.knox.test.category.ManualTests;
import org.apache.knox.test.category.MediumTests;
import org.eclipse.jetty.server.Server;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

@Category( { ManualTests.class, MediumTests.class } )
public class SslSocketTest {

  Logger log = LoggerFactory.getLogger( SslSocketTest.class );

  private Server jetty;

  @BeforeClass
  public static void setupSuite() {
    System.setProperty( "javax.net.ssl.keyStore", "target/test-classes/server-keystore.jks" );
    System.setProperty( "javax.net.ssl.keyStorePassword", "horton" );
    System.setProperty( "javax.net.ssl.trustStore", "target/test-classes/server-keystore.jks" );
    System.setProperty( "javax.net.ssl.trustStorePassword", "horton" );
    System.setProperty( "java.protocol.handler.pkgs", "com.sun.net.ssl.internal.www.protocol" );
    System.setProperty( "javax.net.debug", "ssl" );
  }

  public class SslServer implements Runnable {

    private boolean ready = false;

    public boolean waitUntilReady() throws InterruptedException {
      synchronized( this ) {
        while( !ready ) {
          this.wait();
        }
      }
      return ready;
    }

    public void run() {
      try {
        SSLServerSocketFactory sslserversocketfactory = (SSLServerSocketFactory)SSLServerSocketFactory.getDefault();
        SSLServerSocket sslserversocket = (SSLServerSocket)sslserversocketfactory.createServerSocket( 9999 );
        synchronized( this ) {
          ready = true;
          this.notifyAll();
        }
        SSLSocket sslsocket = (SSLSocket)sslserversocket.accept();

        InputStream inputstream = sslsocket.getInputStream();
        InputStreamReader inputstreamreader = new InputStreamReader( inputstream, StandardCharsets.UTF_8 );
        BufferedReader bufferedreader = new BufferedReader( inputstreamreader );

        String string = bufferedreader.readLine();
        System.out.println( string );
        System.out.flush();
      } catch( IOException e ) {
        e.printStackTrace();
      }
    }
  }

  @Ignore
  @Test
  public void testSsl() throws IOException, InterruptedException {
    SslServer server = new SslServer();
    Thread thread = new Thread( server );
    thread.start();
    server.waitUntilReady();

    HttpParams params = new BasicHttpParams();
    HttpProtocolParams.setVersion( params, HttpVersion.HTTP_1_1 );
    HttpProtocolParams.setContentCharset( params, "utf-8" );
    params.setBooleanParameter( "http.protocol.expect-continue", false );

    SSLSocketFactory sslsocketfactory = SSLSocketFactory.getSocketFactory();
    SSLSocket sslsocket = (SSLSocket)sslsocketfactory.createSocket( params );

    sslsocket.connect( new InetSocketAddress( "localhost", 9999 ) );

    OutputStream outputstream = sslsocket.getOutputStream();
    OutputStreamWriter outputstreamwriter = new OutputStreamWriter( outputstream, StandardCharsets.UTF_8 );
    BufferedWriter bufferedwriter = new BufferedWriter( outputstreamwriter );

    bufferedwriter.write( "HELLO\n" );
    bufferedwriter.flush();
  }

}
