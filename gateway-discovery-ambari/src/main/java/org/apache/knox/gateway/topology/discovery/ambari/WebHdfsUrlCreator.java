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

import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A ServiceURLCreator implementation for WEBHDFS.
 */
public class WebHdfsUrlCreator implements ServiceURLCreator {

  private static final String SERVICE = "WEBHDFS";

  private AmbariServiceDiscoveryMessages log = MessagesFactory.get(AmbariServiceDiscoveryMessages.class);

  private AmbariCluster cluster = null;

  WebHdfsUrlCreator(AmbariCluster cluster) {
    this.cluster = cluster;
  }

  @Override
  public List<String> create(String service) {
    List<String> urls = new ArrayList<>();

    if (SERVICE.equals(service)) {
      AmbariCluster.ServiceConfiguration sc = cluster.getServiceConfiguration("HDFS", "hdfs-site");
      if (sc != null) {
        // First, check if it's HA config
        String nameServices = null;
        AmbariComponent nameNodeComp = cluster.getComponent("NAMENODE");
        if (nameNodeComp != null) {
          nameServices = nameNodeComp.getConfigProperty("dfs.nameservices");
        }

        if (nameServices != null && !nameServices.isEmpty()) {
          // If it is an HA configuration
          Map<String, String> props = sc.getProperties();

          // Name node HTTP addresses are defined as properties of the form:
          //      dfs.namenode.http-address.<NAMESERVICES>.nn<INDEX>
          // So, this iterates over the nn<INDEX> properties until there is no such property (since it cannot be known how
          // many are defined by any other means).
          int i = 1;
          String propertyValue = getHANameNodeHttpAddress(props, nameServices, i++);
          while (propertyValue != null) {
            urls.add(createURL(propertyValue));
            propertyValue = getHANameNodeHttpAddress(props, nameServices, i++);
          }
        } else { // If it's not an HA configuration, get the single name node HTTP address
          urls.add(createURL(sc.getProperties().get("dfs.namenode.http-address")));
        }
      }
    }

    return urls;
  }

  private static String getHANameNodeHttpAddress(Map<String, String> props, String nameServices, int index) {
    return props.get("dfs.namenode.http-address." + nameServices + ".nn" + index);
  }

  private static String createURL(String address) {
    return "http://" + address + "/webhdfs";
  }

}
