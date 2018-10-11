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
package org.apache.knox.gateway.hadoopauth;

import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.ProviderDeploymentContributor;
import org.apache.knox.gateway.descriptor.FilterDescriptor;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.GatewayDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.descriptor.impl.GatewayDescriptorImpl;
import org.apache.knox.gateway.hadoopauth.deploy.HadoopAuthDeploymentContributor;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.impl.DefaultCryptoService;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Topology;
import org.easymock.EasyMock;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class HadoopAuthDeploymentContributorTest {

  @SuppressWarnings("rawtypes")
  @Test
  public void testServiceLoader() {
    ServiceLoader loader = ServiceLoader.load( ProviderDeploymentContributor.class );
    Iterator iterator = loader.iterator();
    assertThat( "Service iterator empty.", iterator.hasNext() );
    while( iterator.hasNext() ) {
      Object object = iterator.next();
      if( object instanceof HadoopAuthDeploymentContributor) {
        return;
      }
    }
    fail( "Failed to find " + HadoopAuthDeploymentContributor.class.getName() + " via service loader." );
  }

  @Test
  public void testDeployment() throws Exception {
    String aliasKey = "signature.secret";
    String aliasValue = "password";
    String normalKey = "type";
    String normalValue = "simple";

    WebArchive webArchive = ShrinkWrap.create( WebArchive.class, "test-acrhive" );

    Provider provider = new Provider();
    provider.setEnabled( true );
    provider.setName( HadoopAuthDeploymentContributor.NAME );
    // Keep order of params in map for testing
    Map<String, String> params = new TreeMap<>();
    params.put(aliasKey, aliasKey);
    params.put(normalKey, normalValue);
    provider.setParams(params);

    Topology topology = new Topology();
    topology.setName( "Sample" );

    DeploymentContext context = EasyMock.createNiceMock( DeploymentContext.class );
    EasyMock.expect( context.getWebArchive() ).andReturn( webArchive ).anyTimes();
    EasyMock.expect( context.getTopology() ).andReturn( topology ).anyTimes();
    EasyMock.replay( context );

    GatewayDescriptor gatewayDescriptor = new GatewayDescriptorImpl();
    ResourceDescriptor resource = gatewayDescriptor.createResource();
    
    AliasService as = EasyMock.createNiceMock( AliasService.class );
    EasyMock.expect(as.getAliasesForCluster(context.getTopology().getName()))
        .andReturn(Collections.singletonList(aliasKey)).anyTimes();
    EasyMock.expect(as.getPasswordFromAliasForCluster(context.getTopology().getName(), aliasKey))
        .andReturn(aliasValue.toCharArray()).anyTimes();
    EasyMock.replay( as );
    DefaultCryptoService cryptoService = new DefaultCryptoService();
    cryptoService.setAliasService( as );

    GatewayServices gatewayServices = EasyMock.createNiceMock( GatewayServices.class );
    EasyMock.expect( gatewayServices.getService( GatewayServices.CRYPTO_SERVICE ) ).andReturn( cryptoService ).anyTimes();

    HadoopAuthDeploymentContributor contributor = new HadoopAuthDeploymentContributor();
    contributor.setAliasService(as);

    assertThat( contributor.getRole(), is( HadoopAuthDeploymentContributor.ROLE ) );
    assertThat( contributor.getName(), is( HadoopAuthDeploymentContributor.NAME ) );

    // Just make sure it doesn't blow up.
    contributor.initializeContribution( context );

    contributor.contributeFilter(context, provider, null, resource, null);

    // Just make sure it doesn't blow up.
    contributor.finalizeContribution( context );

    // Check that the params are properly setup
    FilterDescriptor hadoopAuthFilterDescriptor = resource.filters().get(0);
    assertNotNull(hadoopAuthFilterDescriptor);
    assertEquals(HadoopAuthDeploymentContributor.NAME, hadoopAuthFilterDescriptor.name());
    List<FilterParamDescriptor> hadoopAuthFilterParams = hadoopAuthFilterDescriptor.params();
    assertEquals(2, hadoopAuthFilterParams.size());

    FilterParamDescriptor paramDescriptor = hadoopAuthFilterParams.get(0);
    assertEquals(aliasKey, paramDescriptor.name());
    assertEquals(aliasValue, paramDescriptor.value());

    FilterParamDescriptor paramDescriptor2 = hadoopAuthFilterParams.get(1);
    assertEquals(normalKey, paramDescriptor2.name());
    assertEquals(normalValue, paramDescriptor2.value());
  }
}
