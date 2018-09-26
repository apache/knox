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

import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.CryptoService;
import org.apache.knox.gateway.services.security.impl.DefaultCryptoService;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Topology;
import org.easymock.EasyMock;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SecureQueryDeploymentContributorTest {

  @Test
  public void testDeployment() throws IOException {
    WebArchive webArchive = ShrinkWrap.create( WebArchive.class, "test-acrhive" );

//    UrlRewriteRulesDescriptorImpl rewriteRules = new UrlRewriteRulesDescriptorImpl();

    Map<String,String> providerParams = new HashMap<>();
//    providerParams.put( "test-host-external", "test-host-internal" );
    Provider provider = new Provider();
    provider.setEnabled( true );
    provider.setName( "secure-query" );
    provider.setParams(  providerParams );

    Topology topology = new Topology();
    topology.setName("Sample");
    
    DeploymentContext context = EasyMock.createNiceMock( DeploymentContext.class );
//    EasyMock.expect( context.getDescriptor( "rewrite" ) ).andReturn( rewriteRules ).anyTimes();
    EasyMock.expect( context.getWebArchive() ).andReturn( webArchive ).anyTimes();
    EasyMock.expect( context.getTopology() ).andReturn( topology ).anyTimes();
    EasyMock.replay( context );

    AliasService as = EasyMock.createNiceMock( AliasService.class );
    CryptoService cryptoService = new DefaultCryptoService();
    ((DefaultCryptoService)cryptoService).setAliasService(as);

    GatewayServices gatewayServices = EasyMock.createNiceMock( GatewayServices.class );
    EasyMock.expect( gatewayServices.getService( GatewayServices.CRYPTO_SERVICE ) ).andReturn( cryptoService ).anyTimes();

    UrlRewriteEnvironment encEnvironment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    EasyMock.expect( encEnvironment.getAttribute( GatewayServices.GATEWAY_SERVICES_ATTRIBUTE ) ).andReturn( gatewayServices ).anyTimes();    
    
    SecureQueryDeploymentContributor contributor = new SecureQueryDeploymentContributor();
    contributor.setAliasService(as);

    assertThat( contributor.getRole(), is( "secure-query" ) );
    assertThat( contributor.getName(), is( "default" ) );

    // Just make sure it doesn't blow up.
    contributor.contributeFilter( null, null, null, null, null );

    // Just make sure it doesn't blow up.
    contributor.initializeContribution( context );

    contributor.contributeProvider( context, provider );

//    HostmapFunctionDescriptor funcDesc = rewriteRules.getFunction( "hostmap" );
//    assertThat( funcDesc.config(), is( "/WEB-INF/hostmap.txt" ) );
//
//    Node node = webArchive.get( "/WEB-INF/hostmap.txt" );
//    String asset = IOUtils.toString( node.getAsset().openStream() );
//    assertThat( asset, containsString( "test-host-external=test-host-internal" ) );

    // Just make sure it doesn't blow up.
    contributor.finalizeContribution( context );

  }

}
