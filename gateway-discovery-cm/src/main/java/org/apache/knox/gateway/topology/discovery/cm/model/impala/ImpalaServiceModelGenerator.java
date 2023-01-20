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
package org.apache.knox.gateway.topology.discovery.cm.model.impala;

import com.cloudera.api.swagger.client.ApiException;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModelGeneratorHandleResponse;
import org.apache.knox.gateway.topology.discovery.cm.model.AbstractServiceModelGenerator;

import java.util.Locale;

public class ImpalaServiceModelGenerator extends AbstractServiceModelGenerator {

  public static final String SERVICE      = "IMPALA";
  public static final String SERVICE_TYPE = "IMPALA";
  public static final String ROLE_TYPE    = "IMPALAD";

  public static final String SPECIALIZATION        = "impalad_specialization";
  public static final String NO_SPEC               = "NO_SPECIALIZATION";
  public static final String COORDINATOR_ONLY_SPEC = "COORDINATOR_ONLY";
  public static final String EXECUTOR_ONLY_SPEC    = "EXECUTOR_ONLY";

  static final String SSL_ENABLED = "client_services_ssl_enabled";
  static final String HTTP_PORT   = "hs2_http_port";

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
    return ServiceModel.Type.API;
  }

  @Override
  public ServiceModelGeneratorHandleResponse handles(ApiService service, ApiServiceConfig serviceConfig, ApiRole role, ApiConfigList roleConfig) {
    boolean result = false;
    String configurationIssue = null;

    if (super.handles(service, serviceConfig, role, roleConfig).handled()) {
      // If the Imapala daemon's specialization is "coordinator only" or "no specialization", then we'll handle it
      final String specialization = getRoleConfigValue(roleConfig, SPECIALIZATION);
      if (specialization == null) {
        configurationIssue = "Missing configuration: " + SPECIALIZATION;
      } else {
        result = NO_SPEC.equals(specialization) || COORDINATOR_ONLY_SPEC.equals(specialization);
      }
    }

    final ServiceModelGeneratorHandleResponse response = new ServiceModelGeneratorHandleResponse(result);
    if (configurationIssue != null) {
      response.addConfigurationIssue(configurationIssue);
    }

    return response;
  }

  @Override
  public ServiceModel generateService(ApiService       service,
                                      ApiServiceConfig serviceConfig,
                                      ApiRole          role,
                                      ApiConfigList    roleConfig) throws ApiException {
    String hostname = role.getHostRef().getHostname();

    boolean sslEnabled = Boolean.parseBoolean(getServiceConfigValue(serviceConfig, SSL_ENABLED));
    String scheme = sslEnabled ? "https" : "http";

    // Role config properties
    String port = getRoleConfigValue(roleConfig, HTTP_PORT);

    ServiceModel model = createServiceModel(String.format(Locale.getDefault(), "%s://%s:%s/", scheme, hostname, port));
    model.addServiceProperty(SSL_ENABLED, getServiceConfigValue(serviceConfig, SSL_ENABLED));
    model.addRoleProperty(getRoleType(), HTTP_PORT, port);

    return model;
  }
}
