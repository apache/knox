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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SparkThriftServerUIServiceURLCreator extends SparkCommonServiceURLCreator {

  private static final String RESOURCE_ROLE = "THRIFTSERVERUI";

  @Override
  public void init(AmbariCluster cluster) {
    super.init(cluster);
    primaryComponentName   = "SPARK_THRIFTSERVER";
    secondaryComponentName = "SPARK2_THRIFTSERVER";
    portConfigProperty     = "hive.server2.thrift.http.port";
  }

  @Override
  public String getTargetService() {
    return RESOURCE_ROLE;
  }

  @Override
  public List<String> create(String service, Map<String, String> serviceParams) {
    List<String> thriftURLs = new ArrayList<>();

    // Discover the URLs using the common mechanism
    List<String> urls = super.create(service, serviceParams);

    // If at least one URL was discovered, validate the transport mode, and append the endpoint path if configured
    if (urls != null && !urls.isEmpty()) {
      AmbariComponent comp = cluster.getComponent(primaryComponentName);
      if (comp == null) {
        comp = cluster.getComponent(secondaryComponentName);
      }

      if (comp != null) {
        String transportMode = comp.getConfigProperty("hive.server2.transport.mode");
        if (transportMode.equalsIgnoreCase("http")) {
          String endpoint = comp.getConfigProperty("hive.server2.http.endpoint");
          for (String url : urls) {
            thriftURLs.add(url + (endpoint != null ? "/" + endpoint : ""));
          }
        }
      }
    }

    return thriftURLs;
  }

}
