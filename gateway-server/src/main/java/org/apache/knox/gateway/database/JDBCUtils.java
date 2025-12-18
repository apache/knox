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

import org.apache.commons.io.IOUtils;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import static java.nio.charset.StandardCharsets.UTF_8;

public class JDBCUtils {

    public static boolean tableExists(String tableName, DataSource dataSource) throws SQLException {
        boolean exists;
        try (Connection connection = dataSource.getConnection()) {
            final DatabaseMetaData dbMetadata = connection.getMetaData();
            final String tableNameToCheck = dbMetadata.storesUpperCaseIdentifiers() ? tableName : tableName.toLowerCase(Locale.ROOT);
            try (ResultSet tables = dbMetadata.getTables(connection.getCatalog(), null, tableNameToCheck, null)) {
                exists = tables.next();
            }
        }
        return exists;
    }

    public static void createTableFromSQL(String createSqlFileName, DataSource dataSource, ClassLoader classLoader) throws Exception {
        try (InputStream is = classLoader.getResourceAsStream(createSqlFileName);
             Connection connection = dataSource.getConnection();Statement createTableStatement = connection.createStatement()) {
            String createTableSql = IOUtils.toString(is, UTF_8);
            createTableStatement.execute(createTableSql);
        }
    }
}
