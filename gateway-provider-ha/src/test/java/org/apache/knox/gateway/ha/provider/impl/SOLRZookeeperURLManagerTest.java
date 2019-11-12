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
package org.apache.knox.gateway.ha.provider.impl;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingCluster;
import org.apache.knox.gateway.ha.provider.HaServiceConfig;
import org.apache.knox.gateway.ha.provider.URLManager;
import org.apache.knox.gateway.ha.provider.URLManagerLoader;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Simple unit tests for SOLRZookeeperURLManager.
 *
 * @see SOLRZookeeperURLManager
 */
public class SOLRZookeeperURLManagerTest {
  private TestingCluster cluster;
  private SOLRZookeeperURLManager manager;

  @Before
  public void setUp() throws Exception {
    cluster = new TestingCluster(1);
    cluster.start();

    try(CuratorFramework zooKeeperClient =
        CuratorFrameworkFactory.builder().connectString(cluster.getConnectString())
            .retryPolicy(new ExponentialBackoffRetry(1000, 3)).build()) {

      zooKeeperClient.start();
      assertTrue(zooKeeperClient.blockUntilConnected(10, TimeUnit.SECONDS));
      zooKeeperClient.create().forPath("/live_nodes");
      zooKeeperClient.create().forPath("/live_nodes/host1:8983_solr");
      zooKeeperClient.create().forPath("/live_nodes/host2:8983_solr");
      zooKeeperClient.create().forPath("/live_nodes/host3:8983_solr");
    }
    manager = new SOLRZookeeperURLManager();
    HaServiceConfig config = new DefaultHaServiceConfig("SOLR");
    config.setEnabled(true);
    config.setZookeeperEnsemble(cluster.getConnectString());
    manager.setConfig(config);
  }

  @After
  public void tearDown() throws IOException {
    if(cluster != null) {
      cluster.close();
    }
  }

  @Test
  public void testURLs() throws Exception {
    List<String> urls = manager.getURLs();
    Assert.assertNotNull(urls);

    // Order of URLS is not deterministic out of Zookeeper
    // So we just check for expected values
    TreeSet<String> expected = new TreeSet<>();

    expected.add("http://host1:8983/solr");
    expected.add("http://host2:8983/solr");
    expected.add("http://host3:8983/solr");

    for(String url : urls) {
      assertTrue(expected.contains(url));
      expected.remove(url);
    }

    assertEquals(0,expected.size());

    // Unable to test markFailed because the SOLRZookeeperURLManager always does a refresh on Zookeeper contents.
  }

  @Test
  public void testSOLRZookeeperURLManagerLoading() {
    HaServiceConfig config = new DefaultHaServiceConfig("SOLR");
    config.setEnabled(true);
    config.setZookeeperEnsemble(cluster.getConnectString());
    URLManager manager = URLManagerLoader.loadURLManager(config);
    Assert.assertNotNull(manager);
    Assert.assertTrue(manager instanceof SOLRZookeeperURLManager);
  }
}
