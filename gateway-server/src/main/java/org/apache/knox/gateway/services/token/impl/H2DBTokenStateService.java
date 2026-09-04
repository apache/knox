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
package org.apache.knox.gateway.services.token.impl;

import java.util.Map;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.database.EmbeddedH2Database;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.MasterService;

/**
 * A self-provisioning, embedded-H2 backed token-state service. This is the zero-config OOTB default
 * that replaces the retired {@code DerbyDBTokenStateService} (KNOX-3401): it boots the shared
 * embedded H2 database under {@code ${securityDir}/h2db} and then delegates all persistence to
 * {@link JDBCTokenStateService}.
 */
public class H2DBTokenStateService extends JDBCTokenStateService {

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
      throw new ServiceLifecycleException("Error while initiating H2DBTokenStateService: " + e, e);
    }
  }

  @Override
  public void stop() throws ServiceLifecycleException {
    if (embeddedH2Database != null) {
      embeddedH2Database.stop();
    }
  }

}
