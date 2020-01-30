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
import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;
import org.apache.knox.gateway.topology.discovery.cm.model.hdfs.NameNodeServiceModelGenerator;
import org.easymock.EasyMock;
import org.junit.Test;

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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class PollingConfigurationAnalyzerTest {

  @Test(expected = IllegalArgumentException.class)
  public void testRestartEventWithWrongApiEventCategory() {
    doTestRestartEvent(ApiEventCategory.LOG_EVENT);
  }

  @Test
  public void testRestartEvent() {
    doTestRestartEvent(ApiEventCategory.AUDIT_EVENT);
  }

  private void doTestRestartEvent(final ApiEventCategory category) {
    final String clusterName = "My Cluster";
    final String serviceType = NameNodeServiceModelGenerator.SERVICE_TYPE;
    final String service     = NameNodeServiceModelGenerator.SERVICE;

    List<ApiEventAttribute> apiEventAttrs = new ArrayList<>();
    apiEventAttrs.add(createEventAttribute("CLUSTER", clusterName));
    apiEventAttrs.add(createEventAttribute("SERVICE_TYPE", serviceType));
    apiEventAttrs.add(createEventAttribute("SERVICE", service));
    ApiEvent apiEvent = createApiEvent(category, apiEventAttrs);

    PollingConfigurationAnalyzer.RestartEvent restartEvent = new PollingConfigurationAnalyzer.RestartEvent(apiEvent);
    assertNotNull(restartEvent);
    assertEquals(clusterName, restartEvent.getClusterName());
    assertEquals(serviceType, restartEvent.getServiceType());
    assertEquals(service, restartEvent.getService());
    assertNotNull(restartEvent.getTimestamp());
  }

  @Test
  public void testPollingConfigChangeNotificationForChangedPropertyValue() {
    final String address = "http://host1:1234";
    final String clusterName = "Cluster 5";

    final String failoverPropertyName = "autofailover_enabled";
    final String nsPropertyName = "dfs_federation_namenode_nameservice";
    final String portPropertyName = "namenode_port";

    // Mock the service discovery details
    ServiceDiscoveryConfig sdc = EasyMock.createNiceMock(ServiceDiscoveryConfig.class);
    EasyMock.expect(sdc.getCluster()).andReturn(clusterName).anyTimes();
    EasyMock.expect(sdc.getAddress()).andReturn(address).anyTimes();
    EasyMock.expect(sdc.getUser()).andReturn("u").anyTimes();
    EasyMock.expect(sdc.getPasswordAlias()).andReturn("a").anyTimes();
    EasyMock.replay(sdc);

    final Map<String, List<String>> clusterNames = new HashMap<>();
    clusterNames.put(address, Collections.singletonList(clusterName));

    // Create the original ServiceConfigurationModel details
    final Map<String, ServiceConfigurationModel> serviceConfigurationModels = new HashMap<>();
    final Map<String, String> nnServiceConf = new HashMap<>();
    final Map<String, Map<String, String>> nnRoleConf = new HashMap<>();
    final Map<String, String> nnRoleProps = new HashMap<>();
    nnRoleProps.put(failoverPropertyName, "false");
    nnRoleProps.put(nsPropertyName, "");
    nnRoleProps.put(portPropertyName, "54321");
    nnRoleConf.put(NameNodeServiceModelGenerator.ROLE_TYPE, nnRoleProps);
    serviceConfigurationModels.put(NameNodeServiceModelGenerator.SERVICE_TYPE + "-1", createModel(nnServiceConf, nnRoleConf));

    // Mock a ClusterConfigurationCache for the monitor to use
    ClusterConfigurationCache configCache = EasyMock.createNiceMock(ClusterConfigurationCache.class);
    EasyMock.expect(configCache.getDiscoveryConfig(address, clusterName)).andReturn(sdc).anyTimes();
    EasyMock.expect(configCache.getClusterNames()).andReturn(clusterNames).anyTimes();
    EasyMock.expect(configCache.getClusterServiceConfigurations(address, clusterName))
            .andReturn(serviceConfigurationModels)
            .anyTimes();
    EasyMock.replay(configCache);

    // Create the monitor, registering a listener so we can verify that change notification works
    ChangeListener listener = new ChangeListener();
    TestablePollingConfigAnalyzer pca = new TestablePollingConfigAnalyzer(configCache, listener);
    pca.setInterval(5);

    // Create another version of the same ServiceConfigurationModel with a modified property value
    ServiceConfigurationModel updatedNNModel = new ServiceConfigurationModel();
    updatedNNModel.addRoleProperty(NameNodeServiceModelGenerator.ROLE_TYPE, failoverPropertyName, "false");
    updatedNNModel.addRoleProperty(NameNodeServiceModelGenerator.ROLE_TYPE, nsPropertyName, "");
    updatedNNModel.addRoleProperty(NameNodeServiceModelGenerator.ROLE_TYPE, portPropertyName, "12345");
    pca.addCurrentServiceConfigModel(address, clusterName, NameNodeServiceModelGenerator.SERVICE_TYPE + "-1", updatedNNModel);

    // Start the polling thread
    ExecutorService pollingThreadExecutor = Executors.newSingleThreadExecutor();
    pollingThreadExecutor.execute(pca);
    pollingThreadExecutor.shutdown();

    // Simulate a service restart event
    List<ApiEventAttribute> restartEventAttrs = new ArrayList<>();
    restartEventAttrs.add(createEventAttribute("CLUSTER", clusterName));
    restartEventAttrs.add(createEventAttribute("SERVICE_TYPE", NameNodeServiceModelGenerator.SERVICE_TYPE));
    restartEventAttrs.add(createEventAttribute("SERVICE", NameNodeServiceModelGenerator.SERVICE));
    ApiEvent restartEvent = createApiEvent(ApiEventCategory.AUDIT_EVENT, restartEventAttrs);
    pca.addRestartEvent(clusterName, restartEvent);

    try {
      pollingThreadExecutor.awaitTermination(15, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      //
    }

    // Stop the config analyzer thread
    pca.stop();

    assertTrue("Expected a change notification", listener.wasNotified(address, clusterName));
  }


  private ApiEvent createApiEvent(final ApiEventCategory category, final List<ApiEventAttribute> attrs) {
    ApiEvent event = EasyMock.createNiceMock(ApiEvent.class);
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

    private Map<String, List<ApiEvent>> restartEvents = new HashMap<>();
    private Map<String, ServiceConfigurationModel> serviceConfigModels = new HashMap<>();

    TestablePollingConfigAnalyzer(ClusterConfigurationCache cache) {
      this(cache, null);
    }

    TestablePollingConfigAnalyzer(ClusterConfigurationCache   cache,
                                  ConfigurationChangeListener listener) {
      super(cache, null, listener);
    }

    TestablePollingConfigAnalyzer(ClusterConfigurationCache cache,
                                  ConfigurationChangeListener listener,
                                  int interval) {
      super(cache, null, listener, interval);
    }

    void addRestartEvent(final String service, final ApiEvent restartEvent) {
      restartEvents.computeIfAbsent(service, l -> new ArrayList<>()).add(restartEvent);
    }

    void addCurrentServiceConfigModel(final String address, final String clusterName, final String service, final ServiceConfigurationModel model) {
      serviceConfigModels.put(getServiceConfigModelKey(address, clusterName, service), model);
    }

    @Override
    protected List<ApiEvent> queryRestartEvents(ApiClient client, String clusterName, String since) {
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

    @Override
    public void onConfigurationChange(String source, String clusterName) {
      notifications.put(source, clusterName);
    }

    boolean wasNotified(final String source, final String clusterName) {
      return clusterName.equals(notifications.get(source));
    }
  }

}
