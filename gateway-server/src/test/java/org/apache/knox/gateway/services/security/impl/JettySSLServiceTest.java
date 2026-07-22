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

package org.apache.knox.gateway.services.security.impl;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.Test;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class JettySSLServiceTest {
  @Test
  public void TestBuildSslContextFactoryOnlyIdentityKeystore() throws Exception {
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    Path identityKeystorePath = Paths.get(basedir, "target", "test-classes", "keystores", "server-keystore.jks");
    String identityKeystoreType = "jks";
    char[] identityKeystorePassword = "horton".toCharArray();
    char[] identityKeyPassphrase = "horton".toCharArray();
    String identityKeyAlias = "server";
    Path truststorePath = Paths.get(basedir, "target", "test-classes", "keystores", "server-truststore.jks");
    String truststoreType = "jks";
    String truststorePasswordAlias = "trust_store_password";

    GatewayConfig config = createGatewayConfig(false, false, identityKeystorePath, identityKeystoreType, identityKeyAlias, truststorePath, truststoreType, truststorePasswordAlias);

    AliasService aliasService = createMock(AliasService.class);
    expect(aliasService.getGatewayIdentityKeystorePassword()).andReturn(identityKeystorePassword).atLeastOnce();
    expect(aliasService.getGatewayIdentityPassphrase()).andReturn(identityKeyPassphrase).atLeastOnce();

    KeystoreService keystoreService = createMock(KeystoreService.class);

    replay(config, aliasService, keystoreService);

    JettySSLService sslService = new JettySSLService();
    sslService.setAliasService(aliasService);
    sslService.setKeystoreService(keystoreService);

    Object result = sslService.buildSslContextFactory(config);
    assertNotNull(result);
    assertTrue(result instanceof SslContextFactory);

    SslContextFactory sslContextFactory = (SslContextFactory) result;
    sslContextFactory.start();

    assertEquals(identityKeystorePath.toUri().toString(), sslContextFactory.getKeyStorePath());
    assertEquals(identityKeystoreType, sslContextFactory.getKeyStoreType());
    assertNotNull(sslContextFactory.getKeyStore());

    assertNull(sslContextFactory.getTrustStorePath());
    assertNull(sslContextFactory.getTrustStoreType());

    // If the truststore is not set, by default the identity keystore is used by Jetty.
    assertEquals(sslContextFactory.getKeyStore().size(), sslContextFactory.getTrustStore().size());
    assertTrue(sslContextFactory.getTrustStore().containsAlias(identityKeyAlias));

    verify(config, aliasService, keystoreService);
  }

  @Test(expected = AliasServiceException.class)
  public void TestBuildSslContextFactoryOnlyIdentityKeystoreErrorGettingPassword() throws Exception {
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    Path identityKeystorePath = Paths.get(basedir, "target", "test-classes", "keystores", "server-keystore.jks");
    String identityKeystoreType = "jks";
    char[] identityKeyPassphrase = "horton".toCharArray();
    String identityKeyAlias = "server";
    Path truststorePath = Paths.get(basedir, "target", "test-classes", "keystores", "server-truststore.jks");
    String truststoreType = "jks";
    String truststorePasswordAlias = "trust_store_password";

    GatewayConfig config = createGatewayConfig(false, false, identityKeystorePath, identityKeystoreType, identityKeyAlias, truststorePath, truststoreType, truststorePasswordAlias);

    AliasService aliasService = createMock(AliasService.class);
    expect(aliasService.getGatewayIdentityKeystorePassword()).andThrow(new AliasServiceException(null)).atLeastOnce();
    expect(aliasService.getGatewayIdentityPassphrase()).andReturn(identityKeyPassphrase).atLeastOnce();

    KeystoreService keystoreService = createMock(KeystoreService.class);

    replay(config, aliasService, keystoreService);

    JettySSLService sslService = new JettySSLService();
    sslService.setAliasService(aliasService);
    sslService.setKeystoreService(keystoreService);

    sslService.buildSslContextFactory(config);

    fail("AliasServiceException should have been thrown");
  }

  @Test
  public void TestBuildSslContextFactoryOnlyIdentityKeystoreNullKeystorePassword() throws Exception {
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    Path identityKeystorePath = Paths.get(basedir, "target", "test-classes", "keystores", "server-keystore.jks");
    String identityKeystoreType = "jks";
    char[] identityKeyPassphrase = "horton".toCharArray();
    String identityKeyAlias = "server";
    Path truststorePath = Paths.get(basedir, "target", "test-classes", "keystores", "server-truststore.jks");
    String truststoreType = "jks";
    String truststorePasswordAlias = "trust_store_password";

    GatewayConfig config = createGatewayConfig(false, false, identityKeystorePath, identityKeystoreType, identityKeyAlias, truststorePath, truststoreType, truststorePasswordAlias);

    AliasService aliasService = createMock(AliasService.class);
    expect(aliasService.getGatewayIdentityKeystorePassword()).andReturn(null).atLeastOnce();
    expect(aliasService.getGatewayIdentityPassphrase()).andReturn(identityKeyPassphrase).atLeastOnce();

    KeystoreService keystoreService = createMock(KeystoreService.class);

    replay(config, aliasService, keystoreService);

    JettySSLService sslService = new JettySSLService();
    sslService.setAliasService(aliasService);
    sslService.setKeystoreService(keystoreService);

    Object result = sslService.buildSslContextFactory(config);
    assertNotNull(result);
    assertTrue(result instanceof SslContextFactory);

    SslContextFactory sslContextFactory = (SslContextFactory) result;
    sslContextFactory.start();

    assertEquals(identityKeystorePath.toUri().toString(), sslContextFactory.getKeyStorePath());
    assertEquals(identityKeystoreType, sslContextFactory.getKeyStoreType());
    assertNotNull(sslContextFactory.getKeyStore());

    assertNull(sslContextFactory.getTrustStorePath());
    assertNull(sslContextFactory.getTrustStoreType());

    // If the truststore is not set, by default the identity keystore is used by Jetty.
    assertEquals(sslContextFactory.getKeyStore().size(), sslContextFactory.getTrustStore().size());
    assertTrue(sslContextFactory.getTrustStore().containsAlias(identityKeyAlias));

    verify(config, aliasService, keystoreService);

    // Note: The key password is used if the keystore password is not set; and vice versa
  }

  @Test(expected = UnrecoverableKeyException.class)
  public void TestBuildSslContextFactoryOnlyIdentityKeystoreNullKeyPassword() throws Exception {
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    Path identityKeystorePath = Paths.get(basedir, "target", "test-classes", "keystores", "server-keystore.jks");
    String identityKeystoreType = "jks";
    String identityKeyAlias = "server";
    Path truststorePath = Paths.get(basedir, "target", "test-classes", "keystores", "server-truststore.jks");
    String truststoreType = "jks";
    String truststorePasswordAlias = "trust_store_password";

    GatewayConfig config = createGatewayConfig(false, false, identityKeystorePath, identityKeystoreType, identityKeyAlias, truststorePath, truststoreType, truststorePasswordAlias);

    AliasService aliasService = createMock(AliasService.class);
    expect(aliasService.getGatewayIdentityKeystorePassword()).andReturn(null).atLeastOnce();
    expect(aliasService.getGatewayIdentityPassphrase()).andReturn(null).atLeastOnce();

    KeystoreService keystoreService = createMock(KeystoreService.class);

    replay(config, aliasService, keystoreService);

    JettySSLService sslService = new JettySSLService();
    sslService.setAliasService(aliasService);
    sslService.setKeystoreService(keystoreService);

    Object result = sslService.buildSslContextFactory(config);
    assertNotNull(result);
    assertTrue(result instanceof SslContextFactory);

    SslContextFactory sslContextFactory = (SslContextFactory) result;
    sslContextFactory.start();

    fail("UnrecoverableKeyException should have been thrown");
  }

  @Test
  public void TestBuildSslContextFactoryImplicitTrustStore() throws Exception {
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    Path identityKeystorePath = Paths.get(basedir, "target", "test-classes", "keystores", "server-keystore.jks");
    String identityKeystoreType = "jks";
    char[] identityKeystorePassword = "horton".toCharArray();
    char[] identityKeyPassphrase = "horton".toCharArray();
    String identityKeyAlias = "server";
    Path truststorePath = Paths.get(basedir, "target", "test-classes", "keystores", "server-truststore.jks");
    String truststoreType = "jks";
    String truststorePasswordAlias = "trust_store_password";

    GatewayConfig config = createGatewayConfig(true, false, identityKeystorePath, identityKeystoreType, identityKeyAlias, truststorePath, truststoreType, truststorePasswordAlias);

    AliasService aliasService = createMock(AliasService.class);
    expect(aliasService.getGatewayIdentityKeystorePassword()).andReturn(identityKeystorePassword).atLeastOnce();
    expect(aliasService.getGatewayIdentityPassphrase()).andReturn(identityKeyPassphrase).atLeastOnce();

    KeystoreService keystoreService = createMock(KeystoreService.class);

    replay(config, aliasService, keystoreService);

    JettySSLService sslService = new JettySSLService();
    sslService.setAliasService(aliasService);
    sslService.setKeystoreService(keystoreService);

    Object result = sslService.buildSslContextFactory(config);
    assertNotNull(result);
    assertTrue(result instanceof SslContextFactory);

    SslContextFactory sslContextFactory = (SslContextFactory) result;
    sslContextFactory.start();

    assertEquals(identityKeystorePath.toUri().toString(), sslContextFactory.getKeyStorePath());
    assertEquals(identityKeystoreType, sslContextFactory.getKeyStoreType());
    assertNotNull(sslContextFactory.getKeyStore());

    assertEquals(identityKeystorePath.toUri().toString(), sslContextFactory.getTrustStorePath());
    assertEquals(identityKeystoreType, sslContextFactory.getTrustStoreType());

    // The truststore is expected to be the same as the identity keystore
    assertEquals(sslContextFactory.getKeyStore().size(), sslContextFactory.getTrustStore().size());
    assertTrue(sslContextFactory.getTrustStore().containsAlias(identityKeyAlias));

    verify(config, aliasService, keystoreService);
  }

  @Test
  public void TestBuildSslContextFactoryExplicitTrustStore() throws Exception {
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    Path identityKeystorePath = Paths.get(basedir, "target", "test-classes", "keystores", "server-keystore.jks");
    String identityKeystoreType = "jks";
    char[] identityKeystorePassword = "horton".toCharArray();
    char[] identityKeyPassphrase = "horton".toCharArray();
    String identityKeyAlias = "server";
    Path truststorePath = Paths.get(basedir, "target", "test-classes", "keystores", "server-truststore.jks");
    String truststoreType = "jks";
    char[] truststorePassword = "horton".toCharArray();
    String truststorePasswordAlias = "trust_store_password";

    GatewayConfig config = createGatewayConfig(true, true, identityKeystorePath, identityKeystoreType, identityKeyAlias, truststorePath, truststoreType, truststorePasswordAlias);

    AliasService aliasService = createMock(AliasService.class);
    expect(aliasService.getGatewayIdentityKeystorePassword()).andReturn(identityKeystorePassword).atLeastOnce();
    expect(aliasService.getGatewayIdentityPassphrase()).andReturn(identityKeyPassphrase).atLeastOnce();
    expect(aliasService.getPasswordFromAliasForGateway(eq(truststorePasswordAlias))).andReturn(truststorePassword).atLeastOnce();

    KeystoreService keystoreService = createMock(KeystoreService.class);

    replay(config, aliasService, keystoreService);

    JettySSLService sslService = new JettySSLService();
    sslService.setAliasService(aliasService);
    sslService.setKeystoreService(keystoreService);

    Object result = sslService.buildSslContextFactory(config);
    assertNotNull(result);
    assertTrue(result instanceof SslContextFactory);

    SslContextFactory sslContextFactory = (SslContextFactory) result;
    sslContextFactory.start();

    assertEquals(identityKeystorePath.toUri().toString(), sslContextFactory.getKeyStorePath());
    assertEquals(identityKeystoreType, sslContextFactory.getKeyStoreType());
    assertNotNull(sslContextFactory.getKeyStore());

    assertEquals(truststorePath.toUri().toString(), sslContextFactory.getTrustStorePath());
    assertEquals(truststoreType, sslContextFactory.getTrustStoreType());
    assertNotNull(sslContextFactory.getTrustStore());

    // The truststore is expected to be different than the identity keystore
    assertTrue(sslContextFactory.getKeyStore().containsAlias(identityKeyAlias));
    assertFalse(sslContextFactory.getTrustStore().containsAlias(identityKeyAlias));

    verify(config, aliasService, keystoreService);
  }

  @Test(expected = AliasServiceException.class)
  public void TestBuildSslContextFactoryExplicitTrustStoreErrorGettingPassword() throws Exception {
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    Path identityKeystorePath = Paths.get(basedir, "target", "test-classes", "keystores", "server-keystore.jks");
    String identityKeystoreType = "jks";
    char[] identityKeystorePassword = "horton".toCharArray();
    char[] identityKeyPassphrase = "horton".toCharArray();
    String identityKeyAlias = "server";
    Path truststorePath = Paths.get(basedir, "target", "test-classes", "keystores", "server-truststore.jks");
    String truststoreType = "jks";
    String truststorePasswordAlias = "trust_store_password";

    GatewayConfig config = createGatewayConfig(true, true, identityKeystorePath, identityKeystoreType, identityKeyAlias, truststorePath, truststoreType, truststorePasswordAlias);

    AliasService aliasService = createMock(AliasService.class);
    expect(aliasService.getGatewayIdentityKeystorePassword()).andReturn(identityKeystorePassword).atLeastOnce();
    expect(aliasService.getGatewayIdentityPassphrase()).andReturn(identityKeyPassphrase).atLeastOnce();
    expect(aliasService.getPasswordFromAliasForGateway(eq(truststorePasswordAlias))).andThrow(new AliasServiceException(null)).atLeastOnce();

    KeystoreService keystoreService = createMock(KeystoreService.class);

    replay(config, aliasService, keystoreService);

    JettySSLService sslService = new JettySSLService();
    sslService.setAliasService(aliasService);
    sslService.setKeystoreService(keystoreService);

    sslService.buildSslContextFactory(config);

    fail("AliasServiceException should have been thrown");
  }

  @Test
  public void TestBuildSslContextFactoryExplicitTrustStoreNullPassword() throws Exception {
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    Path identityKeystorePath = Paths.get(basedir, "target", "test-classes", "keystores", "server-keystore.jks");
    String identityKeystoreType = "jks";
    char[] identityKeystorePassword = "horton".toCharArray();
    char[] identityKeyPassphrase = "horton".toCharArray();
    String identityKeyAlias = "server";
    Path truststorePath = Paths.get(basedir, "target", "test-classes", "keystores", "server-truststore.jks");
    String truststoreType = "jks";
    String truststorePasswordAlias = "trust_store_password";

    GatewayConfig config = createGatewayConfig(true, true, identityKeystorePath, identityKeystoreType, identityKeyAlias, truststorePath, truststoreType, truststorePasswordAlias);

    AliasService aliasService = createMock(AliasService.class);
    expect(aliasService.getGatewayIdentityKeystorePassword()).andReturn(identityKeystorePassword).atLeastOnce();
    expect(aliasService.getGatewayIdentityPassphrase()).andReturn(identityKeyPassphrase).atLeastOnce();
    expect(aliasService.getPasswordFromAliasForGateway(eq(truststorePasswordAlias))).andReturn(null).atLeastOnce();

    KeystoreService keystoreService = createMock(KeystoreService.class);

    replay(config, aliasService, keystoreService);

    JettySSLService sslService = new JettySSLService();
    sslService.setAliasService(aliasService);
    sslService.setKeystoreService(keystoreService);

    Object result = sslService.buildSslContextFactory(config);
    assertNotNull(result);
    assertTrue(result instanceof SslContextFactory);

    SslContextFactory sslContextFactory = (SslContextFactory) result;
    sslContextFactory.start();

    assertEquals(identityKeystorePath.toUri().toString(), sslContextFactory.getKeyStorePath());
    assertEquals(identityKeystoreType, sslContextFactory.getKeyStoreType());
    assertNotNull(sslContextFactory.getKeyStore());

    assertEquals(truststorePath.toUri().toString(), sslContextFactory.getTrustStorePath());
    assertEquals(truststoreType, sslContextFactory.getTrustStoreType());
    assertNotNull(sslContextFactory.getTrustStore());

    // The truststore is expected to be different than the identity keystore
    assertTrue(sslContextFactory.getKeyStore().containsAlias(identityKeyAlias));
    assertFalse(sslContextFactory.getTrustStore().containsAlias(identityKeyAlias));

    verify(config, aliasService, keystoreService);

    // Note: The keystore password is used if the truststore password is not set
  }

  @Test(expected = UnrecoverableKeyException.class)
  public void TestBuildSslContextFactoryExplicitTrustStoreNullPasswords() throws Exception {
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    Path identityKeystorePath = Paths.get(basedir, "target", "test-classes", "keystores", "server-keystore.jks");
    String identityKeystoreType = "jks";
    String identityKeyAlias = "server";
    Path truststorePath = Paths.get(basedir, "target", "test-classes", "keystores", "server-truststore.jks");
    String truststoreType = "jks";
    String truststorePasswordAlias = "trust_store_password";

    GatewayConfig config = createGatewayConfig(true, true, identityKeystorePath, identityKeystoreType, identityKeyAlias, truststorePath, truststoreType, truststorePasswordAlias);

    AliasService aliasService = createMock(AliasService.class);
    expect(aliasService.getGatewayIdentityKeystorePassword()).andReturn(null).atLeastOnce();
    expect(aliasService.getGatewayIdentityPassphrase()).andReturn(null).atLeastOnce();
    expect(aliasService.getPasswordFromAliasForGateway(eq(truststorePasswordAlias))).andReturn(null).atLeastOnce();

    KeystoreService keystoreService = createMock(KeystoreService.class);

    replay(config, aliasService, keystoreService);

    JettySSLService sslService = new JettySSLService();
    sslService.setAliasService(aliasService);
    sslService.setKeystoreService(keystoreService);

    Object result = sslService.buildSslContextFactory(config);
    assertNotNull(result);
    assertTrue(result instanceof SslContextFactory);

    SslContextFactory sslContextFactory = (SslContextFactory) result;
    sslContextFactory.start();

    fail("UnrecoverableKeyException should have been thrown");
  }

  @Test
  public void testExcludeTopologyFromClientAuth() {
    SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
    sslContextFactory.setNeedClientAuth(true);
    GatewayConfig config = createGatewayConfigForExcludeTopologyTest(true, true, "health");
    replay(config);

    JettySSLService sslService = new JettySSLService();
    sslService.excludeTopologyFromClientAuth(sslContextFactory, config,"health");

    verify(config);
    assertFalse(sslContextFactory.getNeedClientAuth());
    assertTrue(sslContextFactory.getWantClientAuth());
  }

  @Test
  public void testExcludeTopologyFromClientAuthNoExclude() {
    SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
    sslContextFactory.setNeedClientAuth(true);
    GatewayConfig config = createGatewayConfigForExcludeTopologyTest(true, false, "health");
    replay(config);

    JettySSLService sslService = new JettySSLService();
    sslService.excludeTopologyFromClientAuth(sslContextFactory, config,"health");

    verify(config);
    assertTrue(sslContextFactory.getNeedClientAuth());
    assertFalse(sslContextFactory.getWantClientAuth());
  }

  @Test
  public void testExcludeTopologyFromClientAuthNoPolicy() {
    SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
    sslContextFactory.setNeedClientAuth(false);
    GatewayConfig config = createGatewayConfigForExcludeTopologyTest(false, true, "health");
    replay(config);

    JettySSLService sslService = new JettySSLService();
    sslService.excludeTopologyFromClientAuth(sslContextFactory, config,"health");

    verify(config);
    assertFalse(sslContextFactory.getNeedClientAuth());
    assertFalse(sslContextFactory.getWantClientAuth());
  }

  @Test
  public void testBuildSslContextFactoryCiphersAndProtocols() throws Exception {
    String basedir = System.getProperty("basedir");
    if (basedir == null) {
      basedir = new File(".").getCanonicalPath();
    }

    Path identityKeystorePath = Paths.get(basedir, "target", "test-classes", "keystores", "server-keystore.jks");
    String identityKeystoreType = "jks";
    char[] identityKeystorePassword = "horton".toCharArray();
    char[] identityKeyPassphrase = "horton".toCharArray();
    String identityKeyAlias = "server";
    Path truststorePath = Paths.get(basedir, "target", "test-classes", "keystores", "server-truststore.jks");
    String truststoreType = "jks";
    String truststorePasswordAlias = "trust_store_password";

    List<String> includedCiphers = Arrays.asList("SSL_RSA_WITH_RC4_128_MD5", "SSL_RSA_WITH_RC4_128_SHA");
    List<String> excludedCiphers = List.of("SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA");
    List<String> excludedProtocols = List.of("SSLv3");
    Set<String> includedProtocols = new HashSet<>(Arrays.asList("TLSv1.2", "TLSv1.3"));

    GatewayConfig config = createGatewayConfig(false, false, identityKeystorePath, identityKeystoreType, identityKeyAlias, truststorePath, truststoreType, truststorePasswordAlias,
            includedCiphers, excludedCiphers, includedProtocols, excludedProtocols);

    AliasService aliasService = createMock(AliasService.class);
    expect(aliasService.getGatewayIdentityKeystorePassword()).andReturn(identityKeystorePassword).atLeastOnce();
    expect(aliasService.getGatewayIdentityPassphrase()).andReturn(identityKeyPassphrase).atLeastOnce();

    KeystoreService keystoreService = createMock(KeystoreService.class);

    replay(config, aliasService, keystoreService);

    JettySSLService sslService = new JettySSLService();
    sslService.setAliasService(aliasService);
    sslService.setKeystoreService(keystoreService);

    SslContextFactory sslContextFactory = (SslContextFactory) sslService.buildSslContextFactory(config);
    assertArrayEquals(excludedCiphers.toArray(), sslContextFactory.getExcludeCipherSuites());
    assertArrayEquals(includedCiphers.toArray(), sslContextFactory.getIncludeCipherSuites());
    assertArrayEquals(excludedProtocols.toArray(), sslContextFactory.getExcludeProtocols());
    assertArrayEquals(includedProtocols.toArray(), sslContextFactory.getIncludeProtocols());


    verify(config, aliasService, keystoreService);
  }

  private GatewayConfig createGatewayConfig(boolean isClientAuthNeeded, boolean isExplicitTruststore,
                                            Path identityKeystorePath, String identityKeystoreType,
                                            String identityKeyAlias, Path truststorePath,
                                            String truststoreType, String trustStorePasswordAlias) {
    return createGatewayConfig(isClientAuthNeeded, isExplicitTruststore, identityKeystorePath, identityKeystoreType, identityKeyAlias, truststorePath, truststoreType, trustStorePasswordAlias, null, null, null, null);
  }

  private GatewayConfig createGatewayConfig(boolean isClientAuthNeeded, boolean isExplicitTruststore,
                                            Path identityKeystorePath, String identityKeystoreType,
                                            String identityKeyAlias, Path truststorePath,
                                            String truststoreType, String trustStorePasswordAlias,
                                            List<String> includedCiphers, List<String> excludedCiphers,
                                            Set<String> includedProtocols, List<String> excludedProtocols) {
    GatewayConfig config = createMock(GatewayConfig.class);
    expect(config.getIdentityKeystorePath()).andReturn(identityKeystorePath.toString()).atLeastOnce();
    expect(config.getIdentityKeystoreType()).andReturn(identityKeystoreType).atLeastOnce();
    expect(config.getIdentityKeyAlias()).andReturn(identityKeyAlias).atLeastOnce();

    if (isClientAuthNeeded) {
      expect(config.isClientAuthNeeded()).andReturn(true).atLeastOnce();

      if (isExplicitTruststore) {
        expect(config.getTruststorePath()).andReturn(truststorePath.toString()).atLeastOnce();
        expect(config.getTruststoreType()).andReturn(truststoreType).atLeastOnce();
        expect(config.getTruststorePasswordAlias()).andReturn(trustStorePasswordAlias).atLeastOnce();
      } else {
        expect(config.getTruststorePath()).andReturn(null).atLeastOnce();
      }
    } else {
      expect(config.isClientAuthNeeded()).andReturn(false).atLeastOnce();
    }

    expect(config.isClientAuthWanted()).andReturn(false).atLeastOnce();
    expect(config.getTrustAllCerts()).andReturn(false).atLeastOnce();
    expect(config.getIncludedSSLCiphers()).andReturn(includedCiphers).atLeastOnce();
    expect(config.getExcludedSSLCiphers()).andReturn(excludedCiphers).atLeastOnce();
    expect(config.getIncludedSSLProtocols()).andReturn(includedProtocols).atLeastOnce();
    expect(config.getExcludedSSLProtocols()).andReturn(excludedProtocols).atLeastOnce();
    expect(config.isSSLRenegotiationAllowed()).andReturn(true).atLeastOnce();
    return config;
  }

  private GatewayConfig createGatewayConfigForExcludeTopologyTest(boolean isClientAuthNeeded, boolean isTopologyExcluded, String topologyName) {
    GatewayConfig config = createMock(GatewayConfig.class);
    expect(config.isClientAuthNeeded()).andReturn(isClientAuthNeeded).anyTimes();
    expect(config.isTopologyExcludedFromClientAuth(topologyName)).andReturn(isTopologyExcluded).anyTimes();
    return config;
  }

  private GatewayConfig singleEkuConfig(String clientKeystorePath, String clientKeyAlias,
                                        String truststorePath, boolean clientAuthNeeded,
                                        boolean twoWaySsl, String httpClientTruststorePath) {
    GatewayConfig config = createMock(GatewayConfig.class);
    expect(config.isSingleEkuEnabled()).andReturn(true).anyTimes();
    expect(config.getIdentityKeyAlias()).andReturn("server").anyTimes();
    expect(config.isClientAuthNeeded()).andReturn(clientAuthNeeded).anyTimes();
    expect(config.isClientAuthWanted()).andReturn(false).anyTimes();
    expect(config.getTruststorePath()).andReturn(truststorePath).anyTimes();
    expect(config.isHttpClientTwoWaySslEnabled()).andReturn(twoWaySsl).anyTimes();
    expect(config.getHttpClientKeystorePath()).andReturn(clientKeystorePath).anyTimes();
    expect(config.getHttpClientKeyAlias()).andReturn(clientKeyAlias).anyTimes();
    expect(config.getHttpClientTruststorePath()).andReturn(httpClientTruststorePath).anyTimes();
    return config;
  }

  // Build an in-memory JKS keystore holding a self-signed cert (key entry) at the given alias with the
  // supplied Extended Key Usages. Pass no purposes to produce a cert with NO EKU extension.
  private KeyStore keystoreWithEku(String alias, KeyPurposeId... purposes) throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair keyPair = kpg.generateKeyPair();

    X500Name dn = new X500Name("CN=" + alias + ",OU=Test,O=Knox,C=US");
    Date from = new Date();
    Date to = new Date(from.getTime() + 86_400_000L);
    BigInteger sn = BigInteger.valueOf(from.getTime());

    JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
        dn, sn, from, to, dn, keyPair.getPublic());
    if (purposes != null && purposes.length > 0) {
      builder.addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(purposes));
    }
    ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
    X509Certificate cert = new JcaX509CertificateConverter().getCertificate(builder.build(signer));

    KeyStore ks = KeyStore.getInstance("JKS");
    ks.load(null, null);
    ks.setKeyEntry(alias, keyPair.getPrivate(), "horton".toCharArray(),
        new java.security.cert.Certificate[]{cert});
    return ks;
  }

  // Convenience: a valid serverAuth-only server identity keystore (alias "server").
  private KeyStore serverAuthKeystore() throws Exception {
    return keystoreWithEku("server", KeyPurposeId.id_kp_serverAuth);
  }

  private JettySSLService sslServiceWith(KeystoreService keystoreService) {
    JettySSLService sslService = new JettySSLService();
    sslService.setKeystoreService(keystoreService);
    return sslService;
  }

  // ---- ALWAYS: server identity must be serverAuth-only ----

  @Test
  public void testSingleEkuValidationPassesWhenBothMtlsOff() throws Exception {
    // single-EKU on, inbound OFF, outbound OFF -> only the serverAuth-only server identity is validated.
    GatewayConfig config = singleEkuConfig(null, "server", null, false, false, null);
    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getKeystoreForGateway()).andReturn(serverAuthKeystore()).anyTimes();
    replay(config, keystoreService);

    sslServiceWith(keystoreService).validateSingleEkuConfig(config); // must not throw
    verify(config, keystoreService);
  }

  @Test
  public void testSingleEkuValidationFailsWhenServerCertNotServerAuth() throws Exception {
    // Server identity is clientAuth-only -> cannot serve TLS under single-EKU.
    GatewayConfig config = singleEkuConfig(null, "server", null, false, false, null);
    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getKeystoreForGateway())
        .andReturn(keystoreWithEku("server", KeyPurposeId.id_kp_clientAuth)).anyTimes();
    replay(config, keystoreService);
    try {
      sslServiceWith(keystoreService).validateSingleEkuConfig(config);
      fail("Expected ServiceLifecycleException when server cert lacks serverAuth EKU");
    } catch (ServiceLifecycleException e) {
      assertTrue(e.getMessage().contains("serverAuth"));
      assertTrue(e.getMessage().contains("server identity keystore"));
    }
    verify(config, keystoreService);
  }

  @Test
  public void testSingleEkuValidationFailsWhenServerCertHasNoEku() throws Exception {
    GatewayConfig config = singleEkuConfig(null, "server", null, false, false, null);
    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getKeystoreForGateway())
        .andReturn(keystoreWithEku("server")).anyTimes();
    replay(config, keystoreService);
    try {
      sslServiceWith(keystoreService).validateSingleEkuConfig(config);
      fail("Expected ServiceLifecycleException when server cert has no EKU");
    } catch (ServiceLifecycleException e) {
      assertTrue(e.getMessage().contains("serverAuth"));
    }
    verify(config, keystoreService);
  }

  // ---- INBOUND mTLS: server truststore required only when client-auth is on ----

  @Test
  public void testSingleEkuValidationFailsWhenInboundOnAndTruststoreMissing() throws Exception {
    GatewayConfig config = singleEkuConfig(null, "server", null, true, false, null);
    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getKeystoreForGateway()).andReturn(serverAuthKeystore()).anyTimes();
    replay(config, keystoreService);
    try {
      sslServiceWith(keystoreService).validateSingleEkuConfig(config);
      fail("Expected ServiceLifecycleException for missing server truststore with inbound client-auth");
    } catch (ServiceLifecycleException e) {
      assertTrue(e.getMessage().contains(GatewayConfig.GATEWAY_TRUSTSTORE_PATH));
    }
    verify(config, keystoreService);
  }

  @Test
  public void testSingleEkuValidationPassesWhenInboundOnAndTruststorePresent() throws Exception {
    GatewayConfig config = singleEkuConfig(null, "server", "some-truststore.jks", true, false, null);
    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getKeystoreForGateway()).andReturn(serverAuthKeystore()).anyTimes();
    replay(config, keystoreService);

    sslServiceWith(keystoreService).validateSingleEkuConfig(config); // must not throw
    verify(config, keystoreService);
  }

  // ---- OUTBOUND mTLS: client identity + httpclient truststore required only when two-way SSL is on ----

  @Test
  public void testSingleEkuValidationPassesWithGeneratedServerIdentityAndTwoWayOff() throws Exception {
    // Simulates a self-generated serverAuth identity present in the gateway keystore, mTLS off.
    GatewayConfig config = singleEkuConfig(null, "server", null, false, false, null);
    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getKeystoreForGateway()).andReturn(serverAuthKeystore()).anyTimes();
    replay(config, keystoreService);

    sslServiceWith(keystoreService).validateSingleEkuConfig(config); // must not throw
    verify(config, keystoreService);
  }

  @Test
  public void testSingleEkuValidationPassesWithGeneratedClientIdentityWhenTwoWayOn() throws Exception {
    // Outbound two-way on, no configured client keystore path: the clientAuth identity is
    // available via the (fallback) HTTP-client keystore. Outbound truststore is configured.
    GatewayConfig config = singleEkuConfig(null, "server", null, false, true, "client-trust.jks");
    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getKeystoreForGateway()).andReturn(serverAuthKeystore()).anyTimes();
    expect(keystoreService.getKeystoreForHttpClient())
        .andReturn(keystoreWithEku("server", KeyPurposeId.id_kp_clientAuth)).anyTimes();
    replay(config, keystoreService);

    sslServiceWith(keystoreService).validateSingleEkuConfig(config); // must not throw
    verify(config, keystoreService);
  }

  @Test
  public void testSingleEkuValidationFailsWhenTwoWayOnAndNoClientKeyEntry() throws Exception {
    // Known-limitation case: two-way on, keystore present but no client key entry for the alias.
    GatewayConfig config = singleEkuConfig(null, "server", null, false, true, "client-trust.jks");
    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getKeystoreForGateway()).andReturn(serverAuthKeystore()).anyTimes();
    // A keystore that has NO key entry for alias "server".
    KeyStore empty = KeyStore.getInstance("JKS");
    empty.load(null, null);
    expect(keystoreService.getKeystoreForHttpClient()).andReturn(empty).anyTimes();
    replay(config, keystoreService);
    try {
      sslServiceWith(keystoreService).validateSingleEkuConfig(config);
      fail("Expected ServiceLifecycleException when no client key entry is available");
    } catch (ServiceLifecycleException e) {
      assertTrue(e.getMessage().contains("server"));                              // names the alias
      assertTrue(e.getMessage().contains(GatewayConfig.HTTP_CLIENT_KEY_ALIAS));   // guidance
    }
    verify(config, keystoreService);
  }

  @Test
  public void testSingleEkuValidationFailsWhenOutboundOnAndClientCertNotClientAuth() throws Exception {
    GatewayConfig config = singleEkuConfig("client-keystore.jks", "server", null, false, true, "client-trust.jks");
    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getKeystoreForGateway()).andReturn(serverAuthKeystore()).anyTimes();
    expect(keystoreService.getKeystoreForHttpClient())
        .andReturn(keystoreWithEku("server", KeyPurposeId.id_kp_serverAuth)).anyTimes();
    replay(config, keystoreService);
    try {
      sslServiceWith(keystoreService).validateSingleEkuConfig(config);
      fail("Expected ServiceLifecycleException when client cert lacks clientAuth EKU");
    } catch (ServiceLifecycleException e) {
      assertTrue(e.getMessage().contains("clientAuth"));
      assertTrue(e.getMessage().contains("HTTP client keystore"));
    }
    verify(config, keystoreService);
  }

  @Test
  public void testSingleEkuValidationFailsWhenOutboundOnAndClientCertHasNoEku() throws Exception {
    GatewayConfig config = singleEkuConfig("client-keystore.jks", "server", null, false, true, "client-trust.jks");
    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getKeystoreForGateway()).andReturn(serverAuthKeystore()).anyTimes();
    expect(keystoreService.getKeystoreForHttpClient())
        .andReturn(keystoreWithEku("server")).anyTimes();
    replay(config, keystoreService);
    try {
      sslServiceWith(keystoreService).validateSingleEkuConfig(config);
      fail("Expected ServiceLifecycleException when client cert has no EKU");
    } catch (ServiceLifecycleException e) {
      assertTrue(e.getMessage().contains("clientAuth"));
    }
    verify(config, keystoreService);
  }

  @Test
  public void testSingleEkuValidationFailsWhenOutboundOnAndClientCertDualPurpose() throws Exception {
    GatewayConfig config = singleEkuConfig("client-keystore.jks", "server", null, false, true, "client-trust.jks");
    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getKeystoreForGateway()).andReturn(serverAuthKeystore()).anyTimes();
    expect(keystoreService.getKeystoreForHttpClient())
        .andReturn(keystoreWithEku("server", KeyPurposeId.id_kp_clientAuth, KeyPurposeId.id_kp_serverAuth))
        .anyTimes();
    replay(config, keystoreService);
    try {
      sslServiceWith(keystoreService).validateSingleEkuConfig(config);
      fail("Expected ServiceLifecycleException when client cert is dual-purpose");
    } catch (ServiceLifecycleException e) {
      assertTrue(e.getMessage().contains("also declares"));
      assertTrue(e.getMessage().contains("serverAuth"));
    }
    verify(config, keystoreService);
  }

  @Test
  public void testSingleEkuValidationFailsWhenOutboundOnAndAliasIsTrustedCertNotPrivateKey() throws Exception {
    // Alias exists but as a TrustedCertEntry (no private key) -> isKeyEntry(alias) is false.
    java.security.cert.Certificate cert =
        keystoreWithEku("server", KeyPurposeId.id_kp_clientAuth).getCertificate("server");
    KeyStore ksWithCertEntry = KeyStore.getInstance("JKS");
    ksWithCertEntry.load(null, null);
    ksWithCertEntry.setCertificateEntry("server", cert);

    GatewayConfig config = singleEkuConfig("client-keystore.jks", "server", null, false, true, "client-trust.jks");
    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getKeystoreForGateway()).andReturn(serverAuthKeystore()).anyTimes();
    expect(keystoreService.getKeystoreForHttpClient()).andReturn(ksWithCertEntry).anyTimes();
    replay(config, keystoreService);
    try {
      sslServiceWith(keystoreService).validateSingleEkuConfig(config);
      fail("Expected ServiceLifecycleException when alias is a TrustedCertEntry, not a PrivateKeyEntry");
    } catch (ServiceLifecycleException e) {
      assertTrue(e.getMessage().contains("key entry"));
    }
    verify(config, keystoreService);
  }

  @Test
  public void testSingleEkuValidationFailsWhenOutboundOnAndHttpClientTruststoreMissing() throws Exception {
    GatewayConfig config = singleEkuConfig("client-keystore.jks", "server", null, false, true, null);
    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getKeystoreForGateway()).andReturn(serverAuthKeystore()).anyTimes();
    expect(keystoreService.getKeystoreForHttpClient())
        .andReturn(keystoreWithEku("server", KeyPurposeId.id_kp_clientAuth)).anyTimes();
    replay(config, keystoreService);
    try {
      sslServiceWith(keystoreService).validateSingleEkuConfig(config);
      fail("Expected ServiceLifecycleException for missing HTTP client truststore with two-way SSL");
    } catch (ServiceLifecycleException e) {
      assertTrue(e.getMessage().contains(GatewayConfig.HTTP_CLIENT_TRUSTSTORE_PATH));
    }
    verify(config, keystoreService);
  }

  @Test
  public void testSingleEkuValidationPassesWhenOutboundOnAndAllPresent() throws Exception {
    GatewayConfig config = singleEkuConfig("client-keystore.jks", "server", null, false, true, "client-trust.jks");
    KeystoreService keystoreService = createMock(KeystoreService.class);
    expect(keystoreService.getKeystoreForGateway()).andReturn(serverAuthKeystore()).anyTimes();
    expect(keystoreService.getKeystoreForHttpClient())
        .andReturn(keystoreWithEku("server", KeyPurposeId.id_kp_clientAuth)).anyTimes();
    replay(config, keystoreService);

    sslServiceWith(keystoreService).validateSingleEkuConfig(config); // must not throw
    verify(config, keystoreService);
  }

}
