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

import com.cloudera.api.swagger.model.ApiRoleConfigList;
import org.apache.knox.gateway.topology.discovery.cm.ServiceModelGenerator;

public class OpenSearchApiMasterServiceModelGenerator extends AbstractOpenSearchApiServiceModelGenerator {
  static final String OPENSEARCH_MASTER_ROLE_TYPE = "OPENSEARCH_MASTER";

  @Override
  public String getRoleType() {
      return OPENSEARCH_MASTER_ROLE_TYPE;
  }

  /**
   * If here is an OPENSEARCH_COORDINATOR role for this service o not generate service model for
   * OPENSEARCH_MASTER role because OPENSEARCH_COORDINATOR is the preferred REST API target.
   * @param serviceModelGenerator the actual model generator
   * @param roles all the roles of the service
   * @return <code>true</code> if <code>serviceModelGenerator</code> is for OpenSearch Master and there are Coordinator roles in the cluster.
   */
  public static boolean shouldSkipGeneratorWhenOpenSearchMaster(ServiceModelGenerator serviceModelGenerator, ApiRoleConfigList roles) {
    if (!AbstractOpenSearchServiceModelGenerator.SERVICE_TYPE.equals(serviceModelGenerator.getServiceType())
            || !OpenSearchApiMasterServiceModelGenerator.OPENSEARCH_MASTER_ROLE_TYPE.equals(serviceModelGenerator.getRoleType())) {
      return false;
    }
    return roles.getItems().stream().anyMatch(r -> r.getRoleType().equals(OpenSearchApiCoordinatorServiceModelGenerator.OPENSEARCH_COORDINATOR_ROLE_TYPE));
  }
}
