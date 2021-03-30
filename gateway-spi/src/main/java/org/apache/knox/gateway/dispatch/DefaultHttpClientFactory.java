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
package org.apache.knox.gateway.dispatch;

import java.io.IOException;
import java.security.KeyStore;
import java.security.Principal;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.servlet.FilterConfig;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.apache.knox.gateway.SpiGatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.metrics.MetricsService;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

public class DefaultHttpClientFactory implements HttpClientFactory {
  private static final SpiGatewayMessages LOG = MessagesFactory.get(SpiGatewayMessages.class);
  private static final String PARAMETER_SERVICE_ROLE = "serviceRole";
  static final String PARAMETER_USE_TWO_WAY_SSL = "useTwoWaySsl";
  /* retry in case of NoHttpResponseException */
  static final String PARAMETER_RETRY_COUNT = "retryCount";
  static final String PARAMETER_RETRY_NON_SAFE_REQUEST = "retryNonSafeRequest";
  /* do not retry non-idempotent requests OOTB */
  static final boolean DEFAULT_PARAMETER_RETRY_NON_SAFE_REQUEST = false;

  @Override
  public HttpClient createHttpClient(FilterConfig filterConfig) {
    final String serviceRole = filterConfig.getInitParameter(PARAMETER_SERVICE_ROLE);
    HttpClientBuilder builder;
    GatewayConfig gatewayConfig = (GatewayConfig) filterConfig.getServletContext().getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE);
    GatewayServices services = (GatewayServices) filterConfig.getServletContext()
        .getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE);
    if (gatewayConfig != null && gatewayConfig.isMetricsEnabled()) {
      MetricsService metricsService = services.getService(ServiceType.METRICS_SERVICE);
      builder = metricsService.getInstrumented(HttpClientBuilder.class);
    } else {
      builder = HttpClients.custom();
    }

    // Conditionally set a custom SSLContext
    SSLContext sslContext = createSSLContext(services, filterConfig, serviceRole);
    if(sslContext != null) {
      builder.setSSLSocketFactory(new SSLConnectionSocketFactory(sslContext));
    }

    if (Boolean.parseBoolean(System.getProperty(GatewayConfig.HADOOP_KERBEROS_SECURED))) {
      CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(AuthScope.ANY, new UseJaasCredentials());

      Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder.<AuthSchemeProvider>create()
          .register(AuthSchemes.SPNEGO, new KnoxSpnegoAuthSchemeFactory(true))
          .build();

      builder.setDefaultAuthSchemeRegistry(authSchemeRegistry)
          .setDefaultCookieStore(new HadoopAuthCookieStore(gatewayConfig))
          .setDefaultCredentialsProvider(credentialsProvider);
    } else {
      builder.setDefaultCookieStore(new NoCookieStore());
    }

    builder.setKeepAliveStrategy( DefaultConnectionKeepAliveStrategy.INSTANCE );
    builder.setConnectionReuseStrategy( DefaultConnectionReuseStrategy.INSTANCE );
    builder.setRedirectStrategy( new NeverRedirectStrategy() );
    builder.setRetryHandler( new NeverRetryHandler() );

    int maxConnections = getMaxConnections( filterConfig );
    builder.setMaxConnTotal( maxConnections );
    builder.setMaxConnPerRoute( maxConnections );

    builder.setDefaultRequestConfig(getRequestConfig(filterConfig, serviceRole));

    // See KNOX-1530 for details
    builder.disableContentCompression();

    if (doesRetryParamExist(filterConfig)) {
      int retryCount = Integer.parseInt(filterConfig.getInitParameter(PARAMETER_RETRY_COUNT));
      /* do we want to retry non-idempotent requests? default no */
      boolean retryNonIdempotent = DEFAULT_PARAMETER_RETRY_NON_SAFE_REQUEST;
      if (filterConfig.getInitParameter(PARAMETER_RETRY_NON_SAFE_REQUEST)
          != null) {
        retryNonIdempotent = Boolean.parseBoolean(
            filterConfig.getInitParameter(PARAMETER_RETRY_NON_SAFE_REQUEST));
      }
      builder.setRetryHandler(new DefaultHttpRequestRetryHandler(retryCount,
          retryNonIdempotent));
    }
    return builder.build();
  }

  private boolean doesRetryParamExist(final FilterConfig filterConfig) {
    return filterConfig.getInitParameter(PARAMETER_RETRY_COUNT) != null
        && StringUtils
        .isNumeric(filterConfig.getInitParameter(PARAMETER_RETRY_COUNT));
  }

  /**
   * Conditionally creates a custom {@link SSLContext} based on the Gateway's configuration and whether
   * two-way SSL is enabled or not.
   * <p>
   * If two-way SSL is enabled, then a context with the Gateway's identity and a configured trust store
   * is created.  The trust store is forced to be the same as the identity's keystore if an explicit
   * trust store is not configured.
   * <p>
   * If two-way SSL is not enabled and an explict trust store is configured, then a context with the
   * configured trust store is created.
   * <p>
   * Else, a custom context is not crated and <code>null</code> is returned.
   * <p>
   * This method is package private to allow access to unit tests
   *
   * @param services     the {@link GatewayServices}
   * @param filterConfig a {@link FilterConfig} used to query for parameters for this operation
   * @param serviceRole the name of the service role to whom this HTTP client is being created for
   * @return a {@link SSLContext} or <code>null</code> if a custom {@link SSLContext} is not needed.
   */
  SSLContext createSSLContext(GatewayServices services, FilterConfig filterConfig, String serviceRole) {
    KeyStore identityKeystore;
    char[] identityKeyPassphrase;
    KeyStore trustKeystore;

    KeystoreService ks = services.getService(ServiceType.KEYSTORE_SERVICE);
    try {
      if (Boolean.parseBoolean(filterConfig.getInitParameter(PARAMETER_USE_TWO_WAY_SSL))) {
        LOG.usingTwoWaySsl(serviceRole);
        AliasService as = services.getService(ServiceType.ALIAS_SERVICE);

        // Get the Gateway's configured identity keystore and key passphrase
        identityKeystore = ks.getKeystoreForGateway();
        identityKeyPassphrase = as.getGatewayIdentityPassphrase();

        // The trustKeystore will be the same as the identityKeystore if a truststore was not explicitly
        // configured in gateway-site (gateway.truststore.password.alias, gateway.truststore.path, gateway.truststore.type)
        // This was the behavior before KNOX-1812
        trustKeystore = ks.getTruststoreForHttpClient();
        if (trustKeystore == null) {
          trustKeystore = identityKeystore;
        }
      } else {
        // If not using twoWaySsl, there is no need to calculate the Gateway's identity keystore or
        // identity key.
        identityKeystore = null;
        identityKeyPassphrase = null;

        // The behavior before KNOX-1812 was to use the HttpClients default SslContext. However,
        // if a truststore was explicitly configured in gateway-site (gateway.truststore.password.alias,
        // gateway.truststore.path, gateway.truststore.type) create a custom SslContext and use it.
        trustKeystore = ks.getTruststoreForHttpClient();
      }

      // If an identity keystore or a trust store needs to be set, create and return a custom
      // SSLContext; else return null.
      if ((identityKeystore != null) || (trustKeystore != null)) {
        SSLContextBuilder sslContextBuilder = SSLContexts.custom();

        if (identityKeystore != null) {
          sslContextBuilder.loadKeyMaterial(identityKeystore, identityKeyPassphrase);
        }

        if (trustKeystore != null) {
          sslContextBuilder.loadTrustMaterial(trustKeystore, null);
        }

        return sslContextBuilder.build();
      } else {
        return null;
      }
    } catch (Exception e) {
      throw new IllegalArgumentException("Unable to create SSLContext", e);
    }
  }

  static RequestConfig getRequestConfig(FilterConfig config, String serviceRole) {
    RequestConfig.Builder builder = RequestConfig.custom();
    int connectionTimeout = getConnectionTimeout( config );
    if ( connectionTimeout != -1 ) {
      builder.setConnectTimeout( connectionTimeout );
      builder.setConnectionRequestTimeout( connectionTimeout );
      LOG.setHttpClientConnectionTimeout(connectionTimeout, serviceRole == null ? "N/A" : serviceRole);
    }
    int socketTimeout = getSocketTimeout( config );
    if( socketTimeout != -1 ) {
      builder.setSocketTimeout( socketTimeout );
      LOG.setHttpClientSocketTimeout(socketTimeout, serviceRole == null ? "N/A" : serviceRole);
    }

    // HttpClient 4.5.7 is broken for %2F handling with url normalization.
    // However, HttpClient 4.5.8+ (HTTPCLIENT-1968) has reasonable url
    // normalization that matches what Knox already does related to url handling.
    //
    // If this view changes later, need to change here as well as make sure
    // rest-assured doesn't use the old HttpClient behavior.
    builder.setNormalizeUri(true);

    return builder.build();
  }

  private static class NoCookieStore implements CookieStore {
    @Override
    public void addCookie(Cookie cookie) {
      //no op
    }

    @Override
    public List<Cookie> getCookies() {
      return Collections.emptyList();
    }

    @Override
    public boolean clearExpired(Date date) {
      return true;
    }

    @Override
    public void clear() {
      //no op
    }
  }

  private static class NeverRedirectStrategy implements RedirectStrategy {
    @Override
    public boolean isRedirected( HttpRequest request, HttpResponse response, HttpContext context )
        throws ProtocolException {
      return false;
    }

    @Override
    public HttpUriRequest getRedirect( HttpRequest request, HttpResponse response, HttpContext context )
        throws ProtocolException {
      return null;
    }
  }

  private static class NeverRetryHandler implements HttpRequestRetryHandler {
    @Override
    public boolean retryRequest( IOException exception, int executionCount, HttpContext context ) {
      return false;
    }
  }

  private static class UseJaasCredentials implements Credentials {

    @Override
    public String getPassword() {
      return null;
    }

    @Override
    public Principal getUserPrincipal() {
      return null;
    }

  }

  private int getMaxConnections( FilterConfig filterConfig ) {
    int maxConnections = 32;
    GatewayConfig config =
        (GatewayConfig)filterConfig.getServletContext().getAttribute( GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE );
    if( config != null ) {
      maxConnections = config.getHttpClientMaxConnections();
    }
    String str = filterConfig.getInitParameter( "httpclient.maxConnections" );
    if( str != null ) {
      try {
        maxConnections = Integer.parseInt( str );
      } catch ( NumberFormatException e ) {
        // Ignore it and use the default.
      }
    }
    return maxConnections;
  }

  private static int getConnectionTimeout( FilterConfig filterConfig ) {
    int timeout = -1;
    GatewayConfig globalConfig =
        (GatewayConfig)filterConfig.getServletContext().getAttribute( GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE );
    if( globalConfig != null ) {
      timeout = globalConfig.getHttpClientConnectionTimeout();
    }
    String str = filterConfig.getInitParameter( "httpclient.connectionTimeout" );
    if( str != null ) {
      try {
        timeout = (int)parseTimeout( str );
      } catch ( Exception e ) {
        // Ignore it and use the default.
      }
    }
    return timeout;
  }

  private static int getSocketTimeout( FilterConfig filterConfig ) {
    int timeout = -1;
    GatewayConfig globalConfig =
        (GatewayConfig)filterConfig.getServletContext().getAttribute( GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE );
    if( globalConfig != null ) {
      timeout = globalConfig.getHttpClientSocketTimeout();
    }
    String str = filterConfig.getInitParameter( "httpclient.socketTimeout" );
    if( str != null ) {
      try {
        timeout = (int)parseTimeout( str );
      } catch ( Exception e ) {
        // Ignore it and use the default.
      }
    }
    return timeout;
  }

  private static long parseTimeout( String s ) {
    PeriodFormatter f = new PeriodFormatterBuilder()
        .appendMinutes().appendSuffix("m"," min")
        .appendSeconds().appendSuffix("s"," sec")
        .appendMillis().toFormatter();
    Period p = Period.parse( s, f );
    return p.toStandardDuration().getMillis();
  }
}
