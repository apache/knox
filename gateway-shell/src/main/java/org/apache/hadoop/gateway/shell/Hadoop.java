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
package org.apache.hadoop.gateway.shell;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.protocol.BasicHttpContext;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLContext;

public class Hadoop {

  private static final String GATEWAY_CLIENT_TRUST_DEFAULT_PASS = "changeit";
  private static final String KNOX_CLIENT_TRUSTSTORE_PASS = "KNOX_CLIENT_TRUSTSTORE_PASS";
  private static final String GATEWAY_CLIENT_TRUST = "gateway-client-trust.jks";
  private static final String KNOX_CLIENT_TRUSTSTORE_FILENAME = "KNOX_CLIENT_TRUSTSTORE_FILENAME";
  private static final String KNOX_CLIENT_TRUSTSTORE_DIR = "KNOX_CLIENT_TRUSTSTORE_DIR";

  String base;
  HttpHost host;
  DefaultHttpClient client;
  BasicHttpContext context;
  String username;
  String password;
  ExecutorService executor;

  public static Hadoop login( String url, String username, String password ) throws URISyntaxException {
    return new Hadoop( url, username, password, true );
  }

  public static Hadoop loginInsecure( String url, String username, String password ) throws URISyntaxException {
    System.out.println("**************** WARNING ******************\n"
        + "This is an insecure client instance and may\n"
        + "leave the interactions subject to a man in\n"
        + "the middle attack. Please use the login()\n"
        + "method instead of loginInsecure() for any\n"
        + "sensitive or production usecases.\n"
        + "*******************************************");
    return new Hadoop( url, username, password );
  }

  private Hadoop( String url, String username, String password ) throws HadoopException, URISyntaxException {
    this(url, username, password, false);
  }

  private Hadoop( String url, String username, String password, boolean secure ) throws HadoopException, URISyntaxException {
    this.executor = Executors.newCachedThreadPool();
    this.base = url;
    this.username = username;
    this.password = password;

    URI uri = new URI( url );
    host = new HttpHost( uri.getHost(), uri.getPort(), uri.getScheme() );

    try {
      if (!secure) {
        client = createInsecureClient();
      }
      else {
        client = createClient();
      }
      client.getCredentialsProvider().setCredentials(
          new AuthScope( host.getHostName(), host.getPort() ),
          new UsernamePasswordCredentials( username, password ) );
      AuthCache authCache = new BasicAuthCache();
      BasicScheme authScheme = new BasicScheme();
      authCache.put( host, authScheme );
      context = new BasicHttpContext();
      context.setAttribute( ClientContext.AUTH_CACHE, authCache );
    } catch( GeneralSecurityException e ) {
      throw new HadoopException( "Failed to create HTTP client.", e );
    }
  }

  private static DefaultHttpClient createClient() throws GeneralSecurityException {
    SchemeRegistry registry = new SchemeRegistry();
    KeyStore trustStore = getTrustStore();
    SSLSocketFactory socketFactory = new SSLSocketFactory(trustStore);
    registry.register( new Scheme( "https", 443, socketFactory ) );
    registry.register( new Scheme( "http", 80, new PlainSocketFactory() ) );
    PoolingClientConnectionManager mgr = new PoolingClientConnectionManager( registry );
    DefaultHttpClient client = new DefaultHttpClient( mgr, new DefaultHttpClient().getParams() );
    return client;
  }

  private static KeyStore getTrustStore() throws GeneralSecurityException {
    KeyStore ks = null;
    String truststoreDir = System.getenv(KNOX_CLIENT_TRUSTSTORE_DIR);
    if (truststoreDir == null) {
      truststoreDir = System.getProperty("user.home");
    }
    String truststoreFileName = System.getenv(KNOX_CLIENT_TRUSTSTORE_FILENAME);
    if (truststoreFileName == null) {
      truststoreFileName = GATEWAY_CLIENT_TRUST;
    }
    String truststorePass = System.getenv(KNOX_CLIENT_TRUSTSTORE_PASS);
    if (truststorePass == null) {
      truststorePass = GATEWAY_CLIENT_TRUST_DEFAULT_PASS;
    }

    InputStream is = null;
    try {
      ks = KeyStore.getInstance("JKS");
      File file = new File(truststoreDir, truststoreFileName);
      if (!file.exists()) {
        String truststore = System.getProperty("javax.net.ssl.trustStore");
        if (truststore == null) {
          truststoreDir = System.getProperty("java.home");
          truststore = truststoreDir + File.separator + "lib" + File.separator
              + "security" + File.separator + "cacerts";
          truststorePass = System.getProperty("javax.net.ssl.trustStorePassword", "changeit");
        }
        file = new File(truststore);
      }

      if (file.exists()) {
        is = new FileInputStream(file);
        ks.load(is, truststorePass.toCharArray());
      }
      else {
        throw new HadoopException("Unable to find a truststore for secure login."
            + "Please import the gateway-identity certificate into the JVM"
          + " truststore or set the truststore location ENV variables.");
      }
    } catch (KeyStoreException e) {
      throw new HadoopException("Unable to create keystore of expected type.", e);
    } catch (FileNotFoundException e) {
      throw new HadoopException("Unable to read truststore."
          + " Please import the gateway-identity certificate into the JVM"
          + " truststore or set the truststore location ENV variables.", e);
    } catch (NoSuchAlgorithmException e) {
      throw new HadoopException("Unable to load the truststore."
          + " Please import the gateway-identity certificate into the JVM"
          + " truststore or set the truststore location ENV variables.", e);
    } catch (CertificateException e) {
      throw new HadoopException("Certificate cannot be found in the truststore."
          + " Please import the gateway-identity certificate into the JVM"
          + " truststore or set the truststore location ENV variables.", e);
    } catch (IOException e) {
      throw new HadoopException("Unable to load truststore."
          + " May be related to password setting or truststore format.", e);
    } finally {
       IOUtils.closeQuietly(is);
    }

    return ks;
  }

  private static DefaultHttpClient createInsecureClient() throws GeneralSecurityException {
    SchemeRegistry registry = new SchemeRegistry();
    SSLSocketFactory socketFactory = new SSLSocketFactory(
        new TrustSelfSignedStrategy(),
        SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER );
    registry.register( new Scheme( "https", 443, socketFactory ) );
    registry.register( new Scheme( "http", 80, new PlainSocketFactory() ) );
    PoolingClientConnectionManager mgr = new PoolingClientConnectionManager( registry );
    DefaultHttpClient client = new DefaultHttpClient( mgr, new DefaultHttpClient().getParams() );
    return client;
  }

  public String base() {
    return base;
  }

  public HttpResponse executeNow( HttpRequest request ) throws IOException {
    HttpResponse response = client.execute( host, request, context );
    if( response.getStatusLine().getStatusCode() < 400 ) {
      return response;
    } else {
      throw new ErrorResponse( response );
    }
  }

  public <T> Future<T> executeLater( Callable<T> callable ) {
    return executor.submit( callable );
  }

  public void waitFor( Future<?>... futures ) throws ExecutionException, InterruptedException {
    if( futures != null ) {
      for( Future future : futures ) {
        future.get();
      }
    }
  }

  public void waitFor( long timeout, TimeUnit units, Future<?>... futures ) throws ExecutionException, TimeoutException, InterruptedException {
    if( futures != null ) {
      timeout = TimeUnit.MILLISECONDS.convert( timeout, units );
      long start;
      for( Future future : futures ) {
        start = System.currentTimeMillis();
        future.get( timeout, TimeUnit.MILLISECONDS );
        timeout -= ( System.currentTimeMillis() - start );
      }
    }
  }

  public void shutdown() throws InterruptedException {
    executor.shutdownNow();
  }

  public boolean shutdown( long timeout, TimeUnit unit ) throws InterruptedException {
    executor.shutdown();
    return executor.awaitTermination( timeout, unit );
  }

}
