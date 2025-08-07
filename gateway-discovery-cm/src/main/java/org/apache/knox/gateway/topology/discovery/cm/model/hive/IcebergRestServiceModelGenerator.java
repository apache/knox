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

public class IcebergRestServiceModelGenerator extends AbstractServiceModelGenerator {

  public static final String SERVICE = "ICEBERG-REST";
  public static final String SERVICE_TYPE = "HIVE";
  public static final String ROLE_TYPE = "HIVEMETASTORE";

  static final String HTTP_PORT = "hive_metastore_catalog_servlet_port";
  static final String HTTP_PATH = "hive_metastore_catalog_servlet_path";
  static final String REST_CATALOG_ENABLED = "hive_rest_catalog_enabled";

  static final String DEFAULT_HTTP_PATH = "icecli";

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
      validateCatalogEnabled(serviceConfig, response);
    }
    return response;
  }

  @Override
  public ServiceModel generateService(ApiService service,
                                      ApiServiceConfig serviceConfig,
                                      ApiRole role,
                                      ApiConfigList roleConfig, ApiServiceConfig coreSettingsConfig) throws ApiException {
    String hostname = role.getHostRef().getHostname();
    String scheme = "http";
    String port = getHttpPort(serviceConfig);
    String httpPath = getHttpPath(serviceConfig);

    if (httpPath == null) {
      httpPath = DEFAULT_HTTP_PATH;
    }

    ServiceModel model =
        createServiceModel(String.format(Locale.getDefault(), "%s://%s:%s/%s", scheme, hostname, port, httpPath));
    model.addServiceProperty(HTTP_PORT, getHttpPort(serviceConfig));
    model.addServiceProperty(HTTP_PATH, getHttpPath(serviceConfig));
    model.addServiceProperty(REST_CATALOG_ENABLED, getRestCatalogEnabled(serviceConfig));

    return model;
  }

  protected String getHttpPort(ApiServiceConfig serviceConfig) {
    return getServiceConfigValue(serviceConfig, HTTP_PORT);
  }

  protected String getHttpPath(ApiServiceConfig serviceConfig) {
    return getServiceConfigValue(serviceConfig, HTTP_PATH);
  }

  protected String getRestCatalogEnabled(ApiServiceConfig serviceConfig) {
    return getServiceConfigValue(serviceConfig, REST_CATALOG_ENABLED);
  }

  protected void validateCatalogEnabled(ApiServiceConfig serviceConfig, ServiceModelGeneratorHandleResponse response) {
    String catalogEnabledValue = getRestCatalogEnabled(serviceConfig);
    boolean isCatalogEnabled = Boolean.parseBoolean(catalogEnabledValue);
    if (!isCatalogEnabled) {
      response.addConfigurationIssue("Invalid configuration: " + REST_CATALOG_ENABLED +
          ". Expected=true; Found=" + catalogEnabledValue);
    }
  }

}
