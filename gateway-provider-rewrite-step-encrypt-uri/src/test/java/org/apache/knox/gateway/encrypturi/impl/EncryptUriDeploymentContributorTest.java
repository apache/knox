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

import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.ProviderDeploymentContributor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.impl.DefaultCryptoService;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Topology;
import org.easymock.EasyMock;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

import java.io.IOException;
import java.util.Iterator;
import java.util.ServiceLoader;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class EncryptUriDeploymentContributorTest {

  @SuppressWarnings("rawtypes")
  @Test
  public void testServiceLoader() throws Exception {
    ServiceLoader loader = ServiceLoader.load( ProviderDeploymentContributor.class );
    Iterator iterator = loader.iterator();
    assertThat( "Service iterator empty.", iterator.hasNext() );
    while( iterator.hasNext() ) {
      Object object = iterator.next();
      if( object instanceof EncryptUriDeploymentContributor ) {
        return;
      }
    }
    fail( "Failed to find " + EncryptUriDeploymentContributor.class.getName() + " via service loader." );
  }

  @Test
  public void testDeployment() throws IOException {
    WebArchive webArchive = ShrinkWrap.create( WebArchive.class, "test-acrhive" );

    Provider provider = new Provider();
    provider.setEnabled( true );
    provider.setName( EncryptUriDeploymentContributor.PROVIDER_ROLE_NAME );

    Topology topology = new Topology();
    topology.setName( "Sample" );

    DeploymentContext context = EasyMock.createNiceMock( DeploymentContext.class );

    EasyMock.expect( context.getWebArchive() ).andReturn( webArchive ).anyTimes();
    EasyMock.expect( context.getTopology() ).andReturn( topology ).anyTimes();
    EasyMock.replay( context );

    AliasService as = EasyMock.createNiceMock( AliasService.class );
    DefaultCryptoService cryptoService = new DefaultCryptoService();
    cryptoService.setAliasService( as );

    GatewayServices gatewayServices = EasyMock.createNiceMock( GatewayServices.class );
    EasyMock.expect( gatewayServices.getService( ServiceType.CRYPTO_SERVICE ) ).andReturn( cryptoService ).anyTimes();

    UrlRewriteEnvironment encEnvironment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    EasyMock.expect( encEnvironment.getAttribute( GatewayServices.GATEWAY_SERVICES_ATTRIBUTE ) ).andReturn( gatewayServices ).anyTimes();

    EncryptUriDeploymentContributor contributor = new EncryptUriDeploymentContributor();
    contributor.setAliasService( as );

    assertThat( contributor.getRole(), is( EncryptUriDeploymentContributor.PROVIDER_ROLE_NAME ) );
    assertThat( contributor.getName(), is( EncryptUriDeploymentContributor.PROVIDER_IMPL_NAME ) );

    // Just make sure it doesn't blow up.
    contributor.contributeFilter( null, null, null, null, null );

    // Just make sure it doesn't blow up.
    contributor.initializeContribution( context );

    contributor.contributeProvider( context, provider );

    // Just make sure it doesn't blow up.
    contributor.finalizeContribution( context );

  }

}
