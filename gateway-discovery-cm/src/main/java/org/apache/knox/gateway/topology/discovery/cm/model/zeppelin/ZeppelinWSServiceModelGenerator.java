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
package org.apache.knox.gateway.topology.discovery.cm.model.zeppelin;

import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;

import java.util.Locale;

public class ZeppelinWSServiceModelGenerator extends ZeppelinServiceModelGenerator {
  private static final String SERVICE = "ZEPPELINWS";

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
                                 ApiConfigList roleConfig, ApiServiceConfig coreSettingsConfig) {
    String hostname = role.getHostRef().getHostname();
    String scheme;
    String port;
    if(isSSL(roleConfig)) {
      scheme = "wss";
      port = getSSLPort(roleConfig);
    } else {
      scheme = "ws";
      port = getPort(roleConfig);
    }

    ServiceModel model =
        createServiceModel(String.format(Locale.getDefault(), "%s://%s:%s/ws", scheme, hostname, port));
    model.addRoleProperty(getRoleType(), SSL_ENABLED, getRoleConfigValue(roleConfig, SSL_ENABLED));
    model.addRoleProperty(getRoleType(), SERVER_PORT, getPort(roleConfig));
    model.addRoleProperty(getRoleType(), SERVER_SSL_PORT, getSSLPort(roleConfig));

    return model;
  }

}
