/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.discovery.ambari;

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.topology.discovery.ServiceDiscovery;
import org.apache.knox.test.TestUtils;
import org.easymock.EasyMock;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class AmbariClusterTest {

  @Test
  public void testHiveZooKeeperConfiguration() throws Exception {

    final boolean isEnabled = true;
    final String ensemble = "host1:2181,host2:2181,host3:2181";
    final String namespace = "hiveserver2";

    Map<String, String> serviceConfigProps = new HashMap<>();
    serviceConfigProps.put("hive.server2.support.dynamic.service.discovery", String.valueOf(isEnabled));
    serviceConfigProps.put("hive.zookeeper.quorum", ensemble);
    serviceConfigProps.put("hive.server2.zookeeper.namespace", namespace);

    AmbariCluster.ZooKeeperConfig config = getZooKeeperConfiguration("HIVE", "hive-site", serviceConfigProps);
    assertNotNull(config);
    assertEquals(isEnabled, config.isEnabled());
    assertEquals(ensemble, config.getEnsemble());
    assertEquals(namespace, config.getNamespace());
  }

  @Test
  public void testWebHBaseZooKeeperConfiguration() throws Exception {

    final boolean isEnabled = true;
    final String ensemble = "host1:2181,host2:2181,host3:2181";
    final String namespace = "hbase";

    Map<String, String> serviceConfigProps = new HashMap<>();
    serviceConfigProps.put("hbase.zookeeper.quorum", ensemble);
    serviceConfigProps.put("zookeeper.znode.parent", namespace);

    AmbariCluster.ZooKeeperConfig config = getZooKeeperConfiguration("WEBHBASE", "HBASE", "hbase-site", serviceConfigProps);
    assertNotNull(config);
    assertEquals(isEnabled, config.isEnabled());
    assertEquals(ensemble, config.getEnsemble());
    assertEquals(namespace, config.getNamespace());
  }


  @Test
  public void testKafkaZooKeeperConfiguration() throws Exception {

    final boolean isEnabled = true;
    final String ensemble = "host1:2181,host2:2181,host3:2181";

    Map<String, String> serviceConfigProps = new HashMap<>();
    serviceConfigProps.put("zookeeper.connect", ensemble);

    AmbariCluster.ZooKeeperConfig config = getZooKeeperConfiguration("KAFKA", "kafka-broker", serviceConfigProps);
    assertNotNull(config);
    assertEquals(isEnabled, config.isEnabled());
    assertEquals(ensemble, config.getEnsemble());
    assertNull(config.getNamespace());
  }

  @Test
  public void testWebHDFSZooKeeperConfiguration() throws Exception {

    final boolean isEnabled = true;
    final String ensemble = "host3:2181,host2:2181,host1:2181";

    Map<String, String> serviceConfigProps = new HashMap<>();
    serviceConfigProps.put("ha.zookeeper.quorum", ensemble);

    AmbariCluster.ZooKeeperConfig config = getZooKeeperConfiguration("WEBHDFS", "HDFS", "hdfs-site", serviceConfigProps);
    assertNotNull(config);
    assertEquals(isEnabled, config.isEnabled());
    assertEquals(ensemble, config.getEnsemble());
    assertNull(config.getNamespace());
  }


  @Test
  public void testOozieZooKeeperConfiguration() throws Exception {

    final boolean isEnabled = true;
    final String ensemble = "host1:2181,host2:2181,host3:2181";
    final String namespace = "hiveserver2";

    Map<String, String> serviceConfigProps = new HashMap<>();
    serviceConfigProps.put("oozie.zookeeper.connection.string", ensemble);
    serviceConfigProps.put("oozie.zookeeper.namespace", namespace);

    AmbariCluster.ZooKeeperConfig config = getZooKeeperConfiguration("OOZIE", "oozie-site", serviceConfigProps);
    assertNotNull(config);
    assertEquals(isEnabled, config.isEnabled());
    assertEquals(ensemble, config.getEnsemble());
    assertEquals(namespace, config.getNamespace());
  }


  private ServiceDiscovery.Cluster.ZooKeeperConfig getZooKeeperConfiguration(final String              serviceName,
                                                                             final String              configType,
                                                                             final Map<String, String> serviceConfigProps) {
    return getZooKeeperConfiguration(serviceName, serviceName, configType, serviceConfigProps);
  }


  private ServiceDiscovery.Cluster.ZooKeeperConfig getZooKeeperConfiguration(final String              serviceName,
                                                                             final String              componentName,
                                                                             final String              configType,
                                                                             final Map<String, String> serviceConfigProps) {

    AmbariCluster.ServiceConfiguration sc = EasyMock.createNiceMock(AmbariCluster.ServiceConfiguration.class);
    EasyMock.expect(sc.getProperties()).andReturn(serviceConfigProps).anyTimes();
    EasyMock.replay(sc);

    AmbariCluster cluster = new AmbariCluster("test");
    cluster.addServiceConfiguration(componentName, configType, sc);

    return cluster.getZooKeeperConfiguration(serviceName);
  }


}
