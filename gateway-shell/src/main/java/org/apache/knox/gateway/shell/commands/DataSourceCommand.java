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
import org.apache.groovy.groovysh.Groovysh;

public class DataSourceCommand extends AbstractSQLCommandSupport {
  private static final String USAGE = ":ds (add|remove|select) [ds-name, connection-str, driver classname, authntype(none|basic)]";
  private static final String DESC = "Datasource management commands. Persisted datasources maintain connection details across sessions";

  public DataSourceCommand(Groovysh shell) {
    super(shell, ":datasources", ":ds", DESC, USAGE, DESC);
  }

  @SuppressWarnings({"unchecked", "PMD.CloseResource"})
  @Override
  public Object execute(List<String> args) {
    Map<String, KnoxDataSource> dataSources =
        getDataSources();
    if (args.isEmpty()) {
      args.add("list");
    }
    if (args.get(0).equalsIgnoreCase("add")) {
      KnoxDataSource ds = new KnoxDataSource(args.get(1),
          args.get(2),
          args.get(3),
          args.get(4));
      dataSources.put(ds.getName(), ds);
      getVariables().put(KNOXDATASOURCES, dataSources);
      persistDataSources();
    }
    else if (args.get(0).equalsIgnoreCase("remove")) {
      if (dataSources == null || dataSources.isEmpty()) {
        return "No datasources to remove.";
      }
      // if the removed datasource is currently selected, unselect it
      dataSources.remove(args.get(1));
      if (getVariables().get(KNOXDATASOURCE) != null) {
        if (args.get(1) != null) {
          if (((String)getVariables().get(KNOXDATASOURCE)).equals(args.get(1))) {
            System.out.println("unselecting datasource.");
            getVariables().put(KNOXDATASOURCE, "");
          }
        }
        else {
          System.out.println("Missing datasource name to remove.");
        }
      }
      getVariables().put(KNOXDATASOURCES, dataSources);
      persistDataSources();
    }
    else if (args.get(0).equalsIgnoreCase("list")) {
      // valid command no additional work needed though
    }
    else if(args.get(0).equalsIgnoreCase("select")) {
      if (dataSources == null || dataSources.isEmpty()) {
        return "No datasources to select from.";
      }
      KnoxDataSource dsValue = dataSources.get(args.get(1));
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
              e.printStackTrace();
              return "Error: Credential collection failure.";
            }
            username = dlg.name();
            pass = dlg.chars();
          }
          try {
            getConnection(dsValue, username, new String(pass));
          } catch (Exception e) {
            e.printStackTrace();
            return "Error: Connection creation failure.";
          }
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
      if (dataSources.containsKey(args.get(1))) {
        getVariables().put(KNOXDATASOURCE, args.get(1));
      }
      KnoxShellTable datasource = new KnoxShellTable();
      datasource.title("Knox DataSource Selected");
      datasource.header("Name").header("Connect String").header("Driver").header("Authn Type");
      datasource.row().value(dsValue.getName()).value(dsValue.getConnectStr()).value(dsValue.getDriver()).value(dsValue.getAuthnType());
      return datasource;
    }
    else {
      return "ERROR: unknown datasources command.";
    }

    return buildTable();
  }

  private KnoxShellTable buildTable() {
    KnoxShellTable datasource = new KnoxShellTable();
    datasource.title("Knox DataSources");
    datasource.header("Name").header("Connect String").header("Driver").header("Authn Type");
    @SuppressWarnings("unchecked")
    Map<String, KnoxDataSource> dataSources =
        (Map<String, KnoxDataSource>) getVariables().get(KNOXDATASOURCES);
    if (dataSources != null && !dataSources.isEmpty()) {
      for(KnoxDataSource dsValue : dataSources.values()) {
        datasource.row().value(dsValue.getName()).value(dsValue.getConnectStr()).value(dsValue.getDriver()).value(dsValue.getAuthnType());
      }
    }
    return datasource;
  }

  public static void main(String[] args) {
    DataSourceCommand cmd = new DataSourceCommand(new Groovysh());
    List<String> args2 = new ArrayList<>();
    cmd.execute(args2);
  }
}
