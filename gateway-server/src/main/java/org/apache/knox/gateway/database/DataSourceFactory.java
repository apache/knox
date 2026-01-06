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

public class DataSourceFactory {

    @SuppressWarnings("PMD.ExhaustiveSwitchHasDefault")
    public static DataSource getDataSource(GatewayConfig gatewayConfig, AliasService aliasService) throws AliasServiceException, SQLException {
        DatabaseType dbType = DatabaseType.fromString(gatewayConfig.getDatabaseType());
        AbstractDataSource dsFactory;

        switch (dbType) {
            case POSTGRESQL -> dsFactory = new PostgresDataSource();
            case MYSQL -> dsFactory = new MysqlDataSource();
            case MARIADB -> dsFactory = new MariaDBDataSource();
            case DERBY -> dsFactory = new DerbyDataSource();
            case HSQL -> dsFactory = new HsqlDataSource();
            case ORACLE -> dsFactory = new OracleDataSource();
            default -> throw new IllegalArgumentException("Invalid database type: " + gatewayConfig.getDatabaseType());
        }

        return dsFactory.createDataSource(gatewayConfig, aliasService);
    }
}
