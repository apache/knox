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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Factory for creating cluster-specific service URLs.
 */
public class ServiceURLFactory {

  private Map<String, ServiceURLCreator> urlCreators = new HashMap<>();

  private ServiceURLCreator defaultURLCreator = null;


  private ServiceURLFactory(AmbariCluster cluster) {
    // Default URL creator
    defaultURLCreator = new AmbariDynamicServiceURLCreator(cluster);

    // Custom URL creators (too complex for simple mapping configuration)
    ServiceLoader<ServiceURLCreator> creators = ServiceLoader.load(ServiceURLCreator.class);
    for (ServiceURLCreator creator : creators) {
      String type = creator.getTargetService();
      if (type != null && !type.isEmpty()) {
        creator.init(cluster);
        urlCreators.put(creator.getTargetService(), creator);
      }
    }
  }


  /**
   * Create a new factory for the specified cluster.
   *
   * @param cluster The cluster.
   *
   * @return A ServiceURLFactory instance.
   */
  public static ServiceURLFactory newInstance(AmbariCluster cluster) {
    return new ServiceURLFactory(cluster);
  }


  /**
   * Create one or more cluster-specific URLs for the specified service.
   *
   * @param service       The service identifier.
   * @param serviceParams A map of parameters and their corresponding values for the specified service.
   *
   * @return A List of service URL strings; the list may be empty.
   */
  public List<String> create(String service, Map<String, String> serviceParams) {
    List<String> urls = new ArrayList<>();

    ServiceURLCreator creator = urlCreators.get(service);
    if (creator == null) {
      creator = defaultURLCreator;
    }

    urls.addAll(creator.create(service, serviceParams));

    return urls;
  }

}
