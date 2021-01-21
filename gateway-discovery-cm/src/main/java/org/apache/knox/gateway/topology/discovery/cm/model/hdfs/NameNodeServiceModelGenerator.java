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
  public static final String SERVICE      = "NAMENODE";
  public static final String SERVICE_TYPE = "HDFS";
  public static final String ROLE_TYPE    = "NAMENODE";

  static final String DISCOVERY_NAMESERVICE = "discovery-nameservice";

  static final String AUTOFAILOVER_ENABLED = "autofailover_enabled";
  static final String NN_NAMESERVICE       = "dfs_federation_namenode_nameservice";
  static final String NN_PORT              = "namenode_port";

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
  public ServiceModel generateService(ApiService       service,
                                      ApiServiceConfig serviceConfig,
                                      ApiRole          role,
                                      ApiConfigList    roleConfig) throws ApiException {
    boolean haEnabled = Boolean.parseBoolean(getRoleConfigValue(roleConfig, AUTOFAILOVER_ENABLED));
    String serviceUrl;
    if (haEnabled) {
      String nameservice = getRoleConfigValue(roleConfig, NN_NAMESERVICE);
      serviceUrl = String.format(Locale.getDefault(), "hdfs://%s", nameservice);
    } else {
      String hostname = role.getHostRef().getHostname();
      String port = getRoleConfigValue(roleConfig, NN_PORT);
      serviceUrl = String.format(Locale.getDefault(), "hdfs://%s:%s", hostname, port);
    }

    ServiceModel model =  createServiceModel(serviceUrl);
    model.addRoleProperty(getRoleType(), AUTOFAILOVER_ENABLED, getRoleConfigValue(roleConfig, AUTOFAILOVER_ENABLED));
    model.addRoleProperty(getRoleType(), NN_PORT, getRoleConfigValue(roleConfig, NN_PORT));
    if (haEnabled) {
      model.addRoleProperty(getRoleType(), NN_NAMESERVICE, getRoleConfigValue(roleConfig, NN_NAMESERVICE));

      // Add the nameservice metadata for qualifying the discovery process for this service
      model.addQualifyingServiceParam(DISCOVERY_NAMESERVICE, model.getRoleProperties().get(getRoleType()).get(NN_NAMESERVICE));
    }

    return model;
  }

}
