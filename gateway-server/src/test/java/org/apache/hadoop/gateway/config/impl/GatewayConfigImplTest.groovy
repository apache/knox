package org.apache.hadoop.gateway.config.impl

import org.junit.Test

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat

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

  @Test
  public void testHttpServerSettings() {
    GatewayConfigImpl config = new GatewayConfigImpl();

    // Check the defaults.
    assertThat( config.getHttpServerRequestBuffer(), is( 16*1024 ) );
    assertThat( config.getHttpServerRequestHeaderBuffer(), is( 8*1024 ) );
    assertThat( config.getHttpServerResponseBuffer(), is( 32*1024 ) );
    assertThat( config.getHttpServerResponseHeaderBuffer(), is( 8*1024 ) );

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

}
