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
package org.apache.knox.gateway.identityasserter.hadoop.groups.filter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import org.apache.knox.gateway.GatewayTestConfig;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.ProviderDeploymentContributor;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.descriptor.impl.ResourceDescriptorImpl;
import org.apache.knox.gateway.topology.Provider;
import org.easymock.EasyMock;
import org.junit.Test;

/**
 * Test for {@link HadoopGroupProviderDeploymentContributor}
 *
 * @since 0.11
 */
public class HadoopGroupProviderDeploymentContributorTest {

  @Test
  public void testServiceLoader() throws Exception {
    ServiceLoader<ProviderDeploymentContributor> loader = ServiceLoader.load(ProviderDeploymentContributor.class);

    Iterator<ProviderDeploymentContributor> iterator = loader.iterator();
    assertThat("Service iterator empty.", iterator.hasNext());
    while (iterator.hasNext()) {
      Object object = iterator.next();
      if (object instanceof HadoopGroupProviderDeploymentContributor) {
        return;
      }
    }
    fail("Failed to find " + HadoopGroupProviderDeploymentContributor.class.getName() + " via service loader.");
  }

  @Test
  public void testFilterParametersGenerationOnlyGatewaySiteCentralGroupPrefix() throws Exception {
    testilterParametersGeneration(true, false);
  }

  @Test
  public void testilterParametersGenerationOnlyCustomProviderParameter() throws Exception {
    testilterParametersGeneration(false, true);
  }

  @Test
  public void testilterParametersGenerationBothGatewaySiteCentralGroupPrefixAndCustomProviderParameter() throws Exception {
    testilterParametersGeneration(true, true);
  }

  private void testilterParametersGeneration(boolean addCentralGroupPrefixParamIntoGatewaySite, boolean haveCustomProviderParameter) throws Exception {
    final String centralGroupPrefixParam = "gateway.group.config.";

    final DeploymentContext context = EasyMock.createNiceMock(DeploymentContext.class);
    final Properties gatewaySiteProperties = new Properties();
    if (addCentralGroupPrefixParamIntoGatewaySite) {
      gatewaySiteProperties.put(centralGroupPrefixParam + "gateway.site.property", "gateway.site.property.value");
    }
    final GatewayConfig gatewayConfig = new GatewayTestConfig(gatewaySiteProperties);
    EasyMock.expect(context.getGatewayConfig()).andReturn(gatewayConfig).anyTimes();

    final Provider provider = EasyMock.createNiceMock(Provider.class);
    final Map<String, String> providerParams = new HashMap<>();
    providerParams.put(HadoopGroupProviderDeploymentContributor.CENTRAL_GROUP_CONFIG_PREFIX_PARAM_NAME, centralGroupPrefixParam);
    if (haveCustomProviderParameter) {
      providerParams.put("other.provider.param", "other.provider.param.value");

      if (addCentralGroupPrefixParamIntoGatewaySite) {
        //provider-level property should override the gateway-level property
        providerParams.put("gateway.site.property", "custom.gateway.site.property.value");
      }
    }
    EasyMock.expect(provider.getParams()).andReturn(providerParams).anyTimes();

    final ResourceDescriptor resource = new ResourceDescriptorImpl(null);

    EasyMock.replay(context, provider);

    final HadoopGroupProviderDeploymentContributor deploymentContributor = new HadoopGroupProviderDeploymentContributor();
    deploymentContributor.contributeFilter(context, provider, null, resource, null);
    assertEquals(1, resource.filters().size());
    final int expectedParameterNum = addCentralGroupPrefixParamIntoGatewaySite && haveCustomProviderParameter ? 2 : 1;
    assertEquals(expectedParameterNum, resource.filters().get(0).params().size());

    final List<FilterParamDescriptor> filterParams = resource.filters().get(0).params();
    if (addCentralGroupPrefixParamIntoGatewaySite) {
      final String expectedPropertyValue = haveCustomProviderParameter ? "custom.gateway.site.property.value" : "gateway.site.property.value";
      assertEquals(expectedPropertyValue, getFilterParam(filterParams, "gateway.site.property").value());
    }

    if (haveCustomProviderParameter) {
      assertEquals("other.provider.param.value", getFilterParam(filterParams, "other.provider.param").value());
    }
  }

  private FilterParamDescriptor getFilterParam(List<FilterParamDescriptor> params, String name) {
    final Collection<FilterParamDescriptor> relevantFilterParams = params.stream().filter(param -> param.name().equals(name)).collect(Collectors.toList());
    assertEquals(1, relevantFilterParams.size());
    return relevantFilterParams.iterator().next();
  }

}
