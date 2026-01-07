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

import org.apache.commons.lang3.StringUtils;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.services.security.AliasService;
import org.apache.knox.gateway.services.security.AliasServiceException;

import javax.sql.DataSource;
import java.sql.SQLException;

public class OracleDataSourceFactory extends AbstractDataSourceFactory {

    private static final String THIN_DRIVER = "thin";

    @Override
    public DataSource createDataSource(GatewayConfig gatewayConfig, AliasService aliasService) throws AliasServiceException, SQLException {
        final oracle.jdbc.pool.OracleDataSource oracleDataSource = new oracle.jdbc.pool.OracleDataSource();
        final String dbUser = getDatabaseUser(aliasService);
        final String dbPassword = getDatabasePassword(aliasService);

        if (gatewayConfig.getDatabaseConnectionUrl() != null) {
            oracleDataSource.setURL(gatewayConfig.getDatabaseConnectionUrl());
            if (StringUtils.isNotBlank(dbUser)) {
                oracleDataSource.setUser(dbUser);
            }
            if (StringUtils.isNotBlank(dbPassword)) {
                oracleDataSource.setPassword(dbPassword);
            }
        } else {
            oracleDataSource.setDriverType(THIN_DRIVER);
            oracleDataSource.setServiceName(gatewayConfig.getDatabaseName());
            oracleDataSource.setServerName(gatewayConfig.getDatabaseHost());
            oracleDataSource.setPortNumber(gatewayConfig.getDatabasePort());
            oracleDataSource.setUser(dbUser);
            oracleDataSource.setPassword(dbPassword);
        }
        return oracleDataSource;
    }
}
