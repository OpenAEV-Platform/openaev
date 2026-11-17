package io.openaev.service.finding;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Finding;
import io.openaev.database.repository.FindingOccurrenceRepository;
import io.openaev.database.repository.FindingRepository;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * finding_occurrences has no tenant of its own, it is scoped through its finding (#7848): under one
 * tenant's scope, the occurrence of another tenant's finding is never read nor written. findings is
 * v2-active, as in production.
 *
 * <p>Not {@code @Transactional}: the work runs through {@code TenantScopedTransaction}, which
 * refuses to open inside an active transaction. Rows are seeded, read and cleaned through
 * auto-committed JDBC, so the statement inspector applies to the service calls only.
 */
@DisplayName("Finding occurrence tenant isolation")
class FindingOccurrenceTenantIsolationTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private DataSource dataSource;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private FindingOccurrenceService findingOccurrenceService;
  @Autowired private FindingRepository findingRepository;
  @Autowired private FindingOccurrenceRepository findingOccurrenceRepository;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;
  private String injectA;
  private String injectB;

  @BeforeEach
  void seedTwoTenants() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant("occurrence-isolation-a-");
    tenantB = seedTenant("occurrence-isolation-b-");
    injectA = seedInject(tenantA);
    injectB = seedInject(tenantB);
  }

  @AfterEach
  void cleanup() {
    for (String tenantId : new String[] {tenantA, tenantB}) {
      jdbc.update("DELETE FROM findings WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM injects WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    }
  }

  @Nested
  @DisplayName("When reading an occurrence")
  class WhenReadingAnOccurrence {

    @Test
    @DisplayName("Should read the occurrence of its own tenant")
    void given_anOccurrenceOfTheScopedTenant_should_readIt() {
      // Arrange
      String findingA = seedFindingWithOccurrence(tenantA, injectA);

      // Act
      boolean found =
          tenantTx.execute(
              TxCtx.forTenant(tenantA),
              () -> findingOccurrenceService.occurrence(findingA, injectA).isPresent());

      // Assert
      assertThat(found).isTrue();
    }

    @Test
    @DisplayName("Should not read the occurrence of another tenant")
    void given_anOccurrenceOfAnotherTenant_should_notReadIt() {
      // Arrange
      String findingB = seedFindingWithOccurrence(tenantB, injectB);

      // Act
      boolean found =
          tenantTx.execute(
              TxCtx.forTenant(tenantA),
              () -> findingOccurrenceService.occurrence(findingB, injectB).isPresent());

      // Assert
      assertThat(found).isFalse();
    }
  }

  @Nested
  @DisplayName("When recording occurrences")
  class WhenRecordingOccurrences {

    @Test
    @DisplayName("Should record the occurrence of a finding of its own tenant")
    void given_aFindingOfTheScopedTenant_should_recordItsOccurrence() {
      // Arrange
      String findingA = seedFinding(tenantA, injectA);

      // Act
      tenantTx.execute(
          TxCtx.forTenant(tenantA),
          () -> {
            Finding finding = findingRepository.findById(findingA).orElseThrow();
            findingOccurrenceService.recordOccurrencesByInject(
                finding.getInject(), List.of(finding));
          });

      // Assert
      assertThat(occurrenceCount(findingA)).isEqualTo(1);
    }

    @Test
    @DisplayName("Should not read the recorded occurrences of another tenant")
    void given_occurrencesOfAnotherTenant_should_notReadThem() {
      // Arrange
      String findingB = seedFindingWithOccurrence(tenantB, injectB);

      // Act
      boolean found =
          tenantTx.execute(
              TxCtx.forTenant(tenantA),
              () ->
                  !findingOccurrenceRepository
                      .findFindingIdsWithOccurrenceOnInject(injectB, List.of(findingB))
                      .isEmpty());

      // Assert
      assertThat(found).isFalse();
    }
  }

  // -- PRIVATE --

  private String seedTenant(String prefix) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        prefix + id);
    return id;
  }

  private String seedInject(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, inject_title, inject_created_at, inject_updated_at,"
            + " inject_depends_duration, inject_all_teams, inject_enabled, tenant_id)"
            + " VALUES (?, 'isolation inject', now(), now(), 0, false, true, ?)",
        id,
        tenantId);
    return id;
  }

  private String seedFinding(String tenantId, String injectId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO findings (finding_id, finding_field, finding_type, finding_value,"
            + " finding_inject_id, finding_created_at, finding_updated_at, tenant_id)"
            + " VALUES (?, 'field', 'Text', ?, ?, now(), now(), ?)",
        id,
        "isolation-" + id,
        injectId,
        tenantId);
    return id;
  }

  private String seedFindingWithOccurrence(String tenantId, String injectId) {
    String findingId = seedFinding(tenantId, injectId);
    jdbc.update(
        "INSERT INTO finding_occurrences (finding_occurrence_id, finding_occurrence_finding_id,"
            + " finding_occurrence_inject_id, finding_occurrence_date, finding_occurrence_field)"
            + " VALUES (?, ?, ?, now(), 'field')",
        UUID.randomUUID().toString(),
        findingId,
        injectId);
    return findingId;
  }

  private int occurrenceCount(String findingId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM finding_occurrences WHERE finding_occurrence_finding_id = ?",
        Integer.class,
        findingId);
  }
}
