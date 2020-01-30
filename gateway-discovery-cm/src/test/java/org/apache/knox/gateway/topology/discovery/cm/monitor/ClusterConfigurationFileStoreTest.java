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

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClusterConfigurationFileStoreTest extends AbstractConfigurationStoreTest {

  @Test
  public void testPersistenceWithNoConfigs() {
    final String cmAddress = "http://myhost:1234/";
    final String cluster   = "My Cluster";

    ClusterConfigurationStore configStore = new ClusterConfigurationFileStore(createGatewayConfig());

    assertEquals("Expecting empty data directory initially.", 0, listFiles(DATA_DIR).size());
    assertEquals("Expecting no results since data directory is empty.", 0, configStore.getAll().size());

    configStore.store(cmAddress, cluster, Collections.emptyMap());

    assertEquals("Expecting empty data directory initially.", 1, listFiles(DATA_DIR).size());
    assertEquals("Expecting no results since data directory is empty.", 1, configStore.getAll().size());

    ServiceConfigurationRecord record = configStore.get(cmAddress, cluster);
    assertNotNull(record);
    Map<String, ServiceConfigurationModel> configs = record.getConfigs();
    assertNotNull(configs);
    assertTrue(configs.isEmpty());
  }


  @Test
  public void testPersistence() {
    ClusterConfigurationStore configStore = new ClusterConfigurationFileStore(createGatewayConfig());

    assertEquals("Expecting empty data directory initially.", 0, listFiles(DATA_DIR).size());
    assertEquals("Expecting no results since data directory is empty.", 0, configStore.getAll().size());

    // Construct a configuration model
    final String address = "http://cmhost:9765/";
    final String cluster = "Cluster X";

    Map<String, ServiceConfigurationModel> configModels = new HashMap<>();
    ServiceConfigurationModel model = new ServiceConfigurationModel();
    model.addServiceProperty("s_prop_1", "s_prop_1-value");
    model.addServiceProperty("s_prop_2", "s_prop_2-value");
    model.addServiceProperty("s_prop_3", "s_prop_3-value");

    model.addRoleProperty("ROLE_1", "r_prop_1", "r_prop_1-value");
    model.addRoleProperty("ROLE_2", "r_prop_1", "r_prop_1-value");
    model.addRoleProperty("ROLE_2", "r_prop_2", "r_prop_2-value");
    model.addRoleProperty("ROLE_2", "r_prop_3", "r_prop_3-value");
    model.addRoleProperty("ROLE_3", "r_prop_1", "r_prop_1-value");
    model.addRoleProperty("ROLE_3", "r_prop_2", "r_prop_2-value");
    configModels.put("MY_SERVICE", model);

    configStore.store(address, cluster, configModels);
    assertEquals("Expected a new file in the data directory.", 1, listFiles(DATA_DIR).size());

    // Try to get a record for an unknown cluster
    ServiceConfigurationRecord record = configStore.get("http://unknown-host", cluster);
    assertNull("Unexpected record for an unknown cluster.", record);

    record = configStore.get(address, cluster);
    assertNotNull(record);
    assertEquals(address, record.getDiscoveryAddress());
    assertEquals(cluster, record.getClusterName());
    Map<String, ServiceConfigurationModel> reloadedConfigs = record.getConfigs();
    assertNotNull(reloadedConfigs);
    assertFalse(reloadedConfigs.isEmpty());

    // Validate service config model
    validateModel(model, reloadedConfigs.get("MY_SERVICE"));

    Set<ServiceConfigurationRecord> allRecords = configStore.getAll();
    assertEquals(1, allRecords.size());
    validateModel(model, allRecords.iterator().next().getConfigs().get("MY_SERVICE"));

    configStore.remove(address, cluster);
    assertEquals("Expected no files in the data directory.", 0, listFiles(DATA_DIR).size());
  }


  private void validateModel(final ServiceConfigurationModel original, final ServiceConfigurationModel candidate) {
    assertNotNull(candidate);

    // Compare service properties
    Map<String, String> reloadedServiceProps = candidate.getServiceProps();
    assertNotNull(reloadedServiceProps);
    assertEquals(original.getServiceProps().size(), reloadedServiceProps.size());
    for (Map.Entry<String, String> prop : original.getServiceProps().entrySet()) {
      assertEquals(prop.getValue(), reloadedServiceProps.get(prop.getKey()));
    }

    // Compare the role properties
    assertEquals(original.getRoleTypes().size(), candidate.getRoleTypes().size());
    for (Map.Entry<String, Map<String, String>> entry : original.getRoleProps().entrySet()) {
      Map<String, String> candidateProps = candidate.getRoleProps(entry.getKey());
      for (Map.Entry<String, String> prop : entry.getValue().entrySet()) {
        assertEquals(prop.getValue(), candidateProps.get(prop.getKey()));
      }
    }
  }

}
