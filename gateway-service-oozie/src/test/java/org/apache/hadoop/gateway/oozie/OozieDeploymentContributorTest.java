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
package org.apache.hadoop.gateway.oozie;

import org.apache.hadoop.gateway.deploy.DeploymentContext;
import org.apache.hadoop.gateway.deploy.ServiceDeploymentContributor;
import org.apache.hadoop.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.hadoop.gateway.filter.rewrite.impl.UrlRewriteRulesDescriptorImpl;
import org.apache.hadoop.gateway.topology.Service;
import org.easymock.EasyMock;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.ServiceLoader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;

public class OozieDeploymentContributorTest {

  @Test
  public void testServiceLoader() throws Exception {
    ServiceLoader loader = ServiceLoader.load( ServiceDeploymentContributor.class );
    Iterator iterator = loader.iterator();
    assertThat( "Service iterator empty.", iterator.hasNext() );
    while( iterator.hasNext() ) {
      Object object = iterator.next();
      if( object instanceof OozieDeploymentContributor ) {
        return;
      }
    }
    fail( "Failed to find " + OozieDeploymentContributor.class.getName() + " via service loader." );
  }

  @Test
  public void testLoadRulesFromTemplate() throws IOException, URISyntaxException {
    WebArchive webArchive = ShrinkWrap.create( WebArchive.class, "test-archive" );

    UrlRewriteRulesDescriptorImpl clusterRules = new UrlRewriteRulesDescriptorImpl();

    DeploymentContext context = EasyMock.createNiceMock( DeploymentContext.class );
    EasyMock.expect( context.getDescriptor( "rewrite" ) ).andReturn( clusterRules ).anyTimes();
    EasyMock.expect( context.getWebArchive() ).andReturn( webArchive ).anyTimes();

    Service service = EasyMock.createNiceMock( Service.class );
    EasyMock.expect( service.getRole() ).andReturn( "OOZIE" ).anyTimes();
    EasyMock.expect( service.getName() ).andReturn( null ).anyTimes();
    EasyMock.expect( service.getUrl() ).andReturn( "http://test-host:777" ).anyTimes();

    EasyMock.replay( context, service );

    OozieDeploymentContributor contributor = new OozieDeploymentContributor();

    UrlRewriteRulesDescriptor oozieRules = contributor.loadRulesFromTemplate();

    assertThat( oozieRules, notNullValue() );
    assertThat( oozieRules.getFilter( "OOZIE/oozie/configuration" ), notNullValue() );
  }

//  @Test
//  public void testDeployment() throws Exception {
//    WebArchive webArchive = ShrinkWrap.create( WebArchive.class, "test-archive" );
//
//    UrlRewriteRulesDescriptorImpl rewriteRules = new UrlRewriteRulesDescriptorImpl();
//
//    Map<String,String> serviceParams = new HashMap<String, String>();
//    Service service = new Service();
//    service.setRole( "OOZIE" );
//    service.setName( "oozie" );
//    service.setUrl( "http://test-host:42/test-path" );
//    service.setParams( serviceParams );
//
//    Topology topology = new Topology();
//    topology.setName( "test-topology" );
//    topology.addService( service );
//
//    GatewayDescriptor gateway = EasyMock.createNiceMock( GatewayDescriptor.class );
//    DeploymentContext context = EasyMock.createNiceMock( DeploymentContext.class );
//    EasyMock.expect( context.getWebArchive() ).andReturn( webArchive ).anyTimes();
//    EasyMock.expect( context.getDescriptor( "rewrite" ) ).andReturn( rewriteRules ).anyTimes();
//    EasyMock.expect( context.getWebAppDescriptor() ).andReturn( Descriptors.create( WebAppDescriptor.class ) ).anyTimes();
//    EasyMock.expect( context.getTopology() ).andReturn( topology ).anyTimes();
//    EasyMock.expect( context.getGatewayDescriptor() ).andReturn( gateway ).anyTimes();
//    Capture<Service> capturedService = new Capture<Service>();
//    Capture<ResourceDescriptor> capturedResource = new Capture<ResourceDescriptor>();
//    Capture<String> capturedRole = new Capture<String>();
//    Capture<String> capturedName = new Capture<String>();
//    Capture<List<FilterParamDescriptor>> capturedParams = new Capture<List<FilterParamDescriptor>>();
//    context.contributeFilter( capture(capturedService) , capture(capturedResource), capture(capturedRole), capture(capturedName), capture(capturedParams) );
//    EasyMock.expectLastCall().anyTimes();
//    EasyMock.replay( gateway, context );
//
//    OozieDeploymentContributor contributor = new OozieDeploymentContributor();
//
//    assertThat( contributor.getRole(), CoreMatchers.is( "OOZIE" ) );
//    assertThat( contributor.getName(), CoreMatchers.is( "oozie" ) );
//
//    // Just make sure it doesn't blow up.
//    contributor.initializeContribution( context );
//
//    contributor.contributeService( context, service );
//
//    // Just make sure it doesn't blow up.
//    contributor.finalizeContribution( context );
//
//    assertThat( capturedRole.getValue(), is( "dispatch" ) );
//    assertThat( capturedName.getValue(), is( "http-client" ) );
//  }

}
