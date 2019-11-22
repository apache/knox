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
import org.apache.knox.gateway.topology.discovery.cm.model.AbstractServiceModelGenerator;

import java.util.Locale;

public class NameNodeServiceModelGenerator extends AbstractServiceModelGenerator {
  private static final String SERVICE      = "NAMENODE";
  private static final String SERVICE_TYPE = "HDFS";
  private static final String ROLE_TYPE    = "NAMENODE";

  @Override
  public String getServiceType() {
    return SERVICE_TYPE;
  }

  @Override
  public String getService() {
    return SERVICE;
  }

  @Override
  public String getRoleType() {
    return ROLE_TYPE;
  }

  @Override
  public ServiceModel.Type getModelType() {
    return ServiceModel.Type.API;
  }

  @Override
  public boolean handles(ApiService       service,
                         ApiServiceConfig serviceConfig,
                         ApiRole          role,
                         ApiConfigList    roleConfig) {
    return getServiceType().equals(service.getType()) && getRoleType().equals(role.getType());
  }

  @Override
  public ServiceModel generateService(ApiService       service,
                                      ApiServiceConfig serviceConfig,
                                      ApiRole          role,
                                      ApiConfigList    roleConfig) throws ApiException {
    boolean haEnabled = Boolean.parseBoolean(getRoleConfigValue(roleConfig, "autofailover_enabled"));
    String serviceUrl;
    if(haEnabled) {
      String nameservice = getRoleConfigValue(roleConfig, "dfs_federation_namenode_nameservice");
      serviceUrl = String.format(Locale.getDefault(), "hdfs://%s", nameservice);
    } else {
      String hostname = role.getHostRef().getHostname();
      String port = getRoleConfigValue(roleConfig, "namenode_port");
      serviceUrl = String.format(Locale.getDefault(), "hdfs://%s:%s", hostname, port);
    }
    return createServiceModel(serviceUrl);
  }

}
