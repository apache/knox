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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of URLManager intended for query of Zookeeper for active SOLR Cloud hosts. 
 * In the event of a failure via markFailed, Zookeeper is queried again for active
 * host information.
 * 
 * When configuring the HAProvider in the topology, the zookeeperEnsemble
 * attribute must be set to a comma delimited list of the host and port number,
 * i.e. host1:2181,host2:2181.
 */
public class SOLRZookeeperURLManager extends BaseZookeeperURLManager {

  // -------------------------------------------------------------------------------------
  // Abstract methods
  // -------------------------------------------------------------------------------------

  /**
   * Look within Zookeeper under the /live_nodes branch for active SOLR hosts
   *
   * @return A List of URLs (never null)
   */
  @Override
  protected List<String> lookupURLs() {
    // Retrieve list of potential hosts from ZooKeeper
    List<String> hosts = retrieveHosts();

    // Randomize the hosts list for simple load balancing
    if (!hosts.isEmpty()) {
      Collections.shuffle(hosts);
    }

    return hosts;
  }

  protected String getServiceName() {
    return "SOLR";
  };

  // -------------------------------------------------------------------------------------
  // Private methods
  // -------------------------------------------------------------------------------------

  /**
   * @return Retrieve lists of hosts from ZooKeeper
   */
  private List<String> retrieveHosts()
  {
    List<String> serverHosts = new ArrayList<>();

    try (CuratorFramework zooKeeperClient = CuratorFrameworkFactory.builder()
        .connectString(getZookeeperEnsemble())
        .retryPolicy(new ExponentialBackoffRetry(1000, 3))
        .build()) {

      zooKeeperClient.start();
      zooKeeperClient.blockUntilConnected(10, TimeUnit.SECONDS);
      List<String> serverNodes = zooKeeperClient.getChildren().forPath("/live_nodes");
      for (String serverNode : serverNodes) {
        String serverURL = constructURL(serverNode);
        serverHosts.add(serverURL);
      }
    } catch (Exception e) {
      LOG.failedToGetZookeeperUrls(e);
      throw new RuntimeException(e);
    }

    return serverHosts;
  }

  /**
   * Given a String of the format "host:port_solr" convert to a URL of the format
   * "http://host:port/solr".
   *
   * @param serverInfo Server Info from Zookeeper (required)
   *
   * @return URL to SOLR
   */
  private String constructURL(String serverInfo) {
    String scheme = "http";

    StringBuffer buffer = new StringBuffer();
    buffer.append(scheme);
    buffer.append("://");
    buffer.append(serverInfo.replace("_", "/"));
    return buffer.toString();
  }
}
