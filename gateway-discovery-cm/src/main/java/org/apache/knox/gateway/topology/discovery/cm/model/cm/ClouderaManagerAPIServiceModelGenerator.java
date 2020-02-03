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
package org.apache.knox.gateway.topology.discovery.cm.model.cm;

import com.cloudera.api.swagger.client.ApiException;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import org.apache.knox.gateway.topology.discovery.cm.ClouderaManagerServiceDiscovery;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;
import org.apache.knox.gateway.topology.discovery.cm.model.AbstractServiceModelGenerator;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public class ClouderaManagerAPIServiceModelGenerator
    extends AbstractServiceModelGenerator {

  public static final String SERVICE = "CM-API";
  public static final String SERVICE_TYPE = ClouderaManagerServiceDiscovery.CM_SERVICE_TYPE;
  public static final String ROLE_TYPE = ClouderaManagerServiceDiscovery.CM_ROLE_TYPE;

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

  /**
   * This method functions differently than others. This method inquires the
   * discovery client and uses the CM url used by the driver (which was
   * populated by the descriptor).
   *
   * @param service
   * @param serviceConfig
   * @param role
   * @param roleConfig
   * @return
   * @throws ApiException
   */
  @Override
  public ServiceModel generateService(ApiService service,
      ApiServiceConfig serviceConfig, ApiRole role, ApiConfigList roleConfig)
      throws ApiException {

    final String basePath = getClient().getBasePath();
    URI uri;
    try {
      uri = new URI(basePath);
    } catch (URISyntaxException e) {
      throw new ApiException(e);
    }

    final String serviceURL = getModelType() == ServiceModel.Type.API ?
        String.format(Locale.getDefault(), "%s://%s:%s/api", uri.getScheme(),
            uri.getHost(), uri.getPort()) :
        String.format(Locale.getDefault(), "%s://%s:%s", uri.getScheme(),
            uri.getHost(), uri.getPort());

    return new ServiceModel(getModelType(), getService(), getServiceType(),
        getRoleType(), serviceURL);
  }
}
