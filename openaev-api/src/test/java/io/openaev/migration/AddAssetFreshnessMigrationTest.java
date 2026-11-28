package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.migration.MigrationSessions.contextOn;
import static io.openaev.migration.MigrationSessions.withConnection;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The freshness migration adds its columns, backfills an unstamped asset from the latest heartbeat
 * of its agents, and a rerun changes nothing. Rows are seeded and read over the session connection,
 * like the migration runs; the test transaction ({@code @Transactional}) rolls them back.
 */
@Transactional
@DisplayName("Migration V6_20261121170000000: add asset freshness")
class AddAssetFreshnessMigrationTest extends IntegrationTest {

  private static final Instant HEARTBEAT = Instant.parse("2026-11-20T08:00:00Z");

  @Autowired private V6_20261121170000000__Add_asset_freshness migration;

  @Test
  @DisplayName("Given an asset with an agent should backfill its last seen, a rerun is a no-op")
  void given_assetWithAgent_should_backfillLastSeen() {
    // Arrange
    String assetId = UUID.randomUUID().toString();
    execute(
        "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at, asset_updated_at,"
            + " tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
            + " VALUES ('%s', 'freshness-mig', 'Endpoint', now(), now(), '%s', 'freshness-mig',"
            + " 'Linux', 'x86_64')",
        assetId, DEFAULT_TENANT_UUID);
    withConnection(
        entityManager,
        connection -> {
          try (var insert =
              connection.prepareStatement(
                  "INSERT INTO agents (agent_id, agent_asset, agent_privilege,"
                      + " agent_deployment_mode, agent_executed_by_user, agent_status,"
                      + " agent_last_seen, agent_created_at, agent_updated_at, tenant_id)"
                      + " VALUES (?, ?, 'admin', 'service', 'root', 'ACTIVE', ?, now(), now(),"
                      + " ?)")) {
            insert.setString(1, UUID.randomUUID().toString());
            insert.setString(2, assetId);
            insert.setTimestamp(3, Timestamp.from(HEARTBEAT));
            insert.setString(4, DEFAULT_TENANT_UUID);
            insert.executeUpdate();
          }
        });
    execute("UPDATE assets SET asset_last_seen = NULL WHERE asset_id = '%s'", assetId);

    // Act
    runMigration();
    runMigration();

    // Assert
    withConnection(
        entityManager,
        connection -> {
          try (var read =
              connection.prepareStatement(
                  "SELECT asset_last_seen, asset_last_seen_source FROM assets"
                      + " WHERE asset_id = ?")) {
            read.setString(1, assetId);
            try (var row = read.executeQuery()) {
              assertThat(row.next()).isTrue();
              assertThat(row.getTimestamp(1).toInstant()).isEqualTo(HEARTBEAT);
              assertThat(row.getString(2)).isEqualTo("agent");
            }
          }
        });
  }

  private void runMigration() {
    withConnection(entityManager, connection -> migration.migrate(contextOn(connection)));
  }

  private void execute(String sql, Object... args) {
    withConnection(
        entityManager,
        connection -> {
          try (var statement = connection.createStatement()) {
            statement.execute(sql.formatted(args));
          }
        });
  }
}
