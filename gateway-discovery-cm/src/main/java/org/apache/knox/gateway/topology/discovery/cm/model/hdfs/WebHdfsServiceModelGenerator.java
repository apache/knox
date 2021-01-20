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
package org.apache.knox.gateway.topology.discovery.cm.model.hdfs;

import com.cloudera.api.swagger.client.ApiException;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModelGeneratorHandleResponse;

public class WebHdfsServiceModelGenerator extends HdfsUIServiceModelGenerator {
  private static final String SERVICE = "WEBHDFS";
  private static final String WEBHDFS_SUFFIX = "/webhdfs";

  static final String WEBHDFS_ENABLED = "dfs_webhdfs_enabled";

  @Override
  public String getService() {
    return SERVICE;
  }

  @Override
  public ServiceModel.Type getModelType() {
    return ServiceModel.Type.API;
  }

  @Override
  public ServiceModelGeneratorHandleResponse handles(ApiService service, ApiServiceConfig serviceConfig, ApiRole role, ApiConfigList roleConfig) {
    final ServiceModelGeneratorHandleResponse response = super.handles(service, serviceConfig, role, roleConfig);
    if (response.handled()) {
      final String webHdfsEnabled = getServiceConfigValue(serviceConfig, WEBHDFS_ENABLED);
      if (webHdfsEnabled == null) {
        response.addConfigurationIssue("Missing configuration: " + WEBHDFS_ENABLED);
      } else if (!Boolean.parseBoolean(webHdfsEnabled)) {
        response.addConfigurationIssue("Invalid configuration: " + WEBHDFS_ENABLED + ". Expected=true; Found=" + webHdfsEnabled);
      }
    }
    return response;
  }

  @Override
  public ServiceModel generateService(ApiService service, ApiServiceConfig serviceConfig, ApiRole role, ApiConfigList roleConfig) throws ApiException {
    ServiceModel parent = super.generateService(service, serviceConfig, role, roleConfig);
    String serviceUrl = parent.getServiceUrl() + WEBHDFS_SUFFIX;

    ServiceModel model = createServiceModel(serviceUrl);
    model.addServiceProperty(WEBHDFS_ENABLED, getServiceConfigValue(serviceConfig, WEBHDFS_ENABLED));

    // Add parent model metadata
    addParentModelMetadata(model, parent);

    return model;
  }

}
