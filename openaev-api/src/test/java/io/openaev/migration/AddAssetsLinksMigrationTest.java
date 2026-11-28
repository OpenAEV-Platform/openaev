package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.migration.MigrationSessions.contextOn;
import static io.openaev.migration.MigrationSessions.withConnection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Migration V6_20261118230000000: assets links")
class AddAssetsLinksMigrationTest extends IntegrationTest {

  @Autowired private V6_20261118230000000__Add_assets_links migration;

  private final String hostId = UUID.randomUUID().toString();
  private final String accountId = UUID.randomUUID().toString();

  // Puts the schema back as it was before the migration, inside the test transaction.
  @BeforeEach
  void seedTwoAssetsBeforeLinks() {
    execute("DROP TABLE IF EXISTS assets_links");
    insertAsset(hostId, "migration-host");
    insertAsset(accountId, "migration-account");
  }

  @Test
  @DisplayName("Creates the table once, a rerun is a no-op, and a link follows its assets")
  void given_twoAssets_should_storeOneLinkAndCascadeItsDeletion() {
    // Act
    runMigration();
    runMigration();
    execute(
        "INSERT INTO assets_links (asset_id, linked_asset_id, asset_link_type)"
            + " VALUES ('%s', '%s', 'LOGGED_ON')",
        accountId, hostId);

    // Assert
    assertThat(
            rows(
                "SELECT asset_link_type FROM assets_links WHERE asset_id = '%s'"
                    + " AND linked_asset_id = '%s'",
                accountId, hostId))
        .containsExactly("LOGGED_ON");
    execute("DELETE FROM assets WHERE asset_id = '%s'", hostId);
    assertThat(rows("SELECT asset_link_type FROM assets_links WHERE asset_id = '%s'", accountId))
        .isEmpty();
  }

  @Test
  @DisplayName("Refuses a link from an asset to itself")
  void given_selfLink_should_beRefused() {
    // Act
    runMigration();

    // Assert
    assertThatThrownBy(
            () ->
                execute(
                    "INSERT INTO assets_links (asset_id, linked_asset_id, asset_link_type)"
                        + " VALUES ('%s', '%s', 'USES')",
                    hostId, hostId))
        .hasMessageContaining("assets_links_not_self");
  }

  private void insertAsset(String id, String name) {
    execute(
        "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at, asset_updated_at,"
            + " tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
            + " VALUES ('%s', '%s', 'Endpoint', now(), now(), '%s', '%s', 'Linux', 'x86_64')",
        id, name, DEFAULT_TENANT_UUID, name);
  }

  private void runMigration() {
    withConnection(entityManager, connection -> migration.migrate(contextOn(connection)));
  }

  @SuppressWarnings("unchecked")
  private List<String> rows(String sql, Object... args) {
    return entityManager.createNativeQuery(sql.formatted(args)).getResultList();
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
