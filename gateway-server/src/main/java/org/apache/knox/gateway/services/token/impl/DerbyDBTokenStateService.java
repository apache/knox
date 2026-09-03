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
import org.apache.knox.gateway.database.EmbeddedDerbyDatabase;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.MasterService;

public class DerbyDBTokenStateService extends JDBCTokenStateService {

  private EmbeddedDerbyDatabase embeddedDerbyDatabase;
  private MasterService masterService;

  public void setMasterService(MasterService masterService) {
    this.masterService = masterService;
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
    try {
      embeddedDerbyDatabase = new EmbeddedDerbyDatabase(config);
      embeddedDerbyDatabase.start(config, getAliasService(), masterService);
      super.init(config, options);
    } catch (Exception e) {
      throw new ServiceLifecycleException("Error while initiating DerbyDBTokenStateService: " + e, e);
    }
  }

  @Override
  public void stop() throws ServiceLifecycleException {
    if (embeddedDerbyDatabase != null) {
      embeddedDerbyDatabase.stop();
    }
  }

}
