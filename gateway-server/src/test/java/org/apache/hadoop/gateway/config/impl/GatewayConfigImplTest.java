package org.apache.hadoop.gateway.config.impl;

import java.util.List;

import org.apache.hadoop.test.TestUtils;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;

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
public class GatewayConfigImplTest {

  @Test( timeout = TestUtils.SHORT_TIMEOUT )
  public void testHttpServerSettings() {
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
  public void testGetGatewayDeploymentsBackupVersionLimit() {
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
  public void testGetGatewayDeploymentsBackupAgeLimit() {
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
  public void testSSLCiphers() {
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

}
