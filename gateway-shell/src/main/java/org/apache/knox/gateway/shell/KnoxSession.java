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

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.AccessController;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.security.auth.Subject;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.security.auth.callback.TextCallbackHandler;
import de.thetaphi.forbiddenapis.SuppressForbidden;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
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
import org.apache.http.impl.client.DefaultServiceUnavailableRetryStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.ssl.SSLContexts;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.shell.util.ClientTrustStoreHelper;
import org.apache.knox.gateway.util.JsonUtils;

public class KnoxSession implements Closeable {
  private static final String DEFAULT_JAAS_FILE = "/jaas.conf";
  public static final String JGSS_LOGIN_MOUDLE = "com.sun.security.jgss.initiate";
  public static final String END_CERTIFICATE = "-----END CERTIFICATE-----\n";
  public static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----\n";

  private static final KnoxShellMessages LOG = MessagesFactory.get(KnoxShellMessages.class);

  private static final CredentialsProvider EMPTY_CREDENTIALS_PROVIDER = new BasicCredentialsProvider();
  static {
    EMPTY_CREDENTIALS_PROVIDER.setCredentials(AuthScope.ANY,
                                                new Credentials() {
                                                  @Override
                                                  public Principal getUserPrincipal () {
                                                    return null;
                                                  }

                                                  @Override
                                                  public String getPassword () {
                                                    return null;
                                                  }
                                                });
  }

  private boolean isKerberos;

  private URL jaasConfigURL;

  String base;
  HttpHost host;
  CloseableHttpClient client;
  BasicHttpContext context;
  ExecutorService executor;
  Map<String, String> headers = new HashMap<>();

  private static final String KNOXSQLHISTORIES_JSON = "knoxsqlhistories.json";
  private static final String KNOXDATASOURCES_JSON = "knoxdatasources.json";
  private static final String KNOXMOUNTPOINTS_JSON = "knoxmountpoints.json";

  public Map<String, String> getHeaders() {
    return headers;
  }

  public void setHeaders(Map<String, String> headers) {
    this.headers = headers;
  }

  protected KnoxSession() throws KnoxShellException, URISyntaxException {
  }

  public KnoxSession(final ClientContext clientContext) throws KnoxShellException, URISyntaxException {
    this.executor = Executors.newCachedThreadPool();
    this.base = clientContext.url();

    try {
      client = createClient(clientContext);
    } catch (GeneralSecurityException e) {
      throw new KnoxShellException("Failed to create HTTP client.", e);
    }
  }

  public static KnoxSession login( String url, Map<String,String> headers ) throws URISyntaxException {
    KnoxSession instance = new KnoxSession(ClientContext.with(url));
    instance.setHeaders(headers);
    return instance;
  }

  public static KnoxSession login( String             url,
                                   Map<String,String> headers,
                                   String             truststoreLocation,
                                   String             truststorePass  ) throws URISyntaxException {
    return login(url, headers, truststoreLocation, truststorePass, null);
  }

  public static KnoxSession login(String url, Map<String, String> headers, String truststoreLocation, String truststorePass, String truststoreType)
      throws URISyntaxException {
    final KnoxSession instance = new KnoxSession(ClientContext.with(url).connection().withTruststore(truststoreLocation, truststorePass, truststoreType).end());
    instance.setHeaders(headers);
    return instance;
  }

  public static KnoxSession login( String url, String username, String password ) throws URISyntaxException {
    return new KnoxSession(ClientContext.with(username, password, url));
  }

  public static KnoxSession login( String url, String username, String password, String truststoreLocation, String truststorePass ) throws URISyntaxException {
    return login(url, username, password, truststoreLocation, truststorePass, null);
  }

  public static KnoxSession login( String url, String username, String password, String truststoreLocation, String truststorePass, String truststoreType) throws URISyntaxException {
    return new KnoxSession(ClientContext.with(username, password, url)
        .connection().withTruststore(truststoreLocation, truststorePass, truststoreType).end());
  }

  public static KnoxSession login(ClientContext context) throws URISyntaxException {
    return new KnoxSession(context);
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
    return kerberosLogin(url, false);
  }

  /**
   * Support kerberos authentication.
   * This method assumed kinit has already been called
   * and the token is persisted on disk.
   * @param url Gateway url
   * @param debug enable debug messages
   * @return KnoxSession
   * @throws URISyntaxException exception in case of malformed url
   * @since 1.3.0
   */
  public static KnoxSession kerberosLogin(final String url, boolean debug)
      throws URISyntaxException {
    return kerberosLogin(url, "", "", debug);
  }

  public static KnoxSession loginInsecure(String url, String username, String password) throws URISyntaxException {
    return new KnoxSession(ClientContext.with(username, password, url)
        .connection().secure(false).end());
  }

  @SuppressForbidden
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
        File f = new File(clientContext.kerberos().jaasConf());
        if (f.exists()) {
          try {
            jaasConfigURL = f.getCanonicalFile().toURI().toURL();
            LOG.jaasConfigurationLocation(jaasConfigURL.toExternalForm());
          } catch (IOException e) {
            LOG.failedToLocateJAASConfiguration(e.getMessage());
          }
        } else {
          LOG.jaasConfigurationDoesNotExist(f.getAbsolutePath());
        }
      }

      // Fall back to the default JAAS config
      if (jaasConfigURL == null) {
        LOG.usingDefaultJAASConfiguration();
        jaasConfigURL = getClass().getResource(DEFAULT_JAAS_FILE);
        LOG.jaasConfigurationLocation(jaasConfigURL.toExternalForm());
      }

      if (clientContext.kerberos().debug()) {
        System.setProperty("sun.security.krb5.debug", "true");
        System.setProperty("sun.security.jgss.debug", "true");
      }

      // (KNOX-2001) Log a warning if the useSubjectCredsOnly restriction is "relaxed"
      String useSubjectCredsOnly = System.getProperty("javax.security.auth.useSubjectCredsOnly");
      if (useSubjectCredsOnly != null && !Boolean.parseBoolean(useSubjectCredsOnly)) {
        LOG.useSubjectCredsOnlyIsFalse();
      }

      final Registry<AuthSchemeProvider> authSchemeRegistry =
          RegistryBuilder.<AuthSchemeProvider>create().register(AuthSchemes.SPNEGO, new SPNegoSchemeFactory(true)).build();

      return HttpClients.custom()
                        .setConnectionManager(connectionManager)
                        .setDefaultAuthSchemeRegistry(authSchemeRegistry)
                        .setDefaultCredentialsProvider(EMPTY_CREDENTIALS_PROVIDER)
                        .build();
    } else {
      AuthCache authCache = new BasicAuthCache();
      BasicScheme authScheme = new BasicScheme();
      authCache.put(host, authScheme);
      context = new BasicHttpContext();
      context.setAttribute(org.apache.http.client.protocol.HttpClientContext.AUTH_CACHE, authCache);

      CredentialsProvider credentialsProvider = null;
      if (clientContext.username() != null && clientContext.password() != null) {
        credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(host.getHostName(), host.getPort()),
                                           new UsernamePasswordCredentials(clientContext.username(),
                                           clientContext.password()));
      }
      HttpClientBuilder httpClientBuilder = HttpClients.custom()
              .setConnectionManager(connectionManager)
              .setDefaultCredentialsProvider(credentialsProvider);
      if (clientContext.connection().retryCount() != -1) {
        httpClientBuilder
                .setRetryHandler(new KnoxClientRetryHandler(
                        clientContext.connection().retryCount(),
                        clientContext.connection().requestSentRetryEnabled()))
                .setServiceUnavailableRetryStrategy(new DefaultServiceUnavailableRetryStrategy(
                        clientContext.connection().retryCount(),
                        clientContext.connection().retryIntervalMillis()));
      }
      return httpClientBuilder.build();
    }
  }

  protected X509Certificate generateCertificateFromBytes(byte[] certBytes) throws CertificateException {
    CertificateFactory factory = CertificateFactory.getInstance("X.509");
    return (X509Certificate)factory.generateCertificate(new ByteArrayInputStream(certBytes));
  }

  private KeyStore getTrustStore(ClientContext clientContext) throws GeneralSecurityException {
    KeyStore ks;
    String truststorePass;

    // if a PEM file was provided create a keystore from that and use
    // it as the truststore
    String pem = clientContext.connection().endpointPublicCertPem();
    if (pem != null) {
      // strip delimiters
      if (pem.contains("BEGIN")) {
        pem = pem.substring(BEGIN_CERTIFICATE.length()-1,
            pem.indexOf(END_CERTIFICATE.substring(0, END_CERTIFICATE.length()-1)));
      }
      try {
        byte[] bytes = Base64.decodeBase64(pem);
        KeyStore keystore = KeyStore.getInstance(clientContext.connection().truststoreType());
        keystore.load(null);
        keystore.setCertificateEntry("knox-gateway", generateCertificateFromBytes(bytes));

        return keystore;
      } catch (IOException e) {
        LOG.unableToLoadProvidedPEMEncodedTrustedCert(e);
      }
    }

    discoverTruststoreDetails(clientContext);

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
        ks = KeyStore.getInstance(clientContext.connection().truststoreType());
        ks.load(is, truststorePass.toCharArray());
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
    } else {
      throw new KnoxShellException("Unable to find a truststore for secure login."
                                       + "Please import the gateway-identity certificate into the JVM"
                                       + " truststore or set the truststore location ENV variables.");
    }

    return ks;
  }

  protected void discoverTruststoreDetails(ClientContext clientContext) {
    if (clientContext.connection().truststoreLocation() != null &&
        clientContext.connection().truststorePass() != null &&
        clientContext.connection().truststoreType()!= null) {
      return;
    } else {
      final String truststoreLocation = ClientTrustStoreHelper.getClientTrustStoreFile().getAbsolutePath();
      final String truststorePass = ClientTrustStoreHelper.getClientTrustStoreFilePassword();
      final String truststoreType = ClientTrustStoreHelper.getClientTrustStoreType();
      clientContext.connection().withTruststore(truststoreLocation, truststorePass, truststoreType);
    }
  }

  public String base() {
    return base;
  }

  @SuppressForbidden
  public CloseableHttpResponse executeNow(HttpRequest request ) throws IOException {
    /* check for kerberos */
    if (isKerberos) {
      Subject subject = Subject.getSubject(AccessController.getContext());
      try {
        if (subject == null) {
          LOG.noSubjectAvailable();
          Configuration jaasConf;
          try {
            jaasConf = new JAASClientConfig(jaasConfigURL);
          } catch (Exception e) {
            LOG.failedToLoadJAASConfiguration(jaasConfigURL.toExternalForm());
            throw new KnoxShellException(e.toString(), e);
          }

          LoginContext lc = new LoginContext(JGSS_LOGIN_MOUDLE,
                                             null,
                                             new TextCallbackHandler(),
                                             jaasConf);
          lc.login();
          subject = lc.getSubject();
        }
        return Subject.doAs(subject,
            (PrivilegedAction<CloseableHttpResponse>) () -> {
              CloseableHttpResponse response;
              try {
                response = client.execute(host, request, context);
                if (response.getStatusLine().getStatusCode() < 400) {
                  return response;
                } else {
                  throw new ErrorResponse(
                      request.getRequestLine().getUri() + ": ", response);
                }
              } catch (final IOException e) {
                throw new KnoxShellException(e.toString(), e);
              }
            });

      } catch (final LoginException e) {
        throw new KnoxShellException(e.toString(), e);
      }
    } else {
      CloseableHttpResponse response = client.execute(host, request, context);
      if (response.getStatusLine().getStatusCode() < 400) {
        return response;
      } else {
        throw new ErrorResponse(request.getRequestLine().getUri() + ": ",
            response);
      }
    }
  }

  public <T> Future<T> executeLater( Callable<T> callable ) {
    return executor.submit( callable );
  }

  public void waitFor( Future<?>... futures ) throws ExecutionException, InterruptedException {
    if( futures != null ) {
      for( Future<?> future : futures ) {
        future.get();
      }
    }
  }

  public void waitFor( long timeout, TimeUnit units, Future<?>... futures ) throws ExecutionException, TimeoutException, InterruptedException {
    if( futures != null ) {
      timeout = TimeUnit.MILLISECONDS.convert( timeout, units );
      long start;
      for( Future<?> future : futures ) {
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
      Thread.currentThread().interrupt();
      throw new KnoxShellException("Can not shutdown underlying resources", e);
    }
  }

  @Override
  public String toString() {
    return String.format(Locale.ROOT, "KnoxSession{base='%s'}", base);
  }

  /**
   * Persist provided Map to a file within the {user.home}/.knoxshell directory
   * @param <T> type of the list
   * @param fileName of persisted file
   * @param map to persist
   */
  public static <T> void persistToKnoxShell(String fileName, Map<String, List<T>> map) {
    String s = JsonUtils.renderAsJsonString(map);
    String home = System.getProperty("user.home");
    try {
      write(new File(
          home + File.separator + ".knoxshell" + File.separator + fileName), s);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Persist provided Map to a file within the {user.home}/.knoxshell directory
   * @param <T> type of the value in the map
   * @param fileName of persisted file
   * @param map to persist
   */
  public static <T> void persistDataSourcesToKnoxShell(String fileName, Map<String, T> map) {
    persistMapToKnoxShell(fileName, map);
  }

  /**
   * Persist provided Map to a file within the {user.home}/.knoxshell directory
   * @param <T> type of the value in the map
   * @param fileName of persisted file
   * @param map to persist
   */
  public static <T> void persistMapToKnoxShell(String fileName, Map<String, T> map) {
    String s = JsonUtils.renderAsJsonString(map);
    String home = System.getProperty("user.home");
    try {
      write(new File(
          home + File.separator +
          ".knoxshell" + File.separator + fileName), s);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static void write(File file, String s) throws IOException {
    synchronized(KnoxSession.class) {
      // Ensure the parent directory exists...
      // This will attempt to create all missing directories.  No failures will occur if the directories already exist.
      Files.createDirectories(file.toPath().getParent());
      try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.WRITE,
          StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
        channel.tryLock();
        FileUtils.write(file, s, StandardCharsets.UTF_8);
      } catch (OverlappingFileLockException e) {
        System.out.println("Unable to acquire write lock for: " + file.getAbsolutePath());
      }
    }
  }

  public static void persistSQLHistory(Map<String, List<String>> sqlHistories) {
    persistToKnoxShell(KNOXSQLHISTORIES_JSON, sqlHistories);
  }

  public static void persistDataSources(Map<String, KnoxDataSource> datasources) {
    persistDataSourcesToKnoxShell(KNOXDATASOURCES_JSON, datasources);
  }

  public static void persistMountPoints(Map<String, String> mounts) {
    persistMapToKnoxShell(KNOXMOUNTPOINTS_JSON, mounts);
  }

  /**
   * Load and return a map of datasource names to sql commands
   * from the {user.home}/.knoxshell/knoxsqlhistories.json file.
   * @return sqlHistory map
   * @throws IOException exception when loading sql history
   */
  public static Map<String, List<String>> loadSQLHistories() throws IOException {
    Map<String, List<String>> sqlHistories = null;
    String home = System.getProperty("user.home");

    File historyFile = new File(
        home + File.separator +
        ".knoxshell" + File.separator + KNOXSQLHISTORIES_JSON);
    if (historyFile.exists()) {
      String json = readFileToString(historyFile);
      sqlHistories = getMapOfStringArrayListsFromJsonString(json);
    }
    return sqlHistories;
  }

  public static Map<String, String> loadMountPoints() throws IOException {
    Map<String, String> mounts = new HashMap<>();
    String home = System.getProperty("user.home");

    File mountFile = new File(
        home + File.separator +
        ".knoxshell" + File.separator + KNOXMOUNTPOINTS_JSON);
    if (mountFile.exists()) {
      String json = readFileToString(mountFile);
      mounts = getMapFromJsonString(json);
    }
    return mounts;
  }

  private static String readFileToString(File file) throws IOException {
    String content = null;

    synchronized(KnoxSession.class) {
      try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ)) {
        channel.tryLock(0L, Long.MAX_VALUE, true);
        content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
      } catch (OverlappingFileLockException e) {
        System.out.println("Unable to acquire write lock for: " + file.getAbsolutePath());
      }
    }

    return content;
  }

  /**
   * Load and return a map of datasource names to KnoxDataSource
   * objects from the {user.home}/.knoxshell/knoxdatasources.json file.
   * @return map of data sources
   * @throws IOException exception when loading data sources
   */
  public static Map<String, KnoxDataSource> loadDataSources() throws IOException {
    Map<String, KnoxDataSource> datasources = null;
    String home = System.getProperty("user.home");
    String json;

    File dsFile = new File(home + File.separator + ".knoxshell" + File.separator + KNOXDATASOURCES_JSON);
    if (dsFile.exists()) {
      json = readFileToString(dsFile);
      datasources = getMapOfDataSourcesFromJsonString(json);
    }

    return datasources;
  }

  public static Map<String, List<String>> getMapOfStringArrayListsFromJsonString(String json) throws IOException {
    Map<String, List<String>> obj;
    JsonFactory factory = new JsonFactory();
    ObjectMapper mapper = new ObjectMapper(factory);
    TypeReference<Map<String, List<String>>> typeRef = new TypeReference<Map<String, List<String>>>() {};
    obj = mapper.readValue(json, typeRef);
    return obj;
  }

  public static <T> Map<String, T> getMapFromJsonString(String json) throws IOException {
    Map<String, T> obj;
    JsonFactory factory = new JsonFactory();
    ObjectMapper mapper = new ObjectMapper(factory);
    TypeReference<Map<String, T>> typeRef = new TypeReference<Map<String, T>>() {};
    obj = mapper.readValue(json, typeRef);
    return obj;
  }

  public static Map<String, KnoxDataSource> getMapOfDataSourcesFromJsonString(String json) throws IOException {
    Map<String, KnoxDataSource> obj;
    JsonFactory factory = new JsonFactory();
    ObjectMapper mapper = new ObjectMapper(factory);
    TypeReference<Map<String, KnoxDataSource>> typeRef = new TypeReference<Map<String, KnoxDataSource>>() {};
    obj = mapper.readValue(json, typeRef);
    return obj;
  }

  private static final class JAASClientConfig extends Configuration {
    private static final Configuration baseConfig = Configuration.getConfiguration();

    private Configuration configFile;

    JAASClientConfig(URL configFileURL) throws Exception {
      if (configFileURL != null) {
        this.configFile = ConfigurationFactory.create(configFileURL.toURI());
      }
    }

    @Override
    public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
      AppConfigurationEntry[] result = null;

      // Try the config file if it exists
      if (configFile != null) {
        result = configFile.getAppConfigurationEntry(name);
      }

      // If the entry isn't there, delegate to the base configuration
      if (result == null) {
        result = baseConfig.getAppConfigurationEntry(name);
      }

      return result;
    }
  }

  @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
  private static class ConfigurationFactory {
    private static final Class implClazz;
    static {
      // Oracle and OpenJDK use the Sun implementation
      String implName = System.getProperty("java.vendor").contains("IBM") ?
                                "com.ibm.security.auth.login.ConfigFile" : "com.sun.security.auth.login.ConfigFile";

      LOG.usingJAASConfigurationFileImplementation(implName);
      Class clazz = null;
      try {
        clazz = Class.forName(implName, false, Thread.currentThread().getContextClassLoader());
      } catch (ClassNotFoundException e) {
        LOG.failedToLoadJAASConfigurationFileImplementation(implName, e.getLocalizedMessage());
      }

      implClazz = clazz;
    }

    static Configuration create(URI uri) {
      Configuration config = null;

      if (implClazz != null) {
        try {
          Constructor ctor = implClazz.getDeclaredConstructor(URI.class);
          config = (Configuration) ctor.newInstance(uri);
        } catch (Exception e) {
          LOG.failedToInstantiateJAASConfigurationFileImplementation(implClazz.getCanonicalName(),
                                                                     e.getLocalizedMessage());
        }
      } else {
        LOG.noJAASConfigurationFileImplementation();
      }

      return config;
    }
  }
}
