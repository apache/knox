/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.ha.deploy;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.ProviderDeploymentContributor;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.GatewayDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.ha.provider.HaDescriptor;
import org.apache.knox.gateway.ha.provider.HaServiceConfig;
import org.apache.knox.gateway.ha.provider.impl.HaServiceConfigConstants;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.easymock.EasyMock;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.descriptor.api.webapp30.WebAppDescriptor;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class HaProviderDeploymentContributorTest {

   @Test
   public void testServiceLoader() throws Exception {
      ServiceLoader loader = ServiceLoader.load( ProviderDeploymentContributor.class );
      Iterator iterator = loader.iterator();
      assertThat( "Service iterator empty.", iterator.hasNext() );
      while( iterator.hasNext() ) {
         Object object = iterator.next();
         if( object instanceof HaProviderDeploymentContributor ) {
            return;
         }
      }
      fail( "Failed to find " + HaProviderDeploymentContributor.class.getName() + " via service loader." );
   }

   /*
    * Basically, a backward-compatibility test to ensure that HaProvider service params specified ONLY at the provider
    * level still work.
    */
   @Test
   public void testProviderLevelParams() throws Exception {
      // Define some provider params
      Map<String, String> providerParams = new HashMap<>();

      // Specify all the possible params at the HaProvider level for TestRoleTwo
      providerParams.put("TestRoleOne", getHaProviderParamValue(false, 40, 4, "testRoleOne", "http://host1:2181,http://host2:2181"));

      Provider haProvider = createHaProvider(providerParams);

      // Define the topology content (e.g., services)
      Collection<Service> topologyServices = new HashSet<>();

      // A service with no param overrides
      Service testRoleOneService = EasyMock.createNiceMock(Service.class);
      EasyMock.expect(testRoleOneService.getRole()).andReturn("TestRoleOne").anyTimes();
      EasyMock.expect(testRoleOneService.getName()).andReturn("TestRoleOneService").anyTimes();
      EasyMock.expect(testRoleOneService.getParams()).andReturn(Collections.emptyMap()).anyTimes();
      EasyMock.replay(testRoleOneService);
      topologyServices.add(testRoleOneService);

      Topology topology = EasyMock.createNiceMock(Topology.class);
      EasyMock.expect(topology.getServices()).andReturn(topologyServices).anyTimes();
      EasyMock.replay(topology);

      WebArchive war = EasyMock.createNiceMock(WebArchive.class);
      EasyMock.replay(war);

      DeploymentContext context = new DescriptorCaptureDeploymentContext(topology, war);

      // Invoke the contributor
      HaProviderDeploymentContributor haPDC = new HaProviderDeploymentContributor();
      haPDC.contributeProvider(context, haProvider);

      HaDescriptor descriptor = context.getDescriptor("ha.provider.descriptor");
      assertNotNull(descriptor);
      assertEquals(1, descriptor.getServiceConfigs().size());

      validateServiceHaConfig(descriptor.getServiceConfig("TestRoleOne"),
                              false, 40, 4, "testRoleOne", "http://host1:2181,http://host2:2181");
   }

   /*
    * Simple test verifying that HaProvider service params specified ONLY at the service level works.
    */
   @Test
   public void testServiceLevelParamOverrides_NoProviderParams() throws Exception {
      // Define some provider params
      Map<String, String> providerParams = new HashMap<>();

      // Specify all the possible params at the HaProvider level for TestRoleTwo
      providerParams.put("TestRoleOne","");

      Provider haProvider = createHaProvider(providerParams);

      // Define the topology content (e.g., services)
      Collection<Service> topologyServices = new HashSet<>();

      // Specify all the possible params in the TestRoleOne service level
      Map<String, String> testRoleOneParams = new HashMap<>();
      testRoleOneParams.put(Service.HA_ENABLED_PARAM, "true");
      testRoleOneParams.put(HaServiceConfigConstants.CONFIG_PARAM_MAX_FAILOVER_ATTEMPTS, "8");
      testRoleOneParams.put(HaServiceConfigConstants.CONFIG_PARAM_FAILOVER_SLEEP, "80");
      testRoleOneParams.put(HaServiceConfigConstants.CONFIG_PARAM_ZOOKEEPER_NAMESPACE, "testRoleOneOverride");
      testRoleOneParams.put(HaServiceConfigConstants.CONFIG_PARAM_ZOOKEEPER_ENSEMBLE, "http://host3:2181,http://host4:2181");

      // A service with all the params overriden
      Service testRoleOneService = EasyMock.createNiceMock(Service.class);
      EasyMock.expect(testRoleOneService.getRole()).andReturn("TestRoleOne").anyTimes();
      EasyMock.expect(testRoleOneService.getName()).andReturn("TestRoleOneService").anyTimes();
      EasyMock.expect(testRoleOneService.getParams()).andReturn(testRoleOneParams).anyTimes();
      EasyMock.replay(testRoleOneService);
      topologyServices.add(testRoleOneService);

      Topology topology = EasyMock.createNiceMock(Topology.class);
      EasyMock.expect(topology.getServices()).andReturn(topologyServices).anyTimes();
      EasyMock.replay(topology);

      WebArchive war = EasyMock.createNiceMock(WebArchive.class);
      EasyMock.replay(war);

      DeploymentContext context = new DescriptorCaptureDeploymentContext(topology, war);

      // Invoke the contributor
      HaProviderDeploymentContributor haPDC = new HaProviderDeploymentContributor();
      haPDC.contributeProvider(context, haProvider);

      HaDescriptor descriptor = context.getDescriptor("ha.provider.descriptor");
      assertNotNull(descriptor);
      assertEquals(1, descriptor.getServiceConfigs().size());

      validateServiceHaConfig(descriptor.getServiceConfig("TestRoleOne"),
                              true, 80, 8, "testRoleOneOverride", "http://host3:2181,http://host4:2181");
   }

   /*
    * Verify a mixture of provider-level params and service-level params.
    */
   @Test
   public void testServiceLevelParamOverrides_SubsetProviderParams() throws Exception {
      // Define some provider params
      Map<String, String> providerParams = new HashMap<>();

      // Specify all the possible params at the HaProvider level for TestRoleTwo
      providerParams.put("TestRoleOne", getHaProviderParamValue(false, 40, 4));

      Provider haProvider = createHaProvider(providerParams);

      // Define the topology content (e.g., services)
      Collection<Service> topologyServices = new HashSet<>();

      // Specify all the possible params in the TestRoleOne service level
      Map<String, String> testRoleOneParams = new HashMap<>();
      testRoleOneParams.put(Service.HA_ENABLED_PARAM, "true");
      testRoleOneParams.put(HaServiceConfigConstants.CONFIG_PARAM_ZOOKEEPER_NAMESPACE, "testRoleOneOverride");
      testRoleOneParams.put(HaServiceConfigConstants.CONFIG_PARAM_ZOOKEEPER_ENSEMBLE, "http://host3:2181,http://host4:2181");

      // A service with all the params overriden
      Service testRoleOneService = EasyMock.createNiceMock(Service.class);
      EasyMock.expect(testRoleOneService.getRole()).andReturn("TestRoleOne").anyTimes();
      EasyMock.expect(testRoleOneService.getName()).andReturn("TestRoleOneService").anyTimes();
      EasyMock.expect(testRoleOneService.getParams()).andReturn(testRoleOneParams).anyTimes();
      EasyMock.replay(testRoleOneService);
      topologyServices.add(testRoleOneService);

      Topology topology = EasyMock.createNiceMock(Topology.class);
      EasyMock.expect(topology.getServices()).andReturn(topologyServices).anyTimes();
      EasyMock.replay(topology);

      WebArchive war = EasyMock.createNiceMock(WebArchive.class);
      EasyMock.replay(war);

      DeploymentContext context = new DescriptorCaptureDeploymentContext(topology, war);

      // Invoke the contributor
      HaProviderDeploymentContributor haPDC = new HaProviderDeploymentContributor();
      haPDC.contributeProvider(context, haProvider);

      HaDescriptor descriptor = context.getDescriptor("ha.provider.descriptor");
      assertNotNull(descriptor);
      assertEquals(1, descriptor.getServiceConfigs().size());

      validateServiceHaConfig(descriptor.getServiceConfig("TestRoleOne"),
                              true, 40, 4, "testRoleOneOverride", "http://host3:2181,http://host4:2181");
   }

   @Test
   public void testServiceLevelParamOverrides_MultipleMixed() {
      // Define some provider params
      Map<String, String> providerParams = new HashMap<>();

      // Specify a subset of the possible HaProvider-level params for TestRoleOne
      providerParams.put("TestRoleOne", getHaProviderParamValue(true, 20, 2));

      // Specify all the possible params at the HaProvider level for TestRoleTwo
      providerParams.put("TestRoleTwo",
                         getHaProviderParamValue(false, 40, 4, "testRoleTwo", "http://host1:2181,http://host2:2181"));

      Provider testHaProvider = createHaProvider(providerParams);

      // Define the topology content (e.g., services)
      Collection<Service> topologyServices = new HashSet<>();

      // A service with no param overrides
      Service testRoleOneService = EasyMock.createNiceMock(Service.class);
      EasyMock.expect(testRoleOneService.getRole()).andReturn("TestRoleOne").anyTimes();
      EasyMock.expect(testRoleOneService.getName()).andReturn("TestRoleOneService").anyTimes();
      EasyMock.expect(testRoleOneService.getParams()).andReturn(Collections.emptyMap()).anyTimes();
      EasyMock.replay(testRoleOneService);
      topologyServices.add(testRoleOneService);

      // Override all the possible params in the TestRoleTwo service level
      Map<String, String> testRoleTwoParams = new HashMap<>();
      testRoleTwoParams.put(Service.HA_ENABLED_PARAM, "true");
      testRoleTwoParams.put(HaServiceConfigConstants.CONFIG_PARAM_MAX_FAILOVER_ATTEMPTS, "8");
      testRoleTwoParams.put(HaServiceConfigConstants.CONFIG_PARAM_FAILOVER_SLEEP, "80");
      testRoleTwoParams.put(HaServiceConfigConstants.CONFIG_PARAM_ZOOKEEPER_NAMESPACE, "testRoleTwoOverride");
      testRoleTwoParams.put(HaServiceConfigConstants.CONFIG_PARAM_ZOOKEEPER_ENSEMBLE, "http://host3:2181,http://host4:2181");

      Service testRoleTwoService = EasyMock.createNiceMock(Service.class);
      EasyMock.expect(testRoleTwoService.getRole()).andReturn("TestRoleTwo").anyTimes();
      EasyMock.expect(testRoleTwoService.getName()).andReturn("TestRoleTwoService").anyTimes();
      EasyMock.expect(testRoleTwoService.getParams()).andReturn(testRoleTwoParams).anyTimes();
      EasyMock.replay(testRoleTwoService);
      topologyServices.add(testRoleTwoService);

      Topology topology = EasyMock.createNiceMock(Topology.class);
      EasyMock.expect(topology.getServices()).andReturn(topologyServices).anyTimes();
      EasyMock.replay(topology);

      WebArchive war = EasyMock.createNiceMock(WebArchive.class);
      EasyMock.replay(war);

      DeploymentContext context = new DescriptorCaptureDeploymentContext(topology, war);

      // Invoke the contributor
      HaProviderDeploymentContributor haPDC = new HaProviderDeploymentContributor();
      haPDC.contributeProvider(context, testHaProvider);

      HaDescriptor descriptor = context.getDescriptor("ha.provider.descriptor");
      assertNotNull(descriptor);
      assertEquals(2, descriptor.getServiceConfigs().size());

      // Validate the service with no-overrides, checking that the provider-level defaults are applied
      validateServiceHaConfig(descriptor.getServiceConfig("TestRoleOne"),
                              true, 20, 2, null, null);

      // Validate the service with all-overrides, checking that the service-level defaults are applied
      validateServiceHaConfig(descriptor.getServiceConfig("TestRoleTwo"),
                              true, 80, 8, "testRoleTwoOverride", "http://host3:2181,http://host4:2181");
   }

   private static String getHaProviderParamValue(boolean enabled,
                                                 long    failoverSleep,
                                                 int     maxFailoverAttempts) {
      return getHaProviderParamValue(enabled, failoverSleep, maxFailoverAttempts, null, null);
   }

   private static String getHaProviderParamValue(boolean enabled,
                                                 long    failoverSleep,
                                                 int     maxFailoverAttempts,
                                                 String  zooKeeperNamespace,
                                                 String  zooKeeperEnsemble) {
      StringBuilder builder = new StringBuilder();

      builder.append(HaServiceConfigConstants.CONFIG_PARAM_ENABLED)
          .append(HaServiceConfigConstants.CONFIG_PAIR_DELIMITER)
          .append(enabled);

      if (maxFailoverAttempts > -1) {
         builder.append(HaServiceConfigConstants.CONFIG_PAIRS_DELIMITER)
             .append(HaServiceConfigConstants.CONFIG_PARAM_MAX_FAILOVER_ATTEMPTS)
             .append(HaServiceConfigConstants.CONFIG_PAIR_DELIMITER)
             .append(maxFailoverAttempts);
      }

      if (failoverSleep > -1) {
         builder.append(HaServiceConfigConstants.CONFIG_PAIRS_DELIMITER)
             .append(HaServiceConfigConstants.CONFIG_PARAM_FAILOVER_SLEEP)
             .append(HaServiceConfigConstants.CONFIG_PAIR_DELIMITER)
             .append(failoverSleep);
      }

      if (zooKeeperNamespace != null) {
         builder.append(HaServiceConfigConstants.CONFIG_PAIRS_DELIMITER)
             .append(HaServiceConfigConstants.CONFIG_PARAM_ZOOKEEPER_NAMESPACE)
             .append(HaServiceConfigConstants.CONFIG_PAIR_DELIMITER)
             .append(zooKeeperNamespace);
      }

      if (zooKeeperEnsemble != null) {
         builder.append(HaServiceConfigConstants.CONFIG_PAIRS_DELIMITER)
             .append(HaServiceConfigConstants.CONFIG_PARAM_ZOOKEEPER_ENSEMBLE)
             .append(HaServiceConfigConstants.CONFIG_PAIR_DELIMITER)
             .append(zooKeeperEnsemble);
      }

      return builder.toString();
   }

   /**
    * Validate the service ha config.
    * @param config              The HaServiceConfig to validate
    * @param isEnabled           The expected enabled param value
    * @param failoverSleep       The expected failoverSleep param value
    * @param maxFailoverAttempts The expected maxFailoverAttempts param value
    * @param zookeeperNamespace  The expected zookeeperNamespace param value
    * @param zookeeperEnsemble   The expected zookeeperEnsemble param value
    */
   private static void validateServiceHaConfig(HaServiceConfig config,
                                               boolean         isEnabled,
                                               int             failoverSleep,
                                               int             maxFailoverAttempts,
                                               String          zookeeperNamespace,
                                               String          zookeeperEnsemble) {
      assertNotNull(config);
      assertEquals(isEnabled, config.isEnabled());
      assertEquals(failoverSleep, config.getFailoverSleep());
      assertEquals(maxFailoverAttempts, config.getMaxFailoverAttempts());

      if (zookeeperNamespace == null) {
         assertNull(config.getZookeeperNamespace());
      } else {
         assertEquals(zookeeperNamespace, config.getZookeeperNamespace());
      }

      if (zookeeperEnsemble== null) {
         assertNull(config.getZookeeperEnsemble());
      } else {
         assertEquals(zookeeperEnsemble, config.getZookeeperEnsemble());
      }
   }

   private static Provider createHaProvider(Map<String, String> params) {
      Provider provider = EasyMock.createNiceMock(Provider.class);
      EasyMock.expect(provider.getRole()).andReturn("ha").anyTimes();
      EasyMock.expect(provider.getName()).andReturn("HaProvider").anyTimes();
      EasyMock.expect(provider.getParams()).andReturn(params).anyTimes();
      EasyMock.replay(provider);
      return provider;
   }

   private static class DescriptorCaptureDeploymentContext implements DeploymentContext {

      private Topology topology;
      private WebArchive war;
      private Map<String, Object> descriptors = new HashMap<>();

      DescriptorCaptureDeploymentContext(Topology topology, WebArchive war) {
         this.topology = topology;
         this.war      = war;
      }

      @Override
      public GatewayConfig getGatewayConfig() {
         return null;
      }

      @Override
      public Topology getTopology() {
         return topology;
      }

      @Override
      public WebArchive getWebArchive() {
         return war;
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
      public void contributeFilter(Service service, ResourceDescriptor resource, String role, String name, List<FilterParamDescriptor> params) {

      }

      @Override
      public void addDescriptor(String name, Object descriptor) {
         descriptors.put(name, descriptor);
      }

      @Override
      public <T> T getDescriptor(String name) {
         return (T)descriptors.get(name);
      }
   }
}
