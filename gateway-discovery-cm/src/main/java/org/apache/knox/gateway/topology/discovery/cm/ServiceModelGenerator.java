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
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;

public interface ServiceModelGenerator {

  /**
   * @return The name of the Knox service for which the implementation will generate a model.
   */
  String getService();

  /**
   * @return The Cloudera Manager configuration service type.
   */
  String getServiceType();

  /**
   * @return The Cloudera Manager configuration role type.
   */
  String getRoleType();

  ServiceModel.Type getModelType();

  void setApiClient(DiscoveryApiClient client);

  ServiceModelGeneratorHandleResponse handles(ApiService service, ApiServiceConfig serviceConfig, ApiRole role, ApiConfigList roleConfig);

  ServiceModel generateService(ApiService service, ApiServiceConfig serviceConfig, ApiRole role, ApiConfigList roleConfig, ApiServiceConfig coreSettingsConfig) throws ApiException;
}
