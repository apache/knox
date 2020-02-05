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

  @SuppressWarnings("PMD.DoNotUseThreads") // we need to define a Thread to be able to register a shutdown hook
  public void execute(String[] args)
      throws ClassNotFoundException, SQLException, CredentialCollectionException {

    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        System.out.println("Closing any open connections ...");
        closeConnection();
      }
    });
    executeShell();
  }

  private void executeShell() {
    String sql;

    System.out.println(" _                    _ _            ");
    System.out.println("| | ___ __   _____  _| (_)_ __   ___ ");
    System.out.println("| |/ / '_ \\ / _ \\ \\/ / | | '_ \\ / _ \\");
    System.out.println("|   <| | | | (_) >  <| | | | | |  __/");
    System.out.println("|_|\\_\\_| |_|\\___/_/\\_\\_|_|_| |_|\\\\__|");
    System.out.println("powered by Apache Knox");
    System.out.println("");
    System.out.println("");

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
            System.out.println(sql);
            try {
              establishConnection();
              try (Statement statement = conn.createStatement()) {
                if (statement.execute(sql)) {
                  try (ResultSet resultSet = statement.getResultSet()) {
                    KnoxShellTable table = KnoxShellTable.builder().jdbc().resultSet(resultSet);
                    System.out.println(table.toString());
                    System.out.println("\nRows: " + table.getRows().size() + "\n");
                  }
                }
              }
            }
            catch (SQLException e) {
              System.out.println("SQL Exception encountered... " + e.getMessage());
            }
          }
          else {
            System.out.println("No datasource selected. Use :ds select {datasource-name}");
          }
        }
      }
    }
  }

  private void establishConnection() throws SQLException {
    if (conn == null || conn.isClosed()) {
      System.out.println("Connecting...");
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
        System.out.println("Invalid number of arguments for :ds add. Useage: :ds add {ds-name} {connectStr} {driver} {authnType: none|basic}");
      }
    }
    else if (args[1].contentEquals("remove")) {
      if (args.length == 3) {
        removeDataSource(args[2]);
      }
      else {
        System.out.println("Invalid number of arguments for :ds remove. Useage: :ds remove {ds-name}");
      }
    }
  }

  private void listDataSources() {
    Map<String, KnoxDataSource> sources = getDataSources();
    if (sources != null) {
      sources.forEach((name, ds)->System.out.println("Name : " + name + " : " + ds.getConnectStr()));
    }
    else {
      System.out.println("No datasources configured. Use :ds add {ds-name} {connectStr} {driver} {authnType: none|basic}");
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
      System.out.println("Invalid datasource name provided. See output from: :ds list");
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
