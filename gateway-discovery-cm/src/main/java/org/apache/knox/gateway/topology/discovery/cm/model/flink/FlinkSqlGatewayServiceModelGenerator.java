/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.topology.discovery.cm.model.flink;

import com.cloudera.api.swagger.client.ApiException;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModel.Type;
import org.apache.knox.gateway.topology.discovery.cm.model.AbstractServiceModelGenerator;

import java.util.Locale;

public class FlinkSqlGatewayServiceModelGenerator extends AbstractServiceModelGenerator {

  static final String SERVICE = "FLINK-SQL-GATEWAY";
  static final String SERVICE_TYPE = "FLINK";
  static final String ROLE_TYPE = "FLINK_SQL_GATEWAY";

  static final String SSL_ENABLED = "ssl_enabled";
  static final String PORT = "sql_gateway_port";

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
  public Type getModelType() {
    return Type.API;
  }

  @Override
  public ServiceModel generateService(ApiService service, ApiServiceConfig serviceConfig, ApiRole role, ApiConfigList roleConfig, ApiServiceConfig coreSettingsConfig) throws ApiException {
    final String hostname = role.getHostRef().getHostname();
    final String port = getRoleConfigValue(roleConfig, PORT);
    final String scheme = "http";

    final ServiceModel model = createServiceModel(String.format(Locale.getDefault(), "%s://%s:%s", scheme, hostname, port));
    model.addRoleProperty(getRoleType(), SSL_ENABLED, Boolean.toString(false));
    model.addRoleProperty(getRoleType(), PORT, port);

    return model;
  }

}
