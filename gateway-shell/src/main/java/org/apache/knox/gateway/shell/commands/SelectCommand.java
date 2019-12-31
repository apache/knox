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

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import org.apache.knox.gateway.shell.CredentialCollectionException;
import org.apache.knox.gateway.shell.KnoxDataSource;
import org.apache.knox.gateway.shell.table.KnoxShellTable;
import org.codehaus.groovy.tools.shell.Groovysh;

public class SelectCommand extends AbstractSQLCommandSupport {

  public SelectCommand(Groovysh shell) {
    super(shell, ":SQL", ":sql");
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object execute(List<String> args) {
    boolean ok = false;
    String sql = "";
    String bindVariableName = null;

    if (!args.isEmpty()) {
      bindVariableName = getBindingVariableNameForResultingTable(args);
    }

    String dsName = (String) getVariables().get(KNOXDATASOURCE);
    @SuppressWarnings("unchecked")
    Map<String, KnoxDataSource> dataSources =
        (Map<String, KnoxDataSource>) getVariables().get(KNOXDATASOURCES);
    KnoxDataSource ds = null;
    if (dsName == null || dsName.isEmpty()) {
      if (dataSources == null || dataSources.isEmpty()) {
        return "please configure a datasource with ':datasources add {name} {connectStr} {driver} {authntype: none|basic}'.";
      }
      if (dataSources.size() == 1) {
        dsName = (String) dataSources.keySet().toArray()[0];
      }
      else {
        return "mulitple datasources configured. please disambiguate with ':datasources select {name}'.";
      }
    }
    ds = dataSources.get(dsName);
    if (ds != null) {
      JLabel jl = new JLabel("Query: ");
      JTextArea sqlField = new JTextArea(5,40);
      sqlField.setLineWrap(true);
      JScrollPane scrollPane = new JScrollPane(sqlField);
      Box box = Box.createHorizontalBox();
      box.add(jl);
      box.add(scrollPane);

      // JDK-5018574 : Unable to set focus to another component in JOptionPane
      workAroundFocusIssue(sqlField);

      int x = JOptionPane.showConfirmDialog(null, box,
          "SQL Query Input", JOptionPane.OK_CANCEL_OPTION);

      if (x == JOptionPane.OK_OPTION) {
        ok = true;
        sql = sqlField.getText();
      }

      //KnoxShellTable.builder().jdbc().connect("jdbc:derby:codejava/webdb1").driver("org.apache.derby.jdbc.EmbeddedDriver").username("lmccay").pwd("xxxx").sql("SELECT * FROM book");
      try {
        if (ok) {
          Connection conn = getConnection(ds);
          KnoxShellTable table = KnoxShellTable.builder().jdbc().connection(conn).sql(sql);
          conn.close();
          if (bindVariableName != null) {
            getVariables().put(bindVariableName, table);
          }
          return table;
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    else {
      return "please select a datasource via ':datasources select {name}'.";
    }
    return null;
  }

  private Connection getConnection(KnoxDataSource ds)
      throws CredentialCollectionException, SQLException, Exception {
    Connection conn = getConnectionFromSession(ds);
    if (ds.getAuthnType().equalsIgnoreCase("none")) {
      if (conn == null || conn.isClosed()) {
        conn = getConnection(ds, null, null);
      }
    }
    else if (ds.getAuthnType().equalsIgnoreCase("basic")) {
      if (conn == null || conn.isClosed()) {
        KnoxLoginDialog dlg = new KnoxLoginDialog();
        dlg.collect();
        if (dlg.ok) {
          String u = dlg.username;
          String p = new String(dlg.pass);
          if (u == null || u.isEmpty() || p.isEmpty()) {
            throw new CredentialCollectionException(
                "selected datasource requires username and password.");
          }
          conn = getConnection(ds, u, p);
        }
      }
    }
    return conn;
  }

  public static void main(String[] args) {
    SelectCommand cmd = new SelectCommand(new Groovysh());
    List<String> args2 = new ArrayList<>();
    cmd.execute(args2);
  }
}
