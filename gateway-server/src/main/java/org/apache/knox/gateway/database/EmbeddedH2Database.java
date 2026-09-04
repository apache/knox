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
import static org.apache.knox.gateway.database.DatabaseType.H2;
import static org.apache.knox.gateway.services.security.AliasService.NO_CLUSTER_NAME;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.hadoop.conf.Configuration;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.MasterService;
import org.apache.knox.gateway.util.FileUtils;

/**
 * Manages the single embedded H2 database that the KnoxIDF self-provisioning services share under
 * {@code ${securityDir}/h2db}. This is the embedded, zero-config replacement for the retired
 * Apache Derby backend (KNOX-3401). Unlike the legacy Derby folder ({@code tokens}), this database
 * holds more than token state (federated identities, trusted OIDC issuers, delegation policies), so
 * it lives in its own dedicated folder; keeping it separate from Derby's also eases Derby-to-H2
 * migration.
 * <p>
 * Unlike Derby there is no network server to boot: H2 creates the database file on first connection
 * to the {@code jdbc:h2:<file>} URL, so {@link #start} only has to point the shared
 * {@link GatewayConfig} at the file, ensure the connection user/password aliases exist and tighten
 * folder permissions. Because H2 shares one in-JVM database per URL, the several {@code H2DB*}
 * services that each construct their own instance simply attach to the same already-open database.
 * <p>
 * At-rest encryption (the Derby capability this backend must preserve) is available by appending
 * {@code ;CIPHER=AES} to the URL and supplying a file password; wiring that to the master secret is
 * a follow-up to this POC.
 */
public final class EmbeddedH2Database {

  /** Folder name (under the gateway security dir) that holds the shared embedded database. */
  public static final String DB_FOLDER = "h2db";
  /** Base file name for the H2 database within {@link #DB_FOLDER}; H2 appends {@code .mv.db}. */
  public static final String DB_FILE_BASE = "knoxdb";

  private final Path folder;
  private final Path fileBase;

  public EmbeddedH2Database(GatewayConfig config) {
    this.folder = Paths.get(config.getGatewaySecurityDir(), DB_FOLDER);
    this.fileBase = folder.resolve(DB_FILE_BASE);
  }

  /**
   * Points the shared {@link GatewayConfig} at the embedded H2 file and ensures the connection
   * credentials exist. Callers delegate the actual persistence to their JDBC service by invoking
   * {@code super.init(...)} afterwards, which opens the URL and self-creates the tables.
   */
  public void start(GatewayConfig config, AliasService aliasService, MasterService masterService) throws Exception {
    // H2 creates the database file but not missing parent directories.
    Files.createDirectories(folder);
    ((Configuration) config).set(GATEWAY_DATABASE_TYPE, H2.type());
    ((Configuration) config).set(GATEWAY_DATABASE_NAME, fileBase.toString());
    aliasService.addAliasForCluster(NO_CLUSTER_NAME, DATABASE_USER_ALIAS_NAME, EmbeddedDatabaseCredentials.getDatabaseUserName(aliasService));
    aliasService.addAliasForCluster(NO_CLUSTER_NAME, DATABASE_PASSWORD_ALIAS_NAME, EmbeddedDatabaseCredentials.getDatabasePassword(aliasService, masterService));

    // we need the "x" permission too to be able to browse that folder (600 is not enough)
    FileUtils.chmod("700", folder.toFile());
  }

  /**
   * No-op: an embedded H2 database has no separate server process and releases its file when the
   * last connection closes / the JVM exits. Kept for lifecycle symmetry with the Derby backend.
   */
  public void stop() {
    // nothing to shut down for embedded H2
  }
}
