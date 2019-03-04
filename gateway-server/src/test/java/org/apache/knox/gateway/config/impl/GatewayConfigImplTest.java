/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.config.impl;

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.config.GatewayConfigurationException;
import org.apache.knox.test.TestUtils;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.apache.knox.gateway.config.impl.GatewayConfigImpl.GATEWAY_CONFIG_DIR_PREFIX;
import static org.apache.knox.gateway.config.impl.GatewayConfigImpl.GATEWAY_CONFIG_FILE_PREFIX;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GatewayConfigImplTest {

  @Rule
  public final TemporaryFolder testFolder = new TemporaryFolder();

  @Rule
  public final ExpectedException expectedException = ExpectedException.none();

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testHttpServerSettings() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();

    // Check the defaults.
    assertThat( config.getHttpServerRequestBuffer(), is( 16*1024 ) );
    assertThat( config.getHttpServerRequestHeaderBuffer(), is( 8*1024 ) );
    assertThat( config.getHttpServerResponseBuffer(), is( 32*1024 ) );
    assertThat( config.getHttpServerResponseHeaderBuffer(), is( 8*1024 ) );

    assertThat( GatewayConfigImpl.HTTP_SERVER_REQUEST_BUFFER, is( "gateway.httpserver.requestBuffer" ) );
    assertThat( GatewayConfigImpl.HTTP_SERVER_REQUEST_HEADER_BUFFER, is( "gateway.httpserver.requestHeaderBuffer" ) );
    assertThat( GatewayConfigImpl.HTTP_SERVER_RESPONSE_BUFFER, is( "gateway.httpserver.responseBuffer" ) );
    assertThat( GatewayConfigImpl.HTTP_SERVER_RESPONSE_HEADER_BUFFER, is( "gateway.httpserver.responseHeaderBuffer" ) );

    config.setInt( GatewayConfigImpl.HTTP_SERVER_REQUEST_BUFFER, 32*1024 );
    assertThat( config.getHttpServerRequestBuffer(), is( 32*1024 ) );

    config.setInt( GatewayConfigImpl.HTTP_SERVER_REQUEST_HEADER_BUFFER, 4*1024 );
    assertThat( config.getHttpServerRequestHeaderBuffer(), is( 4*1024 ) );

    config.setInt( GatewayConfigImpl.HTTP_SERVER_RESPONSE_BUFFER, 16*1024 );
    assertThat( config.getHttpServerResponseBuffer(), is( 16*1024 ) );

    config.setInt( GatewayConfigImpl.HTTP_SERVER_RESPONSE_HEADER_BUFFER, 6*1024 );
    assertThat( config.getHttpServerResponseHeaderBuffer(), is( 6*1024 ) );

    // Restore the defaults.
    config.setInt( GatewayConfigImpl.HTTP_SERVER_REQUEST_BUFFER, 16*1024 );
    config.setInt( GatewayConfigImpl.HTTP_SERVER_REQUEST_HEADER_BUFFER, 8*1024 );
    config.setInt( GatewayConfigImpl.HTTP_SERVER_RESPONSE_BUFFER, 32*1024 );
    config.setInt( GatewayConfigImpl.HTTP_SERVER_RESPONSE_HEADER_BUFFER, 8*1024 );
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testGetGatewayDeploymentsBackupVersionLimit() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();
    assertThat( config.getGatewayDeploymentsBackupVersionLimit(), is(5) );

    config.setInt( config.DEPLOYMENTS_BACKUP_VERSION_LIMIT, 3 );
    assertThat( config.getGatewayDeploymentsBackupVersionLimit(), is(3) );

    config.setInt( config.DEPLOYMENTS_BACKUP_VERSION_LIMIT, -3 );
    assertThat( config.getGatewayDeploymentsBackupVersionLimit(), is(-1) );

    config.setInt( config.DEPLOYMENTS_BACKUP_VERSION_LIMIT, 0 );
    assertThat( config.getGatewayDeploymentsBackupVersionLimit(), is(0) );
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testGetGatewayDeploymentsBackupAgeLimit() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();
    assertThat( config.getGatewayDeploymentsBackupAgeLimit(), is(-1L) );

    config.set( config.DEPLOYMENTS_BACKUP_AGE_LIMIT, "1" );
    assertThat( config.getGatewayDeploymentsBackupAgeLimit(), is(86400000L) );

    config.set( config.DEPLOYMENTS_BACKUP_AGE_LIMIT, "2" );
    assertThat( config.getGatewayDeploymentsBackupAgeLimit(), is(86400000L*2L) );

    config.set( config.DEPLOYMENTS_BACKUP_AGE_LIMIT, "0" );
    assertThat( config.getGatewayDeploymentsBackupAgeLimit(), is(0L) );

    config.set( config.DEPLOYMENTS_BACKUP_AGE_LIMIT, "X" );
    assertThat( config.getGatewayDeploymentsBackupAgeLimit(), is(-1L) );
  }


  @Test
  public void testSSLCiphers() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();
    List<String> list;

    list = config.getIncludedSSLCiphers();
    assertThat( list, is(nullValue()) );

    config.set( "ssl.include.ciphers", "none" );
    assertThat( config.getIncludedSSLCiphers(), is(nullValue()) );

    config.set( "ssl.include.ciphers", "" );
    assertThat( config.getIncludedSSLCiphers(), is(nullValue()) );

    config.set( "ssl.include.ciphers", "ONE" );
    assertThat( config.getIncludedSSLCiphers(), is(hasItems("ONE")) );

    config.set( "ssl.include.ciphers", " ONE " );
    assertThat( config.getIncludedSSLCiphers(), is(hasItems("ONE")) );

    config.set( "ssl.include.ciphers", "ONE,TWO" );
    assertThat( config.getIncludedSSLCiphers(), is(hasItems("ONE","TWO")) );

    config.set( "ssl.include.ciphers", "ONE,TWO,THREE" );
    assertThat( config.getIncludedSSLCiphers(), is(hasItems("ONE","TWO","THREE")) );

    config.set( "ssl.include.ciphers", " ONE , TWO , THREE " );
    assertThat( config.getIncludedSSLCiphers(), is(hasItems("ONE","TWO","THREE")) );

    list = config.getExcludedSSLCiphers();
    assertThat( list, is(nullValue()) );

    config.set( "ssl.exclude.ciphers", "none" );
    assertThat( config.getExcludedSSLCiphers(), is(nullValue()) );

    config.set( "ssl.exclude.ciphers", "" );
    assertThat( config.getExcludedSSLCiphers(), is(nullValue()) );

    config.set( "ssl.exclude.ciphers", "ONE" );
    assertThat( config.getExcludedSSLCiphers(), is(hasItems("ONE")) );

    config.set( "ssl.exclude.ciphers", " ONE " );
    assertThat( config.getExcludedSSLCiphers(), is(hasItems("ONE")) );

    config.set( "ssl.exclude.ciphers", "ONE,TWO" );
    assertThat( config.getExcludedSSLCiphers(), is(hasItems("ONE","TWO")) );

    config.set( "ssl.exclude.ciphers", "ONE,TWO,THREE" );
    assertThat( config.getExcludedSSLCiphers(), is(hasItems("ONE","TWO","THREE")) );

    config.set( "ssl.exclude.ciphers", " ONE , TWO , THREE " );
    assertThat( config.getExcludedSSLCiphers(), is(hasItems("ONE","TWO","THREE")) );
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testGlobalRulesServices() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();
    List<String> list;

    list = config.getGlobalRulesServices();
    assertThat( list, is(notNullValue()) );

    assertThat( list, is( CoreMatchers.hasItems("NAMENODE","JOBTRACKER", "WEBHDFS", "WEBHCAT", "OOZIE", "WEBHBASE", "HIVE", "RESOURCEMANAGER")));


    config.set( GatewayConfigImpl.GLOBAL_RULES_SERVICES, "none" );
    assertThat( config.getGlobalRulesServices(), is( CoreMatchers.hasItems("NAMENODE","JOBTRACKER", "WEBHDFS", "WEBHCAT", "OOZIE", "WEBHBASE", "HIVE", "RESOURCEMANAGER")) );

    config.set( GatewayConfigImpl.GLOBAL_RULES_SERVICES, "" );
    assertThat( config.getGlobalRulesServices(), is( CoreMatchers.hasItems("NAMENODE","JOBTRACKER", "WEBHDFS", "WEBHCAT", "OOZIE", "WEBHBASE", "HIVE", "RESOURCEMANAGER")) );

    config.set( GatewayConfigImpl.GLOBAL_RULES_SERVICES, "ONE" );
    assertThat( config.getGlobalRulesServices(), is(hasItems("ONE")) );

    config.set( GatewayConfigImpl.GLOBAL_RULES_SERVICES, "ONE,TWO,THREE" );
    assertThat( config.getGlobalRulesServices(), is(hasItems("ONE","TWO","THREE")) );

    config.set( GatewayConfigImpl.GLOBAL_RULES_SERVICES, " ONE , TWO , THREE " );
    assertThat( config.getGlobalRulesServices(), is(hasItems("ONE","TWO","THREE")) );
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testMetricsSettings() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();
    //test defaults
    assertThat(config.isMetricsEnabled(), is(false));
    assertThat(config.isJmxMetricsReportingEnabled(), is(false));
    assertThat(config.isGraphiteMetricsReportingEnabled(), is(false));
    assertThat(config.getGraphiteHost(), is("localhost"));
    assertThat(config.getGraphitePort(), is(32772));
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testGatewayIdleTimeout() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();
    long idleTimeout;

    idleTimeout = config.getGatewayIdleTimeout();
    assertThat( idleTimeout, is(300000L));

    config.set( GatewayConfigImpl.GATEWAY_IDLE_TIMEOUT, "15000" );
    idleTimeout = config.getGatewayIdleTimeout();
    assertThat( idleTimeout, is(15000L));
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testGatewayServerHeaderEnabled() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();
    boolean serverHeaderEnabled;

    serverHeaderEnabled = config.isGatewayServerHeaderEnabled();
    assertThat( serverHeaderEnabled, is(true));

    config.set( GatewayConfigImpl.SERVER_HEADER_ENABLED, "false");
    serverHeaderEnabled = config.isGatewayServerHeaderEnabled();
    assertThat( serverHeaderEnabled, is(false));
  }


  @Test
  public void testGetRemoteConfigurationRegistryNames() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();

    List<String> registryNames = config.getRemoteRegistryConfigurationNames();
    assertNotNull(registryNames);
    assertTrue(registryNames.isEmpty());

    config.set(GatewayConfigImpl.CONFIG_REGISTRY_PREFIX + ".test1",
               "type=ZooKeeper;address=host1:2181;authType=digest;principal=itsme;credentialAlias=testAlias");
    registryNames = config.getRemoteRegistryConfigurationNames();
    assertNotNull(registryNames);
    assertFalse(registryNames.isEmpty());
    assertEquals(1, registryNames.size());

    config.set(GatewayConfigImpl.CONFIG_REGISTRY_PREFIX + ".test2",
               "type=ZooKeeper;address=host2:2181,host3:2181,host4:2181");
    registryNames = config.getRemoteRegistryConfigurationNames();
    assertNotNull(registryNames);
    assertFalse(registryNames.isEmpty());
    assertEquals(registryNames.size(), 2);
  }


  @Test
  public void testHTTPDefaultTimeouts() throws Exception {
    final GatewayConfigImpl config = new GatewayConfigImpl();

    assertNotEquals(config.getHttpClientConnectionTimeout(), -1);
    assertNotEquals(config.getHttpClientSocketTimeout(), -1);

    assertEquals(TimeUnit.SECONDS.toMillis(20), config.getHttpClientConnectionTimeout());
    assertEquals(TimeUnit.SECONDS.toMillis(20), config.getHttpClientSocketTimeout());
  }


  // KNOX-1322
  @Test
  public void testGetReadOnlyOverrideTopologyNames() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();

    List<String> names = config.getReadOnlyOverrideTopologyNames();
    assertNotNull(names);
    assertTrue(names.isEmpty());

    config.set(GatewayConfigImpl.READ_ONLY_OVERRIDE_TOPOLOGIES, "");
    names = config.getReadOnlyOverrideTopologyNames();
    assertNotNull(names);
    assertTrue(names.isEmpty());

    config.set(GatewayConfigImpl.READ_ONLY_OVERRIDE_TOPOLOGIES, "admin");
    names = config.getReadOnlyOverrideTopologyNames();
    assertNotNull(names);
    assertFalse(names.isEmpty());
    assertEquals(1, names.size());
    assertEquals("admin", names.get(0));

    config.set(GatewayConfigImpl.READ_ONLY_OVERRIDE_TOPOLOGIES, "admin, sandbox, test ,default");
    names = config.getReadOnlyOverrideTopologyNames();
    assertNotNull(names);
    assertFalse(names.isEmpty());
    assertEquals(4, names.size());
    assertTrue(names.contains("admin"));
    assertTrue(names.contains("sandbox"));
    assertTrue(names.contains("test"));
    assertTrue(names.contains("default"));
  }

  // KNOX-1756
  @Test
  public void testCustomIdentityKeystoreOptions() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();

    // Validate default options (backwards compatibility)
    assertEquals("gateway-identity", config.getIdentityKeyAlias());
    assertEquals("gateway-identity-passphrase", config.getIdentityKeyPassphraseAlias());
    assertEquals("gateway-identity", config.getSigningKeyAlias());
    assertEquals("gateway-identity-passphrase", config.getSigningKeyPassphraseAlias());
    assertNull(config.getSigningKeystoreName());

    // Validate default options (new)
    assertEquals(GatewayConfig.DEFAULT_IDENTITY_KEYSTORE_PASSWORD_ALIAS, config.getIdentityKeystorePasswordAlias());
    assertEquals(GatewayConfig.DEFAULT_IDENTITY_KEYSTORE_TYPE, config.getIdentityKeystoreType());
    assertEquals(Paths.get(config.getGatewayKeystoreDir(), "gateway.jks").toAbsolutePath().toString(),
        config.getIdentityKeystorePath());

    // By default the signing keystore name will not be set, so the values will be taken from the identity's configs
    assertEquals(config.getIdentityKeystorePath(), config.getSigningKeystorePath());
    assertEquals(config.getIdentityKeystorePasswordAlias(), config.getSigningKeystorePasswordAlias());
    assertEquals(config.getIdentityKeystoreType(), config.getSigningKeystoreType());
    assertEquals(config.getIdentityKeyAlias(), config.getSigningKeyAlias());
    assertEquals(config.getIdentityKeyPassphraseAlias(), config.getSigningKeyPassphraseAlias());

    String tlsKeystorePath = Paths.get("custom", "keystore", "path", "keystore.p12").toString();

    // Validate changed options
    config.set("gateway.tls.key.alias", "custom_key_alias");
    config.set("gateway.tls.key.passphrase.alias", "custom_key_passphrase_alias");
    config.set("gateway.tls.keystore.path", tlsKeystorePath);
    config.set("gateway.tls.keystore.type", "PKCS12");
    config.set("gateway.tls.keystore.password.alias", "custom_keystore_password_alias");

    config.set("gateway.signing.key.alias", "custom_key_alias");
    config.set("gateway.signing.key.passphrase.alias", "custom_key_passphrase_alias");
    config.set("gateway.signing.keystore.name", "custom_keystore_name");
    config.set("gateway.signing.keystore.type", "PKCS12");
    config.set("gateway.signing.keystore.password.alias", "custom_keystore_password_alias");

    assertEquals("custom_key_alias", config.getIdentityKeyAlias());
    assertEquals("custom_key_passphrase_alias", config.getIdentityKeyPassphraseAlias());
    assertEquals(tlsKeystorePath, config.getIdentityKeystorePath());
    assertEquals("PKCS12", config.getIdentityKeystoreType());
    assertEquals("custom_keystore_password_alias", config.getIdentityKeystorePasswordAlias());

    assertEquals("custom_key_alias", config.getSigningKeyAlias());
    assertEquals("custom_key_passphrase_alias", config.getSigningKeyPassphraseAlias());
    assertEquals("custom_keystore_name", config.getSigningKeystoreName());
    assertEquals("PKCS12", config.getSigningKeystoreType());
    assertEquals("custom_keystore_password_alias", config.getSigningKeystorePasswordAlias());
  }

  @Test
  public void shouldRaiseAnExeptionInCaseKerberosIsEnabledAndKerberosConfigDoesNotExist() throws Exception {
    testNonExistingKerberosConfigFile("nonExistingKrb5Conf", null);
  }

  @Test
  public void shouldRaiseAnExeptionInCaseKerberosIsEnabledAndKerberosConfigCannotBeRead() throws Exception {
    testNonReadableKerberosConfigFile(true);
  }

  @Test
  public void shouldRaiseAnExeptionInCaseKerberosIsEnabledAndKerberosLoginConfigDoesNotExist() throws Exception {
    testNonExistingKerberosConfigFile(null, "nonExistingKrb5LoginConf");
  }

  @Test
  public void shouldRaiseAnExeptionInCaseKerberosIsEnabledAndKerberosLoginConfigCannotBeRead() throws Exception {
    testNonReadableKerberosConfigFile(false);
  }

  private void testNonExistingKerberosConfigFile(String krb5Config, String krb5LoginConfig) throws Exception {
    try {
      expectedException.expect(GatewayConfigurationException.class);
      expectedException.expectMessage(containsString("set to a non-existing file"));

      final File gatewayHome = testFolder.newFolder("gatewayHome");
      System.setProperty(GatewayConfig.GATEWAY_HOME_VAR, gatewayHome.getAbsolutePath());
      writeTestGatewayConfig(krb5Config, krb5LoginConfig);
      new GatewayConfigImpl(); //exception is expected here
    } finally {
      System.clearProperty(GatewayConfig.GATEWAY_HOME_VAR);
    }
  }

  private void testNonReadableKerberosConfigFile(boolean krb5Conf) throws Exception {
    try {
      expectedException.expect(GatewayConfigurationException.class);
      expectedException.expectMessage(containsString("set to a non-readable file"));

      final File gatewayHome = testFolder.newFolder("gatewayHome");
      System.setProperty(GatewayConfig.GATEWAY_HOME_VAR, gatewayHome.getAbsolutePath());
      final File krb5ConfFile = new File(gatewayHome, "krb5.conf");
      krb5ConfFile.createNewFile();
      //krb5ConfFile.setReadable(false); //for some reason this does not work with root user
      Files.setPosixFilePermissions(Paths.get(krb5ConfFile.getAbsolutePath()), new HashSet<PosixFilePermission>());
      if (krb5Conf) {
        writeTestGatewayConfig(krb5ConfFile.getAbsolutePath(), null);
      } else {
        writeTestGatewayConfig(null, krb5ConfFile.getAbsolutePath());
      }
      new GatewayConfigImpl();  //exception is expected here
    } finally {
      System.clearProperty(GatewayConfig.GATEWAY_HOME_VAR);
    }
  }

  private void writeTestGatewayConfig(String krb5Config, String krb5LoginConfig) throws IOException {
    final File configDir = testFolder.newFolder("gatewayHome", GATEWAY_CONFIG_DIR_PREFIX);
    final File gatewayConfigFile = new File(configDir, GATEWAY_CONFIG_FILE_PREFIX + "-site.xml");
    final StringBuilder configFileContentBuilder = new StringBuilder(512);
    configFileContentBuilder.append("<configuration  xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n<property><name>gateway.hadoop.kerberos.secured</name><value>true</value></property>");
    if (krb5Config != null) {
      configFileContentBuilder.append("<property><name>java.security.krb5.conf</name><value>" + krb5Config + "</value></property>\n");
    }
    if (krb5LoginConfig != null) {
      configFileContentBuilder.append("<property><name>java.security.auth.login.config</name><value>" + krb5LoginConfig + "</value></property>\n");
    }
    configFileContentBuilder.append("</configuration>");
    FileUtils.writeStringToFile(gatewayConfigFile, configFileContentBuilder.toString(), StandardCharsets.UTF_8);
  }

}
