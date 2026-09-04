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
package org.apache.knox.gateway.services.knoxidf.trustedoidcissuer;

import java.util.Map;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.database.EmbeddedH2Database;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.services.token.impl.H2DBTokenStateService;

/**
 * A self-provisioning, embedded-H2 backed {@link TrustedOidcIssuerService}. This is the
 * auto-enabled default when KnoxIDF is deployed without an operator-configured external database,
 * mirroring how {@link H2DBTokenStateService} is the default token-state service and
 * {@code H2DBFederatedIdentityService} is the default federated-identity service. It replaces the
 * retired embedded-Derby backend (KNOX-3401).
 * <p>
 * It reuses the single embedded H2 database that the token-state service already provisions under
 * {@code ${securityDir}/h2db} (connecting to an already-open embedded database simply attaches to
 * it), sets the shared {@link GatewayConfig} to point at it, ensures the connection user/password
 * aliases exist, and then delegates all persistence to {@link JdbcTrustedOidcIssuerService} (which
 * builds the {@link TrustedOidcIssuerDatabase} and self-creates its table).
 */
public class H2DBTrustedOidcIssuerService extends JdbcTrustedOidcIssuerService {

  private EmbeddedH2Database embeddedH2Database;
  private MasterService masterService;

  public void setMasterService(MasterService masterService) {
    this.masterService = masterService;
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
    try {
      embeddedH2Database = new EmbeddedH2Database(config);
      embeddedH2Database.start(config, getAliasService(), masterService);
      super.init(config, options);
    } catch (Exception e) {
      throw new ServiceLifecycleException("Error while initiating H2DBTrustedOidcIssuerService: " + e, e);
    }
  }

  @Override
  public void stop() throws ServiceLifecycleException {
    if (embeddedH2Database != null) {
      embeddedH2Database.stop();
    }
  }
}
