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
package org.apache.knox.gateway.deploy.impl;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.ProviderDeploymentContributor;
import org.apache.knox.gateway.descriptor.FilterDescriptor;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.descriptor.impl.GatewayDescriptorImpl;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.knox.gateway.service.definition.CustomDispatch;
import org.apache.knox.gateway.service.definition.Rewrite;
import org.apache.knox.gateway.service.definition.Route;
import org.apache.knox.gateway.service.definition.ServiceDefinition;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.easymock.EasyMock;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class ServiceDefinitionDeploymentContributorTest {

  @Test
  public void testServiceLoader() throws Exception {
    ServiceLoader loader = ServiceLoader.load( ProviderDeploymentContributor.class );
    Iterator iterator = loader.iterator();
    assertThat( "Service iterator empty.", iterator.hasNext() );
    while( iterator.hasNext() ) {
      Object object = iterator.next();
      if( object instanceof ServiceDefinitionDeploymentContributor ) {
        fail("The ServiceDefinition deployment contributor is not meant to be loaded using the service loader mechanism");
      }
    }
  }

  /*
   * Test that service param useTwoWaySsl in topologies overrides the corresponding custom dispatch property.
   */
  @Test
  public void testServiceAttributeUseTwoWaySSLParamOverride() throws Exception {

    final String TEST_SERVICE_ROLE     = "Test";
    final String TEST_SERVICE_NAME     = "Test";
    final String USE_TWO_WAY_SSL_PARAM = "useTwoWaySsl";

    UrlRewriteRulesDescriptor clusterRules = EasyMock.createNiceMock(UrlRewriteRulesDescriptor.class);
    EasyMock.replay(clusterRules);

    UrlRewriteRulesDescriptor svcRules = EasyMock.createNiceMock(UrlRewriteRulesDescriptor.class);
    EasyMock.replay(svcRules);

    ServiceDefinition svcDef = EasyMock.createNiceMock(ServiceDefinition.class);
    EasyMock.expect(svcDef.getRole()).andReturn(TEST_SERVICE_ROLE).anyTimes();
    List<Route> svcRoutes = new ArrayList<>();
    Route route = EasyMock.createNiceMock(Route.class);
    List<Rewrite> filters = new ArrayList<>();
    EasyMock.expect(route.getRewrites()).andReturn(filters).anyTimes();
    svcRoutes.add(route);
    EasyMock.replay(route);
    EasyMock.expect(svcDef.getRoutes()).andReturn(svcRoutes).anyTimes();
    CustomDispatch cd = EasyMock.createNiceMock(CustomDispatch.class);
    EasyMock.expect(cd.getClassName()).andReturn("TestDispatch").anyTimes();
    EasyMock.expect(cd.getHaClassName()).andReturn("TestHADispatch").anyTimes();
    EasyMock.expect(cd.getHaContributorName()).andReturn(null).anyTimes();

    // Let useTwoWaySsl be FALSE by default
    EasyMock.expect(cd.getUseTwoWaySsl()).andReturn(false).anyTimes();

    EasyMock.replay(cd);
    EasyMock.expect(svcDef.getDispatch()).andReturn(cd).anyTimes();
    EasyMock.replay(svcDef);

    ServiceDefinitionDeploymentContributor sddc = new ServiceDefinitionDeploymentContributor(svcDef, svcRules);

    DeploymentContext context = EasyMock.createNiceMock(DeploymentContext.class);
    EasyMock.expect(context.getDescriptor("rewrite")).andReturn(clusterRules).anyTimes();
    GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gc.isXForwardedEnabled()).andReturn(false).anyTimes();
    EasyMock.expect(gc.isCookieScopingToPathEnabled()).andReturn(false).anyTimes();
    EasyMock.replay(gc);
    EasyMock.expect(context.getGatewayConfig()).andReturn(gc).anyTimes();

    // Configure the HaProvider
    Topology topology = EasyMock.createNiceMock(Topology.class);
    List<Provider> providers = new ArrayList<>();
    Provider haProvider = EasyMock.createNiceMock(Provider.class);
    EasyMock.expect(haProvider.getRole()).andReturn("ha").anyTimes();
    EasyMock.expect(haProvider.isEnabled()).andReturn(true).anyTimes();
    Map<String, String> providerParams = new HashMap<>();
    providerParams.put(TEST_SERVICE_ROLE, "whatever");
    EasyMock.expect(haProvider.getParams()).andReturn(providerParams).anyTimes();

    EasyMock.replay(haProvider);
    providers.add(haProvider);
    EasyMock.expect(topology.getProviders()).andReturn(providers).anyTimes();
    EasyMock.replay(topology);
    EasyMock.expect(context.getTopology()).andReturn(topology).anyTimes();

    TestGatewayDescriptor gd = new TestGatewayDescriptor();
    EasyMock.expect(context.getGatewayDescriptor()).andReturn(gd).anyTimes();
    EasyMock.replay(context);

    // Configure the service with the useTwoWaySsl param to OVERRIDE the value in the service definition
    Service service = EasyMock.createNiceMock(Service.class);
    Map<String, String> svcParams = new HashMap<>();
    svcParams.put(USE_TWO_WAY_SSL_PARAM, "true");
    EasyMock.expect(service.getParams()).andReturn(svcParams).anyTimes();
    EasyMock.expect(service.getName()).andReturn(TEST_SERVICE_NAME).anyTimes();
    EasyMock.replay(service);

    sddc.contributeService(context, service);

    List<ResourceDescriptor> resources = gd.resources();
    assertEquals(1, resources.size());
    ResourceDescriptor res = resources.get(0);
    assertNotNull(res);
    List<FilterDescriptor> filterList = res.filters();
    assertEquals(1, filterList.size());
    FilterDescriptor f = filterList.get(0);
    assertNotNull(f);
    assertEquals("dispatch", f.role());
    List<FilterParamDescriptor> fParams = f.params();
    assertNotNull(fParams);

    // Collect the values of filter params named useTwoWaySsl
    List<String> useTwoWaySslFilterParamValues = new ArrayList<>();
    for (FilterParamDescriptor param : fParams) {
      if (param.name().equals(USE_TWO_WAY_SSL_PARAM)) {
        useTwoWaySslFilterParamValues.add(param.value());
      }
    }

    assertEquals("Expected only a single filter param named " + USE_TWO_WAY_SSL_PARAM,
                 1, useTwoWaySslFilterParamValues.size());
    assertEquals("Expected the service param to override the service definition value for " + USE_TWO_WAY_SSL_PARAM,
                 "true", useTwoWaySslFilterParamValues.get(0));
  }

  /*
   * Test for a feature that sets dispatch the services defined in a
   * topology. The dispatch is defined per service in a topology.
   * @since 1.2.0
   */
  @Test
  public void testTopologyDispatch() throws Exception {

    final String TEST_SERVICE_ROLE     = "Test";
    final String TEST_SERVICE_NAME     = "Test";
    final String DISPATCH = "dispatch-impl";
    final String EXPECTED_DISPATCH_CLASS = "org.apache.knox.gateway.hdfs.dispatch.HdfsHttpClientDispatch";
    final String EXPECTED_HA_DISPATCH_CLASS = "org.apache.knox.gateway.hdfs.dispatch.HdfsUIHaDispatch";

    UrlRewriteRulesDescriptor clusterRules = EasyMock.createNiceMock(UrlRewriteRulesDescriptor.class);
    EasyMock.replay(clusterRules);

    UrlRewriteRulesDescriptor svcRules = EasyMock.createNiceMock(UrlRewriteRulesDescriptor.class);
    EasyMock.replay(svcRules);

    ServiceDefinition svcDef = EasyMock.createNiceMock(ServiceDefinition.class);
    EasyMock.expect(svcDef.getRole()).andReturn(TEST_SERVICE_ROLE).anyTimes();
    List<Route> svcRoutes = new ArrayList<>();
    Route route = EasyMock.createNiceMock(Route.class);
    List<Rewrite> filters = new ArrayList<>();
    EasyMock.expect(route.getRewrites()).andReturn(filters).anyTimes();
    svcRoutes.add(route);
    EasyMock.replay(route);
    EasyMock.expect(svcDef.getRoutes()).andReturn(svcRoutes).anyTimes();

    CustomDispatch serviceDispatch = EasyMock.createNiceMock(CustomDispatch.class);
    EasyMock.expect(serviceDispatch.getClassName()).andReturn("TestDispatch").anyTimes();
    EasyMock.expect(serviceDispatch.getHaClassName()).andReturn("TestHADispatch").anyTimes();
    EasyMock.expect(serviceDispatch.getHaContributorName()).andReturn(null).anyTimes();
    EasyMock.expect(serviceDispatch.getUseTwoWaySsl()).andReturn(false).anyTimes();
    EasyMock.replay(serviceDispatch);

    EasyMock.expect(svcDef.getDispatch()).andReturn(serviceDispatch).anyTimes();
    EasyMock.replay(svcDef);

    ServiceDefinitionDeploymentContributor sddc = new ServiceDefinitionDeploymentContributor(svcDef, svcRules);

    DeploymentContext context = EasyMock.createNiceMock(DeploymentContext.class);
    EasyMock.expect(context.getDescriptor("rewrite")).andReturn(clusterRules).anyTimes();
    GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gc.isXForwardedEnabled()).andReturn(false).anyTimes();
    EasyMock.expect(gc.isCookieScopingToPathEnabled()).andReturn(false).anyTimes();
    EasyMock.replay(gc);
    EasyMock.expect(context.getGatewayConfig()).andReturn(gc).anyTimes();

    /* dispatch configured in topology */
    CustomDispatch topologyDispatch = EasyMock.createNiceMock(CustomDispatch.class);
    EasyMock.expect(topologyDispatch.getClassName()).andReturn(EXPECTED_DISPATCH_CLASS).anyTimes();
    EasyMock.expect(topologyDispatch.getHaClassName()).andReturn(EXPECTED_HA_DISPATCH_CLASS).anyTimes();
    EasyMock.expect(topologyDispatch.getHaContributorName()).andReturn(null).anyTimes();
    EasyMock.expect(topologyDispatch.getUseTwoWaySsl()).andReturn(false).anyTimes();
    EasyMock.replay(topologyDispatch);

    // Configure the topology
    Topology topology = EasyMock.createNiceMock(Topology.class);
    List<Provider> providers = new ArrayList<>();
    Provider haProvider = EasyMock.createNiceMock(Provider.class);
    EasyMock.expect(haProvider.getRole()).andReturn("ha").anyTimes();
    EasyMock.expect(haProvider.isEnabled()).andReturn(true).anyTimes();
    Map<String, String> providerParams = new HashMap<>();
    providerParams.put(TEST_SERVICE_ROLE, "whatever");
    EasyMock.expect(haProvider.getParams()).andReturn(providerParams).anyTimes();

    EasyMock.replay(haProvider);
    providers.add(haProvider);
    EasyMock.expect(topology.getProviders()).andReturn(providers).anyTimes();
    /* mock topology dispatch */
    //EasyMock.expect(topology.getDispatch()).andReturn(topologyDispatch).anyTimes();
    EasyMock.replay(topology);
    EasyMock.expect(context.getTopology()).andReturn(topology).anyTimes();

    TestGatewayDescriptor gd = new TestGatewayDescriptor();
    EasyMock.expect(context.getGatewayDescriptor()).andReturn(gd).anyTimes();
    EasyMock.replay(context);

    // Configure the service with the useTwoWaySsl param to OVERRIDE the value in the service definition
    Service service = EasyMock.createNiceMock(Service.class);
    Map<String, String> svcParams = new HashMap<>();
    EasyMock.expect(service.getParams()).andReturn(svcParams).anyTimes();
    EasyMock.expect(service.getRole()).andReturn(TEST_SERVICE_ROLE).anyTimes();
    EasyMock.expect(service.getName()).andReturn(TEST_SERVICE_NAME).anyTimes();
    EasyMock.expect(service.getUrl()).andReturn("http://localhost:8081").anyTimes();
    EasyMock.expect(service.getDispatch()).andReturn(topologyDispatch).anyTimes();
    EasyMock.replay(service);

    sddc.contributeService(context, service);

    List<ResourceDescriptor> resources = gd.resources();
    assertEquals(1, resources.size());
    ResourceDescriptor res = resources.get(0);
    assertNotNull(res);
    List<FilterDescriptor> filterList = res.filters();
    assertEquals(1, filterList.size());
    FilterDescriptor f = filterList.get(0);
    assertNotNull(f);
    assertEquals("dispatch", f.role());
    List<FilterParamDescriptor> fParams = f.params();
    assertNotNull(fParams);

    // Collect the values of filter params named useTwoWaySsl
    List<String> dispatchImpl = new ArrayList<>();
    for (FilterParamDescriptor param : fParams) {
      if (param.name().equals(DISPATCH)) {
        dispatchImpl.add(param.value());
      }
    }

    assertEquals("Expected the topology dispatch to override the service definition value for dispatch ",
        EXPECTED_HA_DISPATCH_CLASS, dispatchImpl.get(0));

  }

  @Test
  public void testServiceAttributeStickyCookiePath() throws Exception {
    checkServiceAttributeStickyCookiePath(Arrays.asList(new String[] {"foo"}), "foo");
    checkServiceAttributeStickyCookiePath(Arrays.asList(new String[] {"foobar", "foobaz"}), "fooba");
    checkServiceAttributeStickyCookiePath(Arrays.asList(new String[] {"foo/**/bar", "foo/**/bar"}), "foo/");
    checkServiceAttributeStickyCookiePath(Arrays.asList(new String[] {"/foo/{**}?{**}"}), "/foo/");
    checkServiceAttributeStickyCookiePath(Arrays.asList(new String[] {"/hbase/webui/prof-output/{**}?{**}", "/hbase/webui/static/**", "/hbase/webui?"}), "/hbase/webui");

    // There is currently no service definition like this
    checkServiceAttributeStickyCookiePath(Arrays.asList(new String[] {"foo", "bar"}), "test");

    // These can not happen in a real service definition
    checkServiceAttributeStickyCookiePath(Arrays.asList(new String[] {}), "test");
    checkServiceAttributeStickyCookiePath(Arrays.asList(new String[] {"foo", null}), "test");
  }

  private void checkServiceAttributeStickyCookiePath(List<String> paths, String stickyCookiePath) throws Exception {
    final String TEST_SERVICE_ROLE     = "Test";
    final String TEST_SERVICE_NAME     = "Test";

    UrlRewriteRulesDescriptor clusterRules = EasyMock.createNiceMock(UrlRewriteRulesDescriptor.class);
    EasyMock.replay(clusterRules);

    UrlRewriteRulesDescriptor svcRules = EasyMock.createNiceMock(UrlRewriteRulesDescriptor.class);
    EasyMock.replay(svcRules);

    ServiceDefinition svcDef = EasyMock.createNiceMock(ServiceDefinition.class);
    EasyMock.expect(svcDef.getRole()).andReturn(TEST_SERVICE_ROLE).anyTimes();
    List<Route> svcRoutes = new ArrayList<>();

    for (String path : paths) {
      Route route = EasyMock.createNiceMock(Route.class);
      List<Rewrite> filters = new ArrayList<>();
      EasyMock.expect(route.getRewrites()).andReturn(filters).anyTimes();
      EasyMock.expect(route.getPath()).andReturn(path).anyTimes();
      svcRoutes.add(route);
      EasyMock.replay(route);
    }
    EasyMock.expect(svcDef.getRoutes()).andReturn(svcRoutes).anyTimes();
    CustomDispatch cd = EasyMock.createNiceMock(CustomDispatch.class);
    EasyMock.expect(cd.getClassName()).andReturn("TestDispatch").anyTimes();
    EasyMock.expect(cd.getHaClassName()).andReturn("TestHADispatch").anyTimes();
    EasyMock.expect(cd.getHaContributorName()).andReturn(null).anyTimes();

    EasyMock.replay(cd);
    EasyMock.expect(svcDef.getDispatch()).andReturn(cd).anyTimes();
    EasyMock.replay(svcDef);

    ServiceDefinitionDeploymentContributor sddc = new ServiceDefinitionDeploymentContributor(svcDef, svcRules);

    DeploymentContext context = EasyMock.createNiceMock(DeploymentContext.class);
    EasyMock.expect(context.getDescriptor("rewrite")).andReturn(clusterRules).anyTimes();
    GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gc.isXForwardedEnabled()).andReturn(false).anyTimes();
    EasyMock.expect(gc.isCookieScopingToPathEnabled()).andReturn(false).anyTimes();
    EasyMock.replay(gc);
    EasyMock.expect(context.getGatewayConfig()).andReturn(gc).anyTimes();

    // Configure the HaProvider
    Topology topology = EasyMock.createNiceMock(Topology.class);
    List<Provider> providers = new ArrayList<>();
    Provider haProvider = EasyMock.createNiceMock(Provider.class);
    EasyMock.expect(haProvider.getRole()).andReturn("ha").anyTimes();
    EasyMock.expect(haProvider.isEnabled()).andReturn(true).anyTimes();
    Map<String, String> providerParams = new HashMap<>();
    providerParams.put(TEST_SERVICE_ROLE, "whatever");
    EasyMock.expect(haProvider.getParams()).andReturn(providerParams).anyTimes();

    EasyMock.replay(haProvider);
    providers.add(haProvider);
    EasyMock.expect(topology.getProviders()).andReturn(providers).anyTimes();
    EasyMock.replay(topology);
    EasyMock.expect(context.getTopology()).andReturn(topology).anyTimes();

    TestGatewayDescriptor gd = new TestGatewayDescriptor();
    EasyMock.expect(context.getGatewayDescriptor()).andReturn(gd).anyTimes();
    EasyMock.replay(context);

    Service service = EasyMock.createNiceMock(Service.class);
    Map<String, String> svcParams = new HashMap<>();
    EasyMock.expect(service.getParams()).andReturn(svcParams).anyTimes();
    EasyMock.expect(service.getName()).andReturn(TEST_SERVICE_NAME).anyTimes();
    EasyMock.replay(service);

    sddc.contributeService(context, service);

    assertEquals(paths.size(), gd.resources().size());
    for (ResourceDescriptor res : gd.resources()) {
      assertNotNull(res);
      List<FilterDescriptor> filterList = res.filters();
      assertEquals(1, filterList.size());
      FilterDescriptor f = filterList.get(0);
      assertNotNull(f);
      assertEquals("dispatch", f.role());
      List<FilterParamDescriptor> fParams = f.params();
      assertNotNull(fParams);

      Map<String, String> fparamKeyVal = new HashMap<>();
      for(FilterParamDescriptor fparam : fParams) {
        fparamKeyVal.put(fparam.name(), fparam.value());
        }
      assertEquals(stickyCookiePath, fparamKeyVal.get("stickyCookiePath"));
    }
  }

  @Test
  public void testServiceAttributeParameters() throws Exception {
    final String TEST_SERVICE_ROLE     = "Test";
    final String TEST_SERVICE_NAME     = "Test";

    UrlRewriteRulesDescriptor clusterRules = EasyMock.createNiceMock(UrlRewriteRulesDescriptor.class);
    EasyMock.replay(clusterRules);

    UrlRewriteRulesDescriptor svcRules = EasyMock.createNiceMock(UrlRewriteRulesDescriptor.class);
    EasyMock.replay(svcRules);

    ServiceDefinition svcDef = EasyMock.createNiceMock(ServiceDefinition.class);
    EasyMock.expect(svcDef.getRole()).andReturn(TEST_SERVICE_ROLE).anyTimes();
    List<Route> svcRoutes = new ArrayList<>();
    Route route = EasyMock.createNiceMock(Route.class);
    List<Rewrite> filters = new ArrayList<>();
    EasyMock.expect(route.getRewrites()).andReturn(filters).anyTimes();
    svcRoutes.add(route);
    EasyMock.replay(route);
    EasyMock.expect(svcDef.getRoutes()).andReturn(svcRoutes).anyTimes();
    CustomDispatch cd = EasyMock.createNiceMock(CustomDispatch.class);
    EasyMock.expect(cd.getClassName()).andReturn("TestDispatch").anyTimes();
    EasyMock.expect(cd.getHaClassName()).andReturn("TestHADispatch").anyTimes();
    EasyMock.expect(cd.getHaContributorName()).andReturn(null).anyTimes();

    EasyMock.replay(cd);
    EasyMock.expect(svcDef.getDispatch()).andReturn(cd).anyTimes();
    EasyMock.replay(svcDef);

    ServiceDefinitionDeploymentContributor sddc = new ServiceDefinitionDeploymentContributor(svcDef, svcRules);

    DeploymentContext context = EasyMock.createNiceMock(DeploymentContext.class);
    EasyMock.expect(context.getDescriptor("rewrite")).andReturn(clusterRules).anyTimes();
    GatewayConfig gc = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gc.isXForwardedEnabled()).andReturn(false).anyTimes();
    EasyMock.expect(gc.isCookieScopingToPathEnabled()).andReturn(false).anyTimes();
    EasyMock.replay(gc);
    EasyMock.expect(context.getGatewayConfig()).andReturn(gc).anyTimes();

    // Configure the HaProvider
    Topology topology = EasyMock.createNiceMock(Topology.class);
    List<Provider> providers = new ArrayList<>();
    Provider haProvider = EasyMock.createNiceMock(Provider.class);
    EasyMock.expect(haProvider.getRole()).andReturn("ha").anyTimes();
    EasyMock.expect(haProvider.isEnabled()).andReturn(true).anyTimes();
    Map<String, String> providerParams = new HashMap<>();
    providerParams.put(TEST_SERVICE_ROLE, "whatever");
    EasyMock.expect(haProvider.getParams()).andReturn(providerParams).anyTimes();

    EasyMock.replay(haProvider);
    providers.add(haProvider);
    EasyMock.expect(topology.getProviders()).andReturn(providers).anyTimes();
    EasyMock.replay(topology);
    EasyMock.expect(context.getTopology()).andReturn(topology).anyTimes();

    TestGatewayDescriptor gd = new TestGatewayDescriptor();
    EasyMock.expect(context.getGatewayDescriptor()).andReturn(gd).anyTimes();
    EasyMock.replay(context);

    // Configure the service with the useTwoWaySsl param to OVERRIDE the value in the service definition
    Service service = EasyMock.createNiceMock(Service.class);
    Map<String, String> svcParams = new HashMap<>();
    svcParams.put("test1", "test1abc");
    svcParams.put("test2", "test2def");
    EasyMock.expect(service.getParams()).andReturn(svcParams).anyTimes();
    EasyMock.expect(service.getName()).andReturn(TEST_SERVICE_NAME).anyTimes();
    EasyMock.replay(service);

    sddc.contributeService(context, service);

    assertEquals(1, gd.resources().size());
    ResourceDescriptor res = gd.resources().get(0);
    assertNotNull(res);
    List<FilterDescriptor> filterList = res.filters();
    assertEquals(1, filterList.size());
    FilterDescriptor f = filterList.get(0);
    assertNotNull(f);
    assertEquals("dispatch", f.role());
    List<FilterParamDescriptor> fParams = f.params();
    assertNotNull(fParams);

    Map<String, String> fparamKeyVal = new HashMap<>();
    for(FilterParamDescriptor fparam : fParams) {
      fparamKeyVal.put(fparam.name(), fparam.value());
    }

    assertEquals("test1abc", fparamKeyVal.get("test1"));
    assertEquals("test2def", fparamKeyVal.get("test2"));
  }

  private static class TestGatewayDescriptor extends GatewayDescriptorImpl {
  }

}
