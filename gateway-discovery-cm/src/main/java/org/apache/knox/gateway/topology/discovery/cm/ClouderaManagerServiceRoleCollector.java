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
import com.cloudera.api.swagger.model.ApiRoleConfigList;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import java.math.BigDecimal;
import java.util.ArrayList;

public class ClouderaManagerServiceRoleCollector implements ServiceRoleCollector {

    private static final String VIEW_FULL = "full";
    private final RolesResourceApi rolesResourceApi;
    private final String clusterName;
    private final long limit;
    private static final ClouderaManagerServiceDiscoveryMessages log =
            MessagesFactory.get(ClouderaManagerServiceDiscoveryMessages.class);

    public ClouderaManagerServiceRoleCollector(RolesResourceApi rolesResourceApi, String clusterName,
                                               long roleConfigPageSize) {
        this.rolesResourceApi = rolesResourceApi;
        this.clusterName = clusterName;
        this.limit = roleConfigPageSize;
    }

    @Override
    public ApiRoleConfigList getAllServiceRoleConfiguration(String serviceName) throws ApiException {
        long offset = 0;
        ApiRoleConfigList allServiceRoleConfigs = new ApiRoleConfigList();
        allServiceRoleConfigs.setItems(new ArrayList<>());
        ApiRoleConfigList roleConfigList;
        do {
            log.fetchingServiceRoleConfigs(serviceName, clusterName, offset, limit);
            roleConfigList = rolesResourceApi.readRolesConfig(clusterName, serviceName,
                    BigDecimal.valueOf(limit), BigDecimal.valueOf(offset), VIEW_FULL);
            if (roleConfigList != null && roleConfigList.getItems() != null) {
                allServiceRoleConfigs.getItems().addAll(roleConfigList.getItems());
            } else {
                log.receivedNullServiceRoleConfigs(serviceName, clusterName, offset, limit);
            }
            offset += limit;
        } while (configItemSizeMatchesLimit(roleConfigList, limit));
        return allServiceRoleConfigs;
    }

    private boolean configItemSizeMatchesLimit(ApiRoleConfigList roleConfigList, long limit) {
        return roleConfigList != null && roleConfigList.getItems() != null && (roleConfigList.getItems().size() == limit);
    }
}
