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

import javax.sql.DataSource;

public class KnoxDatabase {

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
}
