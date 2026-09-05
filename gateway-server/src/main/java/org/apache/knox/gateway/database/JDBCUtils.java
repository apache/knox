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
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.Locale;

import static java.nio.charset.StandardCharsets.UTF_8;

public class JDBCUtils {

    public static boolean tableExists(String tableName, DataSource dataSource) throws SQLException {
        boolean exists;
        try (Connection connection = dataSource.getConnection()) {
            final DatabaseMetaData dbMetadata = connection.getMetaData();
            final String tableNameToCheck = normalizeIdentifier(tableName, dbMetadata);
            try (ResultSet tables = dbMetadata.getTables(connection.getCatalog(), null, tableNameToCheck, null)) {
                exists = tables.next();
            }
        }
        return exists;
    }

    /**
     * Normalises an unquoted identifier to the case the driver actually stores it in, so it can be
     * matched against {@link DatabaseMetaData#getTables}. Derby (and other uppercase-storing
     * engines) store an unquoted {@code federated_identity} as {@code FEDERATED_IDENTITY}; passing
     * the name verbatim would match nothing and cause {@code createTableIfNotExists} to re-run the
     * CREATE and fail with "table already exists". Callers that use already-uppercase constants
     * (KNOX_TOKENS, KNOX_PROVIDERS, TRUSTED_OIDC_ISSUERS) are unaffected since upper-casing them is
     * a no-op.
     */
    private static String normalizeIdentifier(String identifier, DatabaseMetaData dbMetadata) throws SQLException {
        if (dbMetadata.storesUpperCaseIdentifiers()) {
            return identifier.toUpperCase(Locale.ROOT);
        } else if (dbMetadata.storesLowerCaseIdentifiers()) {
            return identifier.toLowerCase(Locale.ROOT);
        }
        return identifier;
    }

    public static void createTableFromSQL(String createSqlFileName, DataSource dataSource, ClassLoader classLoader) throws Exception {
        try (InputStream is = classLoader.getResourceAsStream(createSqlFileName)) {
            createTableFromSQL(IOUtils.toString(is, UTF_8), dataSource);
        }
    }

    public static void createTableFromSQL(String createTableSql, DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement createTableStatement = connection.createStatement()) {
            createTableStatement.execute(createTableSql);
        }
    }

    /**
     * Recognises a unique/primary-key constraint violation across dialects: either a
     * {@link SQLIntegrityConstraintViolationException} or any {@link SQLException} in the cause
     * chain whose SQLState is in the {@code 23} (integrity constraint violation) class.
     */
    public static boolean isUniqueConstraintViolation(SQLException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
            if (t instanceof SQLException) {
                final String sqlState = ((SQLException) t).getSQLState();
                if (sqlState != null && sqlState.startsWith("23")) {
                    return true;
                }
            }
        }
        return false;
    }
}
