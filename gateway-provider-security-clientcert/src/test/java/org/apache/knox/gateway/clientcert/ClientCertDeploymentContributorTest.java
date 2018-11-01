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
package org.apache.knox.gateway.clientcert;

import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.ProviderDeploymentContributor;
import org.apache.knox.gateway.clientcert.deploy.ClientCertDeploymentContributor;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Topology;
import org.easymock.EasyMock;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

import java.util.Iterator;
import java.util.ServiceLoader;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class ClientCertDeploymentContributorTest {

  @SuppressWarnings("rawtypes")
  @Test
  public void testServiceLoader() {
    ServiceLoader loader = ServiceLoader.load( ProviderDeploymentContributor.class );
    Iterator iterator = loader.iterator();
    assertThat( "Service iterator empty.", iterator.hasNext() );
    while( iterator.hasNext() ) {
      Object object = iterator.next();
      if( object instanceof ClientCertDeploymentContributor) {
        return;
      }
    }
    fail( "Failed to find " + ClientCertDeploymentContributor.class.getName() + " via service loader." );
  }

  @Test
  public void testDeployment() {
    WebArchive webArchive = ShrinkWrap.create( WebArchive.class, "test-archive" );

    Provider provider = new Provider();
    provider.setEnabled( true );
    provider.setName( ClientCertDeploymentContributor.NAME );

    Topology topology = new Topology();
    topology.setName( "Sample" );

    DeploymentContext context = EasyMock.createNiceMock( DeploymentContext.class );
    EasyMock.expect( context.getWebArchive() ).andReturn( webArchive ).anyTimes();
    EasyMock.expect( context.getTopology() ).andReturn( topology ).anyTimes();
    EasyMock.replay( context );

    ClientCertDeploymentContributor contributor = new ClientCertDeploymentContributor();

    assertThat( contributor.getRole(), is( ClientCertDeploymentContributor.ROLE ) );
    assertThat( contributor.getName(), is( ClientCertDeploymentContributor.NAME ) );

    // Just make sure it doesn't blow up.
    contributor.initializeContribution( context );

    // Just make sure it doesn't blow up.
    contributor.finalizeContribution( context );
  }
}
