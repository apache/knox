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
package org.apache.knox.gateway.services.topology;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.topology.impl.DefaultTopologyService;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.test.TestUtils;
import org.apache.knox.gateway.topology.Param;
import org.apache.knox.gateway.topology.Provider;
import org.apache.knox.gateway.topology.Topology;
import org.apache.knox.gateway.topology.TopologyEvent;
import org.apache.knox.gateway.topology.TopologyListener;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.easymock.EasyMock.anyObject;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class DefaultTopologyServiceTest {

  @Before
  public void setUp() throws Exception {
  }

  @After
  public void tearDown() throws Exception {
  }

  private File createDir() throws IOException {
    return TestUtils.createTempDir(this.getClass().getSimpleName() + "-");
  }

  private File createFile(File parent, String name, String resource, long timestamp) throws IOException {
    File file = new File(parent, name);
    if (!file.exists()) {
      FileUtils.touch(file);
    }
    InputStream input = ClassLoader.getSystemResourceAsStream(resource);
    OutputStream output = FileUtils.openOutputStream(file);
    IOUtils.copy(input, output);
    //KNOX-685: output.flush();
    input.close();
    output.close();
    file.setLastModified(timestamp);
    assertTrue("Failed to create test file " + file.getAbsolutePath(), file.exists());
    assertTrue("Failed to populate test file " + file.getAbsolutePath(), file.length() > 0);

    return file;
  }

  @Test
  public void testGetTopologies() throws Exception {

    File dir = createDir();
    File topologyDir = new File(dir, "topologies");

    File descriptorsDir = new File(dir, "descriptors");
    descriptorsDir.mkdirs();

    File sharedProvidersDir = new File(dir, "shared-providers");
    sharedProvidersDir.mkdirs();

    long time = topologyDir.lastModified();
    try {
      createFile(topologyDir, "one.xml", "org/apache/knox/gateway/topology/file/topology-one.xml", time);

      TestTopologyListener topoListener = new TestTopologyListener();
      FileAlterationMonitor monitor = new FileAlterationMonitor(Long.MAX_VALUE);

      TopologyService provider = new DefaultTopologyService();
      Map<String, String> c = new HashMap<>();

      GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
      EasyMock.expect(config.getGatewayTopologyDir()).andReturn(topologyDir.getAbsolutePath()).anyTimes();
      EasyMock.expect(config.getGatewayConfDir()).andReturn(descriptorsDir.getParentFile().getAbsolutePath()).anyTimes();
      EasyMock.expect(config.getGatewayProvidersConfigDir()).andReturn(sharedProvidersDir.getAbsolutePath()).anyTimes();
      EasyMock.expect(config.getGatewayDescriptorsDir()).andReturn(descriptorsDir.getAbsolutePath()).anyTimes();
      EasyMock.replay(config);

      provider.init(config, c);

      provider.addTopologyChangeListener(topoListener);

      provider.reloadTopologies();

      Collection<Topology> topologies = provider.getTopologies();
      assertThat(topologies, notNullValue());
      assertThat(topologies.size(), is(1));
      Topology topology = topologies.iterator().next();
      assertThat(topology.getName(), is("one"));
      assertThat(topology.getTimestamp(), is(time));
      assertThat(topoListener.events.size(), is(1));
      topoListener.events.clear();

      // Add a file to the directory.
      File two = createFile(topologyDir, "two.xml",
          "org/apache/knox/gateway/topology/file/topology-two.xml", 1L);
      provider.reloadTopologies();
      topologies = provider.getTopologies();
      assertThat(topologies.size(), is(2));
      Set<String> names = new HashSet<>(Arrays.asList("one", "two"));
      Iterator<Topology> iterator = topologies.iterator();
      topology = iterator.next();
      assertThat(names, hasItem(topology.getName()));
      names.remove(topology.getName());
      topology = iterator.next();
      assertThat(names, hasItem(topology.getName()));
      names.remove(topology.getName());
      assertThat(names.size(), is(0));
      assertThat(topoListener.events.size(), is(1));
      List<TopologyEvent> events = topoListener.events.get(0);
      assertThat(events.size(), is(1));
      TopologyEvent event = events.get(0);
      assertThat(event.getType(), is(TopologyEvent.Type.CREATED));
      assertThat(event.getTopology(), notNullValue());

      // Update a file in the directory.
      two = createFile(topologyDir, "two.xml",
          "org/apache/knox/gateway/topology/file/topology-three.xml", 2L);
      provider.reloadTopologies();
      topologies = provider.getTopologies();
      assertThat(topologies.size(), is(2));
      names = new HashSet<>(Arrays.asList("one", "two"));
      iterator = topologies.iterator();
      topology = iterator.next();
      assertThat(names, hasItem(topology.getName()));
      names.remove(topology.getName());
      topology = iterator.next();
      assertThat(names, hasItem(topology.getName()));
      names.remove(topology.getName());
      assertThat(names.size(), is(0));

      // Remove a file from the directory.
      two.delete();
      provider.reloadTopologies();
      topologies = provider.getTopologies();
      assertThat(topologies.size(), is(1));
      topology = topologies.iterator().next();
      assertThat(topology.getName(), is("one"));
      assertThat(topology.getTimestamp(), is(time));

    } finally {
      FileUtils.deleteQuietly(dir);
    }
  }

  /**
   * KNOX-1014
   *
   * Test the lifecycle relationship between simple descriptors and topology files.
   *
   * N.B. This test depends on the DummyServiceDiscovery extension being configured:
   *        org.apache.knox.gateway.topology.discovery.test.extension.DummyServiceDiscovery
   */
  @Test
  public void testSimpleDescriptorsTopologyGeneration() throws Exception {

    File dir = createDir();
    File topologyDir = new File(dir, "topologies");
    topologyDir.mkdirs();

    File descriptorsDir = new File(dir, "descriptors");
    descriptorsDir.mkdirs();

    File sharedProvidersDir = new File(dir, "shared-providers");
    sharedProvidersDir.mkdirs();

    try {
      TestTopologyListener topoListener = new TestTopologyListener();
      FileAlterationMonitor monitor = new FileAlterationMonitor(Long.MAX_VALUE);

      TopologyService provider = new DefaultTopologyService();
      Map<String, String> c = new HashMap<>();

      GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
      EasyMock.expect(config.getGatewayTopologyDir()).andReturn(topologyDir.getAbsolutePath()).anyTimes();
      EasyMock.expect(config.getGatewayConfDir()).andReturn(descriptorsDir.getParentFile().getAbsolutePath()).anyTimes();
      EasyMock.replay(config);

      provider.init(config, c);
      provider.addTopologyChangeListener(topoListener);
      provider.reloadTopologies();

      // Add a simple descriptor to the descriptors dir to verify topology generation and loading (KNOX-1006)
      AliasService aliasService = EasyMock.createNiceMock(AliasService.class);
      EasyMock.expect(aliasService.getPasswordFromAliasForGateway(anyObject(String.class))).andReturn(null).anyTimes();
      EasyMock.replay(aliasService);
      DefaultTopologyService.DescriptorsMonitor dm =
              new DefaultTopologyService.DescriptorsMonitor(config, topologyDir, aliasService);

      // Listener to simulate the topologies directory monitor, to notice when a topology has been deleted
      provider.addTopologyChangeListener(new TestTopologyDeleteListener((DefaultTopologyService)provider));

      // Write out the referenced provider config first
      File provCfgFile = createFile(sharedProvidersDir,
                                    "ambari-cluster-policy.xml",
                                    "org/apache/knox/gateway/topology/file/ambari-cluster-policy.xml",
                                    System.currentTimeMillis());
      try {
        // Create the simple descriptor in the descriptors dir
        File simpleDesc = createFile(descriptorsDir,
                                     "four.json",
                                     "org/apache/knox/gateway/topology/file/simple-topology-four.json",
                                     System.currentTimeMillis());

        // Trigger the topology generation by noticing the simple descriptor
        dm.onFileChange(simpleDesc);

        // Load the generated topology
        provider.reloadTopologies();
        Collection<Topology> topologies = provider.getTopologies();
        assertThat(topologies.size(), is(1));
        Iterator<Topology> iterator = topologies.iterator();
        Topology topology = iterator.next();
        assertThat("four", is(topology.getName()));
        int serviceCount = topology.getServices().size();
        assertEquals("Expected the same number of services as are declared in the simple dscriptor.", 10, serviceCount);

        // Overwrite the simple descriptor with a different set of services, and check that the changes are
        // propagated to the associated topology
        simpleDesc = createFile(descriptorsDir,
                                "four.json",
                                "org/apache/knox/gateway/topology/file/simple-descriptor-five.json",
                                System.currentTimeMillis());
        dm.onFileChange(simpleDesc);
        provider.reloadTopologies();
        topologies = provider.getTopologies();
        topology = topologies.iterator().next();
        assertNotEquals(serviceCount, topology.getServices().size());
        assertEquals(6, topology.getServices().size());

        // Delete the simple descriptor, and make sure that the associated topology file is deleted
        simpleDesc.delete();
        dm.onFileDelete(simpleDesc);
        provider.reloadTopologies();
        topologies = provider.getTopologies();
        assertTrue(topologies.isEmpty());

        // Delete a topology file, and make sure that the associated simple descriptor is deleted
        // Overwrite the simple descriptor with a different set of services, and check that the changes are
        // propagated to the associated topology
        simpleDesc = createFile(descriptorsDir,
                                "deleteme.json",
                                "org/apache/knox/gateway/topology/file/simple-descriptor-five.json",
                                System.currentTimeMillis());
        dm.onFileChange(simpleDesc);
        provider.reloadTopologies();
        topologies = provider.getTopologies();
        assertFalse(topologies.isEmpty());
        topology = topologies.iterator().next();
        assertEquals("deleteme", topology.getName());
        File topologyFile = new File(topologyDir, topology.getName() + ".xml");
        assertTrue(topologyFile.exists());
        topologyFile.delete();
        provider.reloadTopologies();
        assertTrue("Simple descriptor should NOT have been deleted because the associated topology was.",
                    simpleDesc.exists());

      } finally {
        provCfgFile.delete();
      }
    } finally {
      FileUtils.deleteQuietly(dir);
    }
  }

  /**
   * KNOX-1014
   *
   * Test the lifecycle relationship between provider configuration files, simple descriptors, and topology files.
   *
   * N.B. This test depends on the DummyServiceDiscovery extension being configured:
   *        org.apache.knox.gateway.topology.discovery.test.extension.DummyServiceDiscovery
   */
  @Test
  public void testTopologiesUpdateFromProviderConfigChange() throws Exception {
    File dir = createDir();
    File topologyDir = new File(dir, "topologies");
    topologyDir.mkdirs();

    File descriptorsDir = new File(dir, "descriptors");
    descriptorsDir.mkdirs();

    File sharedProvidersDir = new File(dir, "shared-providers");
    sharedProvidersDir.mkdirs();

    try {
      TestTopologyListener topoListener = new TestTopologyListener();
      FileAlterationMonitor monitor = new FileAlterationMonitor(Long.MAX_VALUE);

      TopologyService ts = new DefaultTopologyService();
      Map<String, String> c = new HashMap<>();

      GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
      EasyMock.expect(config.getGatewayTopologyDir()).andReturn(topologyDir.getAbsolutePath()).anyTimes();
      EasyMock.expect(config.getGatewayConfDir()).andReturn(descriptorsDir.getParentFile().getAbsolutePath()).anyTimes();
      EasyMock.replay(config);

      ts.init(config, c);
      ts.addTopologyChangeListener(topoListener);
      ts.reloadTopologies();

      java.lang.reflect.Field dmField = ts.getClass().getDeclaredField("descriptorsMonitor");
      dmField.setAccessible(true);
      DefaultTopologyService.DescriptorsMonitor dm = (DefaultTopologyService.DescriptorsMonitor) dmField.get(ts);

      // Write out the referenced provider configs first
      createFile(sharedProvidersDir,
                 "provider-config-one.xml",
                 "org/apache/knox/gateway/topology/file/provider-config-one.xml",
                 System.currentTimeMillis());

      // Create the simple descriptor, which depends on provider-config-one.xml
      File simpleDesc = createFile(descriptorsDir,
                                   "six.json",
                                   "org/apache/knox/gateway/topology/file/simple-descriptor-six.json",
                                   System.currentTimeMillis());

      // "Notice" the simple descriptor change, and generate a topology based on it
      dm.onFileChange(simpleDesc);

      // Load the generated topology
      ts.reloadTopologies();
      Collection<Topology> topologies = ts.getTopologies();
      assertThat(topologies.size(), is(1));
      Iterator<Topology> iterator = topologies.iterator();
      Topology topology = iterator.next();
      assertFalse("The Shiro provider is disabled in provider-config-one.xml",
                  topology.getProvider("authentication", "ShiroProvider").isEnabled());

      // Overwrite the referenced provider configuration with a different ShiroProvider config, and check that the
      // changes are propagated to the associated topology
      File providerConfig = createFile(sharedProvidersDir,
                                       "provider-config-one.xml",
                                       "org/apache/knox/gateway/topology/file/ambari-cluster-policy.xml",
                                       System.currentTimeMillis());

      // "Notice" the simple descriptor change as a result of the referenced config change
      dm.onFileChange(simpleDesc);

      // Load the generated topology
      ts.reloadTopologies();
      topologies = ts.getTopologies();
      assertFalse(topologies.isEmpty());
      topology = topologies.iterator().next();
      assertTrue("The Shiro provider is enabled in ambari-cluster-policy.xml",
              topology.getProvider("authentication", "ShiroProvider").isEnabled());

      // Delete the provider configuration, and make sure that the associated topology file is unaffected.
      // The topology file should not be affected because the simple descriptor handling will fail to resolve the
      // referenced provider configuration.
      providerConfig.delete();     // Delete the file
      dm.onFileChange(simpleDesc); // The provider config deletion will trigger a descriptor change notification
      ts.reloadTopologies();
      topologies = ts.getTopologies();
      assertFalse(topologies.isEmpty());
      assertTrue("The Shiro provider is enabled in ambari-cluster-policy.xml",
              topology.getProvider("authentication", "ShiroProvider").isEnabled());

    } finally {
      FileUtils.deleteQuietly(dir);
    }
  }

  /**
   * KNOX-1039
   */
  @Test
  public void testConfigurationCRUDAPI() throws Exception {
    File dir = createDir();
    File topologyDir = new File(dir, "topologies");
    topologyDir.mkdirs();

    File descriptorsDir = new File(dir, "descriptors");
    descriptorsDir.mkdirs();

    File sharedProvidersDir = new File(dir, "shared-providers");
    sharedProvidersDir.mkdirs();

    try {
      TestTopologyListener topoListener = new TestTopologyListener();
      FileAlterationMonitor monitor = new FileAlterationMonitor(Long.MAX_VALUE);

      TopologyService ts = new DefaultTopologyService();
      Map<String, String> c = new HashMap<>();

      GatewayConfig config = EasyMock.createNiceMock(GatewayConfig.class);
      EasyMock.expect(config.getGatewayTopologyDir()).andReturn(topologyDir.getAbsolutePath()).anyTimes();
      EasyMock.expect(config.getGatewayConfDir()).andReturn(descriptorsDir.getParentFile().getAbsolutePath()).anyTimes();
      EasyMock.replay(config);

      ts.init(config, c);
      ts.addTopologyChangeListener(topoListener);
      ts.reloadTopologies();

      java.lang.reflect.Field dmField = ts.getClass().getDeclaredField("descriptorsMonitor");
      dmField.setAccessible(true);
      DefaultTopologyService.DescriptorsMonitor dm = (DefaultTopologyService.DescriptorsMonitor) dmField.get(ts);

      final String simpleDescName  = "six.json";
      final String provConfOne     = "provider-config-one.xml";
      final String provConfTwo     = "ambari-cluster-policy.xml";

      // "Deploy" the referenced provider configs first
      boolean isDeployed =
        ts.deployProviderConfiguration(provConfOne,
                FileUtils.readFileToString(
                    new File(ClassLoader.getSystemResource(
                        "org/apache/knox/gateway/topology/file/provider-config-one.xml").toURI()),
                    StandardCharsets.UTF_8));
      assertTrue(isDeployed);
      File provConfOneFile = new File(sharedProvidersDir, provConfOne);
      assertTrue(provConfOneFile.exists());

      isDeployed =
        ts.deployProviderConfiguration(provConfTwo,
                FileUtils.readFileToString(
                    new File(ClassLoader.getSystemResource(
                        "org/apache/knox/gateway/topology/file/ambari-cluster-policy.xml").toURI()),
                    StandardCharsets.UTF_8));
      assertTrue(isDeployed);
      File provConfTwoFile = new File(sharedProvidersDir, provConfTwo);
      assertTrue(provConfTwoFile.exists());

      // Validate the provider configurations known by the topology service
      Collection<File> providerConfigurations = ts.getProviderConfigurations();
      assertNotNull(providerConfigurations);
      assertEquals(2, providerConfigurations.size());
      assertTrue(providerConfigurations.contains(provConfOneFile));
      assertTrue(providerConfigurations.contains(provConfTwoFile));

      // "Deploy" the simple descriptor, which depends on provConfOne
      isDeployed =
        ts.deployDescriptor(simpleDescName,
            FileUtils.readFileToString(
                new File(ClassLoader.getSystemResource(
                    "org/apache/knox/gateway/topology/file/simple-descriptor-six.json").toURI()),
                StandardCharsets.UTF_8));
      assertTrue(isDeployed);
      File simpleDesc = new File(descriptorsDir, simpleDescName);
      assertTrue(simpleDesc.exists());

      // Validate the simple descriptors known by the topology service
      Collection<File> descriptors = ts.getDescriptors();
      assertNotNull(descriptors);
      assertEquals(1, descriptors.size());
      assertTrue(descriptors.contains(simpleDesc));

      // "Notice" the simple descriptor, so the provider configuration dependency relationship is recorded
      dm.onFileChange(simpleDesc);

      // Attempt to delete the referenced provConfOne
      assertFalse("Should not be able to delete a provider configuration that is referenced by one or more descriptors",
                  ts.deleteProviderConfiguration(FilenameUtils.getBaseName(provConfOne)));

      // Overwrite the simple descriptor with content that changes the provider config reference to provConfTwo
      isDeployed =
        ts.deployDescriptor(simpleDescName,
              FileUtils.readFileToString(
                  new File(ClassLoader.getSystemResource(
                      "org/apache/knox/gateway/topology/file/simple-descriptor-five.json").toURI()),
                  StandardCharsets.UTF_8));
      assertTrue(isDeployed);
      assertTrue(simpleDesc.exists());
      ts.getProviderConfigurations();

      // "Notice" the simple descriptor, so the provider configuration dependency relationship is updated
      dm.onFileChange(simpleDesc);

      // Attempt to delete the referenced provConfOne
      assertTrue("Should be able to delete the provider configuration, now that it's not referenced by any descriptors",
                 ts.deleteProviderConfiguration(FilenameUtils.getBaseName(provConfOne)));

      // Re-validate the provider configurations known by the topology service
      providerConfigurations = ts.getProviderConfigurations();
      assertNotNull(providerConfigurations);
      assertEquals(1, providerConfigurations.size());
      assertFalse(providerConfigurations.contains(provConfOneFile));
      assertTrue(providerConfigurations.contains(provConfTwoFile));

      // Attempt to delete the referenced provConfTwo
      assertFalse("Should not be able to delete a provider configuration that is referenced by one or more descriptors",
                  ts.deleteProviderConfiguration(FilenameUtils.getBaseName(provConfTwo)));

      // Delete the referencing simple descriptor
      assertTrue(ts.deleteDescriptor(FilenameUtils.getBaseName(simpleDescName)));
      assertFalse(simpleDesc.exists());

      // Re-validate the simple descriptors known by the topology service
      descriptors = ts.getDescriptors();
      assertNotNull(descriptors);
      assertTrue(descriptors.isEmpty());

      // "Notice" the simple descriptor, so the provider configuration dependency relationship is updated
      dm.onFileDelete(simpleDesc);

      // Attempt to delete the referenced provConfTwo
      assertTrue("Should be able to delete the provider configuration, now that it's not referenced by any descriptors",
                 ts.deleteProviderConfiguration(FilenameUtils.getBaseName(provConfTwo)));

      // Re-validate the provider configurations known by the topology service
      providerConfigurations = ts.getProviderConfigurations();
      assertNotNull(providerConfigurations);
      assertTrue(providerConfigurations.isEmpty());

    } finally {
      FileUtils.deleteQuietly(dir);
    }
  }

  private void kickMonitor(FileAlterationMonitor monitor) {
    for (FileAlterationObserver observer : monitor.getObservers()) {
      observer.checkAndNotify();
    }
  }


  @Test
  public void testProviderParamsOrderIsPreserved() {

    Provider provider = new Provider();
    String names[] = {"ldapRealm=",
        "ldapContextFactory",
        "ldapRealm.contextFactory",
        "ldapGroupRealm",
        "ldapGroupRealm.contextFactory",
        "ldapGroupRealm.contextFactory.systemAuthenticationMechanism"
    };

    Param param = null;
    for (String name : names) {
      param = new Param();
      param.setName(name);
      param.setValue(name);
      provider.addParam(param);

    }
    Map<String, String> params = provider.getParams();
    Set<String> keySet = params.keySet();
    Iterator<String> iter = keySet.iterator();
    int i = 0;
    while (iter.hasNext()) {
      assertTrue(iter.next().equals(names[i++]));
    }

  }

  private class TestTopologyListener implements TopologyListener {

    ArrayList<List<TopologyEvent>> events = new ArrayList<>();

    @Override
    public void handleTopologyEvent(List<TopologyEvent> events) {
      this.events.add(events);
    }

  }


  private class TestTopologyDeleteListener implements TopologyListener {

    FileAlterationListener delegate;

    TestTopologyDeleteListener(FileAlterationListener delegate) {
      this.delegate = delegate;
    }

    @Override
    public void handleTopologyEvent(List<TopologyEvent> events) {
      for (TopologyEvent event : events) {
        if (event.getType().equals(TopologyEvent.Type.DELETED)) {
          delegate.onFileDelete(new File(event.getTopology().getUri()));
        }
      }
    }

  }

}
