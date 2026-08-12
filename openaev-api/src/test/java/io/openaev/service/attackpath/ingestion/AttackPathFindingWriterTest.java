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
 * The batched snapshot inserts are idempotent. A finding row is deduped on its primary key, which
 * is a deterministic, injective encoding of its natural key (simulation, type, field, value,
 * endpoint_key; the value is hashed inside the id only when its raw encoding would overflow the
 * column), so re-copying the same finding always collides with itself; a link is deduped on its
 * composite key. What a replay does change is the row version, and only that: it is what lets a
 * re-discovered finding's new link reach a polling client. Read back through raw JDBC; not
 * {@code @Transactional} (the inserts are asserted after they commit).
 */
@TestPropertySource(
    properties = {
      "openaev.enabled-dev-features=ATTACK_PATH",
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
  @DisplayName("a finding row is deduped on its id (a deterministic encoding of its natural key)")
  void findingInsertIsIdempotentOnItsId() {
    String id = AttackPathIds.findingRow(SIM, "cve", "text_field", "CVE-1", "asset-1");
    insertFinding(id, "cve", "text_field", "CVE-1", "asset-1");
    assertThat(findingCount()).isEqualTo(1);

    // Same batch again: the PK conflict is skipped.
    insertFinding(id, "cve", "text_field", "CVE-1", "asset-1");
    assertThat(findingCount()).isEqualTo(1);

    // The same natural key always resolves to the same id (a deterministic encoding of it), so a
    // re-copy collides with itself on the PK — no duplicate, the original row is kept.
    String sameId = AttackPathIds.findingRow(SIM, "cve", "text_field", "CVE-1", "asset-1");
    assertThat(sameId).isEqualTo(id);
    insertFinding(sameId, "cve", "text_field", "CVE-1", "asset-1");
    assertThat(findingCount()).isEqualTo(1);
    assertThat(theOnlyFindingId()).isEqualTo(id);
  }

  @Test
  @DisplayName("a re-copied finding keeps its identity but takes the new row version")
  void aRecopiedFindingIsRestamped() {
    String id = AttackPathIds.findingRow(SIM, "cve", "text_field", "CVE-2", "asset-1");
    insertFinding(id, "cve", "text_field", "CVE-2", "asset-1", 3L);

    // A later execution rediscovers the same value: no new row, but the row must carry the new
    // version. Otherwise the link that copy adds next is unreachable by every delta — the link is
    // only ever read through its finding row.
    insertFinding(id, "cve", "text_field", "CVE-2", "asset-1", 7L);

    assertThat(findingCount()).isEqualTo(1);
    assertThat(rowVersion(id)).isEqualTo(7L);
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

  @Test
  @DisplayName("on a PK conflict, is_finding is never downgraded: a real finding wins")
  void isFindingIsNeverDowngradedOnConflict() {
    String id = AttackPathIds.findingRow(SIM, "port", "text_field", "22", "asset-1");
    // First seen as an output-only value...
    insertFinding(id, "port", "text_field", "22", "asset-1", 1L, false);
    assertThat(isFindingOf(id)).isFalse();

    // ...then re-copied as a real finding: the flag flips to true (true wins).
    insertFinding(id, "port", "text_field", "22", "asset-1", 2L, true);
    assertThat(findingCount()).isEqualTo(1);
    assertThat(isFindingOf(id)).isTrue();

    // A later output-only re-copy must NOT downgrade it back to false.
    insertFinding(id, "port", "text_field", "22", "asset-1", 3L, false);
    assertThat(isFindingOf(id)).isTrue();
  }

  @Test
  @DisplayName("a very long value inserts without overflowing the id, idempotently")
  void longValueInsertsAndIsIdempotent() {
    // ADR-004 lets arbitrarily long parsed outputs reach attackpath_finding. The value is hashed
    // inside the id, so the varchar(255) PK stays bounded and the value (text, un-indexed) is never
    // indexed — a 10 KB value must insert and dedup on the PK like any other.
    String longValue = "x".repeat(10_000);
    String id = AttackPathIds.findingRow(SIM, "output", "text_field", longValue, "asset-1");

    insertFinding(id, "output", "text_field", longValue, "asset-1");
    assertThat(findingCount()).isEqualTo(1);

    // Re-copied: same value -> same id, PK conflict skipped, no duplicate.
    insertFinding(id, "output", "text_field", longValue, "asset-1");
    assertThat(findingCount()).isEqualTo(1);
    assertThat(theOnlyFindingId()).isEqualTo(id);
  }

  private void insertFinding(
      String id, String type, String field, String value, String endpointKey) {
    insertFinding(id, type, field, value, endpointKey, 1L);
  }

  private void insertFinding(
      String id, String type, String field, String value, String endpointKey, long rowVersion) {
    insertFinding(id, type, field, value, endpointKey, rowVersion, true);
  }

  private void insertFinding(
      String id,
      String type,
      String field,
      String value,
      String endpointKey,
      long rowVersion,
      boolean isFinding) {
    findingWriter.insertFindings(
        List.of(
            new FindingRow(
                id,
                tenant.getId(),
                SIM,
                type,
                field,
                value,
                endpointKey,
                null,
                endpointKey,
                isFinding)),
        rowVersion);
  }

  private boolean isFindingOf(String findingId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT attackpath_finding_is_finding FROM attackpath_finding"
                + " WHERE attackpath_finding_id = ?",
            Boolean.class,
            findingId));
  }

  private long rowVersion(String findingId) {
    return jdbc.queryForObject(
        "SELECT attackpath_finding_row_version FROM attackpath_finding"
            + " WHERE attackpath_finding_id = ?",
        Long.class,
        findingId);
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
