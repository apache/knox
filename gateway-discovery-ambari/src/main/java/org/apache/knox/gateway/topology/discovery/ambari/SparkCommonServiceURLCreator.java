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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class SparkCommonServiceURLCreator implements ServiceURLCreator {

  private static final String URL_TEMPLATE = "http://%s:%s";

  protected AmbariCluster cluster = null;

  String primaryComponentName = null;

  String secondaryComponentName = null;

  String portConfigProperty = null;

  @Override
  public void init(AmbariCluster cluster) {
    this.cluster = cluster;
  }

  @Override
  public List<String> create(String service, Map<String, String> serviceParams) {
    List<String> urls = new ArrayList<>();

    if (getTargetService().equalsIgnoreCase(service)) {
      AmbariComponent comp = cluster.getComponent(primaryComponentName);
      if (comp == null) {
        comp = cluster.getComponent(secondaryComponentName);
      }

      if (comp != null) {
        String port = comp.getConfigProperty(portConfigProperty);
        List<String> hostNames = comp.getHostNames();
        for (String host : hostNames) {
          urls.add(String.format(URL_TEMPLATE, host, port));
        }
      }
    }

    return urls;
  }



}
