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

import com.cloudera.api.swagger.ServicesResourceApi;
import com.cloudera.api.swagger.client.ApiException;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;
import org.apache.knox.gateway.topology.discovery.cm.model.AbstractServiceModelGenerator;

import java.util.Locale;

public class YarnUIServiceModelGenerator extends AbstractServiceModelGenerator {
  private static final String SERVICE = "YARNUI";
  private static final String SERVICE_TYPE = "YARN";
  private static final String ROLE_TYPE = "RESOURCEMANAGER";

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
  public boolean handles(ApiService service, ApiServiceConfig serviceConfig, ApiRole role, ApiConfigList roleConfig) {
    return getServiceType().equals(service.getType()) && getRoleType().equals(role.getType());
  }

  @Override
  public ServiceModel generateService(ApiService       service,
                                      ApiServiceConfig serviceConfig,
                                      ApiRole          role,
                                      ApiConfigList    roleConfig) throws ApiException {
    return createServiceModel(generateURL(service, serviceConfig, role, roleConfig));
  }

  protected String generateURL(ApiService       service,
                               ApiServiceConfig serviceConfig,
                               ApiRole          role,
                               ApiConfigList    roleConfig) throws ApiException {

    String hostname = role.getHostRef().getHostname();
    String scheme;
    String port;

    if(isSSLEnabled(service, serviceConfig)) {
      scheme = "https";
      port = getRoleConfigValue(roleConfig, "resourcemanager_webserver_https_port");
    } else {
      scheme = "http";
      port = getRoleConfigValue(roleConfig, "resourcemanager_webserver_port");
    }
    return String.format(Locale.getDefault(), "%s://%s:%s", scheme, hostname, port);
  }

  private boolean isSSLEnabled(ApiService service, ApiServiceConfig serviceConfig)
      throws ApiException {
    ServicesResourceApi servicesResourceApi = new ServicesResourceApi(getClient());
    String clusterName = service.getClusterRef().getClusterName();
    String hdfsService = getServiceConfigValue(serviceConfig, "hdfs_service");
    ApiServiceConfig hdfsServiceConfig = servicesResourceApi.readServiceConfig(clusterName, hdfsService, "full");
    return Boolean.parseBoolean(getServiceConfigValue(hdfsServiceConfig, "hdfs_hadoop_ssl_enabled"));
  }

}
