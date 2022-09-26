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
import org.apache.knox.gateway.topology.discovery.cm.ServiceModelGeneratorHandleResponse;
import org.apache.knox.gateway.topology.discovery.cm.model.AbstractServiceModelGenerator;

import java.util.Locale;

public class HiveServiceModelGenerator extends AbstractServiceModelGenerator {

  public static final String SERVICE      = "HIVE";
  public static final String SERVICE_TYPE = "HIVE";
  public static final String ROLE_TYPE    = "HIVESERVER2";

  static final String TRANSPORT_MODE_HTTP = "http";
  static final String TRANSPORT_MODE_ALL  = "all";

  static final String SAFETY_VALVE   = "hive_hs2_config_safety_valve";
  public static final String SSL_ENABLED = "hiveserver2_enable_ssl";
  static final String TRANSPORT_MODE = "hive.server2.transport.mode";
  static final String HTTP_PORT      = "hive.server2.thrift.http.port";
  static final String HTTP_PATH      = "hive.server2.thrift.http.path";

  static final String DEFAULT_HTTP_PATH = "cliservice";

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
    final ServiceModelGeneratorHandleResponse response = super.handles(service, serviceConfig, role, roleConfig);
    if (response.handled()) {
      checkHiveServer2HTTPMode(roleConfig, response);
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

    String port     = getHttpPort(roleConfig);
    String httpPath = getHttpPath(roleConfig);
    if (httpPath == null) {
      httpPath = DEFAULT_HTTP_PATH;
    }

    ServiceModel model =
        createServiceModel(String.format(Locale.getDefault(), "%s://%s:%s/%s", scheme, hostname, port, httpPath));
    model.addServiceProperty(SSL_ENABLED, Boolean.toString(sslEnabled));
    model.addRoleProperty(getRoleType(), SAFETY_VALVE, getRoleConfigValue(roleConfig, SAFETY_VALVE));

    return model;
  }

  private String getHS2SafetyValveValue(final ApiConfigList roleConfig, final String name) {
    String value = null;
    String hs2SafetyValve = getRoleConfigValue(roleConfig, SAFETY_VALVE);
    if (hs2SafetyValve != null && !hs2SafetyValve.isEmpty()) {
      value = getSafetyValveValue(hs2SafetyValve, name);
    }
    return value;
  }

  protected String getHttpPort(ApiConfigList roleConfig) {
    return getHS2SafetyValveValue(roleConfig, HTTP_PORT);
  }

  protected String getHttpPath(ApiConfigList roleConfig) {
    return getHS2SafetyValveValue(roleConfig, HTTP_PATH);
  }

  protected void checkHiveServer2HTTPMode(ApiConfigList roleConfig, ServiceModelGeneratorHandleResponse response) {
    final String hiveServer2SafetyValve = getRoleConfigValue(roleConfig, SAFETY_VALVE);
    final String hiveServer2TransportMode =
            hiveServer2SafetyValve == null ? null : getSafetyValveValue(hiveServer2SafetyValve, TRANSPORT_MODE);
    validateTransportMode(TRANSPORT_MODE, hiveServer2TransportMode, response);
  }

  protected void validateTransportMode(final String configPropName,
                                       final String transportMode,
                                       final ServiceModelGeneratorHandleResponse response) {
    if (transportMode == null ) {
      response.addConfigurationIssue("Missing configuration: " + configPropName);
    } else if (!TRANSPORT_MODE_HTTP.equalsIgnoreCase(transportMode) && !TRANSPORT_MODE_ALL.equalsIgnoreCase(transportMode)) {
      response.addConfigurationIssue("Invalid configuration: " + configPropName +
              ". Expected=" + TRANSPORT_MODE_HTTP + " or " + TRANSPORT_MODE_ALL +"; Found=" + transportMode);
    }
  }
}
