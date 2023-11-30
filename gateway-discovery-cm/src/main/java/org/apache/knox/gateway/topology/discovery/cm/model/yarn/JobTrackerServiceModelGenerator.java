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
package org.apache.knox.gateway.topology.discovery.cm.model.yarn;

import com.cloudera.api.swagger.client.ApiException;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;

import java.util.Locale;

public class JobTrackerServiceModelGenerator extends ResourceManagerServiceModelGeneratorBase {

  private static final String SERVICE = "JOBTRACKER";

  private static final String RM_PORT = "yarn_resourcemanager_address";

  @Override
  public String getService() {
    return SERVICE;
  }

  @Override
  public ServiceModel.Type getModelType() {
    return ServiceModel.Type.API;
  }

  @Override
  public ServiceModel generateService(ApiService service,
                                 ApiServiceConfig serviceConfig,
                                 ApiRole role,
                                 ApiConfigList roleConfig, ApiServiceConfig coreSettingsConfig) throws ApiException {

    String hostname = role.getHostRef().getHostname();
    String port = getRoleConfigValue(roleConfig, RM_PORT);

    ServiceModel model = createServiceModel(String.format(Locale.getDefault(), "rpc://%s:%s", hostname, port));
    model.addRoleProperty(getRoleType(), RM_PORT, port);

    // N.B. It is not necessary to register the hdfs_hadoop_ssl_enabled configuration property for monitoring here
    //      because that property is already registered for the HDFS ServiceModelGenerator types.

    return model;
  }
}
