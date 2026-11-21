package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The asset software table: created with its unique identity per asset, and a rerun is a no-op.
 * Runs over the session connection, like the migration; the test transaction rolls back.
 */
@Transactional
@DisplayName("Migration V6_20261121160000000: asset software")
class AddAssetSoftwareMigrationTest extends IntegrationTest {

  @Autowired private V6_20261121160000000__Add_asset_software migration;

  @Test
  @DisplayName(
      "Given the migration run twice should leave one asset_software table, unique per key")
  void given_migrationRunTwice_should_beANoOp() {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try {
                migration.migrate(contextOn(connection));
                migration.migrate(contextOn(connection));
                assertThat(columnExists(connection, "asset_software_version_state")).isTrue();
                assertThat(columnExists(connection, "asset_software_parent_id")).isTrue();
                try (var statement =
                        connection.prepareStatement(
                            "SELECT count(*) FROM pg_constraint"
                                + " WHERE conname = 'asset_software_asset_key_unique'");
                    ResultSet rs = statement.executeQuery()) {
                  rs.next();
                  assertThat(rs.getInt(1)).isEqualTo(1);
                }
              } catch (RuntimeException e) {
                throw e;
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            });
  }

  private static boolean columnExists(Connection connection, String column) throws Exception {
    try (var statement =
        connection.prepareStatement(
            "SELECT count(*) FROM information_schema.columns"
                + " WHERE table_name = 'asset_software' AND column_name = ?")) {
      statement.setString(1, column);
      try (ResultSet rs = statement.executeQuery()) {
        rs.next();
        return rs.getInt(1) == 1;
      }
    }
  }

  private static Context contextOn(Connection connection) {
    return new Context() {
      @Override
      public Configuration getConfiguration() {
        return null;
      }

      @Override
      public Connection getConnection() {
        return connection;
      }
    };
  }
}
