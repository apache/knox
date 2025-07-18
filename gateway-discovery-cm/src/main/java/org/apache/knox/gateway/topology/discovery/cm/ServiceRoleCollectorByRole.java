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
package org.apache.knox.gateway.topology.discovery.cm;

import com.cloudera.api.swagger.RolesResourceApi;
import com.cloudera.api.swagger.client.ApiException;
import com.cloudera.api.swagger.model.ApiConfigList;
import com.cloudera.api.swagger.model.ApiRole;
import com.cloudera.api.swagger.model.ApiRoleConfig;
import com.cloudera.api.swagger.model.ApiRoleConfigList;
import com.cloudera.api.swagger.model.ApiRoleList;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import java.util.ArrayList;

public class ServiceRoleCollectorByRole implements ServiceRoleCollector {

    private static final String VIEW_FULL = "full";
    private final RolesResourceApi rolesResourceApi;
    private final TypeNameFilter roleTypeNameFilter;
    private static final ClouderaManagerServiceDiscoveryMessages log =
            MessagesFactory.get(ClouderaManagerServiceDiscoveryMessages.class);

    public ServiceRoleCollectorByRole(RolesResourceApi rolesResourceApi, TypeNameFilter roleTypeNameFilter) {
        this.rolesResourceApi = rolesResourceApi;
        this.roleTypeNameFilter = roleTypeNameFilter;
    }

    @Override
    public ApiRoleConfigList getAllServiceRoleConfigurations(String clusterName, String serviceName) throws ApiException {
        ApiRoleConfigList allServiceRoleConfigs = new ApiRoleConfigList();
        allServiceRoleConfigs.setItems(new ArrayList<>());

        ApiRoleList roles = rolesResourceApi.readRoles(clusterName, serviceName, "", VIEW_FULL);
        if (roles == null || roles.getItems() == null) {
            log.receivedNullServiceRoleList(serviceName, clusterName);
            return allServiceRoleConfigs;
        }
        for (ApiRole role : roles.getItems()) {
            String roleName = role.getName();
            if (roleTypeNameFilter.isExcluded(role.getType())) {
                log.skipRoleDiscovery(role.getName(), role.getType());
                continue;
            }
            log.fetchingServiceRoleConfigs(serviceName, clusterName, roleName);
            ApiConfigList config = rolesResourceApi.readRoleConfig(clusterName, role.getName(), serviceName, VIEW_FULL);
            if (config != null && config.getItems() != null) {
                ApiRoleConfig roleConfig = new ApiRoleConfig().roleType(role.getType())
                        .name(role.getName())
                        .hostRef(role.getHostRef())
                        .config(config);
                allServiceRoleConfigs.getItems().add(roleConfig);
            } else {
                log.receivedNullServiceRoleConfigs(serviceName, clusterName, roleName);
            }
        }
        return allServiceRoleConfigs;
    }

}
