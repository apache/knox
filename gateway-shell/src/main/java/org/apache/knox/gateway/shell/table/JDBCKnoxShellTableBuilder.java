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
package org.apache.knox.gateway.shell.table;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import org.apache.knox.gateway.shell.jdbc.JDBCUtils;

public class JDBCKnoxShellTableBuilder extends KnoxShellTableBuilder {

  private String connectionUrl;
  private String driver;
  private Connection conn;
  private boolean tableManagedConnection = true;
  private String username;
  private String pass;

  public JDBCKnoxShellTableBuilder username(String username) {
    this.username = username;
    return this;
  }

  public JDBCKnoxShellTableBuilder password(String pass) {
    this.pass = pass;
    return this;
  }

  public String username() {
    return username;
  }

  public String password() {
    return pass;
  }

  JDBCKnoxShellTableBuilder(KnoxShellTable table) {
    super(table);
  }

  @Override
  public JDBCKnoxShellTableBuilder title(String title) {
    this.title = title;
    return this;
  }

  public JDBCKnoxShellTableBuilder connectTo(String connectionUrl) {
    this.connectionUrl = connectionUrl;
    return this;
  }

  public JDBCKnoxShellTableBuilder driver(String driver) throws Exception {
    this.driver = driver;
    loadDriver();
    return this;
  }

  private void loadDriver() throws Exception {
    try {
      Class.forName(driver).newInstance();
    } catch (ClassNotFoundException e) {
      System.out.println(String.format(Locale.US, "Unable to load the JDBC driver %s. Check your CLASSPATH.", driver));
      throw e;
    } catch (InstantiationException e) {
      System.out.println(String.format(Locale.US, "Unable to instantiate the JDBC driver %s", driver));
      throw e;
    } catch (IllegalAccessException e) {
      System.out.println(String.format(Locale.US, "Not allowed to access the JDBC driver %s", driver));
      throw e;
    }
  }

  public JDBCKnoxShellTableBuilder connection(Connection connection) {
    this.conn = connection;
    this.tableManagedConnection = false;
    return this;
  }

  public KnoxShellTable sql(String sql) throws IOException, SQLException {
    conn = conn == null ? createConnection() : conn;
    try (Statement statement = conn.createStatement(); ResultSet resultSet = statement.executeQuery(sql);) {
      processResultSet(resultSet);
    } finally {
      if (conn != null && tableManagedConnection) {
        conn.close();
      }
    }
    return this.table;
  }

  private Connection createConnection() throws SQLException {
    return JDBCUtils.createConnection(connectionUrl, username, pass);
  }

  // added this as a private method so that KnoxShellTableHistoryAspect will not
  // intercept this call
  private void processResultSet(ResultSet resultSet) throws SQLException {
    final ResultSetMetaData metadata = resultSet.getMetaData();
    final int colCount = metadata.getColumnCount();
    try {
      table.title(metadata.getTableName(1));
    }
    catch (SQLException e) {
      // nop. Apache HiveDriver doesn't support this.
    }
    for (int i = 1; i < colCount + 1; i++) {
      this.table.header(metadata.getColumnName(i));
    }
    while (resultSet.next()) {
      this.table.row();
      for (int i = 1; i < colCount + 1; i++) {
        try {
          table.value(resultSet.getObject(metadata.getColumnName(i), Comparable.class));
        }
        catch (SQLException e) {
          table.value(resultSet.getString(metadata.getColumnName(i)));
        }
      }
    }
  }

  public KnoxShellTable resultSet(ResultSet resultSet) throws SQLException {
    processResultSet(resultSet);
    return this.table;
  }
}