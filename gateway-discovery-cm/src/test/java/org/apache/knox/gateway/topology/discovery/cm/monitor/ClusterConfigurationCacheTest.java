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
import java.util.HashSet;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClusterConfigurationCacheTest {

  private static final String ADDRESS = "https://host:7183";
  private static final String CLUSTER = "Cluster 1";

  @Test
  public void testMergeFullReplaceWhenScopeNull() {
    ClusterConfigurationCache cache = new ClusterConfigurationCache();
    cache.addServiceConfiguration(ADDRESS, CLUSTER, mutableMap("A", "B"));

    // null scope == unfiltered discovery -> replace the whole cluster baseline
    Map<String, ServiceConfigurationModel> merged =
        cache.mergeServiceConfiguration(ADDRESS, CLUSTER, mutableMap("C"), null);

    assertEquals(Collections.singleton("C"), merged.keySet());
    assertEquals(Collections.singleton("C"), cache.getClusterServiceConfigurations(ADDRESS, CLUSTER).keySet());
  }

  @Test
  public void testMergeScopedReplacePreservesOutOfScope() {
    ClusterConfigurationCache cache = new ClusterConfigurationCache();
    cache.addServiceConfiguration(ADDRESS, CLUSTER, mutableMap("A", "B"));

    // Re-discovering only A must refresh A and preserve B (belonging to another descriptor)
    Map<String, ServiceConfigurationModel> merged =
        cache.mergeServiceConfiguration(ADDRESS, CLUSTER, mutableMap("A"), new HashSet<>(Collections.singletonList("A")));

    assertEquals(new HashSet<>(java.util.Arrays.asList("A", "B")), merged.keySet());
  }

  @Test
  public void testMergeScopedRemovesInScopeServiceWithNoModel() {
    ClusterConfigurationCache cache = new ClusterConfigurationCache();
    cache.addServiceConfiguration(ADDRESS, CLUSTER, mutableMap("A", "B"));

    // A is in scope but produced no model this run (became invalid / removed) -> dropped; B preserved
    Map<String, ServiceConfigurationModel> merged =
        cache.mergeServiceConfiguration(ADDRESS, CLUSTER, mutableMap(), new HashSet<>(Collections.singletonList("A")));

    assertFalse("A should have been removed", merged.containsKey("A"));
    assertTrue("B should be preserved", merged.containsKey("B"));
  }

  @Test
  public void testMergeIntoEmptyBaseline() {
    ClusterConfigurationCache cache = new ClusterConfigurationCache();

    Map<String, ServiceConfigurationModel> merged =
        cache.mergeServiceConfiguration(ADDRESS, CLUSTER, mutableMap("A"), new HashSet<>(Collections.singletonList("A")));

    assertEquals(Collections.singleton("A"), merged.keySet());
  }

  private Map<String, ServiceConfigurationModel> mutableMap(String... serviceTypes) {
    Map<String, ServiceConfigurationModel> map = new HashMap<>();
    for (String serviceType : serviceTypes) {
      ServiceConfigurationModel model = new ServiceConfigurationModel();
      model.addServiceProperty("type", serviceType);
      map.put(serviceType, model);
    }
    return map;
  }
}
