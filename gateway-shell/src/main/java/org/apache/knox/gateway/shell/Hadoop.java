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
package org.apache.knox.gateway.shell;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.ssl.SSLContexts;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.Closeable;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Hadoop implements Closeable {

  private static final String GATEWAY_CLIENT_TRUST_DEFAULT_PASS = "changeit";
  private static final String KNOX_CLIENT_TRUSTSTORE_PASS = "KNOX_CLIENT_TRUSTSTORE_PASS";
  private static final String GATEWAY_CLIENT_TRUST = "gateway-client-trust.jks";
  private static final String KNOX_CLIENT_TRUSTSTORE_FILENAME = "KNOX_CLIENT_TRUSTSTORE_FILENAME";
  private static final String KNOX_CLIENT_TRUSTSTORE_DIR = "KNOX_CLIENT_TRUSTSTORE_DIR";

  String base;
  HttpHost host;
  CloseableHttpClient client;
  BasicHttpContext context;
  ExecutorService executor;
  Map<String, String> headers = new HashMap<>();

  public Map<String, String> getHeaders() {
    return headers;
  }

  public void setHeaders(Map<String, String> headers) {
    this.headers = headers;
  }

  public static Hadoop login( String url, Map<String,String> headers ) throws URISyntaxException {
    Hadoop instance = new Hadoop(ClientContext.with(url));
    instance.setHeaders(headers);
    return instance;
  }

  public static Hadoop login( String url, String username, String password ) throws URISyntaxException {
    return new Hadoop(ClientContext.with(username, password, url));
  }

  public static Hadoop loginInsecure(String url, String username, String password) throws URISyntaxException {
    return new Hadoop(ClientContext.with(username, password, url)
            .connection().secure(false).end());
  }

  public Hadoop( ClientContext clientContext) throws HadoopException, URISyntaxException {
    this.executor = Executors.newCachedThreadPool();
    this.base = clientContext.url();

    try {
      client = createClient(clientContext);
    } catch (GeneralSecurityException e) {
      throw new HadoopException("Failed to create HTTP client.", e);
    }
  }

  private CloseableHttpClient createClient(ClientContext clientContext) throws GeneralSecurityException {

    // SSL
    HostnameVerifier hostnameVerifier = NoopHostnameVerifier.INSTANCE;
    TrustStrategy trustStrategy = null;
    if (clientContext.connection().secure()) {
      hostnameVerifier = SSLConnectionSocketFactory.getDefaultHostnameVerifier();
    } else {
      trustStrategy = TrustSelfSignedStrategy.INSTANCE;
      System.out.println("**************** WARNING ******************\n"
              + "This is an insecure client instance and may\n"
              + "leave the interactions subject to a man in\n"
              + "the middle attack. Please use the login()\n"
              + "method instead of loginInsecure() for any\n"
              + "sensitive or production usecases.\n"
              + "*******************************************");
    }

    KeyStore trustStore = getTrustStore();
    SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(trustStore, trustStrategy).build();
    Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http", PlainConnectionSocketFactory.getSocketFactory())
            .register("https", new SSLConnectionSocketFactory(sslContext, hostnameVerifier)).build();

    // Pool
    PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(registry);
    connectionManager.setMaxTotal(clientContext.pool().maxTotal());
    connectionManager.setDefaultMaxPerRoute(clientContext.pool().defaultMaxPerRoute());

    ConnectionConfig connectionConfig = ConnectionConfig.custom()
            .setBufferSize(clientContext.connection().bufferSize())
            .build();
    connectionManager.setDefaultConnectionConfig(connectionConfig);

    SocketConfig socketConfig = SocketConfig.custom()
            .setSoKeepAlive(clientContext.socket().keepalive())
            .setSoLinger(clientContext.socket().linger())
            .setSoReuseAddress(clientContext.socket().reuseAddress())
            .setSoTimeout(clientContext.socket().timeout())
            .setTcpNoDelay(clientContext.socket().tcpNoDelay())
            .build();
    connectionManager.setDefaultSocketConfig(socketConfig);

    // Auth
    URI uri = URI.create(clientContext.url());
    host = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
    
    CredentialsProvider credentialsProvider = null; 
    if (clientContext.username() != null && clientContext.password() != null) {
      credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(
              new AuthScope(host.getHostName(), host.getPort()),
              new UsernamePasswordCredentials(clientContext.username(), clientContext.password()));
  
      AuthCache authCache = new BasicAuthCache();
      BasicScheme authScheme = new BasicScheme();
      authCache.put(host, authScheme);
      context = new BasicHttpContext();
      context.setAttribute(org.apache.http.client.protocol.HttpClientContext.AUTH_CACHE, authCache);
    }
    return HttpClients.custom()
        .setConnectionManager(connectionManager)
        .setDefaultCredentialsProvider(credentialsProvider)
        .build();

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

  public String base() {
    return base;
  }

  public CloseableHttpResponse executeNow(HttpRequest request ) throws IOException {
    CloseableHttpResponse response = client.execute( host, request, context );
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

  private void closeClient() throws IOException {
    if(client != null) {
      client.close();
    }
  }

  public void shutdown() throws InterruptedException, IOException {
    try {
      executor.shutdownNow();
    } finally {
      closeClient();
    }
  }

  public boolean shutdown( long timeout, TimeUnit unit ) throws InterruptedException, IOException {
    try{
      executor.shutdown();
      return executor.awaitTermination( timeout, unit );
    } finally {
      closeClient();
    }
  }

  @Override
  public void close() throws IOException {
    try {
      shutdown();
    } catch (InterruptedException e) {
      throw new HadoopException("Can not shutdown underlying resources", e);
    }
  }
}
