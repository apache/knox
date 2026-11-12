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
package org.apache.knox.gateway.topology.discovery.cm.model.opensearch;

import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiService;
import com.cloudera.api.swagger.model.ApiServiceConfig;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModel;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModelGeneratorHandleResponse;

/**
 * CM service discovery generator for the OpenSearch Dashboards web UI.
 * <p>
 * This generator maps the CM service type {@code OPENSEARCH} / role type
 * {@code OPENSEARCH_DASHBOARD} to the Knox service role
 * {@code OPENSEARCH-DASHBOARDS}, building the backend URL from the
 * {@code dashboards_server_port} CM configuration key.
 * <p>
 * The resulting topology service entry is consumed by the
 * {@code opensearch-dashboards} Knox service definition, which uses
 * {@code ConfigurableDispatch} to inject the Knox-authenticated principal
 * as the {@code x-proxy-user} header for OpenSearch Dashboards proxy auth.
 */
public class OpenSearchDashboardsServiceModelGenerator extends AbstractOpenSearchServiceModelGenerator {

  public static final String OPENSEARCH_DASHBOARD_ROLE_TYPE = "OPENSEARCH_DASHBOARD";
  public static final String OPENSEARCH_DASHBOARDS_SERVICE_NAME = "OPENSEARCH-DASHBOARDS";

  /** Port configuration key in the CM OPENSEARCH_DASHBOARD role. */
  static final String DASHBOARDS_PORT_CONFIG = "dashboards_server_port";

  static final String ENABLE_SSO_CONFIG = "enable_dashboards_sso";

  @Override
  public ServiceModelGeneratorHandleResponse handles(ApiService service, ApiServiceConfig serviceConfig,
                                                     ApiRole role, ApiConfigList roleConfig) {
    final ServiceModelGeneratorHandleResponse response = super.handles(service, serviceConfig, role, roleConfig);
    if (response.handled()) {
      final String ssoEnabled = getRoleConfigValue(roleConfig, ENABLE_SSO_CONFIG);
      if (ssoEnabled == null) {
        response.addConfigurationIssue("Missing configuration: " + ENABLE_SSO_CONFIG);
      } else if (!Boolean.parseBoolean(ssoEnabled)) {
        response.addConfigurationIssue("Invalid configuration: " + ENABLE_SSO_CONFIG + ". Expected=true; Found=" + ssoEnabled);
      }
    }
    return response;
  }

  @Override
  public String getService() {
    // Override to return the Knox topology service name, which must match the
    // role= attribute in the opensearch-dashboards service.xml definition.
    // This is intentionally different from the CM service type (OPENSEARCH).
    return OPENSEARCH_DASHBOARDS_SERVICE_NAME;
  }

  @Override
  public String getRoleType() {
    return OPENSEARCH_DASHBOARD_ROLE_TYPE;
  }

  @Override
  public ServiceModel.Type getModelType() {
    return ServiceModel.Type.UI;
  }

  @Override
  protected String getPortConfigName() {
    return DASHBOARDS_PORT_CONFIG;
  }
}
