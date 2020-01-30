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
package org.apache.knox.gateway.topology.discovery.cm.monitor;

import com.cloudera.api.swagger.model.ApiConfig;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiServiceConfig;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data model for the cluster configuration monitor.
 */
final class ServiceConfigurationModel {

  private static final String NULL_VALUE = "null";

  private Map<String, String> serviceProps = new ConcurrentHashMap<>();
  private Map<String, Map<String, String>> roleProps = new ConcurrentHashMap<>();

  ServiceConfigurationModel() {
  }

  ServiceConfigurationModel(final ApiServiceConfig            serviceConfig,
                            final Map<ApiRole, ApiConfigList> roles) {
    // Service properties
    for (ApiConfig item : serviceConfig.getItems()) {
      String value = item.getValue();
      if (value == null || value.isEmpty()) {
        value = item.getDefault();
      }
      addServiceProperty(item.getName(), value);
    }

    // Role properties
    if (roles != null && !roles.isEmpty()) {
      for (Map.Entry<ApiRole, ApiConfigList> entry : roles.entrySet()) {
        ApiRole role = entry.getKey();
        ApiConfigList roleConfigList = entry.getValue();

        for (ApiConfig roleConfig : roleConfigList.getItems()) {
          roleConfig.getName();
          String value = roleConfig.getValue();
          if (value == null) {
            value = roleConfig.getDefault();
          }
          addRoleProperty(role.getType(), roleConfig.getName(), value);
        }
      }
    }
  }

  void addServiceProperty(final String name, final String value) {
    serviceProps.put(name, (value != null ? value : NULL_VALUE));
  }

  Map<String, String> getServiceProps() {
    return serviceProps;
  }

  void addRoleProperty(final String roleType, final String name, final String value) {
    roleProps.computeIfAbsent(roleType, p -> new ConcurrentHashMap<>())
        .put(name, (value != null ? value : NULL_VALUE));
  }

  Set<String> getRoleTypes() {
    return roleProps.keySet();
  }

  Map<String, String> getRoleProps(final String roleType) {
    return roleProps.get(roleType);
  }

  Map<String, Map<String, String>> getRoleProps() {
    return roleProps;
  }
}
