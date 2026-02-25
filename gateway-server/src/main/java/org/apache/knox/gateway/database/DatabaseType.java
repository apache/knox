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

public enum DatabaseType {
    POSTGRESQL("postgresql",
            AbstractDataSourceFactory.POSTGRES_TOKENS_TABLE_CREATE_SQL_FILE_NAME,
            AbstractDataSourceFactory.POSTGRES_TOKEN_METADATA_TABLE_CREATE_SQL_FILE_NAME,
            AbstractDataSourceFactory.KNOX_PROVIDERS_TABLE_CREATE_SQL_FILE_NAME,
            AbstractDataSourceFactory.KNOX_DESCRIPTORS_TABLE_CREATE_SQL_FILE_NAME
    ),
    MYSQL("mysql",
            AbstractDataSourceFactory.TOKENS_TABLE_CREATE_SQL_FILE_NAME,
            AbstractDataSourceFactory.TOKEN_METADATA_TABLE_CREATE_SQL_FILE_NAME,
            AbstractDataSourceFactory.KNOX_PROVIDERS_TABLE_CREATE_SQL_FILE_NAME,
            AbstractDataSourceFactory.KNOX_DESCRIPTORS_TABLE_CREATE_SQL_FILE_NAME
    ),
    MARIADB("mariadb",
            AbstractDataSourceFactory.TOKENS_TABLE_CREATE_SQL_FILE_NAME,
            AbstractDataSourceFactory.TOKEN_METADATA_TABLE_CREATE_SQL_FILE_NAME,
            AbstractDataSourceFactory.KNOX_PROVIDERS_TABLE_CREATE_SQL_FILE_NAME,
            AbstractDataSourceFactory.KNOX_DESCRIPTORS_TABLE_CREATE_SQL_FILE_NAME
    ),
    HSQL("hsql",
            AbstractDataSourceFactory.TOKENS_TABLE_CREATE_SQL_FILE_NAME,
            AbstractDataSourceFactory.TOKEN_METADATA_TABLE_CREATE_SQL_FILE_NAME,
            AbstractDataSourceFactory.KNOX_PROVIDERS_TABLE_CREATE_SQL_FILE_NAME,
            AbstractDataSourceFactory.KNOX_DESCRIPTORS_TABLE_CREATE_SQL_FILE_NAME
    ),
    DERBY("derbydb",
            AbstractDataSourceFactory.DERBY_TOKENS_TABLE_CREATE_SQL_FILE_NAME,
            AbstractDataSourceFactory.DERBY_TOKEN_METADATA_TABLE_CREATE_SQL_FILE_NAME,
            AbstractDataSourceFactory.DERBY_KNOX_PROVIDERS_TABLE_CREATE_SQL_FILE_NAME,
            AbstractDataSourceFactory.DERBY_KNOX_DESCRIPTORS_TABLE_CREATE_SQL_FILE_NAME
    ),
    ORACLE("oracle",
            AbstractDataSourceFactory.ORACLE_TOKENS_TABLE_CREATE_SQL_FILE_NAME,
            AbstractDataSourceFactory.ORACLE_TOKEN_METADATA_TABLE_CREATE_SQL_FILE_NAME,
            AbstractDataSourceFactory.ORACLE_KNOX_PROVIDERS_TABLE_CREATE_SQL_FILE_NAME,
            AbstractDataSourceFactory.ORACLE_KNOX_DESCRIPTORS_TABLE_CREATE_SQL_FILE_NAME
    );

    private final String type;
    private final String tokensTableSql;
    private final String metadataTableSql;
    private final String providersTableSql;
    private final String descriptorsTableSql;

    DatabaseType(String type, String tokensTableSql, String metadataTableSql,  String providersTableSql, String descriptorsTableSql) {
        this.type = type;
        this.tokensTableSql = tokensTableSql;
        this.metadataTableSql = metadataTableSql;
        this.providersTableSql = providersTableSql;
        this.descriptorsTableSql = descriptorsTableSql;
    }

    public String type() {
        return type;
    }

    public String tokensTableSql() {
        return tokensTableSql;
    }

    public String metadataTableSql() {
        return metadataTableSql;
    }

    public String providersTableSql() {
        return providersTableSql;
    }

    public String descriptorsTableSql() {
        return descriptorsTableSql;
    }

    public static DatabaseType fromString(String dbType) {
        for (DatabaseType dt : values()) {
            if (dt.type.equalsIgnoreCase(dbType)) {
                return dt;
            }
        }
        throw new IllegalArgumentException("Invalid database type: " + dbType);
    }
}
