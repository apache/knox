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
package org.apache.knox.gateway.util;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.database.DataSourceProvider;
import org.apache.knox.gateway.services.security.AliasService;

/**
 * Copies Knox Tokens from a legacy embedded Apache Derby database into the currently configured
 * JDBC token backend (the embedded H2 database by default).
 * <p>
 * Knox no longer ships the Apache Derby JDBC driver, so this tool reaches Derby purely through
 * reflection ({@code Class.forName("org.apache.derby.jdbc.EmbeddedDriver")}) plus {@code java.sql.*}.
 * The operator supplies a Derby driver jar (e.g. {@code derby-10.14.2.0.jar}, the last version Knox
 * shipped) by dropping it into {@code $KNOX_GATEWAY_HOME/ext/}, which is already on the KnoxCLI
 * classpath. This keeps gateway-server free of any compile- or runtime Derby dependency.
 * <p>
 * The migration scope is tokens only ({@code KNOX_TOKENS} and {@code KNOX_TOKEN_METADATA}): those
 * were the only tables persisted by every Derby-shipping Knox release. Rows are copied verbatim so
 * absolute {@code max_lifetime}/{@code expiration} timestamps and Base64-encoded passcodes are
 * preserved exactly. The copy is idempotent: token IDs already present in the destination are
 * skipped, so the tool is safe to re-run.
 */
public class EmbeddedDerbyToH2TokenMigrationTool {

  static final String DERBY_DRIVER_CLASS = "org.apache.derby.jdbc.EmbeddedDriver";

  /** The legacy embedded Derby database lived in the {@code tokens} folder under the security dir. */
  static final String LEGACY_DERBY_DB_FOLDER = "tokens";

  private static final String SELECT_TOKEN_IDS_SQL = "SELECT token_id FROM KNOX_TOKENS";
  private static final String SELECT_TOKENS_SQL = "SELECT token_id, issue_time, expiration, max_lifetime FROM KNOX_TOKENS";
  private static final String INSERT_TOKEN_SQL = "INSERT INTO KNOX_TOKENS(token_id, issue_time, expiration, max_lifetime) VALUES(?, ?, ?, ?)";
  private static final String SELECT_METADATA_SQL = "SELECT token_id, md_name, md_value FROM KNOX_TOKEN_METADATA";
  private static final String INSERT_METADATA_SQL = "INSERT INTO KNOX_TOKEN_METADATA(token_id, md_name, md_value) VALUES(?, ?, ?)";

  private final GatewayConfig config;
  private final AliasService aliasService;
  private final PrintStream out;

  private String derbyDatabasePath;
  private boolean verbose;

  public EmbeddedDerbyToH2TokenMigrationTool(GatewayConfig config, AliasService aliasService, PrintStream out) {
    this.config = config;
    this.aliasService = aliasService;
    this.out = out;
  }

  /**
   * Overrides the source Derby database location. When unset the tool defaults to the legacy
   * {@code ${securityDir}/tokens} folder.
   */
  public void setDerbyDatabasePath(String derbyDatabasePath) {
    this.derbyDatabasePath = derbyDatabasePath;
  }

  public void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }

  /**
   * Runs the migration.
   *
   * @return {@code true} if the migration completed (or there was nothing to migrate), {@code false}
   *         if it could not run (e.g. the Derby driver is not on the classpath).
   */
  public boolean migrate() {
    final String sourcePath = resolveDerbyDatabasePath();
    if (!Files.isDirectory(Paths.get(sourcePath))) {
      log("No legacy Derby database found at " + sourcePath + "; nothing to migrate.");
      return true;
    }

    try {
      Class.forName(DERBY_DRIVER_CLASS);
    } catch (ClassNotFoundException e) {
      log("Apache Derby JDBC driver (" + DERBY_DRIVER_CLASS + ") was not found on the classpath.");
      log("Copy a Derby driver jar (e.g. derby-10.14.2.0.jar) into $KNOX_GATEWAY_HOME/ext/ and re-run this command.");
      return false;
    }

    final String derbyUrl = "jdbc:derby:" + sourcePath;
    log("Migrating tokens from the legacy Derby database at " + sourcePath + " into the configured token backend...");
    try {
      final DataSource destinationDataSource = DataSourceProvider.getDataSource(config, aliasService);
      try (Connection source = DriverManager.getConnection(derbyUrl);
          Connection destination = destinationDataSource.getConnection()) {
        final MigrationResult result = copyTokens(source, destination, verbose);
        log(result.toString());
      }
      return true;
    } catch (Exception e) {
      throw new RuntimeException("Error while migrating tokens from the legacy Derby database: " + e.getMessage(), e);
    } finally {
      shutdownDerby(derbyUrl);
    }
  }

  private String resolveDerbyDatabasePath() {
    return derbyDatabasePath != null ? derbyDatabasePath : Paths.get(config.getGatewaySecurityDir(), LEGACY_DERBY_DB_FOLDER).toString();
  }

  /*
   * Derby signals a successful embedded-database shutdown by throwing a SQLException (SQLState
   * 08006). Anything else is unexpected but non-fatal at this point since the data was already
   * copied, so we only log it in verbose mode.
   */
  private void shutdownDerby(String derbyUrl) {
    try {
      DriverManager.getConnection(derbyUrl + ";shutdown=true");
    } catch (SQLException expected) {
      if (verbose) {
        log("Derby database shut down (" + expected.getMessage() + ").");
      }
    }
  }

  /**
   * Copies the {@code KNOX_TOKENS} and {@code KNOX_TOKEN_METADATA} rows from {@code source} into
   * {@code destination}. Token IDs already present in the destination are skipped so the copy is
   * idempotent. Package-visible so it can be exercised directly by unit tests with any two JDBC
   * databases sharing the Knox token schema.
   */
  MigrationResult copyTokens(Connection source, Connection destination, boolean verboseOutput) throws SQLException {
    final Set<String> migratedTokenIds = new HashSet<>();

    final boolean autoCommit = destination.getAutoCommit();
    destination.setAutoCommit(false);
    try {
      final int skippedTokens = insertTokens(source, destination, migratedTokenIds, verboseOutput);
      final int migratedMetadataRows = insertTokenMetadata(source, destination, migratedTokenIds);
      destination.commit();
      return new MigrationResult(migratedTokenIds.size(), skippedTokens, migratedMetadataRows);
    } catch (SQLException e) {
      destination.rollback();
      throw e;
    } finally {
      destination.setAutoCommit(autoCommit);
    }
  }

  /**
   * Copies the {@code KNOX_TOKENS} rows that are not yet present in the destination, collecting the
   * migrated token IDs into {@code migratedTokenIds}. Returns the number of tokens skipped because
   * they were already present in the destination.
   */
  private int insertTokens(Connection source, Connection destination, Set<String> migratedTokenIds, boolean verboseOutput) throws SQLException {
    final Set<String> existingTokenIds = loadExistingTokenIds(destination);
    int skippedTokens = 0;
    try (PreparedStatement insertToken = destination.prepareStatement(INSERT_TOKEN_SQL);
        PreparedStatement selectTokens = source.prepareStatement(SELECT_TOKENS_SQL);
        ResultSet tokens = selectTokens.executeQuery()) {
      while (tokens.next()) {
        final String tokenId = tokens.getString(1);
        if (existingTokenIds.contains(tokenId)) {
          skippedTokens++;
          if (verboseOutput) {
            log("Skipping token " + Tokens.getTokenIDDisplayText(tokenId) + "; already present in the destination.");
          }
          continue;
        }
        insertToken.setString(1, tokenId);
        insertToken.setLong(2, tokens.getLong(2));
        insertToken.setLong(3, tokens.getLong(3));
        insertToken.setLong(4, tokens.getLong(4));
        insertToken.addBatch();
        migratedTokenIds.add(tokenId);
        if (verboseOutput) {
          log("Migrating token " + Tokens.getTokenIDDisplayText(tokenId) + ".");
        }
      }
      insertToken.executeBatch();
    }
    return skippedTokens;
  }

  /**
   * Copies the {@code KNOX_TOKEN_METADATA} rows belonging to the freshly-inserted tokens in
   * {@code migratedTokenIds}. Metadata for tokens that were already present in the destination is
   * left alone (it was migrated on a previous run). Returns the number of metadata rows migrated.
   */
  private int insertTokenMetadata(Connection source, Connection destination, Set<String> migratedTokenIds) throws SQLException {
    int migratedMetadataRows = 0;
    try (PreparedStatement insertMetadata = destination.prepareStatement(INSERT_METADATA_SQL);
        PreparedStatement selectMetadata = source.prepareStatement(SELECT_METADATA_SQL);
        ResultSet metadata = selectMetadata.executeQuery()) {
      while (metadata.next()) {
        final String tokenId = metadata.getString(1);
        if (!migratedTokenIds.contains(tokenId)) {
          continue;
        }
        insertMetadata.setString(1, tokenId);
        insertMetadata.setString(2, metadata.getString(2));
        insertMetadata.setString(3, metadata.getString(3));
        insertMetadata.addBatch();
        migratedMetadataRows++;
      }
      insertMetadata.executeBatch();
    }
    return migratedMetadataRows;
  }

  private Set<String> loadExistingTokenIds(Connection destination) throws SQLException {
    final Set<String> tokenIds = new HashSet<>();
    try (PreparedStatement statement = destination.prepareStatement(SELECT_TOKEN_IDS_SQL); ResultSet rs = statement.executeQuery()) {
      while (rs.next()) {
        tokenIds.add(rs.getString(1));
      }
    }
    return tokenIds;
  }

  private void log(String message) {
    if (out != null) {
      out.println(message);
    }
  }

  /** Summary of a single {@link #copyTokens(Connection, Connection, boolean)} invocation. */
  static final class MigrationResult {
    final int migratedTokens;
    final int skippedTokens;
    final int migratedMetadataRows;

    MigrationResult(int migratedTokens, int skippedTokens, int migratedMetadataRows) {
      this.migratedTokens = migratedTokens;
      this.skippedTokens = skippedTokens;
      this.migratedMetadataRows = migratedMetadataRows;
    }

    @Override
    public String toString() {
      return "Migration finished. Tokens migrated: " + migratedTokens + ", tokens skipped (already present): " + skippedTokens
          + ", metadata rows migrated: " + migratedMetadataRows + ".";
    }
  }
}
