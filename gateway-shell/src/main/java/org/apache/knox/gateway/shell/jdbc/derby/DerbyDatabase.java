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
package org.apache.knox.gateway.shell.jdbc.derby;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.shell.jdbc.Database;

public class DerbyDatabase implements Database {

  public static final String EMBEDDED_DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";
  public static final String NETWORK_SERVER_DRIVER = "org.apache.derby.jdbc.ClientDriver";
  public static final String PROTOCOL = "jdbc:derby:";
  private static final String CREATE_ATTRIBUTE = ";create=true";
  private static final String SHUTDOWN_ATTRIBUTE = ";shutdown=true";
  private static final String DEFAULT_SCHEMA_NAME = "APP";

  private final String dbUri;

  /**
   * Constructor
   *
   * @param directory
   *          the directory where the database should be placed
   * @throws DerbyDatabaseException
   *           if the database engine can not be load for some reasons
   */
  public DerbyDatabase(String directory) throws DerbyDatabaseException {
    this(directory, false);
  }

  public DerbyDatabase(String directory, boolean networkServer) throws DerbyDatabaseException {
    this.dbUri = PROTOCOL + (networkServer ? "//localhost:1527/" : "") + directory;
    loadDriver(networkServer);
  }

  @Override
  public void create() throws SQLException {
    Connection conn = null;
    try {
      conn = DriverManager.getConnection(dbUri + CREATE_ATTRIBUTE);
    } finally {
      if (conn != null) {
        conn.close();
      }
    }
  }

  @Override
  public void shutdown() {
    try {
      DriverManager.getConnection(dbUri + SHUTDOWN_ATTRIBUTE);
    } catch (SQLException e) {
      // See http://db.apache.org/derby/docs/dev/getstart/rwwdactivity3.html and check
      // out 'Shut down the database' for an
      // explanation of this check
      if (((e.getErrorCode() == 45000) && ("08006".equals(e.getSQLState())))) {
        // We got the expected exception
        // Note that for single database shutdown, the expected
        // SQL state is "08006", and the error code is 45000.
        log(MessageLevel.INFO, "Derby database is closed");
      } else {
        // if the error code or SQLState is different, we have
        // an unexpected exception (shutdown failed)
        log(MessageLevel.WARN, "Derby database closed abnormally", e);
      }
    }
  }

  @Override
  public Connection getConnection() throws SQLException {
    return DriverManager.getConnection(dbUri);
  }

  @Override
  public boolean hasTable(String tableName) throws SQLException {
    return hasTable(DEFAULT_SCHEMA_NAME, tableName);
  }

  @Override
  public boolean hasTable(String schemaName, String tableName) throws SQLException {
    boolean result = false;
    try (Connection connection = getConnection();
        ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), schemaName, tableName, null)) {
      result = tables.next();
    } catch (SQLException e) {
      log(MessageLevel.ERROR, "SQL error occured while checking table " + tableName + " in the database", e);
    }

    return result;
  }

  private void loadDriver(boolean networkServer) throws DerbyDatabaseException {
    final String driverToLoad = networkServer ? NETWORK_SERVER_DRIVER : EMBEDDED_DRIVER;
    try {
      Class.forName(driverToLoad).newInstance();
    } catch (ClassNotFoundException e) {
      throw new DerbyDatabaseException("Unable to load the JDBC driver " + driverToLoad + ". Check your CLASSPATH.", e);
    } catch (InstantiationException e) {
      throw new DerbyDatabaseException("Unable to instantiate the JDBC driver " + driverToLoad, e);
    } catch (IllegalAccessException e) {
      throw new DerbyDatabaseException("Not allowed to access the JDBC driver " + driverToLoad, e);
    }
  }

  private void log(MessageLevel logLevel, String message) {
    log(logLevel, message, null);
  }

  // being a test only class we now log into the STDOUT for now
  private void log(MessageLevel logLevel, String message, Throwable error) {
    System.out.println(logLevel.name() + " - " + message + (error == null ? "" : " - caused by " + error));
  }
}
