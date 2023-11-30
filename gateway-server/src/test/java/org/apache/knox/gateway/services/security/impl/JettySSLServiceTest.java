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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.services.security.KeystoreService;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.UnrecoverableKeyException;

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

  private GatewayConfig createGatewayConfig(boolean isClientAuthNeeded, boolean isExplicitTruststore,
                                            Path identityKeystorePath, String identityKeystoreType,
                                            String identityKeyAlias, Path truststorePath,
                                            String truststoreType, String trustStorePasswordAlias) {
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
    expect(config.getIncludedSSLCiphers()).andReturn(null).atLeastOnce();
    expect(config.getExcludedSSLCiphers()).andReturn(null).atLeastOnce();
    expect(config.getIncludedSSLProtocols()).andReturn(null).atLeastOnce();
    expect(config.getExcludedSSLProtocols()).andReturn(null).atLeastOnce();
    expect(config.isSSLRenegotiationAllowed()).andReturn(true).atLeastOnce();
    return config;
  }

}