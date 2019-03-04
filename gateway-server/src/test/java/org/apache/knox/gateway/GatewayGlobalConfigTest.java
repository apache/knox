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
package org.apache.knox.gateway;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.config.impl.GatewayConfigImpl;
import org.apache.knox.test.TestUtils;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.io.File;
import java.net.URL;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GatewayGlobalConfigTest {

  private String getHomeDirName( String resource ) {
    URL url = ClassLoader.getSystemResource( resource );
    String fileName = url.getFile();
    File file = new File( fileName );
    return file.getParentFile().getParent();
  }

  @Test
  public void testFullConfig() throws Exception {
    System.setProperty( GatewayConfigImpl.GATEWAY_HOME_VAR, getHomeDirName( "conf-full/conf/gateway-default.xml" ) );
    GatewayConfig config = new GatewayConfigImpl();
    assertThat( config.getGatewayPort(), is( 7777 ) );
    assertThat( config.isClientAuthNeeded(), is( false ) );
    assertNull("ssl.exclude.protocols should be null.", config.getExcludedSSLProtocols());
    //assertThat( config.getShiroConfigFile(), is( "full-shiro.ini") );
  }

  @Test
  public void testDemoConfig() throws Exception {
    System.setProperty( GatewayConfigImpl.GATEWAY_HOME_VAR, getHomeDirName( "conf-demo/conf/gateway-default.xml" ) );
    GatewayConfig config = new GatewayConfigImpl();
    assertThat(config.getGatewayPort(), is( 8888 ) );
    assertEquals("SSLv3", config.getExcludedSSLProtocols().get(0));
    //assertThat( config.getShiroConfigFile(), is( "full-shiro.ini") );
  }

  @Test
  public void testSiteConfig() throws Exception {
    System.setProperty( GatewayConfigImpl.GATEWAY_HOME_VAR, getHomeDirName( "conf-site/conf/gateway-site.xml" ) );
    GatewayConfig config = new GatewayConfigImpl();
    assertThat( config.getGatewayPort(), is( 5555 ) );
    assertThat( config.isClientAuthNeeded(), is( true ) );
    assertThat( config.getTruststorePath(), is("./gateway-trust.jks"));
    assertThat( config.getTruststoreType(), is( "PKCS12" ) );
    assertThat( config.getKeystoreType(), is( "JKS" ) );
  }

  @Test
  public void testEmptyConfig() throws Exception {
    System.setProperty( GatewayConfigImpl.GATEWAY_HOME_VAR, getHomeDirName( "conf-empty/conf/empty" ) );
    GatewayConfig config = new GatewayConfigImpl();
    assertThat( config.getGatewayPort(), is( 8888 ) );
    //assertThat( config.getShiroConfigFile(), is( "shiro.ini") );
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testDefaultTopologyName() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();
    assertThat( config.getDefaultTopologyName(), is( nullValue() ) );

    config.set("default.app.topology.name", "test-topo-name" );
    assertThat( config.getDefaultTopologyName(), is( "test-topo-name" ) );
  }

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testDefaultAppRedirectPath() throws Exception {
    GatewayConfigImpl config = new GatewayConfigImpl();
    assertThat( config.getDefaultAppRedirectPath(), nullValue() );

    config.set("default.app.topology.name", "test-topo-name" );
    assertThat( config.getDefaultAppRedirectPath(), is("/gateway/test-topo-name") );
  }

  @Test
  public void testForUpdatedDeploymentDir() throws Exception {
    String homeDirName = getHomeDirName("conf-demo/conf/gateway-site.xml");
    System.setProperty(GatewayConfigImpl.GATEWAY_HOME_VAR, homeDirName);
    System.setProperty(GatewayConfigImpl.GATEWAY_DATA_HOME_VAR, homeDirName);
    GatewayConfig config = new GatewayConfigImpl();
    assertTrue(("target/test").equalsIgnoreCase(config.getGatewayDeploymentDir()));
  }

  @Test
  public void testDefaultDeploymentDir() throws Exception {
    String homeDirName = getHomeDirName("conf-site/conf/gateway-site.xml");
    System.setProperty(GatewayConfigImpl.GATEWAY_HOME_VAR, homeDirName);
    System.setProperty(GatewayConfigImpl.GATEWAY_DATA_HOME_VAR, homeDirName);
    GatewayConfig config = new GatewayConfigImpl();
    assertThat(config.getGatewayDeploymentDir(), is(homeDirName + File.separator + "deployments"));
  }

  @Test
  public void testForDefaultSecurityDataDir() throws Exception {
    String homeDirName = getHomeDirName("conf-site/conf/gateway-site.xml");
    System.setProperty(GatewayConfigImpl.GATEWAY_HOME_VAR, homeDirName);
    System.setProperty(GatewayConfigImpl.GATEWAY_DATA_HOME_VAR, homeDirName);
    GatewayConfig config = new GatewayConfigImpl();
    assertThat(config.getGatewaySecurityDir(), is(homeDirName + File.separator + "security"));
  }

  @Test
  public void testForUpdatedSecurityDataDir() throws Exception {
    String homeDirName = getHomeDirName("conf-demo/conf/gateway-site.xml");
    System.setProperty(GatewayConfigImpl.GATEWAY_HOME_VAR, homeDirName);
    System.setProperty(GatewayConfigImpl.GATEWAY_DATA_HOME_VAR, homeDirName);
    GatewayConfig config = new GatewayConfigImpl();
    assertTrue(("target/test").equalsIgnoreCase(config.getGatewaySecurityDir()));
  }

  @Test
  public void testForDataDirSetAsSystemProperty() throws Exception {
    String homeDirName = getHomeDirName("conf-demo/conf/gateway-site.xml");
    System.setProperty(GatewayConfigImpl.GATEWAY_DATA_HOME_VAR, homeDirName + File.separator
        + "DataDirSystemProperty");
    GatewayConfig config = new GatewayConfigImpl();
    assertTrue((homeDirName + File.separator + "DataDirSystemProperty").equalsIgnoreCase(config
        .getGatewayDataDir()));
  }

  @Test
  public void testForDataDirSetAsConfiguration() throws Exception {
    String homeDirName = getHomeDirName("conf-demo/conf/gateway-site.xml");
    System.setProperty(GatewayConfigImpl.GATEWAY_HOME_VAR, homeDirName);
    System.clearProperty(GatewayConfigImpl.GATEWAY_DATA_HOME_VAR);
    GatewayConfig config = new GatewayConfigImpl();
    assertTrue(("target/testDataDir").equalsIgnoreCase(config
        .getGatewayDataDir()));
  }

  @Test
  public void testForDefaultDataDir() throws Exception {
    String homeDirName = getHomeDirName("conf-site/conf/gateway-site.xml");
    System.setProperty(GatewayConfigImpl.GATEWAY_HOME_VAR, homeDirName);
    System.clearProperty(GatewayConfigImpl.GATEWAY_DATA_HOME_VAR);
    GatewayConfig config = new GatewayConfigImpl();
    assertTrue((homeDirName + File.separator + "data").equalsIgnoreCase(config.getGatewayDataDir()));
  }

  /**
   * When data dir is set at both system property and configuration level , then system property
   * value should be considered
   **/
  @Test
  public void testDataDirSetAsBothSystemPropertyAndConfig() throws Exception {
    String homeDirName = getHomeDirName("conf-demo/conf/gateway-site.xml");
    System.setProperty(GatewayConfigImpl.GATEWAY_HOME_VAR, homeDirName);
    System.setProperty(GatewayConfigImpl.GATEWAY_DATA_HOME_VAR, homeDirName + File.separator
        + "DataDirSystemProperty");
    GatewayConfig config = new GatewayConfigImpl();
    assertTrue((homeDirName + File.separator + "DataDirSystemProperty").equalsIgnoreCase(config
        .getGatewayDataDir()));
  }

  @Test
  public void testStacksServicesDir() throws Exception {
    System.clearProperty(GatewayConfigImpl.GATEWAY_HOME_VAR);
    GatewayConfig config = new GatewayConfigImpl();
    assertThat(config.getGatewayServicesDir(), Matchers.endsWith("data" + File.separator + "services"));
    String homeDirName = getHomeDirName("conf-demo/conf/gateway-site.xml");
    System.setProperty(GatewayConfigImpl.GATEWAY_HOME_VAR, homeDirName);
    config = new GatewayConfigImpl();
    assertEquals("target/test", config.getGatewayServicesDir());
  }
}