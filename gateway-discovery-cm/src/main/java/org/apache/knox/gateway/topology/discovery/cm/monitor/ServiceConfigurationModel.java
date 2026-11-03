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
import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;

import java.util.Collection;
import java.util.HashMap;
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

  /**
   * Transform the given discovered {@link ServiceModel}s into a map of service type to its
   * {@link ServiceConfigurationModel}, preserving exactly the service and role properties that the model generators
   * extracted. This is the representation the configuration monitor compares against, so building both the persisted
   * baseline and the current snapshot through this method keeps them comparable.
   *
   * @param models the service models produced by the model generators
   * @return a map keyed by service type; empty if there are no models
   */
  static Map<String, ServiceConfigurationModel> fromServiceModels(final Collection<ServiceModel> models) {
    final Map<String, ServiceConfigurationModel> result = new HashMap<>();
    if (models != null) {
      for (ServiceModel model : models) {
        final ServiceConfigurationModel scp =
            result.computeIfAbsent(model.getServiceType(), p -> new ServiceConfigurationModel());

        for (Map.Entry<String, String> entry : model.getServiceProperties().entrySet()) {
          scp.addServiceProperty(entry.getKey(), entry.getValue());
        }

        final Map<String, Map<String, String>> roleProperties = model.getRoleProperties();
        for (String roleName : roleProperties.keySet()) {
          final Map<String, String> rp = roleProperties.get(roleName);
          for (Map.Entry<String, String> entry : rp.entrySet()) {
            scp.addRoleProperty(roleName, entry.getKey(), entry.getValue());
          }
        }
      }
    }
    return result;
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
