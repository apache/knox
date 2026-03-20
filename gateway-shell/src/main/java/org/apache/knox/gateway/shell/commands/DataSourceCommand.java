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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.knox.gateway.shell.CredentialCollectionException;
import org.apache.knox.gateway.shell.CredentialCollector;
import org.apache.knox.gateway.shell.KnoxDataSource;
import org.apache.knox.gateway.shell.table.KnoxShellTable;

import org.apache.groovy.groovysh.jline.GroovyEngine;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class DataSourceCommand extends AbstractSQLCommandSupport {
  private static final String USAGE = ":ds (add|remove|list|select) [ds-name] [connection-str] [driver-classname] [authntype(none|basic)]";
  private static final String DESC = "Datasource management commands. Persisted datasources maintain connection details across sessions";

  public DataSourceCommand(GroovyEngine engine, Terminal terminal) {
    super(engine, terminal, ":datasources", ":ds", DESC, USAGE, DESC);
  }

  @SuppressWarnings({"PMD.CloseResource"})
  @Override
  public Object execute(List<String> args) {
    Map<String, KnoxDataSource> dataSources = getDataSources();

    String action = (args == null || args.isEmpty()) ? "list" : args.get(0);

    if ("add".equalsIgnoreCase(action)) {
      if (args.size() < 5) {
        terminal.writer().println("Error: Missing arguments for 'add'.");
        terminal.writer().println("Usage: :ds add ds-name connection-str driver-classname authntype");
        terminal.writer().flush();
        return null;
      }
      KnoxDataSource ds = new KnoxDataSource(args.get(1), args.get(2), args.get(3), args.get(4));
      dataSources.put(ds.getName(), ds);
      engine.put(KNOXDATASOURCES, dataSources);
      persistDataSources();
    }
    else if ("remove".equalsIgnoreCase(action)) {
      if (dataSources == null || dataSources.isEmpty()) {
        return "No datasources to remove.";
      }
      if (args.size() < 2) {
        terminal.writer().println("Error: Missing datasource name to remove.");
        terminal.writer().flush();
        return null;
      }

      String dsName = args.get(1);
      dataSources.remove(dsName);

      if (engine.get(KNOXDATASOURCE) != null) {
        if ((engine.get(KNOXDATASOURCE)).equals(dsName)) {
          terminal.writer().println("Unselecting datasource.");
          terminal.writer().flush();
          engine.put(KNOXDATASOURCE, "");
        }
      }
      engine.put(KNOXDATASOURCES, dataSources);
      persistDataSources();
    } else if ("list".equalsIgnoreCase(action)) {
      // valid command no additional work needed though
    } else if ("select".equalsIgnoreCase(action)) {
      if (dataSources == null || dataSources.isEmpty()) {
        return "No datasources to select from.";
      }
      if (args.size() < 2) {
        terminal.writer().println("Error: Missing datasource name to select.");
        terminal.writer().flush();
        return null;
      }

      KnoxDataSource dsValue = dataSources.get(args.get(1));
      if (dsValue == null) {
        return "Error: Datasource '" + args.get(1) + "' not found.";
      }

      Connection conn = getConnectionFromSession(dsValue);
      try {
        if (conn == null || conn.isClosed()) {
          String username = null;
          char[] pass = null;
          if (dsValue.getAuthnType().equalsIgnoreCase("basic")) {
            CredentialCollector dlg;
            try {
              dlg = login();
            } catch (CredentialCollectionException e) {
              terminal.writer().println("Error: Credential collection failure.");
              e.printStackTrace(terminal.writer());
              terminal.writer().flush();
              return null;
            }
            username = dlg.name();
            pass = dlg.chars();
          }
          try {
            String passStr = (pass == null) ? null : new String(pass);
            getConnection(dsValue, username, passStr);
          } catch (Exception e) {
            terminal.writer().println("Error: Connection creation failure.");
            e.printStackTrace(terminal.writer());
            terminal.writer().flush();
            return null;
          }
        }
      } catch (SQLException e) {
        e.printStackTrace(terminal.writer());
        terminal.writer().flush();
      }

      if (dataSources.containsKey(args.get(1))) {
        engine.put(KNOXDATASOURCE, args.get(1));
      }

      KnoxShellTable datasource = new KnoxShellTable();
      datasource.title("Knox DataSource Selected");
      datasource.header("Name").header("Connect String").header("Driver").header("Authn Type");
      datasource.row().value(dsValue.getName()).value(dsValue.getConnectStr()).value(dsValue.getDriver()).value(dsValue.getAuthnType());
      return datasource;
    } else {
      return "ERROR: unknown datasources command: " + action;
    }

    return buildTable();
  }

  private KnoxShellTable buildTable() {
    KnoxShellTable datasource = new KnoxShellTable();
    datasource.title("Knox DataSources");
    datasource.header("Name").header("Connect String").header("Driver").header("Authn Type");

    @SuppressWarnings("unchecked")
    Map<String, KnoxDataSource> dataSources =
    (Map<String, KnoxDataSource>) engine.get(KNOXDATASOURCES);

    if (dataSources != null && !dataSources.isEmpty()) {
      for (KnoxDataSource dsValue : dataSources.values()) {
        datasource.row().value(dsValue.getName()).value(dsValue.getConnectStr()).value(dsValue.getDriver()).value(dsValue.getAuthnType());
      }
    }
    return datasource;
  }

  public static void main(String[] args) {
    try {
      Terminal terminal = TerminalBuilder.builder().system(true).build();
      GroovyEngine engine = new GroovyEngine();
      DataSourceCommand cmd = new DataSourceCommand(engine, terminal);

      List<String> args2 = new ArrayList<>();
      Object res = cmd.execute(args2);
      if (res != null) {
        terminal.writer().println(res);
        terminal.writer().flush();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
