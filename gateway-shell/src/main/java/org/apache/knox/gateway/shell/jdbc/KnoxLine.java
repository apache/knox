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
package org.apache.knox.gateway.shell.jdbc;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.Map;

import org.apache.knox.gateway.shell.CredentialCollectionException;
import org.apache.knox.gateway.shell.Credentials;
import org.apache.knox.gateway.shell.KnoxDataSource;
import org.apache.knox.gateway.shell.KnoxSession;
import org.apache.knox.gateway.shell.table.KnoxShellTable;

public class KnoxLine {
  private String user;
  private String pass;
  private KnoxDataSource datasource;
  private Connection conn;

  Logger logger = Logger.getLogger(getClass().getName());

  @SuppressWarnings("PMD.DoNotUseThreads") // we need to define a Thread to be able to register a shutdown hook
  public void execute(String[] args)
      throws ClassNotFoundException, SQLException, CredentialCollectionException {

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        logger.info("Closing any open connections ...");
        closeConnection();
      }
    });
    executeShell();
  }

  private void executeShell() {
    String sql;

    logger.info(" _                    _ _            ");
    logger.info("| | ___ __   _____  _| (_)_ __   ___ ");
    logger.info("| |/ / '_ \\ / _ \\ \\/ / | | '_ \\ / _ \\");
    logger.info("|   <| | | | (_) >  <| | | | | |  __/");
    logger.info("|_|\\_\\_| |_|\\___/_/\\_\\_|_|_| |_|\\\\__|");
    logger.info("powered by Apache Knox");
    logger.info("");
    logger.info("");

    while(true) {
      sql = System.console().readLine("knoxline> ");
      if (sql != null && !sql.isEmpty()) {
        if (sql.startsWith(":ds") || sql.startsWith(":datasource")) {
          try {
            processDataSourceCommand(sql);
          } catch (CredentialCollectionException | SQLException e) {
            e.printStackTrace();
          }
        }
        else {
          // Configure JDBC connection
          if (datasource != null) {
            logger.info(sql);
            try {
              establishConnection();
              try (Statement statement = conn.createStatement()) {
                if (statement.execute(sql)) {
                  try (ResultSet resultSet = statement.getResultSet()) {
                    KnoxShellTable table = KnoxShellTable.builder().jdbc().resultSet(resultSet);
                    logger.info(table.toString());
                    logger.info("\nRows: " + table.getRows().size() + "\n");
                  }
                }
              }
            }
            catch (SQLException e) {
              logger.info("SQL Exception encountered... " + e.getMessage());
            }
          }
          else {
            logger.info("No datasource selected. Use :ds select {datasource-name}");
          }
        }
      }
    }
  }

  private void establishConnection() throws SQLException {
    if (conn == null || conn.isClosed()) {
      logger.info("Connecting...");
      conn = JDBCUtils.createConnection(datasource.getConnectStr(), user, pass);
    }
  }

  void processDataSourceCommand(String sql) throws CredentialCollectionException, SQLException {
    String[] args = sql.split(" ");
    if (args.length == 1 || args[1].equals("list")) {
      listDataSources();
    }
    else if (args[1].equals("select")) {
      selectDataSource(args[2]);
    }
    else if (args[1].equals("add")) {
      if (args.length == 6) {
        addDataSource(args[2], args[3], args[4], args[5]);
      }
      else {
        logger.info("Invalid number of arguments for :ds add. Useage: :ds add {ds-name} {connectStr} {driver} {authnType: none|basic}");
      }
    }
    else if (args[1].contentEquals("remove")) {
      if (args.length == 3) {
        removeDataSource(args[2]);
      }
      else {
        logger.info("Invalid number of arguments for :ds remove. Useage: :ds remove {ds-name}");
      }
    }
  }

  private void listDataSources() {
    Map<String, KnoxDataSource> sources = getDataSources();
    if (sources != null) {
      sources.forEach((name, ds)->logger.info("Name : " + name + " : " + ds.getConnectStr()));
    }
    else {
      logger.info("No datasources configured. Use :ds add {ds-name} {connectStr} {driver} {authnType: none|basic}");
    }
  }

  private Map<String, KnoxDataSource> getDataSources() {
    Map<String, KnoxDataSource> datasources = new HashMap<>();
    try {
      datasources = KnoxSession.loadDataSources();
    } catch (IOException e) {
      e.printStackTrace();
    }

    return datasources;
  }

  private KnoxDataSource addDataSource(String name, String connectStr, String driver, String authnType) {
    Map<String, KnoxDataSource> datasources = getDataSources();
    KnoxDataSource ds = new KnoxDataSource(name, connectStr, driver, authnType);
    datasources.put(name, ds);
    KnoxSession.persistDataSources(datasources);
    return ds;
  }

  private void removeDataSource(String name) {
    Map<String, KnoxDataSource> datasources = getDataSources();
    datasources.remove(name);
    KnoxSession.persistDataSources(datasources);
  }

  private void selectDataSource(String name) throws CredentialCollectionException {
    Map<String, KnoxDataSource> sources = getDataSources();
    datasource = sources.get(name);

    if (datasource == null) {
      logger.info("Invalid datasource name provided. See output from: :ds list");
      return;
    }

    user = null;
    pass = null;
    if (datasource.getAuthnType().contentEquals("basic")) {
      collectCredentials();
    }

    closeConnection();

    try {
      establishConnection();
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  private void closeConnection() {
    try {
      if (conn != null && !conn.isClosed()) {
        conn.close();
      }
    } catch (SQLException e) {
      //nop
    }
    finally {
      conn = null;
    }
  }

  private void collectCredentials() throws CredentialCollectionException {
    Credentials credentials = new Credentials();
    credentials.add("ClearInput", "Enter username: ", "user")
      .add("HiddenInput", "Enter pas" + "sword: ", "pass")
      .collect();

    user = credentials.get("user").string();
    pass = credentials.get("pass").string();
  }
}
