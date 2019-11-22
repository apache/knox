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
package org.apache.knox.gateway.encrypturi.impl;

import org.apache.knox.gateway.encrypturi.EncryptStepContextParams;
import org.apache.knox.gateway.encrypturi.api.DecryptUriDescriptor;
import org.apache.knox.gateway.encrypturi.api.EncryptUriDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteStepProcessor;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteStepStatus;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.impl.DefaultCryptoService;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.ServiceLoader;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.fail;

public class EncryptDecryptUriProcessorTest {

  @SuppressWarnings("rawtypes")
  @Test
  public void testServiceLoader() throws Exception {
    ServiceLoader loader = ServiceLoader.load( UrlRewriteStepProcessor.class );
    Iterator iterator = loader.iterator();
    assertThat( "Service iterator empty.", iterator.hasNext() );
    while( iterator.hasNext() ) {
      Object object = iterator.next();
      if( object instanceof EncryptUriProcessor ) {
        return;
      }
    }
    fail( "Failed to find " + EncryptUriProcessor.class.getName() + " via service loader." );

    loader = ServiceLoader.load( UrlRewriteStepProcessor.class );
    iterator = loader.iterator();
    assertThat( "Service iterator empty.", iterator.hasNext() );
    while( iterator.hasNext() ) {
      Object object = iterator.next();
      if( object instanceof DecryptUriProcessor ) {
        return;
      }
    }
    fail( "Failed to find " + DecryptUriProcessor.class.getName() + " via service loader." );
  }

  @Test
  public void testEncryptDecrypt() throws Exception {
    String encryptedValueParamName = "address";
    String clusterName = "test-cluster-name";
    String passwordAlias = "encryptQueryString";

    // Test encryption.  Result is in encryptedAdrress

    AliasService as = EasyMock.createNiceMock( AliasService.class );
    String secret = "asdf";
    EasyMock.expect( as.getPasswordFromAliasForCluster( clusterName, passwordAlias ) ).andReturn( secret.toCharArray() ).anyTimes();
    DefaultCryptoService cryptoService = new DefaultCryptoService();
    cryptoService.setAliasService( as );
    GatewayServices gatewayServices = EasyMock.createNiceMock( GatewayServices.class );
    EasyMock.expect( gatewayServices.getService( ServiceType.CRYPTO_SERVICE ) ).andReturn( cryptoService );

    UrlRewriteEnvironment encEnvironment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    EasyMock.expect( encEnvironment.getAttribute( GatewayServices.GATEWAY_SERVICES_ATTRIBUTE ) ).andReturn( gatewayServices ).anyTimes();
    EasyMock.expect( encEnvironment.getAttribute( GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE ) ).andReturn( clusterName ).anyTimes();
    UrlRewriteContext encContext = EasyMock.createNiceMock( UrlRewriteContext.class );

    EncryptStepContextParams hostPortParams = new EncryptStepContextParams();
    hostPortParams.addParam( "host", Collections.singletonList("host.yarn.com"));
    hostPortParams.addParam( "port", Collections.singletonList("8088"));
    EasyMock.expect( encContext.getParameters() ).andReturn( hostPortParams );


    Capture<EncryptStepContextParams> encodedValue = Capture.newInstance();
    encContext.addParameters( EasyMock.capture( encodedValue ) );

    EasyMock.replay( gatewayServices, as, encEnvironment, encContext );

    EncryptUriDescriptor descriptor = new EncryptUriDescriptor();
    descriptor.setTemplate( "{host}:{port}" );
    descriptor.setParam( encryptedValueParamName );
    EncryptUriProcessor processor = new EncryptUriProcessor();
    processor.initialize( encEnvironment, descriptor );
    UrlRewriteStepStatus encStatus = processor.process( encContext );

    assertThat( encStatus, is ( UrlRewriteStepStatus.SUCCESS ) );
    assertThat( encodedValue.getValue(), notNullValue() );
    assertThat( encodedValue.getValue().resolve( encryptedValueParamName ).size(), is( 1 ) );
    String encryptedAdrress = encodedValue.getValue().resolve( encryptedValueParamName ).get( 0 );
    assertThat( encryptedAdrress, not( is(emptyOrNullString()) ) );
    assertThat( encryptedAdrress, not( "{host}:{port}" ) );
    assertThat( encryptedAdrress, not( "hdp:8088" ) );

    // Test decryption.  Result is in dectryptedAdrress.
    String decParam = "foo";
    gatewayServices = EasyMock.createNiceMock( GatewayServices.class );
    EasyMock.expect( gatewayServices.getService( ServiceType.CRYPTO_SERVICE ) ).andReturn( cryptoService );
    as = EasyMock.createNiceMock( AliasService.class );
    EasyMock.expect( as.getPasswordFromAliasForCluster( clusterName, passwordAlias ) ).andReturn( secret.toCharArray() ).anyTimes();

    UrlRewriteEnvironment decEnvironment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    EasyMock.expect( decEnvironment.getAttribute( GatewayServices.GATEWAY_SERVICES_ATTRIBUTE ) ).andReturn( gatewayServices ).anyTimes();
    EasyMock.expect( decEnvironment.getAttribute( GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE ) ).andReturn( clusterName ).anyTimes();
    UrlRewriteContext decContext = EasyMock.createNiceMock( UrlRewriteContext.class );

    EncryptStepContextParams encryptedParams = new EncryptStepContextParams();
    encryptedParams.addParam( decParam, Collections.singletonList(encryptedAdrress)); //Value was encrypted by EncryptUriProcessor
    encryptedParams.addParam( "foo1", Collections.singletonList("test"));
    EasyMock.expect( decContext.getParameters() ).andReturn( encryptedParams );

    Capture<EncryptStepContextParams> decodedValue = Capture.newInstance();
    decContext.addParameters( EasyMock.capture( decodedValue ) );

    EasyMock.replay( gatewayServices, as, decEnvironment, decContext );

    DecryptUriDescriptor decDescriptor = new DecryptUriDescriptor();
    decDescriptor.setParam( decParam );

    DecryptUriProcessor decProcessor = new DecryptUriProcessor();
    decProcessor.initialize( decEnvironment, decDescriptor );
    UrlRewriteStepStatus decStatus = decProcessor.process( decContext );
    assertThat( decStatus, is ( UrlRewriteStepStatus.SUCCESS ) );
    assertThat( decodedValue.getValue(), notNullValue() );
    assertThat( decodedValue.getValue().resolve( decParam ).size(), is( 1 ) );
    String dectryptedAdrress = decodedValue.getValue().resolve( decParam ).get( 0 );
    assertThat( dectryptedAdrress, is ( "host.yarn.com:8088" ) );
  }

}
