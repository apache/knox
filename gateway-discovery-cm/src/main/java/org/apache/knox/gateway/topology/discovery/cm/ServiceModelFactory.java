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

import com.cloudera.api.swagger.client.ApiException;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiRoleConfig;
import com.cloudera.api.swagger.model.ApiRoleConfigList;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.topology.discovery.cm.model.opensearch.OpenSearchApiMasterServiceModelGenerator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs the registered {@link ServiceModelGenerator}s against a single Cloudera Manager service and returns the
 * resulting {@link ServiceModel}s.
 * <p>
 * Shared by {@link ClouderaManagerServiceDiscovery} (during cluster discovery) and by the configuration monitor
 * (to compute the current, model-based configuration of a service). Keeping a single implementation ensures the
 * monitor sees exactly what discovery would produce: a service whose configuration is invalid yields no model here,
 * just as it would be absent from the discovered/persisted cluster configuration.
 */
public final class ServiceModelFactory {

  private static final ClouderaManagerServiceDiscoveryMessages log =
      MessagesFactory.get(ClouderaManagerServiceDiscoveryMessages.class);

  private static final ServiceModelGeneratorsHolder serviceModelGeneratorsHolder = ServiceModelGeneratorsHolder.getInstance();

  private ServiceModelFactory() {
  }

  /**
   * Generate the service models for the given service by running every {@link ServiceModelGenerator} registered for
   * the service's type against each of the service's roles.
   *
   * @param client             the discovery API client
   * @param service            the CM service
   * @param serviceConfig      the service-level configuration (may be null for services without config, e.g. CM)
   * @param roleConfigList     the role configurations of the service
   * @param coreSettingsConfig the CORE_SETTINGS service configuration (may be null)
   * @return the generated service models; empty if no generator handles the service (e.g. invalid configuration)
   */
  public static Set<ServiceModel> generateServiceModels(DiscoveryApiClient client, ApiService service,
      ApiServiceConfig serviceConfig, ApiRoleConfigList roleConfigList, ApiServiceConfig coreSettingsConfig) throws ApiException {
    Set<ServiceModel> serviceModels = new HashSet<>();
    final List<ServiceModelGenerator> modelGenerators = serviceModelGeneratorsHolder.getServiceModelGenerators(service.getType());
    if (roleConfigList != null && roleConfigList.getItems() != null) {
      for (ApiRoleConfig roleConfig : roleConfigList.getItems()) {
        ApiRole role = new ApiRole()
            .name(roleConfig.getName())
            .type(roleConfig.getRoleType())
            .hostRef(roleConfig.getHostRef());
        ApiConfigList roleConfigs = roleConfig.getConfig();
        log.discoveringServiceRole(role.getName(), role.getType());
        serviceModels.addAll(
            generateServiceModelsForRole(client, service, serviceConfig, role, roleConfigs, coreSettingsConfig, modelGenerators, roleConfigList));
        log.discoveredServiceRole(role.getName(), role.getType());
      }
    }
    return serviceModels;
  }

  private static Set<ServiceModel> generateServiceModelsForRole(DiscoveryApiClient client, ApiService service,
      ApiServiceConfig serviceConfig, ApiRole role, ApiConfigList roleConfig, ApiServiceConfig coreSettingsConfig,
      List<ServiceModelGenerator> modelGenerators, ApiRoleConfigList roleConfigList) throws ApiException {
    Set<ServiceModel> serviceModels = new HashSet<>();
    if (modelGenerators != null) {
      for (ServiceModelGenerator serviceModelGenerator : modelGenerators) {
        if (OpenSearchApiMasterServiceModelGenerator.shouldSkipGeneratorWhenOpenSearchMaster(serviceModelGenerator, roleConfigList)) {
          continue;
        }

        ServiceModel serviceModel = generateServiceModel(client, service, serviceConfig, role, roleConfig, coreSettingsConfig, serviceModelGenerator);
        if (serviceModel != null) {
          serviceModels.add(serviceModel);
        }
      }
    }
    return serviceModels;
  }

  private static ServiceModel generateServiceModel(DiscoveryApiClient client, ApiService service, ApiServiceConfig serviceConfig,
      ApiRole role, ApiConfigList roleConfig, ApiServiceConfig coreSettingsConfig, ServiceModelGenerator serviceModelGenerator) throws ApiException {
    serviceModelGenerator.setApiClient(client);
    ServiceModelGeneratorHandleResponse response = serviceModelGenerator.handles(service, serviceConfig, role, roleConfig);
    if (response.handled()) {
      return serviceModelGenerator.generateService(service, serviceConfig, role, roleConfig, coreSettingsConfig);
    } else if (!response.getConfigurationIssues().isEmpty()) {
      log.serviceRoleHasConfigurationIssues(role.getName(), String.join(";", response.getConfigurationIssues()));
    }
    return null;
  }
}
