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

import org.apache.commons.io.IOUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

public class KnoxDatabase {

    // Matches the leading "CREATE TABLE [IF NOT EXISTS] <name>" of a DDL statement so the table name
    // can be handed to the existence check. Case-insensitive; tolerates the IF NOT EXISTS that the
    // standard (PostgreSQL/MySQL/H2) scripts use but Oracle omit.
    private static final Pattern CREATE_TABLE_PATTERN =
        Pattern.compile("(?i)^\\s*CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([\\w.]+)");

    protected final DataSource dataSource;

    public KnoxDatabase(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    protected void createTableIfNotExists(String tableName, String createSqlFileName) throws Exception {
        if (!JDBCUtils.tableExists(tableName, dataSource)) {
            // Resolve the DDL resource via the actual subclass's classloader so each KnoxDatabase
            // subclass (TokenStateDatabase, FederatedIdentityDatabase) loads its own create*.sql
            // rather than being coupled to one hardcoded sibling class's classloader.
            JDBCUtils.createTableFromSQL(createSqlFileName, dataSource, getClass().getClassLoader());
        }
    }

    protected void createTablesIfNotExist(String sqlFileName) throws Exception {
        final Map<String, String> createSqlByTableName = parseCreateTableStatements(sqlFileName);
        for (Map.Entry<String, String> entry : createSqlByTableName.entrySet()) {
            if (!JDBCUtils.tableExists(entry.getKey(), dataSource)) {
                JDBCUtils.createTableFromSQL(entry.getValue(), dataSource);
            }
        }
    }

    protected Map<String, String> parseCreateTableStatements(String sqlFileName) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(sqlFileName)) {
            if (is == null) {
                throw new IllegalStateException("DDL script not found on classpath: " + sqlFileName);
            }
            final String script = IOUtils.toString(is, UTF_8);
            final String withoutLicenseHeader = removeLicenseHeader(script);
            final Map<String, String> createSqlByTableName = new LinkedHashMap<>();
            for (String statement : withoutLicenseHeader.split(";")) {
                final String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    createSqlByTableName.put(extractTableName(trimmed), trimmed);
                }
            }
            return createSqlByTableName;
        }
    }

    /* The DDL scripts begin with the ASF license as a block of "--" comment lines. Stripping only
    the comment lines (rather than cutting everything before the first CREATE TABLE) removes the
    header while leaving every SQL statement intact -- notably the PostgreSQL pg_advisory_lock
    statements that guard concurrent CREATE TABLE IF NOT EXISTS, which the earlier approach cut.
     */
    private String removeLicenseHeader(String script) {
        return script.replaceAll("(?m)^\\s*--.*$", "");
    }

    private  String extractTableName(String createTableStatement) {
        final Matcher matcher = CREATE_TABLE_PATTERN.matcher(createTableStatement);
        if (!matcher.find()) {
            throw new IllegalStateException("Expected a CREATE TABLE statement but found: " + createTableStatement);
        }
        return matcher.group(1);
    }
}
