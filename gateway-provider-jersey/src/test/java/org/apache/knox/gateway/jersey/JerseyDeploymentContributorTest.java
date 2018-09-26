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
package org.apache.knox.gateway.jersey;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.ProviderDeploymentContributor;
import org.apache.knox.gateway.descriptor.FilterDescriptor;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.GatewayDescriptor;
import org.apache.knox.gateway.descriptor.GatewayDescriptorFactory;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.easymock.EasyMock;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.webapp30.WebAppDescriptor;
import org.junit.Test;

import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class JerseyDeploymentContributorTest {

  @Test
  public void testServiceLoader() throws Exception {
    ServiceLoader loader = ServiceLoader.load( ProviderDeploymentContributor.class );
    Iterator iterator = loader.iterator();
    assertThat( "Service iterator empty.", iterator.hasNext() );
    while( iterator.hasNext() ) {
      Object object = iterator.next();
      if( object instanceof JerseyDispatchDeploymentContributor ) {
        return;
      }
    }
    fail( "Failed to find " + JerseyDispatchDeploymentContributor.class.getName() + " via service loader." );
  }

  @Test
  public void testDeploymentContributors() throws Exception {
    JerseyDispatchDeploymentContributor providerContributor = new JerseyDispatchDeploymentContributor();
    assertThat( providerContributor.getRole(), is( "pivot" ) );
    assertThat( providerContributor.getName(), is( "jersey" ) );

    MockJerseyService serviceContributor = new MockJerseyService();

    WebArchive webArchive = ShrinkWrap.create( WebArchive.class, "test-archive" );

    Topology topology = new Topology();
    topology.setName( "test-topology" );
    Provider provider = new Provider();
    provider.setRole( "pivot" );
    provider.setName( "jersey" );
    provider.setEnabled( true );
    topology.addProvider( provider );

    GatewayDescriptor descriptor = GatewayDescriptorFactory.create();

    DeploymentContext context = EasyMock.createNiceMock( DeploymentContext.class );
    EasyMock.expect( context.getWebArchive() ).andReturn( webArchive ).anyTimes();
    EasyMock.expect( context.getTopology() ).andReturn( topology ).anyTimes();
    EasyMock.expect( context.getGatewayDescriptor() ).andReturn( descriptor ).anyTimes();
    context.contributeFilter(
        EasyMock.<Service> isA( Service.class ),
        EasyMock.<ResourceDescriptor> isA( ResourceDescriptor.class ),
        EasyMock.<String> isA( String.class ),
        EasyMock.<String> isA( String.class ),
        EasyMock.<List> isA( List.class ) );
    EasyMock.expectLastCall().andDelegateTo(
        new MockDeploymentContext( context, providerContributor, provider ) ).anyTimes();

    EasyMock.replay( context );

    // Just make sure they don't blow up.
    providerContributor.initializeContribution( context );
    serviceContributor.initializeContribution( context );

    Service service = new Service();
    service.setRole( "test-service-role" );
    service.setName( "test-service-name" );
    service.addUrl( "http://test-service-host:777/test-service-path" );

    // This should end up calling providerContributor.contributeFilter
    serviceContributor.contributeService( context, service );
    ResourceDescriptor resource = context.getGatewayDescriptor().resources().get( 0 );

    // Just make sure they don't blow up.
    serviceContributor.finalizeContribution( context );
    providerContributor.finalizeContribution( context );

    /*
    GatewayDescriptorFactory.store( descriptor, "xml", new PrintWriter( System.out ) );
    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
    <gateway>
      <resource>
        <role>test-service-role</role>
        <pattern>test-service/?**</pattern>
        <filter>
          <role>dispatch</role>
          <name>jersey</name>
          <class>org.glassfish.jersey.servlet.ServletContainer</class>
          <param>
            <name>jersey.config.server.provider.packages</name>
            <value>test-package-1;test-package-2</value>
          </param>
        </filter>
      </resource>
      <resource>
        <role>test-service-role</role>
        <pattern>test-service/**?**</pattern>
        <filter>
          <role>dispatch</role>
          <name>jersey</name>
          <class>org.glassfish.jersey.servlet.ServletContainer</class>
          <param>
            <name>jersey.config.server.provider.packages</name>
            <value>test-package-1;test-package-2</value>
          </param>
        </filter>
      </resource>
    </gateway>
    */
    List<ResourceDescriptor> resources = context.getGatewayDescriptor().resources();
    assertThat( resources.size(), is( 2 ) );

    resource = resources.get( 0 );
    assertThat( resource.role(), is( "test-service-role" ) );
    assertThat( resource.pattern(), is( "test-service/?**" ) );
    List<FilterDescriptor> filters = resource.filters();
    assertThat( filters.size(), is( 1 ) );
    FilterDescriptor filter = filters.get( 0 );
    assertThat( filter.role(), is( "pivot") );
    assertThat( filter.name(), is( "jersey" ) );
    assertThat( filter.impl(), is( "org.glassfish.jersey.servlet.ServletContainer" ) );
    List<FilterParamDescriptor> params = filter.params();
    assertThat( params.size(), is( 1 ) );
    FilterParamDescriptor param = params.get( 0 );
    assertThat( param.name(), is( "jersey.config.server.provider.packages" ) );
    assertThat( param.value(), is( "test-package-1;test-package-2"  ) );

    resource = resources.get( 1 );
    assertThat( resource.role(), is( "test-service-role" ) );
    assertThat( resource.pattern(), is( "test-service/**?**" ) );
    filters = resource.filters();
    assertThat( filters.size(), is( 1 ) );
    filter = filters.get( 0 );
    assertThat( filter.role(), is( "pivot") );
    assertThat( filter.name(), is( "jersey" ) );
    assertThat( filter.impl(), is( "org.glassfish.jersey.servlet.ServletContainer" ) );
    params = filter.params();
    assertThat( params.size(), is( 1 ) );
    param = params.get( 0 );
    assertThat( param.name(), is( "jersey.config.server.provider.packages" ) );
    assertThat( param.value(), is( "test-package-1;test-package-2"  ) );
  }

  private static class MockJerseyService extends JerseyServiceDeploymentContributorBase {

    @Override
    protected String[] getPatterns() {
      return new String[]{ "test-service/?**", "test-service/**?**" };
    }

    @Override
    protected String[] getPackages() {
      return new String[]{ "test-package-1", "test-package-2" };
    }

    @Override
    public String getRole() {
      return "test-service-role";
    }

    @Override
    public String getName() {
      return "test-service-name";
    }

  }

  private static class MockDeploymentContext implements DeploymentContext {

    DeploymentContext context;
    ProviderDeploymentContributor providerContributor;
    Provider provider;

    public MockDeploymentContext(
        DeploymentContext context,
        ProviderDeploymentContributor providerContributor,
        Provider provider ) {
      this.context = context;
      this.providerContributor = providerContributor;
      this.provider = provider;
    }

    @Override
    public GatewayConfig getGatewayConfig() {
      return null;
    }

    @Override
    public Topology getTopology() {
      return null;
    }

    @Override
    public WebArchive getWebArchive() {
      return null;
    }

    @Override
    public WebAppDescriptor getWebAppDescriptor() {
      return null;
    }

    @Override
    public GatewayDescriptor getGatewayDescriptor() {
      return null;
    }

    @Override
    public void contributeFilter( Service service, ResourceDescriptor resource, String role, String name, List<FilterParamDescriptor> params ) {
      providerContributor.contributeFilter( context, provider, service, resource, params );
    }

    @Override
    public void addDescriptor( String name, Object descriptor ) {
    }

    @Override
    public <T> T getDescriptor( String name ) {
      return null;
    }

  }

}
