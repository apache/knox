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
 * Implementation of URLManager intended for query of Zookeeper for active HBase RegionServer hosts.
 *
 * The assumption is that the HBase REST Server will be installed on the same host.  For safety
 * reasons, the REST Server is pinged for access before inclusion in the list of returned hosts.
 *
 * In the event of a failure via markFailed, Zookeeper is queried again for active
 * host information.
 *
 * When configuring the HAProvider in the topology, the zookeeperEnsemble
 * attribute must be set to a comma delimited list of the host and port number,
 * i.e. host1:2181,host2:2181.
 */
public class HBaseZookeeperURLManager extends BaseZookeeperURLManager {
  /**
   * Default Port Number for HBase REST Server
   */
  private static final int PORT_NUMBER = 8080;

  private static final String DEFAULT_ZOOKEEPER_NAMESPACE_SECURE = "/hbase-secure";

  private static final String DEFAULT_ZOOKEEPER_NAMESPACE_UNSECURE = "/hbase-unsecure";


  // -------------------------------------------------------------------------------------
  // Abstract methods
  // -------------------------------------------------------------------------------------

  /**
   * Look within Zookeeper under the /hbase-unsecure/rs branch for active HBase RegionServer hosts
   *
   * @return A List of URLs (never null)
   */
  @Override
  protected List<String> lookupURLs() {
    // Retrieve list of potential hosts from ZooKeeper
    List<String> hosts = retrieveHosts();

    // Validate access to hosts using cheap ping style operation
    List<String> validatedHosts = validateHosts(hosts,"/version/rest","text/xml");

    // Randomize the hosts list for simple load balancing
    if (!validatedHosts.isEmpty()) {
      Collections.shuffle(validatedHosts);
    }

    return validatedHosts;
  }

  @Override
  protected String getServiceName() {
    return "WEBHBASE";
  }

  @Override
  protected String getZookeeperNamespace() {
    return super.getZookeeperNamespace();
  }

  // -------------------------------------------------------------------------------------
  // Private methods
  // -------------------------------------------------------------------------------------

  /**
   * @return Retrieve lists of hosts from ZooKeeper
   */
  private List<String> retrieveHosts()
  {
    List<String> serverHosts = new ArrayList<>();

    try (CuratorFramework zooKeeperClient = CuratorFrameworkFactory.builder().connectString(getZookeeperEnsemble())
        .retryPolicy(new ExponentialBackoffRetry(1000, 3))
        .build()) {
      zooKeeperClient.start();
      zooKeeperClient.blockUntilConnected(10, TimeUnit.SECONDS);

      List<String> serverNodes = null;

      String namespace = getZookeeperNamespace();
      if (namespace != null && !namespace.isEmpty()) {
        if (!namespace.startsWith("/")) {
          namespace = "/" + namespace;
        }
        serverNodes = zooKeeperClient.getChildren().forPath(namespace + "/rs");
      } else {
        // If no namespace is explicitly specified, try the default secure namespace
        try {
          serverNodes = zooKeeperClient.getChildren().forPath(DEFAULT_ZOOKEEPER_NAMESPACE_SECURE + "/rs");
        } catch (Exception e) {
          // Ignore -- znode may not exist
        }

        if (serverNodes == null || serverNodes.isEmpty()) {
          // Fall back to the default unsecure namespace if no secure nodes are found
          serverNodes = zooKeeperClient.getChildren().forPath(DEFAULT_ZOOKEEPER_NAMESPACE_UNSECURE + "/rs");
        }
      }

      if (serverNodes != null) {
        for (String serverNode : serverNodes) {
          String serverURL = constructURL(serverNode);
          serverHosts.add(serverURL);
        }
      }
    } catch (Exception e) {
      LOG.failedToGetZookeeperUrls(e);
      throw new RuntimeException(e);
    }

    return serverHosts;
  }

  /**
   * Given a String of the format "host,number,number" convert to a URL of the format
   * "http://host:port".
   *
   * @param serverInfo Server Info from Zookeeper (required)
   *
   * @return URL to HBASE
   */
  private String constructURL(String serverInfo) {
    String scheme = "http";

    // Strip off the host name
    return scheme +
               "://" +
               serverInfo.substring(0, serverInfo.indexOf(',')) +
               ":" +
               PORT_NUMBER;
  }
}
