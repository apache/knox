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

import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of URLManager intended for query of Zookeeper for active Kafka hosts.
 *
 * The assumption is that the Confluent REST Proxy will be installed on the same host.  For safety
 * reasons, the REST Server is pinged for access before inclusion in the list of returned hosts.
 *
 * In the event of a failure via markFailed, Zookeeper is queried again for active
 * host information.
 *
 * When configuring the HAProvider in the topology, the zookeeperEnsemble
 * attribute must be set to a comma delimited list of the host and port number,
 * i.e. host1:2181,host2:2181.
 */
public class KafkaZookeeperURLManager extends BaseZookeeperURLManager {
  /**
   * Default Port Number for Confluent Kafka REST Server
   */
  private static final int PORT_NUMBER = 8082;
  /**
   * Base path for retrieval from Zookeeper
   */
  private static final String BASE_PATH = "/brokers/ids";

  // -------------------------------------------------------------------------------------
  // Abstract methods
  // -------------------------------------------------------------------------------------

  /**
   * Look within Zookeeper under the /broker/ids branch for active Kafka hosts
   *
   * @return A List of URLs (never null)
   */
  @Override
  protected List<String> lookupURLs() {
    // Retrieve list of potential hosts from ZooKeeper
    List<String> hosts = retrieveHosts();

    // Validate access to hosts using cheap ping style operation
    List<String> validatedHosts = validateHosts(hosts,"/topics","application/vnd.kafka.v2+json");

    // Randomize the hosts list for simple load balancing
    if (!validatedHosts.isEmpty()) {
      Collections.shuffle(validatedHosts);
    }

    return validatedHosts;
  }

  @Override
  protected String getServiceName() {
    return "KAFKA";
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

    try (CuratorFramework zooKeeperClient = CuratorFrameworkFactory.builder()
        .connectString(getZookeeperEnsemble())
        .retryPolicy(new ExponentialBackoffRetry(1000, 3))
        .build()) {
      zooKeeperClient.start();
      zooKeeperClient.blockUntilConnected(10, TimeUnit.SECONDS);

      // Retrieve list of host URLs from ZooKeeper
      List<String> brokers = zooKeeperClient.getChildren().forPath(BASE_PATH);

      for (String broker : brokers) {
        String serverInfo = new String(zooKeeperClient.getData().forPath(BASE_PATH + "/" + broker), StandardCharsets.UTF_8);

        String serverURL = constructURL(serverInfo);
        serverHosts.add(serverURL);
      }
    } catch (Exception e) {
      LOG.failedToGetZookeeperUrls(e);
      throw new RuntimeException(e);
    }

    return serverHosts;
  }

  /**
   * Given a String of the format "{"jmx_port":-1,"timestamp":"1505763958072","endpoints":["PLAINTEXT://host:6667"],"host":"host","version":3,"port":6667}"
   * convert to a URL of the format "http://host:port".
   *
   * @param serverInfo Server Info in JSON Format from Zookeeper (required)
   *
   * @return URL to Kafka
   * @throws ParseException failure parsing json from string
   */
  private String constructURL(String serverInfo) throws ParseException {
    String scheme = "http";

    StringBuilder buffer = new StringBuilder();

    buffer.append(scheme).append("://");

    JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
    JSONObject obj = (JSONObject) parser.parse(serverInfo);
    buffer.append(obj.get("host")).append(':').append(PORT_NUMBER);

    return buffer.toString();
  }
}
