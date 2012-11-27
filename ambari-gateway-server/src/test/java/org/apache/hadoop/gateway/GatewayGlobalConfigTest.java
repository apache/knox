/**
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
package org.apache.hadoop.gateway;

import org.apache.hadoop.gateway.config.GatewayConfig;
import org.junit.Test;

import java.io.File;
import java.net.URL;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class GatewayGlobalConfigTest {

  private String getDirName( String resource ) {
    URL url = ClassLoader.getSystemResource( resource );
    String fileName = url.getFile();
    File file = new File( fileName );
    String dirName = file.getParent();
    return dirName;
  }

  @Test
  public void testFullConfig() {
    System.setProperty( GatewayConfig.GATEWAY_HOME_VAR, getDirName( "conf-full/gateway-default.xml" ) );
    GatewayConfig config = new GatewayConfig();
    assertThat( config.getGatewayPort(), is( 7777 ) );
    assertThat( config.getShiroConfigFile(), is( "full-shiro.ini") );
  }

  @Test
  public void testSiteConfig() {
    System.setProperty( GatewayConfig.GATEWAY_HOME_VAR, getDirName( "conf-site/gateway-site.xml" ) );
    GatewayConfig config = new GatewayConfig();
    assertThat( config.getGatewayPort(), is( 5555 ) );
    assertThat( config.getShiroConfigFile(), is( "site-shiro.ini") );
  }

  @Test
  public void testEmptyConfig() {
    System.setProperty( GatewayConfig.GATEWAY_HOME_VAR, getDirName( "conf-empty/empty" ) );
    GatewayConfig config = new GatewayConfig();
    assertThat( config.getGatewayPort(), is( 8888 ) );
    assertThat( config.getShiroConfigFile(), is( "shiro.ini") );
  }

}
