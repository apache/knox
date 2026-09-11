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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

public class ServiceModelGeneratorsHolder {

  private static final ServiceModelGeneratorsHolder INSTANCE = new ServiceModelGeneratorsHolder();
  private final Map<String, List<ServiceModelGenerator>> serviceModelGenerators = new HashMap<>();
  private final Set<String> allRoleTypes = new HashSet<>();

  private ServiceModelGeneratorsHolder() {
    final ServiceLoader<ServiceModelGenerator> loader = ServiceLoader.load(ServiceModelGenerator.class);
    for (ServiceModelGenerator serviceModelGenerator : loader) {
      List<ServiceModelGenerator> smgList = serviceModelGenerators.computeIfAbsent(serviceModelGenerator.getServiceType(), k -> new ArrayList<>());
      smgList.add(serviceModelGenerator);

      final String roleType = serviceModelGenerator.getRoleType();
      if (roleType != null) {
        allRoleTypes.add(roleType);
      }
    }
  }

  public static ServiceModelGeneratorsHolder getInstance() {
    return INSTANCE;
  }

  public List<ServiceModelGenerator> getServiceModelGenerators(String serviceType) {
    return serviceModelGenerators.get(serviceType);
  }

  /**
   * @return the union of the CM role types that any registered
   *         {@link ServiceModelGenerator} operates on. Role types outside of this
   *         set can never produce a service model and therefore do not need to be
   *         fetched or cached during CM service discovery.
   */
  public Set<String> getAllRoleTypes() {
    return Collections.unmodifiableSet(allRoleTypes);
  }

}
