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

import static org.apache.knox.gateway.config.impl.GatewayConfigImpl.GATEWAY_DATABASE_NAME;
import static org.apache.knox.gateway.config.impl.GatewayConfigImpl.GATEWAY_DATABASE_TYPE;
import static org.apache.knox.gateway.services.security.AliasService.NO_CLUSTER_NAME;
import static org.apache.knox.gateway.util.JDBCUtils.DATABASE_PASSWORD_ALIAS_NAME;
import static org.apache.knox.gateway.util.JDBCUtils.DATABASE_USER_ALIAS_NAME;
import static org.apache.knox.gateway.util.JDBCUtils.DERBY_DB_TYPE;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.hadoop.conf.Configuration;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.shell.jdbc.derby.DerbyDatabase;
import org.apache.knox.gateway.util.FileUtils;

public class DerbyDBTokenStateService extends JDBCTokenStateService {

  public static final String DEFAULT_TOKEN_DB_USER_NAME = "knox";
  public static final String DB_NAME = "tokens";

  private DerbyDatabase derbyDatabase;
  private Path derbyDatabaseFolder;
  private MasterService masterService;

  public void setMasterService(MasterService masterService) {
    this.masterService = masterService;
  }

  @Override
  public void init(GatewayConfig config, Map<String, String> options) throws ServiceLifecycleException {
    try {
      derbyDatabaseFolder = Paths.get(config.getGatewaySecurityDir(), DB_NAME);
      startDerby();
      ((Configuration) config).set(GATEWAY_DATABASE_TYPE, DERBY_DB_TYPE);
      ((Configuration) config).set(GATEWAY_DATABASE_NAME, derbyDatabaseFolder.toString());
      getAliasService().addAliasForCluster(NO_CLUSTER_NAME, DATABASE_USER_ALIAS_NAME, getDatabaseUserName());
      getAliasService().addAliasForCluster(NO_CLUSTER_NAME, DATABASE_PASSWORD_ALIAS_NAME, getDatabasePassword());
      super.init(config, options);

      // we need the "x" permission too to be able to browse that folder (600 is not enough)
      if (Files.exists(derbyDatabaseFolder)) {
        FileUtils.chmod("700", derbyDatabaseFolder.toFile());
      }
    } catch (Exception e) {
      throw new ServiceLifecycleException("Error while initiating DerbyDBTokenStateService: " + e, e);
    }
  }

  private void startDerby() throws Exception {
    derbyDatabase = new DerbyDatabase(derbyDatabaseFolder.toString());
    derbyDatabase.create();
    TimeUnit.SECONDS.sleep(1); // give a bit of time for the server to start
  }

  private String getDatabasePassword() throws Exception {
    final char[] dbPasswordAliasValue = getAliasService().getPasswordFromAliasForGateway(DATABASE_PASSWORD_ALIAS_NAME);
    return dbPasswordAliasValue != null ? new String(dbPasswordAliasValue) : new String(masterService.getMasterSecret());
  }

  private String getDatabaseUserName() throws Exception {
    final char[] dbUserAliasValue = getAliasService().getPasswordFromAliasForGateway(DATABASE_USER_ALIAS_NAME);
    return dbUserAliasValue != null ? new String(dbUserAliasValue) : DEFAULT_TOKEN_DB_USER_NAME;
  }

  @Override
  public void stop() throws ServiceLifecycleException {
    try {
      if (derbyDatabase != null) {
        derbyDatabase.shutdown();
      }
    } catch (Exception e) {
      throw new ServiceLifecycleException("Error while shutting down Derby Database", e);
    }
  }

}
