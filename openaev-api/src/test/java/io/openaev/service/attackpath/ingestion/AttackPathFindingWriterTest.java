package io.openaev.service.attackpath.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.service.attackpath.AttackPathIds;
import io.openaev.service.attackpath.ingestion.AttackPathFindingWriter.FindingRow;
import io.openaev.service.attackpath.ingestion.AttackPathFindingWriter.Link;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * The batched snapshot inserts are idempotent. A finding row is deduped on its natural key
 * (simulation, type, field, value, endpoint_key), not only on its id, so replaying a copy with a
 * different id resolution still writes no duplicate; a link is deduped on its composite key. Read
 * back through raw JDBC; not {@code @Transactional} (the inserts are asserted after they commit).
 */
@TestPropertySource(
    properties = {
      "openaev.enabled-dev-features=INJECT_CHAINING,ATTACK_PATH",
      "openaev.tenant.active-tables=attackpath_execution,attackpath_finding"
    })
@WithMockUser(isAdmin = true)
@DisplayName("attack path: the batched snapshot inserts are idempotent")
class AttackPathFindingWriterTest extends IntegrationTest {

  private static final String SIM = "SIM-WRITER-IDEMPOTENT";

  @Autowired private AttackPathFindingWriter findingWriter;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private Tenant tenant;

  @BeforeEach
  void setUp() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenant = tenantHelper.createTenantWithCurrentUser("ap-writer-idem");
    TenantContext.setCurrentTenant(tenant.getId());
  }

  @AfterEach
  void cleanUp() {
    jdbc.update(
        "DELETE FROM attackpath_execution WHERE attackpath_execution_simulation_id = ?", SIM);
    jdbc.update("DELETE FROM attackpath_finding WHERE attackpath_finding_simulation_id = ?", SIM);
    tenantHelper.deleteCommittedTenants(tenant.getId());
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("a finding row is deduped on its natural key, not only on its id")
  void findingInsertIsIdempotentOnTheNaturalKey() {
    String id = AttackPathIds.findingRow(SIM, "cve", "text_field", "CVE-1", "asset-1");
    insertFinding(id, "cve", "text_field", "CVE-1", "asset-1");
    assertThat(findingCount()).isEqualTo(1);

    // Same batch again: the id conflict is skipped.
    insertFinding(id, "cve", "text_field", "CVE-1", "asset-1");
    assertThat(findingCount()).isEqualTo(1);

    // Same natural key, a different id (a perturbed resolution): the natural-key index rejects it,
    // so no duplicate and the original row is kept.
    insertFinding("other-id", "cve", "text_field", "CVE-1", "asset-1");
    assertThat(findingCount()).isEqualTo(1);
    assertThat(theOnlyFindingId()).isEqualTo(id);
  }

  @Test
  @DisplayName("a link is deduped on its (execution, finding) composite key")
  void linkInsertIsIdempotent() {
    String findingId = AttackPathIds.findingRow(SIM, "cve", "text_field", "CVE-9", "asset-1");
    insertFinding(findingId, "cve", "text_field", "CVE-9", "asset-1");
    seedExecutionRow("exec-1");

    findingWriter.insertLinks(List.of(new Link("exec-1", findingId)));
    findingWriter.insertLinks(List.of(new Link("exec-1", findingId)));

    assertThat(linkCount("exec-1")).isEqualTo(1);
  }

  private void insertFinding(
      String id, String type, String field, String value, String endpointKey) {
    findingWriter.insertFindings(
        List.of(
            new FindingRow(
                id, tenant.getId(), SIM, type, field, value, endpointKey, null, endpointKey)));
  }

  private void seedExecutionRow(String executionId) {
    jdbc.update(
        "INSERT INTO attackpath_execution (attackpath_execution_id, tenant_id,"
            + " attackpath_execution_simulation_id, attackpath_execution_source_kind,"
            + " attackpath_execution_target_kind, attackpath_execution_target_key,"
            + " attackpath_execution_executed_at)"
            + " VALUES (?, ?, ?, 'INJECTOR', 'ASSET', 'asset-1', now())",
        executionId,
        tenant.getId(),
        SIM);
  }

  private Integer findingCount() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM attackpath_finding WHERE attackpath_finding_simulation_id = ?",
        Integer.class,
        SIM);
  }

  private String theOnlyFindingId() {
    return jdbc.queryForObject(
        "SELECT attackpath_finding_id FROM attackpath_finding"
            + " WHERE attackpath_finding_simulation_id = ?",
        String.class,
        SIM);
  }

  private Integer linkCount(String executionId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM attackpath_execution_finding WHERE execution_id = ?",
        Integer.class,
        executionId);
  }
}
