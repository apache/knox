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

import static org.apache.knox.gateway.dispatch.DefaultHttpClientFactory.PARAMETER_USE_TWO_WAY_SSL;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class DefaultHttpClientFactoryTest {

  @Test
  public void testCreateHttpClientSSLContextDefaults() throws Exception {
    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getTruststoreForHttpClient()).andReturn(null).once();

    GatewayConfig gatewayConfig = createMock(GatewayConfig.class);
    expect(gatewayConfig.isMetricsEnabled()).andReturn(false).once();
    expect(gatewayConfig.getHttpClientMaxConnections()).andReturn(32).once();
    expect(gatewayConfig.getHttpClientConnectionTimeout()).andReturn(20000).once();
    expect(gatewayConfig.getHttpClientSocketTimeout()).andReturn(20000).once();

    GatewayServices gatewayServices = createMock(GatewayServices.class);
    expect(gatewayServices.getService(ServiceType.KEYSTORE_SERVICE)).andReturn(keystoreService).once();

    ServletContext servletContext = createMock(ServletContext.class);
    expect(servletContext.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(gatewayConfig).atLeastOnce();
    expect(servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(gatewayServices).atLeastOnce();

    FilterConfig filterConfig = createMock(FilterConfig.class);
    expect(filterConfig.getServletContext()).andReturn(servletContext).atLeastOnce();
    expect(filterConfig.getInitParameter("useTwoWaySsl")).andReturn("false").once();
    expect(filterConfig.getInitParameter("httpclient.maxConnections")).andReturn(null).once();
    expect(filterConfig.getInitParameter("httpclient.connectionTimeout")).andReturn(null).once();
    expect(filterConfig.getInitParameter("httpclient.socketTimeout")).andReturn(null).once();
    expect(filterConfig.getInitParameter("serviceRole")).andReturn(null).once();
    expect(filterConfig.getInitParameter("retryCount")).andReturn(null).once();

    replay(keystoreService, gatewayConfig, gatewayServices, servletContext, filterConfig);

    DefaultHttpClientFactory factory = new DefaultHttpClientFactory();
    HttpClient client = factory.createHttpClient(filterConfig);
    assertNotNull(client);

    verify(keystoreService, gatewayConfig, gatewayServices, servletContext, filterConfig);
  }

  @Test
  public void testCreateSSLContextDefaults() throws Exception {
    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getTruststoreForHttpClient()).andReturn(null).once();

    GatewayServices gatewayServices = createMock(GatewayServices.class);
    expect(gatewayServices.getService(ServiceType.KEYSTORE_SERVICE)).andReturn(keystoreService).once();

    FilterConfig filterConfig = createMock(FilterConfig.class);
    expect(filterConfig.getInitParameter(PARAMETER_USE_TWO_WAY_SSL)).andReturn("false").once();

    replay(keystoreService, gatewayServices, filterConfig);

    DefaultHttpClientFactory factory = new DefaultHttpClientFactory();
    SSLContext context = factory.createSSLContext(gatewayServices, filterConfig, "service");
    assertNull(context);

    verify(keystoreService, gatewayServices, filterConfig);
  }

  @Test
  public void testCreateSSLContextTwoWaySslNoCustomTrustStore() throws Exception {
    KeyStore gatewayIdentityKeyStore = loadKeyStore("target/test-classes/keystores/server-keystore.jks", "horton", "JKS");

    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getTruststoreForHttpClient()).andReturn(null).once();
    expect(keystoreService.getKeystoreForGateway()).andReturn(gatewayIdentityKeyStore).once();

    AliasService aliasService = createMock(AliasService.class);
    expect(aliasService.getGatewayIdentityPassphrase()).andReturn("horton".toCharArray()).once();

    GatewayServices gatewayServices = createMock(GatewayServices.class);
    expect(gatewayServices.getService(ServiceType.KEYSTORE_SERVICE)).andReturn(keystoreService).once();
    expect(gatewayServices.getService(ServiceType.ALIAS_SERVICE)).andReturn(aliasService).once();

    FilterConfig filterConfig = createMock(FilterConfig.class);
    expect(filterConfig.getInitParameter(PARAMETER_USE_TWO_WAY_SSL)).andReturn("true").once();

    replay(keystoreService, aliasService, gatewayServices, filterConfig);

    DefaultHttpClientFactory factory = new DefaultHttpClientFactory();
    SSLContext context = factory.createSSLContext(gatewayServices, filterConfig, "service");
    assertNotNull(context);

    verify(keystoreService, aliasService, gatewayServices, filterConfig);
  }

  @Test
  public void testCreateSSLContextTwoWaySslWithCustomTrustStore() throws Exception {
    KeyStore gatewayIdentityKeyStore = loadKeyStore("target/test-classes/keystores/server-keystore.jks", "horton", "JKS");
    KeyStore trustStore = loadKeyStore("target/test-classes/keystores/server-truststore.jks", "horton", "JKS");

    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getTruststoreForHttpClient()).andReturn(trustStore).once();
    expect(keystoreService.getKeystoreForGateway()).andReturn(gatewayIdentityKeyStore).once();

    AliasService aliasService = createMock(AliasService.class);
    expect(aliasService.getGatewayIdentityPassphrase()).andReturn("horton".toCharArray()).once();

    GatewayServices gatewayServices = createMock(GatewayServices.class);
    expect(gatewayServices.getService(ServiceType.KEYSTORE_SERVICE)).andReturn(keystoreService).once();
    expect(gatewayServices.getService(ServiceType.ALIAS_SERVICE)).andReturn(aliasService).once();

    FilterConfig filterConfig = createMock(FilterConfig.class);
    expect(filterConfig.getInitParameter(PARAMETER_USE_TWO_WAY_SSL)).andReturn("true").once();

    replay(keystoreService, aliasService, gatewayServices, filterConfig);

    DefaultHttpClientFactory factory = new DefaultHttpClientFactory();
    SSLContext context = factory.createSSLContext(gatewayServices, filterConfig, "service");
    assertNotNull(context);

    verify(keystoreService, aliasService, gatewayServices, filterConfig);
  }

  @Test
  public void testCreateSSLContextOneWaySslNoCustomTrustStore() throws Exception {
    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getTruststoreForHttpClient()).andReturn(null).once();

    GatewayServices gatewayServices = createMock(GatewayServices.class);
    expect(gatewayServices.getService(ServiceType.KEYSTORE_SERVICE)).andReturn(keystoreService).once();

    FilterConfig filterConfig = createMock(FilterConfig.class);
    expect(filterConfig.getInitParameter(PARAMETER_USE_TWO_WAY_SSL)).andReturn("false").once();

    replay(keystoreService, gatewayServices, filterConfig);

    DefaultHttpClientFactory factory = new DefaultHttpClientFactory();
    SSLContext context = factory.createSSLContext(gatewayServices, filterConfig, "service");
    assertNull(context);

    verify(keystoreService, gatewayServices, filterConfig);
  }

  @Test
  public void testCreateSSLContextOneWaySslWithCustomTrustStore() throws Exception {
    KeyStore trustStore = loadKeyStore("target/test-classes/keystores/server-truststore.jks", "horton", "JKS");

    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getTruststoreForHttpClient()).andReturn(trustStore).once();

    GatewayServices gatewayServices = createMock(GatewayServices.class);
    expect(gatewayServices.getService(ServiceType.KEYSTORE_SERVICE)).andReturn(keystoreService).once();

    FilterConfig filterConfig = createMock(FilterConfig.class);
    expect(filterConfig.getInitParameter(PARAMETER_USE_TWO_WAY_SSL)).andReturn("false").once();

    replay(keystoreService, gatewayServices, filterConfig);

    DefaultHttpClientFactory factory = new DefaultHttpClientFactory();
    SSLContext context = factory.createSSLContext(gatewayServices, filterConfig, "service");
    assertNotNull(context);

    verify(keystoreService, gatewayServices, filterConfig);
  }

  @Test
  public void testHttpClientPathNormalization() {
    GatewayConfig gatewayConfig = createMock(GatewayConfig.class);
    expect(gatewayConfig.getHttpClientConnectionTimeout()).andReturn(20000).once();
    expect(gatewayConfig.getHttpClientSocketTimeout()).andReturn(20000).once();

    ServletContext servletContext = createMock(ServletContext.class);
    expect(servletContext.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(gatewayConfig).atLeastOnce();

    FilterConfig filterConfig = createMock(FilterConfig.class);
    expect(filterConfig.getServletContext()).andReturn(servletContext).atLeastOnce();
    expect(filterConfig.getInitParameter("httpclient.connectionTimeout")).andReturn(null).once();
    expect(filterConfig.getInitParameter("httpclient.socketTimeout")).andReturn(null).once();

    replay(gatewayConfig, servletContext, filterConfig);

    RequestConfig requestConfig = DefaultHttpClientFactory.getRequestConfig(filterConfig, "service");

    assertTrue(requestConfig.isNormalizeUri());

    verify(gatewayConfig, servletContext, filterConfig);
  }

  private KeyStore loadKeyStore(String keyStoreFile, String password, String storeType)
      throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException {
    KeyStore keyStore = KeyStore.getInstance(storeType);
    try (InputStream input = Files.newInputStream(Paths.get(keyStoreFile))) {
      keyStore.load(input, password.toCharArray());
    }
    return keyStore;
  }

  @Test
  public void testHttpRetriesValues() throws Exception {
    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getTruststoreForHttpClient()).andReturn(null).anyTimes();

    GatewayConfig gatewayConfig = createMock(GatewayConfig.class);
    expect(gatewayConfig.isMetricsEnabled()).andReturn(false).anyTimes();
    expect(gatewayConfig.getHttpClientMaxConnections()).andReturn(32).anyTimes();
    expect(gatewayConfig.getHttpClientConnectionTimeout()).andReturn(20000).anyTimes();
    expect(gatewayConfig.getHttpClientSocketTimeout()).andReturn(20000).anyTimes();

    GatewayServices gatewayServices = createMock(GatewayServices.class);
    expect(gatewayServices.getService(ServiceType.KEYSTORE_SERVICE)).andReturn(keystoreService).anyTimes();

    ServletContext servletContext = createMock(ServletContext.class);
    expect(servletContext.getAttribute(GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE)).andReturn(gatewayConfig).atLeastOnce();
    expect(servletContext.getAttribute(GatewayServices.GATEWAY_SERVICES_ATTRIBUTE)).andReturn(gatewayServices).atLeastOnce();

    FilterConfig filterConfigSafe = createMock(FilterConfig.class);
    expect(filterConfigSafe.getServletContext()).andReturn(servletContext).atLeastOnce();
    expect(filterConfigSafe.getInitParameter("useTwoWaySsl")).andReturn("false").once();
    expect(filterConfigSafe.getInitParameter("httpclient.maxConnections")).andReturn(null).once();
    expect(filterConfigSafe.getInitParameter("httpclient.connectionTimeout")).andReturn(null).once();
    expect(filterConfigSafe.getInitParameter("httpclient.socketTimeout")).andReturn(null).once();
    expect(filterConfigSafe.getInitParameter("serviceRole")).andReturn(null).once();
    expect(filterConfigSafe.getInitParameter("retryCount")).andReturn("3").anyTimes();
    expect(filterConfigSafe.getInitParameter("retryNonSafeRequest")).andReturn(null).anyTimes();

    FilterConfig filterConfigUnSafe = createMock(FilterConfig.class);
    expect(filterConfigUnSafe.getServletContext()).andReturn(servletContext).atLeastOnce();
    expect(filterConfigUnSafe.getInitParameter("useTwoWaySsl")).andReturn("false").once();
    expect(filterConfigUnSafe.getInitParameter("httpclient.maxConnections")).andReturn(null).once();
    expect(filterConfigUnSafe.getInitParameter("httpclient.connectionTimeout")).andReturn(null).once();
    expect(filterConfigUnSafe.getInitParameter("httpclient.socketTimeout")).andReturn(null).once();
    expect(filterConfigUnSafe.getInitParameter("serviceRole")).andReturn(null).once();
    expect(filterConfigUnSafe.getInitParameter("retryCount")).andReturn("3").anyTimes();
    expect(filterConfigUnSafe.getInitParameter("retryNonSafeRequest")).andReturn("true").anyTimes();

    replay(keystoreService, gatewayConfig, gatewayServices, servletContext, filterConfigSafe, filterConfigUnSafe);

    DefaultHttpClientFactory factory = new DefaultHttpClientFactory();
    HttpClient clientSafe = factory.createHttpClient(filterConfigSafe);
    assertNotNull(clientSafe);

    HttpClient clientUnSafe = factory.createHttpClient(filterConfigUnSafe);
    assertNotNull(clientUnSafe);

    verify(keystoreService, gatewayConfig, gatewayServices, servletContext, filterConfigSafe, filterConfigUnSafe);
  }
}
