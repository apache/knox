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
package org.apache.knox.gateway.topology.discovery.cm.model.nifi;

import com.cloudera.api.swagger.client.ApiException;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;
import org.apache.knox.gateway.topology.discovery.cm.model.AbstractServiceModelGenerator;

import java.util.Locale;

public class NifiServiceModelGenerator extends AbstractServiceModelGenerator {

  public static final String SERVICE = "NIFI";
  public static final String SERVICE_TYPE = "NIFI";
  public static final String ROLE_TYPE = "NIFI_NODE";

  /**
   * @return The name of the Knox service for which the implementation will
   * generate a model.
   */
  @Override
  public String getService() {
    return SERVICE;
  }

  /**
   * @return The Cloudera Manager configuration service type.
   */
  @Override
  public String getServiceType() {
    return SERVICE_TYPE;
  }

  /**
   * @return The Cloudera Manager configuration role type.
   */
  @Override
  public String getRoleType() {
    return ROLE_TYPE;
  }

  @Override
  public ServiceModel.Type getModelType() {
    return ServiceModel.Type.UI;
  }

  @Override
  public ServiceModel generateService(ApiService service,
      ApiServiceConfig serviceConfig, ApiRole role, ApiConfigList roleConfig)
      throws ApiException {
    String hostname = role.getHostRef().getHostname();
    String scheme;
    String port;
    boolean sslEnabled = Boolean.parseBoolean(getRoleConfigValue(roleConfig, "ssl_enabled"));
    if(sslEnabled) {
      scheme = "https";
      port = getRoleConfigValue(roleConfig, "nifi.web.https.port");
    } else {
      scheme = "http";
      port = getRoleConfigValue(roleConfig, "nifi.web.http.port");
    }
    return new ServiceModel(getModelType(),
        getService(),
        getServiceType(),
        getRoleType(),
        String.format(Locale.getDefault(), "%s://%s:%s", scheme, hostname, port));
  }
}
