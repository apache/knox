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
package org.apache.knox.gateway.cm.descriptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.knox.gateway.topology.discovery.advanced.AdvancedServiceDiscoveryConfig;
import org.apache.knox.gateway.topology.simple.SimpleDescriptor;
import org.apache.knox.gateway.topology.simple.SimpleDescriptor.Application;
import org.apache.knox.gateway.topology.simple.SimpleDescriptor.Service;
import org.junit.Before;
import org.junit.Test;

public class ClouderaManagerDescriptorParserTest {

  private ClouderaManagerDescriptorParser cmDescriptorParser;

  @Before
  public void setUp() {
    cmDescriptorParser = new ClouderaManagerDescriptorParser();
  }

  @Test
  public void testCMDescriptorParser() throws Exception {
    final String testConfigPath = this.getClass().getClassLoader().getResource("testDescriptor.xml").getPath();
    final Set<SimpleDescriptor> descriptors = cmDescriptorParser.parse(testConfigPath);
    assertEquals(2, descriptors.size());
    final Iterator<SimpleDescriptor> descriptorsIterator = descriptors.iterator();
    validateTopology1(descriptorsIterator.next());
    validateTopology2(descriptorsIterator.next());
  }

  @Test
  public void testCMDescriptorParserWrongDescriptorContent() throws Exception {
    final String testConfigPath = this.getClass().getClassLoader().getResource("testDescriptorConfigurationWithWrongDescriptor.xml").getPath();
    final Set<SimpleDescriptor> descriptors = cmDescriptorParser.parse(testConfigPath);
    assertEquals(1, descriptors.size());
    final Iterator<SimpleDescriptor> descriptorsIterator = descriptors.iterator();
    validateTopology1(descriptorsIterator.next());
  }

  @Test
  public void testCMDescriptorParserWrongXMLContent() throws Exception {
    final String testConfigPath = this.getClass().getClassLoader().getResource("testDescriptorConfigurationWithNonHadoopStyleConfiguration.xml").getPath();
    final Set<SimpleDescriptor> descriptors = cmDescriptorParser.parse(testConfigPath);
    assertTrue(descriptors.isEmpty());
  }

  @Test
  public void testCMDescriptorParserWithNotEnabledServices() throws Exception {
    final String testConfigPath = this.getClass().getClassLoader().getResource("testDescriptor.xml").getPath();
    final Properties advancedConfiguration = new Properties();
    advancedConfiguration.put(AdvancedServiceDiscoveryConfig.PARAMETER_NAME_PREFIX_ENABLED_SERVICE + "HIVE", "false");
    cmDescriptorParser.onAdvancedServiceDiscoveryConfigurationChange(advancedConfiguration);
    final Set<SimpleDescriptor> descriptors = cmDescriptorParser.parse(testConfigPath);
    assertEquals(2, descriptors.size());
    final Iterator<SimpleDescriptor> descriptorsIterator = descriptors.iterator();
    SimpleDescriptor descriptor = descriptorsIterator.next();
    assertNotNull(descriptor);
    // topology1 comes with HIVE which is disabled
    assertTrue(descriptor.getServices().isEmpty());
  }

  @Test
  public void testCMDescriptorParserWithEnabledNotListedServiceInTopology1() throws Exception {
    final String testConfigPath = this.getClass().getClassLoader().getResource("testDescriptor.xml").getPath();
    final Properties advancedConfiguration = new Properties();
    advancedConfiguration.put(AdvancedServiceDiscoveryConfig.PARAMETER_NAME_PREFIX_ENABLED_SERVICE + "OOZIE", "true");
    advancedConfiguration.put(AdvancedServiceDiscoveryConfig.PARAMETER_NAME_EXPECTED_TOPOLOGIES, "topology1, topology100");
    cmDescriptorParser.onAdvancedServiceDiscoveryConfigurationChange(advancedConfiguration);
    final Set<SimpleDescriptor> descriptors = cmDescriptorParser.parse(testConfigPath);
    final Iterator<SimpleDescriptor> descriptorsIterator = descriptors.iterator();
    SimpleDescriptor descriptor = descriptorsIterator.next();
    assertNotNull(descriptor);
    // topology1 comes without OOZIE but it's enabled and topology1 is expected -> OOZIE should be added without any url/version/parameter
    assertService(descriptor, "OOZIE", null, null, null);

    descriptor = descriptorsIterator.next();
    validateTopology2(descriptor);
    assertNull(descriptor.getService("OOZIE"));
  }

  private void validateTopology1(SimpleDescriptor descriptor) {
    assertEquals("topology1", descriptor.getName());
    assertEquals("ClouderaManager", descriptor.getDiscoveryType());
    assertEquals("http://host:123", descriptor.getDiscoveryAddress());
    assertEquals("user", descriptor.getDiscoveryUser());
    assertEquals("alias", descriptor.getDiscoveryPasswordAlias());
    assertEquals("Cluster 1", descriptor.getCluster());
    assertEquals("topology1-provider", descriptor.getProviderConfig());
    assertEquals(2, descriptor.getApplications().size());

    assertApplication(descriptor, "knoxauth", Collections.singletonMap("param1.name", "param1.value"));
    assertApplication(descriptor, "admin-ui", null);

    final Map<String, String> expectedServiceParameters = Stream.of(new String[][] { { "httpclient.connectionTimeout", "5m" }, { "httpclient.socketTimeout", "100m" }, })
        .collect(Collectors.toMap(data -> data[0], data -> data[1]));
    assertService(descriptor, "HIVE", "1.0", Collections.singletonList("http://localhost:456"), expectedServiceParameters);
  }

  private void validateTopology2(SimpleDescriptor descriptor) {
    assertEquals("topology2", descriptor.getName());
    assertEquals("Ambari", descriptor.getDiscoveryType());
    assertEquals("http://host:456", descriptor.getDiscoveryAddress());
    assertEquals("Cluster 2", descriptor.getCluster());
    assertEquals("topology2-provider", descriptor.getProviderConfig());
    assertTrue(descriptor.getApplications().isEmpty());

    final Map<String, String> expectedServiceParameters = Stream.of(new String[][] { { "httpclient.connectionTimeout", "5m" }, { "httpclient.socketTimeout", "100m" }, })
        .collect(Collectors.toMap(data -> data[0], data -> data[1]));
    assertService(descriptor, "ATLAS-API", null, Collections.singletonList("http://localhost:456"), expectedServiceParameters);
    assertService(descriptor, "NIFI", null, null, null);
  }

  private void assertApplication(SimpleDescriptor descriptor, String expectedApplicationName, Map<String, String> expectedParams) {
    final Application application = descriptor.getApplication(expectedApplicationName);
    assertNotNull(application);
    if (expectedParams != null) {
      assertTrue(application.getParams().entrySet().containsAll(expectedParams.entrySet()));
    } else {
      assertNull(application.getParams());
    }
  }

  private void assertService(SimpleDescriptor descriptor, String expectedServiceName, String expectedVersion, List<String> expectedUrls, Map<String, String> expectedParams) {
    final Service service = descriptor.getService(expectedServiceName);
    assertNotNull(service);
    if (expectedVersion != null) {
      assertEquals(expectedVersion, service.getVersion());
    } else {
      assertNull(service.getVersion());
    }

    if (expectedUrls != null) {
      assertTrue(service.getURLs().containsAll(expectedUrls));
    } else {
      assertNull(service.getURLs());
    }

    if (expectedParams != null) {
      assertTrue(service.getParams().entrySet().containsAll(expectedParams.entrySet()));
    } else {
      assertNull(service.getParams());
    }
  }

}
