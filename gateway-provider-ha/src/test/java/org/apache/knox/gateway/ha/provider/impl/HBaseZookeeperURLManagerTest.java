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

import java.io.IOException;
import java.util.concurrent.TimeUnit;

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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Simple unit tests for HBaseZookeeperURLManager.
 *
 * @see HBaseZookeeperURLManager
 */
public class HBaseZookeeperURLManagerTest {

  private static final String UNSECURE_NS = "/hbase-unsecure";
  private static final String SECURE_NS   = "/hbase-secure";

  private TestingCluster cluster;

  @Before
  public void setUp() throws Exception {
    cluster = new TestingCluster(1);
    cluster.start();
  }

  @After
  public void tearDown() throws IOException {
    if(cluster != null) {
      cluster.close();
    }
  }

  @Test
  public void testHBaseZookeeperURLManagerLoading() throws Exception {
    createZNodes(UNSECURE_NS);
    doTest(null);
  }

  @Test
  public void testSecureNSHBaseZookeeperURLManagerLoading() throws Exception {
    createZNodes(SECURE_NS);
    doTest(SECURE_NS);
  }

  /*
   * KNOX-1149
   */
  @Test
  public void testDefaultNSHBaseZookeeperURLManagerLoadingWhenSecureAndUnsecureZNodesPresent() throws Exception {
    createZNodes(UNSECURE_NS);
    createZNodes(SECURE_NS);
    doTest(null);
  }

  /*
   * KNOX-1149
   */
  @Test
  public void testSpecifiedNSHBaseZookeeperURLManagerLoadingWhenSecureAndUnsecureZNodesPresent() throws Exception {
    createZNodes(UNSECURE_NS);
    createZNodes(SECURE_NS);
    doTest(UNSECURE_NS);
  }

  @Test
  public void testSecureNSHBaseZookeeperURLManagerLoadingNoLeadingSlash() throws Exception {
    createZNodes(SECURE_NS);
    doTest(SECURE_NS.substring(1)); // Omit the leading slash from the namespace
  }

  private void doTest(String namespace) {
    HaServiceConfig config = new DefaultHaServiceConfig("WEBHBASE");
    config.setEnabled(true);
    config.setZookeeperEnsemble(cluster.getConnectString());
    config.setZookeeperNamespace(namespace);
    URLManager manager = null;
    try {
      manager = URLManagerLoader.loadURLManager(config);
    } catch (Exception e) {
      fail(e.getMessage());
    }
    Assert.assertNotNull(manager);
    Assert.assertTrue(manager instanceof HBaseZookeeperURLManager);
  }

  private void createZNodes(String namespace) throws Exception {
    try (CuratorFramework zooKeeperClient =
                          CuratorFrameworkFactory.builder().connectString(cluster.getConnectString())
                                                           .retryPolicy(new ExponentialBackoffRetry(1000, 3)).build()) {
      zooKeeperClient.start();
      assertTrue(zooKeeperClient.blockUntilConnected(10, TimeUnit.SECONDS));
      zooKeeperClient.create().forPath(namespace);
      zooKeeperClient.create().forPath(namespace + "/rs");
    }
  }

}
