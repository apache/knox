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
package org.apache.knox.gateway.identityasserter.common.filter;

import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.descriptor.FilterDescriptor;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.GatewayDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.descriptor.impl.GatewayDescriptorImpl;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.easymock.EasyMock;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.knox.gateway.identityasserter.common.filter.AbstractIdentityAsserterDeploymentContributor.IMPERSONATION_PARAMS;
import static org.junit.Assert.assertEquals;

public class AbstractIdentityAsserterDeploymentContributorTest {

  @Test
  public void testDeployment() {
    WebArchive webArchive = ShrinkWrap.create( WebArchive.class, "test-archive" );
    final String CUSTOM_IMPERSONATION_PARAM = "impersonationParam";

    Provider provider = new Provider();
    provider.setEnabled(true);
    provider.setName(MockAbstractIdentityAsserterDeploymentContributor.NAME);

    Service service = new Service();
    Map<String, String> params = new HashMap<>();
    params.put(IMPERSONATION_PARAMS, CUSTOM_IMPERSONATION_PARAM);
    service.setParams(params);

    Topology topology = new Topology();
    topology.setName( "Sample" );

    GatewayDescriptor gatewayDescriptor = new GatewayDescriptorImpl();
    ResourceDescriptor resource = gatewayDescriptor.createResource();

    DeploymentContext context = EasyMock.createNiceMock( DeploymentContext.class );
    EasyMock.expect( context.getWebArchive() ).andReturn( webArchive ).anyTimes();
    EasyMock.expect( context.getTopology() ).andReturn( topology ).anyTimes();
    EasyMock.expect(context.getGatewayDescriptor()).andReturn(gatewayDescriptor).anyTimes();
    EasyMock.replay( context );

    AbstractIdentityAsserterDeploymentContributor contributor = new MockAbstractIdentityAsserterDeploymentContributor();

    contributor.initializeContribution( context );
    contributor.contributeFilter(context, provider, service, resource, null);
    contributor.finalizeContribution( context );

    FilterDescriptor identityFilterDescriptor = resource.filters().get(0);
    List<FilterParamDescriptor> filterParams = identityFilterDescriptor.params();
    assertEquals(1, filterParams.size());

    FilterParamDescriptor paramDescriptor = filterParams.get(0);
    assertEquals(IMPERSONATION_PARAMS, paramDescriptor.name());
    assertEquals(CUSTOM_IMPERSONATION_PARAM, paramDescriptor.value());
  }
}

@SuppressWarnings("PMD")
class MockAbstractIdentityAsserterDeploymentContributor extends AbstractIdentityAsserterDeploymentContributor {

  public static String NAME = "MOCK";
  @Override
  protected String getFilterClassname() {
    return MockAbstractIdentityAsserterDeploymentContributor.class.getName();
  }

  @Override
  public String getName() {
    return NAME;
  }
}
