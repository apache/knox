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
import org.apache.knox.gateway.ha.provider.HaServiceConfig;
import org.apache.knox.gateway.ha.provider.impl.i18n.HaMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HS2ZookeeperURLManager extends DefaultURLManager {

  private static final HaMessages LOG = MessagesFactory.get(HaMessages.class);

  private static final Pattern kvPattern = Pattern.compile("([^=;]*)=([^;]*)[;]?");

  private String zooKeeperEnsemble;

  private String zooKeeperNamespace;

  private Set<String> failedSet;

  public HS2ZookeeperURLManager() {
    failedSet = new LinkedHashSet<>();
  }

  @Override
  public boolean supportsConfig(HaServiceConfig config) {
    if (!config.getServiceName().equalsIgnoreCase("HIVE")) {
      return false;
    }
    String zookeeperEnsemble = config.getZookeeperEnsemble();
    String zookeeperNamespace = config.getZookeeperNamespace();
    if ( zookeeperEnsemble != null && zookeeperNamespace != null && !zookeeperEnsemble.trim().isEmpty() && !zookeeperNamespace.trim().isEmpty()) {
      return true;
    }
    return false;
  }

  @Override
  public void setConfig(HaServiceConfig config) {
    zooKeeperEnsemble = config.getZookeeperEnsemble();
    zooKeeperNamespace = config.getZookeeperNamespace();
    setURLs(lookupURLs());
  }

  public List<String> lookupURLs() {
    List<String> serverHosts = new ArrayList<>();
    try (CuratorFramework zooKeeperClient = CuratorFrameworkFactory.builder().connectString(zooKeeperEnsemble)
        .retryPolicy(new ExponentialBackoffRetry(1000, 3)).build()) {
      zooKeeperClient.start();
      zooKeeperClient.blockUntilConnected(10, TimeUnit.SECONDS);
      List<String> serverNodes = zooKeeperClient.getChildren().forPath("/" + zooKeeperNamespace);
      for (String serverNode : serverNodes) {
        String serverInfo =
            new String(
                zooKeeperClient.getData().forPath("/" + zooKeeperNamespace + "/" + serverNode),
                StandardCharsets.UTF_8);
        String serverURL = constructURL(serverInfo);
        serverHosts.add(serverURL);
      }
    } catch (Exception e) {
      LOG.failedToGetZookeeperUrls(e);
      throw new RuntimeException(e);
    }
    return serverHosts;
  }

  private String constructURL(String serverInfo) {
    Matcher matcher = kvPattern.matcher(serverInfo);
    String scheme = "http";
    String host = null;
    String port = "10001";
    String httpPath = "cliservice";
    while (matcher.find()) {
      if ( (matcher.group(1) != null) && matcher.group(2) != null ) {
        switch ( matcher.group(1) ) {
          case "hive.server2.thrift.bind.host" :
            host = matcher.group(2);
            break;
          case "hive.server2.thrift.http.port" :
            port = matcher.group(2);
            break;
          case "hive.server2.thrift.http.path" :
            httpPath = matcher.group(2);
            break;
          case "hive.server2.use.SSL" :
            if (Boolean.parseBoolean(matcher.group(2))) {
              scheme = "https";
            }
        }
      }
    }
    return scheme +
               "://" +
               host +
               ":" +
               port +
               "/" +
               httpPath;
  }

  @Override
  public synchronized void markFailed(String url) {
    failedSet.add(url);
    //refresh the list when we have hit all urls once
    if (failedSet.size() >= getURLs().size()) {
      failedSet.clear();
      setURLs(lookupURLs());
    }
    super.markFailed(url);
  }
}
