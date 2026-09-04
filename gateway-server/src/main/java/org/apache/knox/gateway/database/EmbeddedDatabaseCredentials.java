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

import static org.apache.knox.gateway.database.AbstractDataSourceFactory.DATABASE_PASSWORD_ALIAS_NAME;
import static org.apache.knox.gateway.database.AbstractDataSourceFactory.DATABASE_USER_ALIAS_NAME;

import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.MasterService;

/**
 * Shared resolution of the embedded-database connection credentials used by the self-provisioning
 * {@code H2DB*} services (token state, federated identity, trusted OIDC issuer, delegation policy).
 * All of these back onto the single embedded H2 database and therefore resolve the same connection
 * user and password: read from the gateway alias store when present, otherwise falling back to a
 * well-known default user and the master secret.
 */
public final class EmbeddedDatabaseCredentials {

  /** Default connection user for the embedded database when no alias is configured. */
  public static final String DEFAULT_DB_USER_NAME = "knox";

  private EmbeddedDatabaseCredentials() {
  }

  public static String getDatabaseUserName(AliasService aliasService) throws Exception {
    final char[] dbUserAliasValue = aliasService.getPasswordFromAliasForGateway(DATABASE_USER_ALIAS_NAME);
    return dbUserAliasValue != null ? new String(dbUserAliasValue) : DEFAULT_DB_USER_NAME;
  }

  public static String getDatabasePassword(AliasService aliasService, MasterService masterService) throws Exception {
    final char[] dbPasswordAliasValue = aliasService.getPasswordFromAliasForGateway(DATABASE_PASSWORD_ALIAS_NAME);
    return dbPasswordAliasValue != null ? new String(dbPasswordAliasValue) : new String(masterService.getMasterSecret());
  }
}
