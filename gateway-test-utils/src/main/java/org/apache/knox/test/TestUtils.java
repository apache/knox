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
package org.apache.knox.test;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.eclipse.jetty.http.HttpTester;
import org.eclipse.jetty.servlet.ServletTester;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class TestUtils {
  private static final Logger LOG = LogManager.getLogger(TestUtils.class);

  public static final long SHORT_TIMEOUT = 5000L;
  public static final long MEDIUM_TIMEOUT = 30 * 1000L;
  public static final long LONG_TIMEOUT = 60 * 1000L;

  private TestUtils() {
  }

  public static String getResourceName( Class clazz, String name ) {
    return clazz.getName().replaceAll( "\\.", "/" ) + "/" + name;
  }

  public static URL getResourceUrl( Class clazz, String name ) throws FileNotFoundException {
    name = getResourceName( clazz, name );
    URL url = ClassLoader.getSystemResource( name );
    if( url == null ) {
      throw new FileNotFoundException( name );
    }
    return url;
  }

  public static URL getResourceUrl( String name ) throws FileNotFoundException {
    URL url = ClassLoader.getSystemResource( name );
    if( url == null ) {
      throw new FileNotFoundException( name );
    }
    return url;
  }

  public static InputStream getResourceStream( String name ) throws IOException {
    URL url = ClassLoader.getSystemResource( name );
    return url.openStream();
  }

  public static InputStream getResourceStream( Class clazz, String name ) throws IOException {
    URL url = getResourceUrl( clazz, name );
    return url.openStream();
  }

  public static Reader getResourceReader( String name, Charset charset ) throws IOException {
    return new InputStreamReader( getResourceStream( name ), charset );
  }

  public static Reader getResourceReader( Class clazz, String name, Charset charset ) throws IOException {
    return new InputStreamReader( getResourceStream( clazz, name ), charset );
  }

  public static String getResourceString( Class clazz, String name, Charset charset ) throws IOException {
    return IOUtils.toString( getResourceReader( clazz, name, charset ) );
  }

  public static File createTempDir( String prefix ) throws IOException {
    File targetDir = new File( System.getProperty( "user.dir" ), "target" );
    File tempDir = new File( targetDir, prefix + UUID.randomUUID() );
    FileUtils.forceMkdir( tempDir );
    return tempDir;
  }

  public static void LOG_ENTER() {
    StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
    System.out.flush();
    System.out.println( String.format( Locale.ROOT, "Running %s#%s", caller.getClassName(), caller.getMethodName() ) );
    System.out.flush();
  }

  public static void LOG_EXIT() {
    StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
    System.out.flush();
    System.out.println( String.format( Locale.ROOT, "Exiting %s#%s", caller.getClassName(), caller.getMethodName() ) );
    System.out.flush();
  }

  public static void awaitPortOpen( InetSocketAddress address, int timeout, int delay ) throws InterruptedException {
    long maxTime = System.currentTimeMillis() + timeout;
    do {
      try (Socket socket = new Socket()) {
        socket.connect( address, delay );
        return;
      } catch ( IOException e ) {
        //e.printStackTrace();
      }
    } while( System.currentTimeMillis() < maxTime );
    throw new IllegalStateException( "Timed out " + timeout + " waiting for port " + address );
  }

  public static void awaitNon404HttpStatus( URL url, int timeout, int delay ) throws InterruptedException {
    long maxTime = System.currentTimeMillis() + timeout;
    do {
      Thread.sleep( delay );
      HttpURLConnection conn = null;
      try {
        conn = (HttpURLConnection)url.openConnection();
        conn.getInputStream().close();
        return;
      } catch ( IOException e ) {
        //e.printStackTrace();
        try {
          if( conn != null && conn.getResponseCode() != 404 ) {
            return;
          }
        } catch ( IOException ee ) {
          //ee.printStackTrace();
        }
      }
    } while( System.currentTimeMillis() < maxTime );
    throw new IllegalStateException( "Timed out " + timeout + " waiting for URL " + url );
  }

  public static String merge( String resource, Properties properties ) {
    ClasspathResourceLoader loader = new ClasspathResourceLoader();
    loader.getResourceStream( resource );

    VelocityEngine engine = new VelocityEngine();
    Properties config = new Properties();
    config.setProperty( RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, "org.apache.velocity.runtime.log.NullLogSystem" );
    config.setProperty( RuntimeConstants.RESOURCE_LOADER, "classpath" );
    config.setProperty( "classpath.resource.loader.class", ClasspathResourceLoader.class.getName() );
    engine.init( config );

    VelocityContext context = new VelocityContext( properties );
    Template template = engine.getTemplate( resource );
    StringWriter writer = new StringWriter();
    template.merge( context, writer );
    return writer.toString();
  }

  public static String merge( Class base, String resource, Properties properties ) {
    String baseResource = base.getName().replaceAll( "\\.", "/" );
    String fullResource = baseResource + "/" + resource;
    return merge( fullResource, properties );
  }

  public static int findFreePort() throws IOException {
    try(ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  /**
   * Waits until a given function meets a given condition
   * @param function function to check before the timeout
   * @param expected boolean expected value to check
   * @param timeout Timeout in milliseconds to wait for condition to be met
   * @return expected based on condition
   * @throws Exception on any error
   */
  public static boolean waitUntil(Callable<Boolean> function, boolean expected, long timeout) throws Exception {
    long before = System.currentTimeMillis();
    while((System.currentTimeMillis() - before) < timeout) {
      if(function.call() == expected) {
        return expected;
      }
      Thread.sleep(100);
    }
    return false;
  }

  public static void waitUntilNextSecond() {
    long before = System.currentTimeMillis();
    long wait;
    while( ( wait = ( 1000 - ( System.currentTimeMillis() - before ) ) ) > 0 ) {
      try {
        Thread.sleep( wait );
      } catch( InterruptedException e ) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public static HttpTester.Response execute( ServletTester server, HttpTester.Request request ) throws Exception {
    LOG.debug( "execute: request=" + request );
    ByteBuffer requestBuffer = request.generate();
    LOG.trace( "execute: requestBuffer=[" + new String(requestBuffer.array(),0,requestBuffer.limit(), StandardCharsets.UTF_8) + "]" );
    ByteBuffer responseBuffer = server.getResponses( requestBuffer, 30, TimeUnit.SECONDS );
    HttpTester.Response response = HttpTester.parseResponse( responseBuffer );
    LOG.trace( "execute: responseBuffer=[" + new String(responseBuffer.array(),0,responseBuffer.limit(), StandardCharsets.UTF_8) + "]" );
    LOG.debug( "execute: reponse=" + response );
    return response;
  }

  public static void updateFile(File parent, String name, String from, String to) throws IOException {
    final File file = new File(parent, name);
    if (file.exists()) {
      final String current = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
      final String updated = current.replace(from, to);
      FileUtils.write(file, updated, StandardCharsets.UTF_8);
    }
  }

}
