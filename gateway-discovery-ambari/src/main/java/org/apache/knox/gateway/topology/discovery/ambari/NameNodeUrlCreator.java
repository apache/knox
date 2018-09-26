/*
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


public class NameNodeUrlCreator implements ServiceURLCreator {

  private static final String SERVICE = "NAMENODE";

  private static final String NAMESERVICE_PARAM = "discovery-nameservice";

  private AmbariServiceDiscoveryMessages log = MessagesFactory.get(AmbariServiceDiscoveryMessages.class);

  private AmbariCluster cluster = null;

  public void init(AmbariCluster cluster) {
    this.cluster = cluster;
  }

  @Override
  public String getTargetService() {
    return SERVICE;
  }

  @Override
  public List<String> create(String service, Map<String, String> serviceParams) {
    List<String> urls = new ArrayList<>();

    if (SERVICE.equals(service)) {
      if (serviceParams != null && serviceParams.containsKey(NAMESERVICE_PARAM)) {
        String declaredNameService = serviceParams.get(NAMESERVICE_PARAM);

        // Validate declared nameservice against available nameservices
        if (!validateDeclaredNameService(cluster, declaredNameService)) {
          log.undefinedHDFSNameService(declaredNameService);
        } else {
          urls.add("hdfs://" + declaredNameService);
        }
      } else {
        // Add the default nameservice URL to the result
        AmbariCluster.ServiceConfiguration coreSite = cluster.getServiceConfiguration("HDFS", "core-site");
        if (coreSite != null) {
          String defaultFS = coreSite.getProperties().get("fs.defaultFS");
          if (defaultFS != null) {
            urls.add(defaultFS);
          }
        }
      }
    }

    return urls;
  }

  // Verify whether the declared nameservice is among the configured nameservices in the cluster
  private static boolean validateDeclaredNameService(AmbariCluster cluster, String declaredNameService) {
    boolean isValid = false;

    AmbariComponent nameNodeComp = cluster.getComponent(SERVICE);
    if (nameNodeComp != null) {
      String nameServices = nameNodeComp.getConfigProperty("dfs.nameservices");
      if (nameServices != null && !nameServices.isEmpty()) {
        // Parse the nameservices value
        String[] namespaces = nameServices.split(",");
        for (String ns : namespaces) {
          if (ns.equals(declaredNameService)) {
            isValid = true;
            break;
          }
        }
      }
    }
    return isValid;
  }

}
