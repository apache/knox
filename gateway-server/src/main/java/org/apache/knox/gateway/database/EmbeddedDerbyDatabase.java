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
package org.apache.knox.gateway.database;

import static org.apache.knox.gateway.config.impl.GatewayConfigImpl.GATEWAY_DATABASE_NAME;
import static org.apache.knox.gateway.config.impl.GatewayConfigImpl.GATEWAY_DATABASE_TYPE;
import static org.apache.knox.gateway.database.AbstractDataSourceFactory.DATABASE_PASSWORD_ALIAS_NAME;
import static org.apache.knox.gateway.database.AbstractDataSourceFactory.DATABASE_USER_ALIAS_NAME;
import static org.apache.knox.gateway.database.DatabaseType.DERBY;
import static org.apache.knox.gateway.services.security.AliasService.NO_CLUSTER_NAME;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.apache.hadoop.conf.Configuration;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.ServiceLifecycleException;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.shell.jdbc.derby.DerbyDatabase;
import org.apache.knox.gateway.util.FileUtils;

/**
 * Manages the lifecycle of the single embedded Apache Derby database that the KnoxIDF
 * self-provisioning services share under {@code ${securityDir}/tokens}.
 * <p>
 * Extracted so that {@code DerbyDBTokenStateService} and the KnoxIDF {@code DerbyDB*Service}s
 * (federated identity, trusted OIDC issuer, delegation policy) do not each duplicate the identical
 * boot/shutdown logic. The {@code ;create=true} JDBC URL is idempotent, so several services
 * starting their own instance simply connect to the already-booted database.
 */
public final class EmbeddedDerbyDatabase {

  /** Folder name (under the gateway security dir) of the shared embedded Derby database. */
  public static final String DB_NAME = "tokens";

  private final Path folder;
  private DerbyDatabase derbyDatabase;

  public EmbeddedDerbyDatabase(GatewayConfig config) {
    this.folder = Paths.get(config.getGatewaySecurityDir(), DB_NAME);
  }

  /**
   * Boots the embedded Derby database and points the shared {@link GatewayConfig} at it: starts the
   * network server, sets the database type/name, ensures the connection user/password aliases exist
   * and tightens the folder permissions. Callers delegate the actual persistence to their JDBC
   * service by invoking {@code super.init(...)} afterwards.
   */
  public void start(GatewayConfig config, AliasService aliasService, MasterService masterService) throws Exception {
    bootDerby();
    ((Configuration) config).set(GATEWAY_DATABASE_TYPE, DERBY.type());
    ((Configuration) config).set(GATEWAY_DATABASE_NAME, folder.toString());
    aliasService.addAliasForCluster(NO_CLUSTER_NAME, DATABASE_USER_ALIAS_NAME, DerbyDatabaseCredentials.getDatabaseUserName(aliasService));
    aliasService.addAliasForCluster(NO_CLUSTER_NAME, DATABASE_PASSWORD_ALIAS_NAME, DerbyDatabaseCredentials.getDatabasePassword(aliasService, masterService));

    // we need the "x" permission too to be able to browse that folder (600 is not enough)
    if (Files.exists(folder)) {
      FileUtils.chmod("700", folder.toFile());
    }
  }

  public void stop() throws ServiceLifecycleException {
    try {
      if (derbyDatabase != null) {
        derbyDatabase.shutdown();
      }
    } catch (Exception e) {
      throw new ServiceLifecycleException("Error while shutting down Derby Database", e);
    }
  }

  private void bootDerby() throws Exception {
    derbyDatabase = new DerbyDatabase(folder.toString());
    derbyDatabase.create();
    TimeUnit.SECONDS.sleep(1); // give a bit of time for the server to start
  }
}
