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
import java.sql.SQLIntegrityConstraintViolationException;
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
                // Double-checked locking: re-test under the lock so a thread that blocked while
                // another was initialising does not re-initialise the database a second time.
                if (!initialized.get()) {
                    if (aliasService == null) {
                        throw new ServiceLifecycleException("The required AliasService reference has not been set.");
                    }
                    try {
                        this.federatedIdentityDatabase = new FederatedIdentityDatabase(DataSourceProvider.getDataSource(config, aliasService), config.getDatabaseType());
                        initialized.set(true);
                    } catch (Exception e) {
                        throw new ServiceLifecycleException("Error while initiating JdbcFederatedIdentityService: " + e, e);
                    }
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
        // Insert-and-catch rather than check-then-insert: the UNIQUE(provider, external_issuer,
        // external_subject) index is the atomic arbiter, so two concurrent requests for the same
        // external identity cannot both insert. A unique-constraint violation means the row already
        // exists, which is exactly the desired end state, so it is treated as benign rather than
        // surfaced as an error (closing the prior TOCTOU race between the pre-check and the insert).
        try {
            federatedIdentityDatabase.addFederatedIdentity(identity);
        } catch (SQLException e) {
            if (isUniqueConstraintViolation(e)) {
                LOG.federatedIdentityAlreadyExists(identity.getProvider(), identity.getExternalIssuer(), identity.getExternalSubject());
                return;
            }
            LOG.errorSavingFederatedIdentityInDatabase(identity.getId(), e.getMessage(), e);
            throw new FederatedIdentityServiceException("An error occurred while saving Federated Identity " + identity.getId() + " in the database", e);
        }
    }

    /**
     * Recognises a unique/primary-key constraint violation across dialects: either a
     * {@link SQLIntegrityConstraintViolationException} or any {@link SQLException} in the cause
     * chain whose SQLState is in the {@code 23} (integrity constraint violation) class.
     */
    private static boolean isUniqueConstraintViolation(SQLException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
            if (t instanceof SQLException) {
                final String sqlState = ((SQLException) t).getSQLState();
                if (sqlState != null && sqlState.startsWith("23")) {
                    return true;
                }
            }
        }
        return false;
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
