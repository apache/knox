/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.shell.commands;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.apache.knox.gateway.shell.CredentialCollector;
import org.apache.knox.gateway.shell.KnoxDataSource;
import org.apache.knox.gateway.shell.table.KnoxShellTable;

import org.apache.groovy.groovysh.jline.GroovyEngine;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;

public class SelectCommand extends AbstractSQLCommandSupport {
  private static final String USAGE = ":sql [assign resulting-variable-name]";
  private static final String DESC = "Build table from SQL ResultSet";

  private static final String KNOXDATASOURCE = "__knoxdatasource";

  public SelectCommand(GroovyEngine engine, Terminal terminal) {
    super(engine, terminal, ":SQL", ":sql", DESC, USAGE, DESC);
  }

  @SuppressWarnings({"unchecked", "PMD.CloseResource"})
  @Override
  public Object execute(List<String> args) {
    String bindVariableName = null;
    KnoxShellTable table = null;

    if (args != null && !args.isEmpty()) {
      bindVariableName = getBindingVariableNameForResultingTable(args);
    }

    String dsName = (String) engine.get(KNOXDATASOURCE);
    Map<String, KnoxDataSource> dataSources = getDataSources();
    KnoxDataSource ds = null;

    if (dsName == null || dsName.isEmpty()) {
      if (dataSources == null || dataSources.isEmpty()) {
        return "Please configure a datasource with ':datasources add {name} {connectStr} {driver} {authntype: none|basic}'.";
      } else if (dataSources.size() == 1) {
        dsName = (String) dataSources.keySet().toArray()[0];
      } else {
        return "Multiple datasources configured. Please disambiguate with ':datasources select {name}'.";
      }
    }

    ds = dataSources.get(dsName);
    if (ds != null) {
      String sql = promptForSQL(dsName);

      if (sql == null || sql.trim().isEmpty()) {
        return "Query cancelled or empty.";
      }

      addToSQLHistory(dsName, sql);

      try {
        terminal.writer().println("Executing: " + sql);
        terminal.writer().flush();

        Connection conn = getConnectionFromSession(ds);
        if (conn == null || conn.isClosed()) {
          String username = null;
          char[] pass = null;
          if ("basic".equalsIgnoreCase(ds.getAuthnType())) {
            CredentialCollector dlg = login();
            username = dlg.name();
            pass = dlg.chars();
          }
          // NullPointerException prevention for pass
          String passStr = (pass == null) ? null : new String(pass);
          conn = getConnection(ds, username, passStr);
        }

        try (Statement statement = conn.createStatement()) {
          if (statement.execute(sql)) {
            try (ResultSet resultSet = statement.getResultSet()) {
              table = KnoxShellTable.builder().jdbc().resultSet(resultSet);
            }
          }
        }
      } catch (SQLException e) {
        terminal.writer().println("SQL Exception encountered: " + e.getMessage());
        terminal.writer().flush();
      } catch (Exception e) {
        e.printStackTrace(terminal.writer());
        terminal.writer().flush();
      }
    } else {
      return "Please select a datasource via ':datasources select {name}'.";
    }

    if (table != null && bindVariableName != null) {
      engine.put(bindVariableName, table);
      terminal.writer().println("Assigned resulting table to variable: " + bindVariableName);
      terminal.writer().flush();
    }

    return table;
  }

  /**
   * Replaces the old Swing JOptionPane and KeyListener with a native JLine 3 prompt.
   */
  private String promptForSQL(String dsName) {
    try {
      // Build a temporary LineReader just for the SQL prompt
      LineReader sqlReader = LineReaderBuilder.builder()
      .terminal(terminal)
      .build();

      // Load the specific SQL history for this datasource into JLine
      List<String> sqlHistory = getSQLHistory(dsName);
      if (sqlHistory != null) {
        for (String pastQuery : sqlHistory) {
          sqlReader.getHistory().add(pastQuery);
        }
      }

      // Prompt the user in the terminal (Up/Down arrows automatically cycle through the history we just added!)
      return sqlReader.readLine("SQL (" + dsName + ")> ");

    } catch (org.jline.reader.UserInterruptException | org.jline.reader.EndOfFileException e) {
      // User hit Ctrl+C or Ctrl+D to cancel the prompt
      return null;
    }
  }
}
