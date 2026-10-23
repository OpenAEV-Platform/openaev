package io.openaev.service.finding;

import static io.openaev.utils.SensitiveValueMaskingUtils.MASK;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies the delayed pass of {@link FindingValueDisplayBackfill}: the findings a pod of the
 * previous version inserts during a rolling deploy get their displayed value, in every tenant
 * (#7893). The fill itself (masking, unknown types, batches, rows already filled) is covered by
 * {@code AddFindingValueDisplayMigrationTest}. The pass is called directly: the test configuration
 * disables its delayed scheduling.
 *
 * <p>Deliberately NOT {@code @Transactional}: the pass takes its own connection from the pool, so
 * it only sees rows that are committed. Rows are seeded, read and cleaned through auto-committed
 * JDBC.
 */
@DisplayName("Finding value display backfill")
class FindingValueDisplayBackfillTest extends IntegrationTest {

  @Autowired private DataSource dataSource;
  @Autowired private FindingValueDisplayBackfill backfill;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;

  @BeforeEach
  void seedTwoTenants() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant("finding-display-a-" + UUID.randomUUID());
    tenantB = seedTenant("finding-display-b-" + UUID.randomUUID());
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM findings WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    jdbc.update("DELETE FROM injects WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    jdbc.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", tenantA, tenantB);
  }

  @Nested
  @DisplayName("When pods of the previous version inserted findings after the migration")
  class WhenOldPodsInsertedFindingsAfterTheMigration {

    @Test
    @DisplayName("Should fill the findings of every tenant and keep their tenant and cleartext")
    void given_findingsWithoutDisplayInTwoTenants_should_fillBoth() {
      // Arrange
      String findingA = insertFindingWithoutDisplay(tenantA, "admin:motdepasse");
      String findingB = insertFindingWithoutDisplay(tenantB, "jdoe:Sup3rS3cret");

      // Act
      backfill.fillNow();

      // Assert
      assertThat(columnOf(findingA, "finding_value_display")).isEqualTo("admin:mo" + MASK);
      assertThat(columnOf(findingB, "finding_value_display")).isEqualTo("jdoe:Su" + MASK);
      assertThat(columnOf(findingA, "finding_value")).isEqualTo("admin:motdepasse");
      assertThat(columnOf(findingA, "tenant_id")).isEqualTo(tenantA);
      assertThat(columnOf(findingB, "tenant_id")).isEqualTo(tenantB);
    }
  }

  // -- PRIVATE --

  private String seedTenant(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name);
    return id;
  }

  /** Inserts a Credentials finding the way a pod of the previous version does: no display. */
  private String insertFindingWithoutDisplay(String tenantId, String value) {
    String injectId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, inject_title, inject_created_at, inject_updated_at,"
            + " inject_depends_duration, inject_all_teams, inject_enabled, tenant_id)"
            + " VALUES (?, 'backfill inject', now(), now(), 0, false, true, ?)",
        injectId,
        tenantId);
    String findingId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO findings (finding_id, finding_field, finding_type, finding_value,"
            + " finding_inject_id, finding_created_at, finding_updated_at, tenant_id)"
            + " VALUES (?, 'field', 'Credentials', ?, ?, now(), now(), ?)",
        findingId,
        value,
        injectId,
        tenantId);
    return findingId;
  }

  private String columnOf(String findingId, String column) {
    return jdbc.queryForObject(
        "SELECT " + column + " FROM findings WHERE finding_id = ?", String.class, findingId);
  }
}
