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

import com.sun.security.auth.callback.TextCallbackHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
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
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.ssl.SSLContexts;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivilegedAction;
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
import de.thetaphi.forbiddenapis.SuppressForbidden;

public class KnoxSession implements Closeable {

  private static final String GATEWAY_CLIENT_TRUST_DEFAULT_PASS = "changeit";
  private static final String KNOX_CLIENT_TRUSTSTORE_PASS = "KNOX_CLIENT_TRUSTSTORE_PASS";
  private static final String GATEWAY_CLIENT_TRUST = "gateway-client-trust.jks";
  private static final String KNOX_CLIENT_TRUSTSTORE_FILENAME = "KNOX_CLIENT_TRUSTSTORE_FILENAME";
  private static final String KNOX_CLIENT_TRUSTSTORE_DIR = "KNOX_CLIENT_TRUSTSTORE_DIR";
  private static final String DEFAULT_JAAS_FILE = "/jaas.conf";
  public static final String JGSS_LOGIN_MOUDLE = "com.sun.security.jgss.initiate";

  private boolean isKerberos;

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

  public static KnoxSession login( String url, Map<String,String> headers ) throws URISyntaxException {
    KnoxSession instance = new KnoxSession(ClientContext.with(url));
    instance.setHeaders(headers);
    return instance;
  }

  public static KnoxSession login( String url, String username, String password ) throws URISyntaxException {
    return new KnoxSession(ClientContext.with(username, password, url));
  }

  public static KnoxSession login( String url, String username, String password,
      String truststoreLocation, String truststorePass ) throws URISyntaxException {

    return new KnoxSession(ClientContext.with(username, password, url)
            .connection().withTruststore(truststoreLocation, truststorePass).end());
  }

  public static KnoxSession loginInsecure(String url, String username, String password) throws URISyntaxException {
    return new KnoxSession(ClientContext.with(username, password, url)
            .connection().secure(false).end());
  }

  /**
   * Support kerberos authentication.
   *
   * @param url Gateway url
   * @param jaasConf jaas configuration (optional- can be null)
   * @param krb5Conf kerberos configuration (optional - can be null)
   * @param debug enable debug messages
   * @return KnoxSession
   * @throws URISyntaxException exception in case of malformed url
   * @since 1.3.0
   */
  public static KnoxSession kerberosLogin(final String url,
      final String jaasConf,
      final String krb5Conf,
      final boolean debug)
      throws URISyntaxException {

    return new KnoxSession(ClientContext.with(url)
        .kerberos()
        .enable(true)
        .jaasConf(jaasConf)
        .krb5Conf(krb5Conf)
        .debug(debug)
        .end());
  }

  /**
   * Support kerberos authentication.
   * This method assumed kinit has already been called
   * and the token is persisted on disk.
   * @param url Gateway url
   * @return KnoxSession
   * @throws URISyntaxException exception in case of malformed url
   * @since 1.3.0
   */
  public static KnoxSession kerberosLogin(final String url)
      throws URISyntaxException {
    return kerberosLogin(url, "", "", false);
  }

  protected KnoxSession() throws KnoxShellException, URISyntaxException {
  }

  public KnoxSession( ClientContext clientContext) throws KnoxShellException, URISyntaxException {
    this.executor = Executors.newCachedThreadPool();
    this.base = clientContext.url();

    try {
      client = createClient(clientContext);
    } catch (GeneralSecurityException e) {
      throw new KnoxShellException("Failed to create HTTP client.", e);
    }
  }

  protected CloseableHttpClient createClient(ClientContext clientContext) throws GeneralSecurityException {

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

    KeyStore trustStore = getTrustStore(clientContext);
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

    /* kerberos auth */
    if (clientContext.kerberos().enable()) {
      isKerberos = true;
      /* set up system properties */
      if (!StringUtils.isBlank(clientContext.kerberos().krb5Conf())) {
        System.setProperty("java.security.krb5.conf",
            clientContext.kerberos().krb5Conf());
      }

      if (!StringUtils.isBlank(clientContext.kerberos().jaasConf())) {
        System.setProperty("java.security.auth.login.config",
            clientContext.kerberos().jaasConf());
      } else {
        final URL url = getClass().getResource(DEFAULT_JAAS_FILE);
        System.setProperty("java.security.auth.login.config",
            url.toExternalForm());
      }

      if (clientContext.kerberos().debug()) {
        System.setProperty("sun.security.krb5.debug", "true");
        System.setProperty("sun.security.jgss.debug", "true");
      }

      System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");

      final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

      credentialsProvider.setCredentials(AuthScope.ANY, new Credentials() {
        @Override
        public Principal getUserPrincipal() {
          return null;
        }

        @Override
        public String getPassword() {
          return null;
        }
      });

      final Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
          .register(AuthSchemes.SPNEGO, new SPNegoSchemeFactory(true)).build();

      return HttpClients.custom()
          .setConnectionManager(connectionManager)
          .setDefaultAuthSchemeRegistry(authSchemeRegistry)
          .setDefaultCredentialsProvider(credentialsProvider)
          .build();

    } else {
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

  }

  private KeyStore getTrustStore(ClientContext clientContext) throws GeneralSecurityException {
    KeyStore ks;
    String truststorePass = null;

    discoverTruststoreDetails(clientContext);

    try {
      ks = KeyStore.getInstance("JKS");
      File file = new File(clientContext.connection().truststoreLocation());
      if (file.exists()) {
        truststorePass = clientContext.connection().truststorePass();
      } else {
        String truststore = System.getProperty("javax.net.ssl.trustStore");
        truststorePass = System.getProperty("javax.net.ssl.trustStorePassword", "changeit");
        if (truststore == null) {
          String truststoreDir = System.getProperty("java.home");
          truststore = truststoreDir + File.separator + "lib" + File.separator
              + "security" + File.separator + "cacerts";
        }
        file = new File(truststore);
      }

      if (file.exists()) {
        try (InputStream is = Files.newInputStream(file.toPath())) {
          ks.load(is, truststorePass.toCharArray());
        }
      }
      else {
        throw new KnoxShellException("Unable to find a truststore for secure login."
            + "Please import the gateway-identity certificate into the JVM"
          + " truststore or set the truststore location ENV variables.");
      }
    } catch (KeyStoreException e) {
      throw new KnoxShellException("Unable to create keystore of expected type.", e);
    } catch (FileNotFoundException e) {
      throw new KnoxShellException("Unable to read truststore."
          + " Please import the gateway-identity certificate into the JVM"
          + " truststore or set the truststore location ENV variables.", e);
    } catch (NoSuchAlgorithmException e) {
      throw new KnoxShellException("Unable to load the truststore."
          + " Please import the gateway-identity certificate into the JVM"
          + " truststore or set the truststore location ENV variables.", e);
    } catch (CertificateException e) {
      throw new KnoxShellException("Certificate cannot be found in the truststore."
          + " Please import the gateway-identity certificate into the JVM"
          + " truststore or set the truststore location ENV variables.", e);
    } catch (IOException e) {
      throw new KnoxShellException("Unable to load truststore."
          + " May be related to password setting or truststore format.", e);
    }

    return ks;
  }

  protected void discoverTruststoreDetails(ClientContext clientContext) {
    String truststoreDir;
    String truststoreFileName;
    if (clientContext.connection().truststoreLocation() != null &&
        clientContext.connection().truststorePass() != null) {
      return;
    } else {
      truststoreDir = System.getenv(KNOX_CLIENT_TRUSTSTORE_DIR);
      if (truststoreDir == null) {
        truststoreDir = System.getProperty("user.home");
      }
      truststoreFileName = System.getenv(KNOX_CLIENT_TRUSTSTORE_FILENAME);
      if (truststoreFileName == null) {
        truststoreFileName = GATEWAY_CLIENT_TRUST;
      }
    }
    String truststorePass = System.getenv(KNOX_CLIENT_TRUSTSTORE_PASS);
    if (truststorePass == null) {
      truststorePass = GATEWAY_CLIENT_TRUST_DEFAULT_PASS;
    }
    String truststoreLocation = truststoreDir + File.separator + truststoreFileName;
    clientContext.connection().withTruststore(truststoreLocation, truststorePass);
  }

  public String base() {
    return base;
  }

  @SuppressForbidden
  public CloseableHttpResponse executeNow(HttpRequest request ) throws IOException {
    /* check for kerberos */
    if (isKerberos) {
      LoginContext lc;
      try {
        lc = new LoginContext(JGSS_LOGIN_MOUDLE, new TextCallbackHandler());
        lc.login();
        return Subject.doAs(lc.getSubject(),
            (PrivilegedAction<CloseableHttpResponse>) () -> {
              CloseableHttpResponse response;
              try {
                response = client.execute(host, request, context);
                if (response.getStatusLine().getStatusCode() < 400) {
                  return response;
                } else {
                  throw new ErrorResponse(response);
                }
              } catch (final IOException e) {
                throw new KnoxShellException(e.toString(), e);
              }
            });

      } catch (final LoginException e) {
        throw new KnoxShellException(e.toString(), e);
      }

    } else {
      CloseableHttpResponse response = client.execute( host, request, context );
      if( response.getStatusLine().getStatusCode() < 400 ) {
        return response;
      } else {
        throw new ErrorResponse( response );
      }
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
      throw new KnoxShellException("Can not shutdown underlying resources", e);
    }
  }
}
