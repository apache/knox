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

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.apache.knox.gateway.shell.CredentialCollector;
import org.apache.knox.gateway.shell.KnoxDataSource;
import org.apache.knox.gateway.shell.table.KnoxShellTable;
import org.apache.groovy.groovysh.Groovysh;

public class SelectCommand extends AbstractSQLCommandSupport implements KeyListener {
  private static final String USAGE = ":sql [assign resulting-variable-name]";
  private static final String DESC = "Build table from SQL ResultSet";

  private static final String KNOXDATASOURCE = "__knoxdatasource";
  private JTextArea sqlField;
  private List<String> sqlHistory;
  private int historyIndex = -1;

  public SelectCommand(Groovysh shell) {
    super(shell, ":SQL", ":sql", DESC, USAGE, DESC);
  }

  @Override
  public void keyPressed(KeyEvent event) {
    int code = event.getKeyCode();
    boolean setFromHistory = false;
    if (sqlHistory != null && !sqlHistory.isEmpty()) {
      if (historyIndex == -1) {
        historyIndex = sqlHistory.size() + 1;
      }
      if (code == KeyEvent.VK_KP_UP ||
          code == KeyEvent.VK_UP) {
        if (historyIndex > 0) {
          historyIndex -= 1;
        }
        setFromHistory = true;
      }
      else if (code == KeyEvent.VK_KP_DOWN ||
          code == KeyEvent.VK_DOWN) {
        if (historyIndex < sqlHistory.size() - 1) {
          historyIndex += 1;
          setFromHistory = true;
        }
      }
      if (setFromHistory) {
        sqlField.setText(sqlHistory.get(historyIndex));
        sqlField.invalidate();
      }
    }
  }

  @Override
  public void keyReleased(KeyEvent event) {
  }

  @Override
  public void keyTyped(KeyEvent event) {
  }

  @SuppressWarnings({"unchecked", "PMD.CloseResource"})
  @Override
  public Object execute(List<String> args) {
    boolean ok = false;
    String sql = "";
    String bindVariableName = null;
    KnoxShellTable table = null;

    if (!args.isEmpty()) {
      bindVariableName = getBindingVariableNameForResultingTable(args);
    }

    String dsName = (String) getVariables().get(KNOXDATASOURCE);
    @SuppressWarnings("unchecked")
    Map<String, KnoxDataSource> dataSources = getDataSources();
    KnoxDataSource ds = null;
    if (dsName == null || dsName.isEmpty()) {
      if (dataSources == null || dataSources.isEmpty()) {
        return "please configure a datasource with ':datasources add {name} {connectStr} {driver} {authntype: none|basic}'.";
      }
      else if (dataSources.size() == 1) {
        dsName = (String) dataSources.keySet().toArray()[0];
      }
      else {
        return "mulitple datasources configured. please disambiguate with ':datasources select {name}'.";
      }
    }

    sqlHistory = getSQLHistory(dsName);
    historyIndex = (sqlHistory != null && !sqlHistory.isEmpty()) ? sqlHistory.size() - 1 : -1;

    ds = dataSources.get(dsName);
    if (ds != null) {
      JLabel jl = new JLabel("Query: ");
      sqlField = new JTextArea(5,40);
      sqlField.addKeyListener(this);
      sqlField.setLineWrap(true);
      JScrollPane scrollPane = new JScrollPane(sqlField);
      Box box = Box.createHorizontalBox();
      box.add(jl);
      box.add(scrollPane);

      // JDK-5018574 : Unable to set focus to another component in JOptionPane
      SwingUtils.workAroundFocusIssue(sqlField);

      int x = JOptionPane.showConfirmDialog(null, box,
          "SQL Query Input", JOptionPane.OK_CANCEL_OPTION);

      if (x == JOptionPane.OK_OPTION) {
        ok = true;
        sql = sqlField.getText();
        addToSQLHistory(dsName, sql);
        historyIndex = -1;
      }

      //KnoxShellTable.builder().jdbc().connect("jdbc:derby:codejava/webdb1").driver("org.apache.derby.jdbc.EmbeddedDriver").username("lmccay").pwd("xxxx").sql("SELECT * FROM book");
      try {
        if (ok) {
          System.out.println(sql);
          try {
            Connection conn = getConnectionFromSession(ds);
            if (conn == null || conn.isClosed()) {
              String username = null;
              char[] pass = null;
              if (ds.getAuthnType().equalsIgnoreCase("basic")) {
                CredentialCollector dlg = login();
                username = dlg.name();
                pass = dlg.chars();
              }
              conn = getConnection(ds, username, new String(pass));
            }
            try (Statement statement = conn.createStatement()) {
              if (statement.execute(sql)) {
                try (ResultSet resultSet = statement.getResultSet()) {
                  table = KnoxShellTable.builder().jdbc().resultSet(resultSet);
                }
              }
            }
          }
          catch (SQLException e) {
            System.out.println("SQL Exception encountered... " + e.getMessage());
          }
        }
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
    else {
      return "please select a datasource via ':datasources select {name}'.";
    }
    if (table != null && bindVariableName != null) {
      getVariables().put(bindVariableName, table);
    }
    return table;
  }
}
