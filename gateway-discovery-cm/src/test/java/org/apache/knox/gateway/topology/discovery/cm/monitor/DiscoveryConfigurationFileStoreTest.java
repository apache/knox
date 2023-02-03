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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Set;

import org.apache.knox.gateway.topology.discovery.ServiceDiscoveryConfig;
import org.junit.Test;


public class DiscoveryConfigurationFileStoreTest extends AbstractConfigurationStoreTest {

  @Test
  public void test() {
    final DiscoveryConfigurationStore configStore = new DiscoveryConfigurationFileStore(createGatewayConfig());

    final ServiceDiscoveryConfig original = createConfig("http://myhost:1234", "myCluster", "iam", "pwd.alias");

    try {
      // Test storage
      configStore.store(original);

      // Verify the file was persisted to disk
      assertEquals(1, listFiles(DATA_DIR).size());

      // Test retrieval
      // Load the persisted config
      Set<ServiceDiscoveryConfig> persistedConfigs = configStore.getAll();
      assertNotNull(persistedConfigs);
      assertFalse(persistedConfigs.isEmpty());
      assertEquals(1, persistedConfigs.size());

      ServiceDiscoveryConfig reloaded = persistedConfigs.iterator().next();
      assertEquals(original.getAddress(), reloaded.getAddress());
      assertEquals(original.getCluster(), reloaded.getCluster());
      assertEquals(original.getUser(), reloaded.getUser());
      assertEquals(original.getPasswordAlias(), reloaded.getPasswordAlias());
    } finally {
      configStore.remove(original.getAddress(), original.getCluster());

      // Verify file is gone
      assertEquals(0, listFiles(DATA_DIR).size());
    }
  }

  @Test
  public void testLoadingEmptyFile() throws IOException {
    final DiscoveryConfigurationFileStore configStore = new DiscoveryConfigurationFileStore(createGatewayConfig());
    final ServiceDiscoveryConfig serviceDiscoveryConfig = createConfig("http://myhost:1234", "myCluster", "iam", "pwd.alias");
    try {
      configStore.store(serviceDiscoveryConfig);
      final File persistenceFile = configStore.getPersistenceFile(serviceDiscoveryConfig.getAddress(), serviceDiscoveryConfig.getCluster());
      assertTrue(persistenceFile.length() > 0);
      //truncate file content
      FileChannel.open(Paths.get(persistenceFile.getAbsolutePath()), StandardOpenOption.WRITE).truncate(0).close();
      assertEquals(0, persistenceFile.length());
      final Set<ServiceDiscoveryConfig> persistedConfigs = configStore.getAll();
      assertTrue(persistedConfigs.isEmpty());
    } finally {
      configStore.remove(serviceDiscoveryConfig.getAddress(), serviceDiscoveryConfig.getCluster());
      assertEquals(0, listFiles(DATA_DIR).size());
    }
  }

  @Test
  public void testMultiple() {
    final DiscoveryConfigurationStore configStore = new DiscoveryConfigurationFileStore(createGatewayConfig());

    final ServiceDiscoveryConfig config1 = createConfig("http://myhost:1234", "Cluster 1", "iam1", "pwd.alias1");
    final ServiceDiscoveryConfig config2 = createConfig("http://myhost:1234", "Cluster 2", "iam2", "pwd.alias2");

    try {
      // Test storage
      configStore.store(config1);
      configStore.store(config2);

      // Verify the files were persisted to disk
      assertEquals(2, listFiles(DATA_DIR).size());

      // Test retrieval
      // Load the persisted config
      Set<ServiceDiscoveryConfig> persistedConfigs = configStore.getAll();
      assertNotNull(persistedConfigs);
      assertFalse(persistedConfigs.isEmpty());
      assertEquals(2, persistedConfigs.size());

      for (ServiceDiscoveryConfig reloaded : persistedConfigs) {
        ServiceDiscoveryConfig original = ("Cluster 1".equals(reloaded.getCluster())) ? config1 : config2;
        assertEquals(original.getAddress(), reloaded.getAddress());
        assertEquals(original.getCluster(), reloaded.getCluster());
        assertEquals(original.getUser(), reloaded.getUser());
        assertEquals(original.getPasswordAlias(), reloaded.getPasswordAlias());
      }
    } finally {
      configStore.remove(config1.getAddress(), config1.getCluster());
      configStore.remove(config2.getAddress(), config2.getCluster());

      // Validate the file removal
      assertEquals(0, listFiles(DATA_DIR).size());
    }
  }

  private ServiceDiscoveryConfig createConfig(final String address,
                                              final String cluster,
                                              final String user,
                                              final String alias) {
    return  new ServiceDiscoveryConfig() {
      @Override
      public String getAddress() {
        return address;
      }

      @Override
      public String getCluster() {
        return cluster;
      }

      @Override
      public String getUser() {
        return user;
      }

      @Override
      public String getPasswordAlias() {
        return alias;
      }
    };
  }

}
