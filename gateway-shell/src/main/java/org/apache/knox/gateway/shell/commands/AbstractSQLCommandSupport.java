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
package org.apache.knox.gateway.shell.commands;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.knox.gateway.shell.KnoxDataSource;
import org.apache.knox.gateway.shell.KnoxSession;
import org.apache.knox.gateway.shell.jdbc.JDBCUtils;

import org.apache.groovy.groovysh.jline.GroovyEngine;
import org.jline.terminal.Terminal;

public abstract class AbstractSQLCommandSupport extends AbstractKnoxShellCommand {


  protected static final String KNOXDATASOURCES = "__knoxdatasources";
  protected static final String KNOXDATASOURCE = "__knoxdatasource";
  private static final String KNOXSQLHISTORY = "__knoxsqlhistory";
  private static final String KNOXDATASOURCE_CONNECTIONS = "__knoxdatasourceconnections";

  public AbstractSQLCommandSupport(GroovyEngine engine, Terminal terminal, String name, String shortcut, String desc, String usage,
                                   String help) {
    super(engine, terminal, name, shortcut, desc, usage, help);
  }

  @SuppressWarnings("unchecked")
  protected Connection getConnectionFromSession(KnoxDataSource ds) {
    //GroovyEngine bindings lack getOrDefault, so we check for null manually
    HashMap<String, Connection> connections = (HashMap<String, Connection>) engine.get(KNOXDATASOURCE_CONNECTIONS);
    if (connections == null) {
      connections = new HashMap<>();
    }
    return connections.get(ds.getName());
  }

  @SuppressWarnings("unchecked")
  protected Connection getConnection(KnoxDataSource ds, String user, String pass) throws SQLException {
    Connection conn = getConnectionFromSession(ds);
    if (conn == null) {
      if (user != null && pass != null) {
        conn = JDBCUtils.createConnection(ds.getConnectStr(), user, pass);
      } else {
        conn = JDBCUtils.createConnection(ds.getConnectStr(), null, null);
      }

      HashMap<String, Connection> connections = (HashMap<String, Connection>) engine.get(KNOXDATASOURCE_CONNECTIONS);
      if (connections == null) {
        connections = new HashMap<>();
      }
      connections.put(ds.getName(), conn);
      engine.put(KNOXDATASOURCE_CONNECTIONS, connections);
    }
    return conn;
  }

  @SuppressWarnings("unchecked")
  protected void persistSQLHistory() {
    Map<String, List<String>> sqlHistories = (Map<String, List<String>>) engine.get(KNOXSQLHISTORY);
    KnoxSession.persistSQLHistory(sqlHistories);
  }

  @SuppressWarnings("unchecked")
  protected void persistDataSources() {
    Map<String, KnoxDataSource> datasources = (Map<String, KnoxDataSource>) engine.get(KNOXDATASOURCES);
    KnoxSession.persistDataSources(datasources);
  }

  @SuppressWarnings("unchecked")
  protected List<String> getSQLHistory(String dataSourceName) {
    List<String> sqlHistory = null;
    Map<String, List<String>> sqlHistories = (Map<String, List<String>>) engine.get(KNOXSQLHISTORY);

    if (sqlHistories == null) {
      // check for persisted histories for known datasources
      sqlHistories = loadSQLHistories();
      if (sqlHistories == null || sqlHistories.isEmpty()) {
        sqlHistories = new HashMap<>();
        engine.put(KNOXSQLHISTORY, sqlHistories);
      }
    }

    // get the history for the specific datasource
    sqlHistory = sqlHistories.get(dataSourceName);
    if (sqlHistory == null) {
      sqlHistory = startSqlHistory(dataSourceName, sqlHistories);
    }
    return sqlHistory;
  }

  private List<String> startSqlHistory(String dataSourceName, Map<String, List<String>> sqlHistories) {
    List<String> sqlHistory = new ArrayList<>();
    sqlHistories.put(dataSourceName, sqlHistory);
    return sqlHistory;
  }

  private Map<String, List<String>> loadSQLHistories() {
    Map<String, List<String>> sqlHistories = null;
    try {
      sqlHistories = KnoxSession.loadSQLHistories();
      if (sqlHistories != null) {
        engine.put(KNOXSQLHISTORY, sqlHistories);
      }
    } catch (IOException e) {
      // Route errors through JLine terminal
      terminal.writer().println("Error loading SQL history: " + e.getMessage());
      e.printStackTrace(terminal.writer());
      terminal.writer().flush();
    }
    return sqlHistories;
  }

  private Map<String, KnoxDataSource> loadDataSources() {
    Map<String, KnoxDataSource> datasources = null;
    try {
      datasources = KnoxSession.loadDataSources();
      if (datasources != null) {
        engine.put(KNOXDATASOURCES, datasources);
      }
    } catch (IOException e) {
      //Route errors through JLine terminal
      terminal.writer().println("Error loading Data Sources: " + e.getMessage());
      e.printStackTrace(terminal.writer());
      terminal.writer().flush();
    }
    return datasources;
  }

  protected void addToSQLHistory(String dsName, String sql) {
    List<String> sqlHistory = null;
    if (sql != null && !sql.isEmpty()) {
      sqlHistory = getSQLHistory(dsName);
      if (sqlHistory != null) {
        sqlHistory.add(sql);
      }
    }

    if (sqlHistory != null && sqlHistory.size() > 20) {
      sqlHistory.remove(0);
    }
    persistSQLHistory();
  }

  protected void addToSQLHistory(List<String> sqlHistory, String sql) {
    if (sql != null && !sql.isEmpty()) {
      sqlHistory.add(sql);
    }

    if (sqlHistory.size() > 20) {
      sqlHistory.remove(0);
    }
    persistSQLHistory();
  }

  @SuppressWarnings("unchecked")
  protected void removeFromSQLHistory(String dsName) {
    Map<String, List<String>> sqlHistories = (Map<String, List<String>>) engine.get(KNOXSQLHISTORY);
    if (sqlHistories != null) {
      sqlHistories.remove(dsName);
      persistSQLHistory();
    }
  }

  @SuppressWarnings("unchecked")
  protected Map<String, KnoxDataSource> getDataSources() {
    Map<String, KnoxDataSource> datasources = (Map<String, KnoxDataSource>) engine.get(KNOXDATASOURCES);
    if (datasources == null) {
      datasources = loadDataSources();
      if (datasources != null) {
        engine.put(KNOXDATASOURCES, datasources);
      } else {
        datasources = new HashMap<>();
      }
    }
    return datasources;
  }

  @SuppressWarnings("unchecked")
  public void closeConnections() {
    // close all JDBC connections in the session - called by shutdown hook
    HashMap<String, Connection> connections = (HashMap<String, Connection>) engine.get((String) KNOXDATASOURCE_CONNECTIONS);
    if (connections == null) {
      connections = new HashMap<>();
    }
    connections.values().forEach(connection -> {
        try {
          if (!connection.isClosed()) {
            connection.close();
          }
        } catch (SQLException e) {
          // nop
        }
      });
  }
}
