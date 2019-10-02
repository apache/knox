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
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public class JDBCKnoxShellTableBuilder extends KnoxShellTableBuilder {

  private String connectionUrl;
  private String driver;
  private Connection conn;
  private boolean tableManagedConnection = true;

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
    KnoxShellTable table = null;
    conn = conn == null ? DriverManager.getConnection(connectionUrl) : conn;
    if (conn != null) {
      try (Statement statement = conn.createStatement(); ResultSet result = statement.executeQuery(sql);) {
        table = new KnoxShellTable();
        final ResultSetMetaData metadata = result.getMetaData();
        table.title(metadata.getTableName(1));
        int colcount = metadata.getColumnCount();
        for (int i = 1; i < colcount + 1; i++) {
          table.header(metadata.getColumnName(i));
        }
        while (result.next()) {
          table.row();
          for (int i = 1; i < colcount + 1; i++) {
            table.value(result.getObject(metadata.getColumnName(i), Comparable.class));
          }
        }
      } finally {
        if (conn != null && tableManagedConnection) {
          conn.close();
        }
      }
    }
    return table;
  }

  public KnoxShellTable build(ResultSet resultSet) throws SQLException {
    KnoxShellTable table = new KnoxShellTable();
    ResultSetMetaData metadata = resultSet.getMetaData();
    table.title(metadata.getTableName(1));
    int colcount = metadata.getColumnCount();
    for (int i = 1; i < colcount + 1; i++) {
      table.header(metadata.getColumnName(i));
    }
    while (resultSet.next()) {
      table.row();
      for (int i = 1; i < colcount + 1; i++) {
        table.value(resultSet.getString(metadata.getColumnName(i)));
      }
    }
    return table;
  }

}
