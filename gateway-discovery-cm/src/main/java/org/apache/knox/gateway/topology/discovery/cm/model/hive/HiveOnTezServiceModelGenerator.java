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
package org.apache.knox.gateway.topology.discovery.cm.model.hive;

import com.cloudera.api.swagger.client.ApiException;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;

public class HiveOnTezServiceModelGenerator extends HiveServiceModelGenerator {

  public static final String SERVICE_TYPE = "HIVE_ON_TEZ";

  static final String HIVEONTEZ_TRANSPORT_MODE = TRANSPORT_MODE.replaceAll("\\.", "_");
  static final String HIVEONTEZ_HTTP_PORT      = HTTP_PORT.replaceAll("\\.", "_");

  @Override
  public String getServiceType() {
    return SERVICE_TYPE;
  }

  @Override
  protected boolean checkHiveServer2HTTPMode(ApiConfigList roleConfig) {
    return TRANSPORT_MODE_HTTP.equals(getRoleConfigValue(roleConfig, HIVEONTEZ_TRANSPORT_MODE));
  }

  @Override
  protected String getHttpPort(ApiConfigList roleConfig) {
    return getRoleConfigValue(roleConfig, HIVEONTEZ_HTTP_PORT);
  }

  @Override
  public ServiceModel generateService(ApiService       service,
                                      ApiServiceConfig serviceConfig,
                                      ApiRole          role,
                                      ApiConfigList    roleConfig) throws ApiException {
    ServiceModel model = super.generateService(service, serviceConfig, role, roleConfig);
    model.addRoleProperty(getRoleType(),
                          HIVEONTEZ_HTTP_PORT,
                          getRoleConfigValue(roleConfig, HIVEONTEZ_HTTP_PORT));
    model.addRoleProperty(getRoleType(),
                          HIVEONTEZ_TRANSPORT_MODE,
                          getRoleConfigValue(roleConfig, HIVEONTEZ_TRANSPORT_MODE));

    return model;
  }

}
