/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.discovery.cm.monitor;

import com.cloudera.api.swagger.client.ApiClient;
import com.cloudera.api.swagger.model.ApiEvent;
import com.cloudera.api.swagger.model.ApiEventAttribute;
import com.cloudera.api.swagger.model.ApiEventCategory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.knox.gateway.GatewayServer;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.topology.TopologyService;
import org.apache.knox.gateway.topology.ClusterConfigurationMonitorService;
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;
import org.apache.knox.gateway.topology.discovery.cm.model.hdfs.NameNodeServiceModelGenerator;
import org.apache.knox.gateway.topology.discovery.cm.model.hive.HiveOnTezServiceModelGenerator;
import org.easymock.EasyMock;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.apache.knox.gateway.topology.discovery.ClusterConfigurationMonitor.ConfigurationChangeListener;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class PollingConfigurationAnalyzerTest {

  @Test(expected = IllegalArgumentException.class)
  public void testRestartEventWithWrongApiEventCategory() {
    doTestStartEvent(ApiEventCategory.LOG_EVENT);
  }

  @Test
  public void testStartEvent() {
    doTestStartEvent(ApiEventCategory.AUDIT_EVENT);
  }

  /**
   * KNOX-2350
   */
  @Test
  public void testEventWithoutCommandOrCommandStatus() {
    final String clusterName = "Cluster T";

    // Simulate an event w/o COMMAND and/or COMMAND_STATUS attributes
    final List<ApiEventAttribute> revisionEventAttrs = new ArrayList<>();
    revisionEventAttrs.add(createEventAttribute("CLUSTER", clusterName));
    revisionEventAttrs.add(createEventAttribute("SERVICE_TYPE", HiveOnTezServiceModelGenerator.SERVICE_TYPE));
    revisionEventAttrs.add(createEventAttribute("SERVICE", HiveOnTezServiceModelGenerator.SERVICE));
    revisionEventAttrs.add(createEventAttribute("REVISION", "215"));
    revisionEventAttrs.add(createEventAttribute("EVENTCODE", "EV_REVISION_CREATED"));
    final ApiEvent revisionEvent = createApiEvent(ApiEventCategory.AUDIT_EVENT, revisionEventAttrs, null);

    doTestEventWithoutConfigChange(revisionEvent, clusterName);
  }

  /**
   * Test the restart of an existing service when no relevant configuration has changed.
   */
  @Test
  public void testRestartEventWithoutConfigChange() {
    final String clusterName = "Cluster 2";

    // Simulate a service restart event
    ApiEvent restartEvent = createApiEvent(clusterName,
                                           NameNodeServiceModelGenerator.SERVICE_TYPE,
                                           NameNodeServiceModelGenerator.SERVICE,
                                           PollingConfigurationAnalyzer.RESTART_COMMAND,
                                           PollingConfigurationAnalyzer.SUCCEEDED_STATUS);

    doTestEventWithoutConfigChange(restartEvent, clusterName);
  }

  /**
   * Test the restart of an existing service when relevant configuration has changed.
   */
  @Test
  public void testRestartEventWithConfigChange() {
    final String clusterName = "Cluster 2";

    // Simulate a service restart event
    ApiEvent restartEvent = createApiEvent(clusterName,
                                           NameNodeServiceModelGenerator.SERVICE_TYPE,
                                           NameNodeServiceModelGenerator.SERVICE,
                                           PollingConfigurationAnalyzer.RESTART_COMMAND,
                                           PollingConfigurationAnalyzer.SUCCEEDED_STATUS);

    doTestEventWithConfigChange(restartEvent, clusterName);
  }

  /**
   * Test the start of a new service.
   */
  @Test
  public void testNewServiceStartEvent() {
    final String address = "http://host1:1234";
    final String clusterName = "Cluster N";

    // Simulate a service Start event
    ApiEvent startEvent = createApiEvent(clusterName,
                                         NameNodeServiceModelGenerator.SERVICE_TYPE,
                                         NameNodeServiceModelGenerator.SERVICE,
                                         PollingConfigurationAnalyzer.START_COMMAND,
                                         PollingConfigurationAnalyzer.SUCCEEDED_STATUS);

    ChangeListener listener =
            doTestEvent(startEvent, address, clusterName, Collections.emptyMap(), Collections.emptyMap());
    assertTrue("Expected a change notification", listener.wasNotified(address, clusterName));
  }

  /**
   * Test the start of an existing service when no relevant configuration has changed.
   */
  @Test
  public void testExistingServiceStartWithoutConfigChange() {
    final String clusterName = "Cluster E";

    // Simulate a service Start event
    ApiEvent startEvent = createApiEvent(clusterName,
                                         NameNodeServiceModelGenerator.SERVICE_TYPE,
                                         NameNodeServiceModelGenerator.SERVICE,
                                         PollingConfigurationAnalyzer.START_COMMAND,
                                         PollingConfigurationAnalyzer.SUCCEEDED_STATUS);

    doTestEventWithoutConfigChange(startEvent, clusterName);
  }

  /**
   * Test the start of an existing service when relevant configuration has changed.
   */
  @Test
  public void testExistingServiceStartWithConfigChange() {
    final String clusterName = "Cluster E";

    // Simulate a service Start event
    ApiEvent startEvent = createApiEvent(clusterName,
                                         NameNodeServiceModelGenerator.SERVICE_TYPE,
                                         NameNodeServiceModelGenerator.SERVICE,
                                         PollingConfigurationAnalyzer.START_COMMAND,
                                         PollingConfigurationAnalyzer.SUCCEEDED_STATUS);

    doTestEventWithConfigChange(startEvent, clusterName);
  }

  /**
   * Test the rolling restart of an existing service when no relevant configuration has changed.
   */
  @Test
  public void testRollingServiceRestartWithoutConfigChange() {
    final String clusterName = "Cluster 1";

    // Simulate a successful rolling service restart event
    ApiEvent rollingRestartEvent = createApiEvent(clusterName,
                                                  NameNodeServiceModelGenerator.SERVICE_TYPE,
                                                  NameNodeServiceModelGenerator.SERVICE,
                                                  PollingConfigurationAnalyzer.ROLLING_RESTART_COMMAND,
                                                  PollingConfigurationAnalyzer.SUCCEEDED_STATUS,
                                                  "EV_SERVICE_ROLLING_RESTARTED");

    doTestEventWithoutConfigChange(rollingRestartEvent, clusterName);
  }

  /**
   * Test the rolling restart of an existing service when relevant configuration has changed.
   */
  @Test
  public void testRollingServiceRestartWithConfigChange() {
    final String clusterName = "Cluster 1";

    // Simulate a successful rolling service restart event
    ApiEvent rollingRestartEvent = createApiEvent(clusterName,
                                                  NameNodeServiceModelGenerator.SERVICE_TYPE,
                                                  NameNodeServiceModelGenerator.SERVICE,
                                                  PollingConfigurationAnalyzer.ROLLING_RESTART_COMMAND,
                                                  PollingConfigurationAnalyzer.SUCCEEDED_STATUS,
                                                  "EV_SERVICE_ROLLING_RESTARTED");

    doTestEventWithConfigChange(rollingRestartEvent, clusterName);
  }

  /**
   * Test the rolling restart of an entire cluster, for which it should be assumed that configuration has changed.
   */
  @Test
  public void testRollingClusterRestartEvent() {
    final String address = "http://host1:1234";
    final String clusterName = "Cluster 6";

    // Simulate a successful rolling cluster restart event
    ApiEvent rollingRestartEvent = createApiEvent(clusterName,
                                                  PollingConfigurationAnalyzer.CM_SERVICE_TYPE,
                                                  PollingConfigurationAnalyzer.CM_SERVICE,
                                                  PollingConfigurationAnalyzer.ROLLING_RESTART_COMMAND,
                                                  PollingConfigurationAnalyzer.SUCCEEDED_STATUS,
                                                  "EV_CLUSTER_ROLLING_RESTARTED");

    ChangeListener listener =
            doTestEvent(rollingRestartEvent, address, clusterName, Collections.emptyMap(), Collections.emptyMap());
    assertTrue("Expected a change notification", listener.wasNotified(address, clusterName));
  }

  /**
   * Test the restart waiting for staleness, for which it should be assumed that configuration has changed.
   */
  @Test
  public void testRestartWaitingForStalenessSuccessEvent() {
    final String address = "http://host1:1234";
    final String clusterName = "Cluster 8";

    // Simulate a successful restart waiting for staleness event
    final ApiEvent rollingRestartEvent = createApiEvent(clusterName, PollingConfigurationAnalyzer.CM_SERVICE_TYPE, PollingConfigurationAnalyzer.CM_SERVICE,
        PollingConfigurationAnalyzer.RESTART_WAITING_FOR_STALENESS_SUCCESS_COMMAND, PollingConfigurationAnalyzer.SUCCEEDED_STATUS, "EV_CLUSTER_RESTARTED");

    final ChangeListener listener = doTestEvent(rollingRestartEvent, address, clusterName, Collections.emptyMap(), Collections.emptyMap());
    assertTrue("Expected a change notification", listener.wasNotified(address, clusterName));
  }

  @Test
  public void testRestartWaitingForStalenessSuccessEventProcessedOnlyOnce() {
    final String address = "http://host1:1234";
    final String clusterName = "Cluster 9";

    // Simulate a successful restart waiting for staleness event with id = 123
    final ApiEvent rollingRestartEvent = createApiEvent(clusterName, PollingConfigurationAnalyzer.CM_SERVICE_TYPE, PollingConfigurationAnalyzer.CM_SERVICE,
        PollingConfigurationAnalyzer.RESTART_WAITING_FOR_STALENESS_SUCCESS_COMMAND, PollingConfigurationAnalyzer.SUCCEEDED_STATUS, "EV_CLUSTER_RESTARTED",
        "123");

    final ChangeListener listener = new ChangeListener();
    final TestablePollingConfigAnalyzer pca = buildPollingConfigAnalyzer(address, clusterName, Collections.emptyMap(), listener);

    // this should trigger a change notification
    doTestEvent(rollingRestartEvent, address, clusterName, Collections.emptyMap(), Collections.emptyMap(), pca);
    assertTrue("Expected a change notification", listener.wasNotified(address, clusterName));

    // this should NOT trigger a notification as the same event has already been processed
    listener.clearNotification();
    doTestEvent(rollingRestartEvent, address, clusterName, Collections.emptyMap(), Collections.emptyMap(), pca);
    assertFalse("Unexpected change notification", listener.wasNotified(address, clusterName));
  }

  @Test
  public void testClusterConfigMonitorTerminationForNoLongerReferencedClusters() {
    final String address = "http://host1:1234";
    final String clusterName = "Cluster 7";

    final String updatedAddress = "http://host2:1234";
    final String descContent =
        "{\n" +
        "  \"discovery-type\": \"ClouderaManager\",\n" +
        "  \"discovery-address\": \"" + updatedAddress + "\",\n" +
        "  \"cluster\": \"" + clusterName + "\",\n" +
        "  \"provider-config-ref\": \"ldap\",\n" +
        "  \"services\": [\n" +
        "    {\n" +
        "      \"name\": \"WEBHDFS\"\n" +
        "    }\n" +
        "  ]\n" +
        "}";

    File descriptor = null;
    try {
      descriptor = File.createTempFile("test", ".json");
      FileUtils.writeStringToFile(descriptor, descContent, StandardCharsets.UTF_8);
    } catch (IOException e) {
      e.printStackTrace();
    }

    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getIncludedSSLCiphers()).andReturn(Collections.emptyList()).anyTimes();
    EasyMock.expect(gatewayConfig.getIncludedSSLProtocols()).andReturn(Collections.emptySet()).anyTimes();
    EasyMock.replay(gatewayConfig);

    // Mock the service discovery details
    ServiceDiscoveryConfig sdc = EasyMock.createNiceMock(ServiceDiscoveryConfig.class);
    EasyMock.expect(sdc.getCluster()).andReturn(clusterName).anyTimes();
    EasyMock.expect(sdc.getAddress()).andReturn(address).anyTimes();
    EasyMock.expect(sdc.getUser()).andReturn("u").anyTimes();
    EasyMock.expect(sdc.getPasswordAlias()).andReturn("a").anyTimes();
    EasyMock.replay(sdc);

    // Create the original ServiceConfigurationModel details
    final Map<String, ServiceConfigurationModel> serviceConfigurationModels = new HashMap<>();
    final Map<String, String> nnServiceConf = new HashMap<>();
    final Map<String, Map<String, String>> nnRoleConf = new HashMap<>();
    nnRoleConf.put(NameNodeServiceModelGenerator.ROLE_TYPE, Collections.emptyMap());
    serviceConfigurationModels.put(NameNodeServiceModelGenerator.SERVICE_TYPE,
                                   createModel(nnServiceConf, nnRoleConf));

    // Create a ClusterConfigurationCache for the monitor to use
    final ClusterConfigurationCache configCache = new ClusterConfigurationCache();
    configCache.addDiscoveryConfig(sdc);
    configCache.addServiceConfiguration(address, clusterName, serviceConfigurationModels);
    assertEquals(1, configCache.getClusterNames().get(address).size());

    // Set up GatewayServices

    // TopologyService mock
    TopologyService ts = EasyMock.createNiceMock(TopologyService.class);
    EasyMock.expect(ts.getDescriptors()).andReturn(Collections.singletonList(descriptor)).anyTimes();

    // ClusterConfigurationMonitorService mock
    ClusterConfigurationMonitorService ccms = EasyMock.createNiceMock(ClusterConfigurationMonitorService.class);
    // Implement the clearing of the cache for the mock
    ccms.clearCache(address, clusterName);
    EasyMock.expectLastCall().andAnswer(() -> {
                                              Object[] args = getCurrentArguments();
                                              configCache.removeServiceConfiguration((String)args[0], (String)args[1]);
                                              return null;
                                            }).once();

    // GatewayServices mock
    GatewayServices gws = EasyMock.createNiceMock(GatewayServices.class);
    EasyMock.expect(gws.getService(ServiceType.TOPOLOGY_SERVICE)).andReturn(ts).anyTimes();
    EasyMock.expect(gws.getService(ServiceType.CLUSTER_CONFIGURATION_MONITOR_SERVICE)).andReturn(ccms).anyTimes();
    EasyMock.replay(ts, ccms, gws);

    try {
      setGatewayServices(gws);

      // Create the monitor
      TestablePollingConfigAnalyzer pca = new TestablePollingConfigAnalyzer(gatewayConfig, configCache);
      pca.setInterval(5);

      // Start the polling thread
      ExecutorService pollingThreadExecutor = Executors.newSingleThreadExecutor();
      pollingThreadExecutor.execute(pca);
      pollingThreadExecutor.shutdown();

      try {
        pollingThreadExecutor.awaitTermination(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        //
      }

      // Stop the config analyzer thread
      pca.stop();

      if (descriptor != null && descriptor.exists()) {
        descriptor.deleteOnExit();
      }

      assertEquals("Expected the config cache entry for " + clusterName + " to have been removed.",
                   0,
                   configCache.getClusterNames().get(address).size());
    } finally {
      // Reset the GatewayServices field of GatewayServer
      setGatewayServices(null);
    }
  }

  private void doTestStartEvent(final ApiEventCategory category) {
    final String clusterName = "My Cluster";
    final String serviceType = NameNodeServiceModelGenerator.SERVICE_TYPE;
    final String service     = NameNodeServiceModelGenerator.SERVICE;

    List<ApiEventAttribute> apiEventAttrs = new ArrayList<>();
    apiEventAttrs.add(createEventAttribute("CLUSTER", clusterName));
    apiEventAttrs.add(createEventAttribute("SERVICE_TYPE", serviceType));
    apiEventAttrs.add(createEventAttribute("SERVICE", service));
    ApiEvent apiEvent = createApiEvent(category, apiEventAttrs, null);

    PollingConfigurationAnalyzer.StartEvent restartEvent = new PollingConfigurationAnalyzer.StartEvent(apiEvent);
    assertNotNull(restartEvent);
    assertEquals(clusterName, restartEvent.getClusterName());
    assertEquals(serviceType, restartEvent.getServiceType());
    assertEquals(service, restartEvent.getService());
    assertNotNull(restartEvent.getTimestamp());
  }

  private ChangeListener doTestEvent(final ApiEvent event, final String address, final String clusterName,
      final Map<String, ServiceConfigurationModel> serviceConfigurationModels, final Map<String, ServiceConfigurationModel> updatedServiceConfigurationModels) {
    return doTestEvent(event, address, clusterName, serviceConfigurationModels, updatedServiceConfigurationModels, null);
  }

  private ChangeListener doTestEvent(final ApiEvent event, final String address, final String clusterName,
      final Map<String, ServiceConfigurationModel> serviceConfigurationModels, final Map<String, ServiceConfigurationModel> updatedServiceConfigurationModels,
      final TestablePollingConfigAnalyzer pollingConfigAnalyzer) {

    // Create the monitor, registering a listener so we can verify that change notification works
    final ChangeListener listener = new ChangeListener();
    final TestablePollingConfigAnalyzer pca = pollingConfigAnalyzer == null ? buildPollingConfigAnalyzer(address, clusterName, serviceConfigurationModels, listener) : pollingConfigAnalyzer;
    pca.setInterval(5);

    // Add updated service config models
    for (String roleType : updatedServiceConfigurationModels.keySet()) {
      pca.addCurrentServiceConfigModel(address, clusterName, roleType, updatedServiceConfigurationModels.get(roleType));
    }

    // Start the polling thread
    ExecutorService pollingThreadExecutor = Executors.newSingleThreadExecutor();
    pollingThreadExecutor.execute(pca);
    pollingThreadExecutor.shutdown();

    pca.addRestartEvent(clusterName, event);

    try {
      pollingThreadExecutor.awaitTermination(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      //
    }

    // Stop the config analyzer thread
    pca.stop();

    return listener;
  }

  private TestablePollingConfigAnalyzer buildPollingConfigAnalyzer(final String address, final String clusterName,
      final Map<String, ServiceConfigurationModel> serviceConfigurationModels, ChangeListener listener) {
    final GatewayConfig gatewayConfig = EasyMock.createNiceMock(GatewayConfig.class);
    EasyMock.expect(gatewayConfig.getIncludedSSLCiphers()).andReturn(Collections.emptyList()).anyTimes();
    EasyMock.expect(gatewayConfig.getIncludedSSLProtocols()).andReturn(Collections.emptySet()).anyTimes();
    EasyMock.replay(gatewayConfig);

    // Mock the service discovery details
    ServiceDiscoveryConfig sdc = EasyMock.createNiceMock(ServiceDiscoveryConfig.class);
    EasyMock.expect(sdc.getCluster()).andReturn(clusterName).anyTimes();
    EasyMock.expect(sdc.getAddress()).andReturn(address).anyTimes();
    EasyMock.expect(sdc.getUser()).andReturn("u").anyTimes();
    EasyMock.expect(sdc.getPasswordAlias()).andReturn("a").anyTimes();
    EasyMock.replay(sdc);

    final Map<String, List<String>> clusterNames = new HashMap<>();
    clusterNames.put(address, Collections.singletonList(clusterName));

    // Mock a ClusterConfigurationCache for the monitor to use
    ClusterConfigurationCache configCache = EasyMock.createNiceMock(ClusterConfigurationCache.class);
    EasyMock.expect(configCache.getDiscoveryConfig(address, clusterName)).andReturn(sdc).anyTimes();
    EasyMock.expect(configCache.getClusterNames()).andReturn(clusterNames).anyTimes();
    EasyMock.expect(configCache.getClusterServiceConfigurations(address, clusterName)).andReturn(serviceConfigurationModels).anyTimes();
    EasyMock.replay(configCache);

    return new TestablePollingConfigAnalyzer(gatewayConfig, configCache, listener);
  }

  private void doTestEventWithConfigChange(final ApiEvent event, final String clusterName) {
    final String address = "http://host1:1234";

    final String failoverPropertyName = "autofailover_enabled";
    final String nsPropertyName = "dfs_federation_namenode_nameservice";
    final String portPropertyName = "namenode_port";

    // Create the original ServiceConfigurationModel details
    final Map<String, ServiceConfigurationModel> serviceConfigurationModels = new HashMap<>();
    final Map<String, String> nnServiceConf = new HashMap<>();
    final Map<String, Map<String, String>> nnRoleConf = new HashMap<>();
    final Map<String, String> nnRoleProps = new HashMap<>();
    nnRoleProps.put(failoverPropertyName, "false");
    nnRoleProps.put(nsPropertyName, "");
    nnRoleProps.put(portPropertyName, "54321");
    nnRoleConf.put(NameNodeServiceModelGenerator.ROLE_TYPE, nnRoleProps);
    serviceConfigurationModels.put(NameNodeServiceModelGenerator.SERVICE_TYPE,
                                   createModel(nnServiceConf, nnRoleConf));

    // Create another version of the same ServiceConfigurationModel with a modified property value
    ServiceConfigurationModel updatedNNModel = new ServiceConfigurationModel();
    updatedNNModel.addRoleProperty(NameNodeServiceModelGenerator.ROLE_TYPE, failoverPropertyName, "false");
    updatedNNModel.addRoleProperty(NameNodeServiceModelGenerator.ROLE_TYPE, nsPropertyName, "");
    updatedNNModel.addRoleProperty(NameNodeServiceModelGenerator.ROLE_TYPE, portPropertyName, "12345");
    Map<String, ServiceConfigurationModel> updatedModels = new HashMap<>();
    updatedModels.put(NameNodeServiceModelGenerator.ROLE_TYPE, updatedNNModel);

    ChangeListener listener = doTestEvent(event, address, clusterName, serviceConfigurationModels, updatedModels);
    assertTrue("Expected a change notification", listener.wasNotified(address, clusterName));
  }

  private void doTestEventWithoutConfigChange(final ApiEvent event, final String clusterName) {
    final String address = "http://host1:1234";

    final String failoverPropertyName = "autofailover_enabled";
    final String nsPropertyName = "dfs_federation_namenode_nameservice";
    final String portPropertyName = "namenode_port";

    final String failoverEnabledValue = "false";
    final String nsValue = "";
    final String portValue = "54321";

    // Create the original ServiceConfigurationModel details
    final Map<String, ServiceConfigurationModel> serviceConfigurationModels = new HashMap<>();
    final Map<String, String> nnServiceConf = new HashMap<>();
    final Map<String, Map<String, String>> nnRoleConf = new HashMap<>();
    final Map<String, String> nnRoleProps = new HashMap<>();
    nnRoleProps.put(failoverPropertyName, failoverEnabledValue);
    nnRoleProps.put(nsPropertyName, nsValue);
    nnRoleProps.put(portPropertyName, portValue);
    nnRoleConf.put(NameNodeServiceModelGenerator.ROLE_TYPE, nnRoleProps);
    serviceConfigurationModels.put(NameNodeServiceModelGenerator.SERVICE_TYPE, createModel(nnServiceConf, nnRoleConf));

    // Create another version of the same ServiceConfigurationModel with unmodified property values
    ServiceConfigurationModel updatedNNModel = new ServiceConfigurationModel();
    updatedNNModel.addRoleProperty(NameNodeServiceModelGenerator.ROLE_TYPE, failoverPropertyName, failoverEnabledValue);
    updatedNNModel.addRoleProperty(NameNodeServiceModelGenerator.ROLE_TYPE, nsPropertyName, nsValue);
    updatedNNModel.addRoleProperty(NameNodeServiceModelGenerator.ROLE_TYPE, portPropertyName, portValue);
    Map<String, ServiceConfigurationModel> updatedModels = new HashMap<>();
    updatedModels.put(NameNodeServiceModelGenerator.ROLE_TYPE, updatedNNModel);

    ChangeListener listener = doTestEvent(event, address, clusterName, serviceConfigurationModels, updatedModels);
    assertFalse("Unexpected change notification", listener.wasNotified(address, clusterName));
  }

  /**
   * Set the static GatewayServices field to the specified value.
   *
   * @param gws A GatewayServices object, or null.
   */
  private void setGatewayServices(final GatewayServices gws) {
    try {
      Field gwsField = GatewayServer.class.getDeclaredField("services");
      gwsField.setAccessible(true);
      gwsField.set(null, gws);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private ApiEvent createApiEvent(final String clusterName,
                                  final String serviceType,
                                  final String service,
                                  final String command,
                                  final String commandStatues) {
    return createApiEvent(clusterName, serviceType, service, command, commandStatues, "");
  }

  private ApiEvent createApiEvent(final String clusterName,
      final String serviceType,
      final String service,
      final String command,
      final String commandStatues,
      final String eventCode) {
    return createApiEvent(clusterName, serviceType, service, command, commandStatues, eventCode, null);
  }
  private ApiEvent createApiEvent(final String clusterName,
                                  final String serviceType,
                                  final String service,
                                  final String command,
                                  final String commandStatues,
                                  final String eventCode,
                                  final String id) {
    List<ApiEventAttribute> attrs = new ArrayList<>();
    attrs.add(createEventAttribute("CLUSTER", clusterName));
    attrs.add(createEventAttribute("SERVICE_TYPE", serviceType));
    attrs.add(createEventAttribute("SERVICE", service));
    attrs.add(createEventAttribute("COMMAND", command));
    attrs.add(createEventAttribute("COMMAND_STATUS", commandStatues));
    attrs.add(createEventAttribute("EVENTCODE", eventCode));
    return createApiEvent(ApiEventCategory.AUDIT_EVENT, attrs, id);
  }

  private ApiEvent createApiEvent(final ApiEventCategory category, final List<ApiEventAttribute> attrs, String id) {
    ApiEvent event = EasyMock.createNiceMock(ApiEvent.class);
    if (id == null) {
      EasyMock.expect(event.getId()).andReturn(RandomStringUtils.random(8, true, true)).anyTimes();
    } else {
      EasyMock.expect(event.getId()).andReturn(id).anyTimes();
    }
    EasyMock.expect(event.getTimeOccurred()).andReturn(Instant.now().toString()).anyTimes();
    EasyMock.expect(event.getCategory()).andReturn(category).anyTimes();
    EasyMock.expect(event.getAttributes()).andReturn(attrs).anyTimes();
    EasyMock.replay(event);
    return event;
  }

  private ApiEventAttribute createEventAttribute(final String name, final String value) {
    ApiEventAttribute attr = EasyMock.createNiceMock(ApiEventAttribute.class);
    EasyMock.expect(attr.getName()).andReturn(name).anyTimes();
    EasyMock.expect(attr.getValues()).andReturn(Collections.singletonList(value)).anyTimes();
    EasyMock.replay(attr);
    return attr;
  }

  private ServiceConfigurationModel createModel(Map<String, String>              serviceConfig,
                                                Map<String, Map<String, String>> roleConfig) {
    ServiceConfigurationModel model = new ServiceConfigurationModel();

    for (Map.Entry<String, String> entry : serviceConfig.entrySet()) {
      model.addServiceProperty(entry.getKey(), entry.getValue());
    }

    for (Map.Entry<String, Map<String, String>> entry : roleConfig.entrySet()) {
      String roleType = entry.getKey();
      for (Map.Entry<String, String> prop : entry.getValue().entrySet()) {
        model.addRoleProperty(roleType, prop.getKey(), prop.getValue());
      }
    }

    return model;
  }


  /**
   * PollingConfigurationAnalyzer extension to override CM API invocations.
   */
  private static class TestablePollingConfigAnalyzer extends PollingConfigurationAnalyzer {

    private final Map<String, List<ApiEvent>> restartEvents = new HashMap<>();
    private final Map<String, ServiceConfigurationModel> serviceConfigModels = new HashMap<>();

    TestablePollingConfigAnalyzer(GatewayConfig gatewayConfig, ClusterConfigurationCache cache) {
      this(gatewayConfig, cache, null);
    }

    TestablePollingConfigAnalyzer(GatewayConfig gatewayConfig, ClusterConfigurationCache   cache,
                                  ConfigurationChangeListener listener) {
      super(gatewayConfig, cache, null, null, listener, 20);
    }

    void addRestartEvent(final String service, final ApiEvent restartEvent) {
      restartEvents.computeIfAbsent(service, l -> new ArrayList<>()).add(restartEvent);
    }

    void addCurrentServiceConfigModel(final String address, final String clusterName, final String service, final ServiceConfigurationModel model) {
      serviceConfigModels.put(getServiceConfigModelKey(address, clusterName, service), model);
    }

    @Override
    protected List<ApiEvent> queryEvents(ApiClient client, String clusterName, String since) {
      return restartEvents.computeIfAbsent(clusterName, l -> new ArrayList<>());
    }

    @Override
    protected ServiceConfigurationModel getCurrentServiceConfiguration(String address,
                                                                       String clusterName,
                                                                       String service) {
      return serviceConfigModels.get(getServiceConfigModelKey(address, clusterName, service));
    }

    static String getServiceConfigModelKey(final String address, final String clusterName, final String service) {
      return address + ":" + clusterName + ":" + service;
    }
  }


  private static class ChangeListener implements ConfigurationChangeListener {
    private final Map<String, String> notifications = new HashMap<>();
    private final List<String> events = new ArrayList<>();

    @Override
    public void onConfigurationChange(String source, String clusterName) {
      notifications.put(source, clusterName);
      events.add(source + "+" + clusterName);
    }

    boolean wasNotified(final String source, final String clusterName) {
      return clusterName.equals(notifications.get(source));
    }

    void clearNotification() {
      notifications.clear();
    }

    int howManyNotifications(final String source, final String clusterName) {
      return events.size();
    }
  }

}
