/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.discovery.cm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public class ServiceModelGeneratorsHolder {

  private static final ServiceModelGeneratorsHolder INSTANCE = new ServiceModelGeneratorsHolder();
  private final Map<String, List<ServiceModelGenerator>> serviceModelGenerators = new HashMap<>();

  private ServiceModelGeneratorsHolder() {
    final ServiceLoader<ServiceModelGenerator> loader = ServiceLoader.load(ServiceModelGenerator.class);
    for (ServiceModelGenerator serviceModelGenerator : loader) {
      List<ServiceModelGenerator> smgList = serviceModelGenerators.computeIfAbsent(serviceModelGenerator.getServiceType(), k -> new ArrayList<>());
      smgList.add(serviceModelGenerator);
    }
  }

  public static ServiceModelGeneratorsHolder getInstance() {
    return INSTANCE;
  }

  public List<ServiceModelGenerator> getServiceModelGenerators(String serviceType) {
    return serviceModelGenerators.get(serviceType);
  }

}
