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
package org.apache.knox.gateway.topology.discovery.cm.model.ozone;

import com.cloudera.api.swagger.client.ApiException;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;
import org.apache.knox.gateway.topology.discovery.cm.model.AbstractServiceModelGenerator;

import java.util.Locale;

public class ReconServiceModelGenerator extends AbstractServiceModelGenerator {

  public static final String SERVICE      = "OZONE-RECON";
  public static final String SERVICE_TYPE = "OZONE";
  public static final String ROLE_TYPE    = "OZONE_RECON";


  static final String SSL_ENABLED = "ssl_enabled";
  static final String HTTP_PORT   = "ozone.recon.http-port";

  static final String HTTPS_PORT  = "ozone.recon.https-port";
  @Override
  public String getService() {
    return SERVICE;
  }

  @Override
  public String getServiceType() {
    return SERVICE_TYPE;
  }

  @Override
  public String getRoleType() {
    return ROLE_TYPE;
  }

  @Override
  public ServiceModel.Type getModelType() {
    return ServiceModel.Type.UI;
  }

  @Override
  public ServiceModel generateService(ApiService       service,
                                      ApiServiceConfig serviceConfig,
                                      ApiRole          role,
                                      ApiConfigList    roleConfig) throws ApiException {
    String hostname = role.getHostRef().getHostname();

    boolean sslEnabled = Boolean.parseBoolean(getRoleConfigValue(roleConfig, SSL_ENABLED));
    String scheme = sslEnabled ? "https" : "http";

    // Role config properties
    String httpPort = getRoleConfigValue(roleConfig, HTTP_PORT);
    String httpsPort = getRoleConfigValue(roleConfig, HTTPS_PORT);
    ServiceModel model = createServiceModel(String.format(Locale.getDefault(), "%s://%s:%s",
            scheme, hostname, sslEnabled ? httpsPort : httpPort));
    model.addRoleProperty(getRoleType(),SSL_ENABLED, getRoleConfigValue(roleConfig, SSL_ENABLED));
    model.addRoleProperty(getRoleType(),HTTP_PORT, httpPort);
    model.addRoleProperty(getRoleType(),HTTPS_PORT,httpsPort);
    return model;
  }
}
