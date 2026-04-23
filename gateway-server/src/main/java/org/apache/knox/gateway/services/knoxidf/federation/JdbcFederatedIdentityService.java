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
package org.apache.knox.gateway.services.knoxidf.federation;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.database.DataSourceProvider;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class JdbcFederatedIdentityService implements FederatedIdentityService {
    private static final FederatedIdentityServiceMessages LOG = MessagesFactory.get(FederatedIdentityServiceMessages.class);

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Lock initLock = new ReentrantLock(true);
    private AliasService aliasService; // connection username/pw are stored here
    private FederatedIdentityDatabase federatedIdentityDatabase;

    @Override
    public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
        if (!initialized.get()) {
            initLock.lock();
            try {
                if (aliasService == null) {
                    throw new ServiceLifecycleException("The required AliasService reference has not been set.");
                }
                try {
                    this.federatedIdentityDatabase = new FederatedIdentityDatabase(DataSourceProvider.getDataSource(config, aliasService), config.getDatabaseType());
                    initialized.set(true);
                } catch (Exception e) {
                    throw new ServiceLifecycleException("Error while initiating JDBCTokenStateService: " + e, e);
                }
            } finally {
                initLock.unlock();
            }
        }
    }

    @Override
    public void start() throws ServiceLifecycleException {
    }

    @Override
    public void stop() throws ServiceLifecycleException {
    }

    public void setAliasService(AliasService aliasService) {
        this.aliasService = aliasService;
    }

    protected AliasService getAliasService() {
        return aliasService;
    }

    @Override
    public void addFederatedIdentity(FederatedIdentity identity) {
        try {
            if (findByProviderAndSubject(identity.getProvider(), identity.getExternalIssuer(), identity.getExternalSubject()).isEmpty()) {
                federatedIdentityDatabase.addFederatedIdentity(identity);
            }
        } catch (SQLException e) {
            LOG.errorSavingFederatedIdentityInDatabase(identity.getId(), e.getMessage(), e);
            throw new FederatedIdentityServiceException("An error occurred while saving Federated Identity " + identity.getId() + " in the database", e);
        }
    }

    @Override
    public Optional<FederatedIdentity> findByProviderAndSubject(String provider, String issuer, String subject) {
        try {
            return federatedIdentityDatabase.findByProviderAndSubject(provider, issuer, subject);
        } catch (SQLException e) {
            LOG.errorFetchingFederatedIdentityFromDatabase(provider, subject, issuer, e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<FederatedIdentity> findById(String id) {
        try {
            return federatedIdentityDatabase.findById(id);
        } catch (SQLException e) {
            LOG.errorFetchingFederatedIdentityFromDatabase(id, e.getMessage(), e);
        }
        return Optional.empty();
    }

}
