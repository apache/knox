/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.database;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;

import javax.sql.DataSource;
import java.sql.SQLException;

public abstract class AbstractDataSource {

    public static final String TOKENS_TABLE_CREATE_SQL_FILE_NAME = "createKnoxTokenDatabaseTable.sql";
    public static final String TOKEN_METADATA_TABLE_CREATE_SQL_FILE_NAME = "createKnoxTokenMetadataDatabaseTable.sql";
    public static final String ORACLE_TOKENS_TABLE_CREATE_SQL_FILE_NAME = "createKnoxTokenDatabaseTableOracle.sql";
    public static final String ORACLE_TOKEN_METADATA_TABLE_CREATE_SQL_FILE_NAME = "createKnoxTokenMetadataDatabaseTableOracle.sql";
    public static final String DERBY_TOKENS_TABLE_CREATE_SQL_FILE_NAME = "createKnoxTokenDatabaseTableDerby.sql";
    public static final String DERBY_TOKEN_METADATA_TABLE_CREATE_SQL_FILE_NAME = "createKnoxTokenMetadataDatabaseTableDerby.sql";

    public static final String KNOX_PROVIDERS_TABLE_CREATE_SQL_FILE_NAME = "createKnoxProvidersTable.sql";
    public static final String KNOX_DESCRIPTORS_TABLE_CREATE_SQL_FILE_NAME = "createKnoxDescriptorsTable.sql";
    public static final String ORACLE_KNOX_PROVIDERS_TABLE_CREATE_SQL_FILE_NAME = "createKnoxProvidersTableOracle.sql";
    public static final String ORACLE_KNOX_DESCRIPTORS_TABLE_CREATE_SQL_FILE_NAME = "createKnoxDescriptorsTableOracle.sql";
    public static final String DERBY_KNOX_PROVIDERS_TABLE_CREATE_SQL_FILE_NAME = "createKnoxProvidersTableDerby.sql";
    public static final String DERBY_KNOX_DESCRIPTORS_TABLE_CREATE_SQL_FILE_NAME = "createKnoxDescriptorsTableDerby.sql";

    public static final String DATABASE_USER_ALIAS_NAME = "gateway_database_user";
    public static final String DATABASE_PASSWORD_ALIAS_NAME = "gateway_database_password";
    public static final String DATABASE_TRUSTSTORE_PASSWORD_ALIAS_NAME = "gateway_database_ssl_truststore_password";

    public abstract DataSource createDataSource(GatewayConfig gatewayConfig, AliasService aliasService) throws AliasServiceException, SQLException;

    protected String getDatabaseUser(AliasService aliasService) throws AliasServiceException {
        return getDatabaseAlias(aliasService, DATABASE_USER_ALIAS_NAME);
    }

    protected String getDatabasePassword(AliasService aliasService) throws AliasServiceException {
        return getDatabaseAlias(aliasService, DATABASE_PASSWORD_ALIAS_NAME);
    }

    protected String getDatabaseAlias(AliasService aliasService, String aliasName) throws AliasServiceException {
        final char[] value = aliasService.getPasswordFromAliasForGateway(aliasName);
        return value == null ? null : new String(value);
    }
}
