/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.monitor;

import java.io.File;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.factory.AbstractServiceFactory;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;
import org.apache.knox.gateway.topology.monitor.db.DbRemoteConfigurationMonitor;
import org.apache.knox.gateway.topology.monitor.db.LocalDirectory;
import org.apache.knox.gateway.topology.monitor.db.RemoteConfigDatabase;
import org.apache.knox.gateway.util.JDBCUtils;


public class DefaultConfigurationMonitorProvider extends AbstractServiceFactory {
    @Override
    protected RemoteConfigurationMonitor createService(GatewayServices gatewayServices,
                                    ServiceType serviceType,
                                    GatewayConfig gatewayConfig,
                                    Map<String, String> options,
                                    String implementation) throws ServiceLifecycleException {
        RemoteConfigurationMonitor service = null;
        if (matchesImplementation(implementation, ZkRemoteConfigurationMonitor.class)) {
            service = new ZkRemoteConfigurationMonitor(gatewayConfig, gatewayServices.getService(ServiceType.REMOTE_REGISTRY_CLIENT_SERVICE));
        } else if (matchesImplementation(implementation, DbRemoteConfigurationMonitor.class)) {
            service = createDbBasedMonitor(gatewayConfig, gatewayServices.getService(ServiceType.ALIAS_SERVICE));
        }
        return service;
    }

    private DbRemoteConfigurationMonitor createDbBasedMonitor(GatewayConfig config, AliasService aliasService) throws ServiceLifecycleException {
        try {
            RemoteConfigDatabase db = new RemoteConfigDatabase(JDBCUtils.getDataSource(config, aliasService));
            LocalDirectory descriptorDir = new LocalDirectory(new File(config.getGatewayDescriptorsDir()));
            LocalDirectory providerDir = new LocalDirectory(new File(config.getGatewayProvidersConfigDir()));
            return new DbRemoteConfigurationMonitor(
                    db, providerDir, descriptorDir, config.getDbRemoteConfigMonitorPollingInterval());
        } catch (SQLException | AliasServiceException e) {
            throw new ServiceLifecycleException("Cannot create DbRemoteConfigurationMonitor", e);
        }
    }

    @Override
    protected ServiceType getServiceType() {
        return ServiceType.REMOTE_CONFIGURATION_MONITOR;
    }

    @Override
    protected Collection<String> getKnownImplementations() {
        return Arrays.asList(ZkRemoteConfigurationMonitor.class.getName(), DbRemoteConfigurationMonitor.class.getName());
    }
}
