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
package org.apache.knox.gateway.securequery;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.impl.DefaultCryptoService;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Template;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SecureQueryEncodeProcessorTest {

  @Test
  public void testSimpleQueryEncoding() throws Exception {
    AliasService as = EasyMock.createNiceMock( AliasService.class );
    String secret = "sdkjfhsdkjfhsdfs";
    EasyMock.expect( as.getPasswordFromAliasForCluster("test-cluster-name", "encryptQueryString")).andReturn( secret.toCharArray() ).anyTimes();
    DefaultCryptoService cryptoService = new DefaultCryptoService();
    cryptoService.setAliasService(as);
    GatewayServices gatewayServices = EasyMock.createNiceMock( GatewayServices.class );
    EasyMock.expect( gatewayServices.getService( GatewayServices.CRYPTO_SERVICE ) ).andReturn( cryptoService );

    UrlRewriteEnvironment environment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    EasyMock.expect( environment.getAttribute( GatewayServices.GATEWAY_SERVICES_ATTRIBUTE ) ).andReturn( gatewayServices ).anyTimes();    
    EasyMock.expect( environment.getAttribute( GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE ) ).andReturn(Collections.singletonList("test-cluster-name")).anyTimes();

    Template inTemplate = Parser.parseLiteral( "http://host:0/root/path?query" );
    UrlRewriteContext context = EasyMock.createNiceMock( UrlRewriteContext.class );
    EasyMock.expect( context.getCurrentUrl() ).andReturn( inTemplate );
    Capture<Template> outTemplate = Capture.newInstance();
    context.setCurrentUrl( EasyMock.capture( outTemplate ) );

    EasyMock.replay( environment, context );

    SecureQueryEncodeDescriptor descriptor = new SecureQueryEncodeDescriptor();
    SecureQueryEncodeProcessor processor = new SecureQueryEncodeProcessor();
    processor.initialize( environment, descriptor );
    processor.process( context );

    String encQuery = Base64.getEncoder().encodeToString( "query".getBytes(StandardCharsets.UTF_8) );
    encQuery = encQuery.replaceAll( "\\=", "" );
    String outExpect = "http://host:0/root/path?_=" + encQuery;
    String outActual = outTemplate.getValue().toString();
    assertThat( outActual, is( outExpect ) );
  }

}
