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
 *
 *
 */

package org.apache.knox.gateway.dispatch;

import static org.apache.knox.gateway.dispatch.DefaultHttpClientFactory.PARAMETER_USE_TWO_WAY_SSL;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.apache.http.client.HttpClient;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
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
    SSLContext context = factory.createSSLContext(gatewayServices, filterConfig);
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
    SSLContext context = factory.createSSLContext(gatewayServices, filterConfig);
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
    SSLContext context = factory.createSSLContext(gatewayServices, filterConfig);
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
    SSLContext context = factory.createSSLContext(gatewayServices, filterConfig);
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
    SSLContext context = factory.createSSLContext(gatewayServices, filterConfig);
    assertNotNull(context);

    verify(keystoreService, gatewayServices, filterConfig);
  }

  private KeyStore loadKeyStore(String keyStoreFile, String password, String storeType)
      throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException {
    KeyStore keyStore = KeyStore.getInstance(storeType);
    try (InputStream input = Files.newInputStream(Paths.get(keyStoreFile))) {
      keyStore.load(input, password.toCharArray());
    }
    return keyStore;
  }
}