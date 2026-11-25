package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.migration.MigrationSessions.columnExists;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import java.sql.Connection;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The migration drops the two read-only marker columns and the coverage_scenario_read_only_first
 * tenant setting rows an instance that ran V6_20261121160000000 holds, and a rerun changes nothing.
 * Each run commits its transaction, as Flyway does, so the state is committed and removed after the
 * test.
 */
@DisplayName("Migration V6_20261124100000000: drop read-only marker and read-only-first setting")
class DropReadOnlyMarkerAndCoverageReadOnlyFirstMigrationTest extends IntegrationTest {

  private static final String SETTING_KEY = "coverage_scenario_read_only_first";
  private static final String KEPT_KEY = "asset_discovery_tag_migration_test";

  @Autowired
  private V6_20261124100000000__Drop_read_only_marker_and_coverage_read_only_first migration;

  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterEach
  void removeState() {
    jdbc.execute(
        "ALTER TABLE injectors_contracts DROP COLUMN IF EXISTS injector_contract_read_only");
    jdbc.execute("ALTER TABLE payloads DROP COLUMN IF EXISTS payload_read_only");
    jdbc.update("DELETE FROM parameters WHERE parameter_key IN (?, ?)", SETTING_KEY, KEPT_KEY);
  }

  @Test
  @DisplayName("Given the columns and the setting row should drop them, a rerun is a no-op")
  void given_columnsAndSettingRow_should_dropThem() throws Exception {
    // Arrange: the state V6_20261121160000000 left on a deployed instance
    jdbc.execute(
        "ALTER TABLE injectors_contracts"
            + " ADD COLUMN IF NOT EXISTS injector_contract_read_only BOOLEAN NOT NULL DEFAULT false");
    jdbc.execute(
        "ALTER TABLE payloads"
            + " ADD COLUMN IF NOT EXISTS payload_read_only BOOLEAN NOT NULL DEFAULT false");
    insertSetting(SETTING_KEY);
    String keptId = insertSetting(KEPT_KEY);

    // Act
    runMigration();
    runMigration();

    // Assert
    assertThat(columnExists(jdbc, "injectors_contracts", "injector_contract_read_only")).isFalse();
    assertThat(columnExists(jdbc, "payloads", "payload_read_only")).isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM parameters WHERE parameter_key = ?", Long.class, SETTING_KEY))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM parameters WHERE parameter_id = ?", Long.class, keptId))
        .isOne();
  }

  private String insertSetting(String key) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO parameters (parameter_id, parameter_key, parameter_value, tenant_id)"
            + " VALUES (?, ?, 'true', ?)",
        id,
        key,
        DEFAULT_TENANT_UUID);
    return id;
  }

  private void runMigration() throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      MigrationSessions.migrate(migration, connection);
    }
  }
}
