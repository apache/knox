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

import java.util.List;
import java.util.Map;

public interface ServiceURLCreator {

  /**
   *
   * @return The name of the service for which the implementation is able to create URLs.
   */
  String getTargetService();


  /**
   *
   * @param cluster the cluster from which the service URLs will be derived.
   */
  void init(AmbariCluster cluster);


  /**
   * Creates one or more cluster-specific URLs for the specified service.
   *
   * @param service       The service identifier.
   * @param serviceParams A map of parameters and their corresponding values for the specified service.
   *
   * @return A List of created URL strings; the list may be empty.
   */
  List<String> create(String service, Map<String, String> serviceParams);

}
