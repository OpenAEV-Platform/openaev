package io.openaev.rest.finding;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TxCtx;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
 * Every current finding write path records one occurrence per written row (#7848), built from the
 * row: its inject, the inject's sent date and its field. A repeated detection adds none.
 *
 * <p>Not {@code @Transactional}: {@code FindingWriter} opens its own {@code REQUIRES_NEW}
 * transaction, which would not see rows seeded in a test transaction. Rows are seeded, read and
 * cleaned through auto-committed JDBC.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Finding occurrences recorded by the write paths")
class FindingOccurrenceWritePathTest extends IntegrationTest {

  private static final Instant SENT_DATE = Instant.parse("2026-10-01T08:30:00Z");

  @Autowired private FindingWriter findingWriter;
  @Autowired private FindingService findingService;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantId;
  private String injectId;
  private String assetId;

  @BeforeEach
  void seedAnInjectSentOnAKnownDate() {
    jdbc = new JdbcTemplate(dataSource);
    tenantId = UUID.randomUUID().toString();
    injectId = UUID.randomUUID().toString();
    assetId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "occurrence-" + tenantId);
    jdbc.update(
        "INSERT INTO injects (inject_id, inject_title, inject_created_at, inject_updated_at,"
            + " inject_depends_duration, inject_all_teams, inject_enabled, tenant_id)"
            + " VALUES (?, 'occurrence inject', now(), now(), 0, false, true, ?)",
        injectId,
        tenantId);
    jdbc.update(
        "INSERT INTO injects_statuses (status_id, status_inject, status_name, tracking_sent_date)"
            + " VALUES (?, ?, 'SUCCESS', ?)",
        UUID.randomUUID().toString(),
        injectId,
        Timestamp.from(SENT_DATE));
    jdbc.update(
        "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at, asset_updated_at,"
            + " tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
            + " VALUES (?, 'occurrence-asset', 'Endpoint', now(), now(), ?, 'occurrence-asset',"
            + " 'Linux', 'x86_64')",
        assetId,
        tenantId);
  }

  @AfterEach
  void sweep() {
    jdbc.update("DELETE FROM findings WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM assets WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM injects_statuses WHERE status_inject = ?", injectId);
    jdbc.update("DELETE FROM injects WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
  }

  @Nested
  @DisplayName("When an agent reports a finding")
  class WhenAnAgentReportsAFinding {

    @Test
    @DisplayName("Should record one occurrence with the inject, its sent date and the field")
    void given_anAgentFinding_should_recordItsOccurrence() {
      // Act
      String findingId = saveAgentFinding("agent-" + UUID.randomUUID());

      // Assert
      Map<String, Object> occurrence = singleOccurrenceOf(findingId);
      assertThat(occurrence.get("finding_occurrence_inject_id")).isEqualTo(injectId);
      assertThat(occurrence.get("finding_occurrence_field")).isEqualTo("hostname");
      assertThat(((Timestamp) occurrence.get("finding_occurrence_date")).toInstant())
          .isEqualTo(SENT_DATE);
      assertThat(occurrence.get("finding_occurrence_id")).isNotEqualTo(findingId);
    }

    @Test
    @DisplayName("Should keep a single occurrence when the same finding is reported again")
    void given_aRepeatedReport_should_keepOneOccurrence() {
      // Arrange
      String value = "agent-" + UUID.randomUUID();
      String findingId = saveAgentFinding(value);

      // Act
      saveAgentFinding(value);

      // Assert
      assertThat(occurrenceCount(findingId)).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("When an injector or phishing writes findings")
  class WhenAnInjectorWritesFindings {

    @Test
    @DisplayName("Should record one occurrence per written finding")
    void given_twoFindings_should_recordTwoOccurrences() {
      // Arrange
      Finding first = finding("injector-" + UUID.randomUUID());
      Finding second = finding("injector-" + UUID.randomUUID());

      // Act
      findingService.createFindings(List.of(first, second), injectId);

      // Assert
      assertThat(singleOccurrenceOf(first.getId()).get("finding_occurrence_inject_id"))
          .isEqualTo(injectId);
      assertThat(singleOccurrenceOf(second.getId()).get("finding_occurrence_field"))
          .isEqualTo("hostname");
    }
  }

  @Nested
  @DisplayName("When a user creates a finding through the API")
  class WhenAUserCreatesAFinding {

    @Test
    @DisplayName("Should record the occurrence of the created finding")
    void given_aManualFinding_should_recordItsOccurrence() {
      // Act
      Finding created =
          findingService.createFinding(
              TxCtx.forTenant(tenantId), finding("manual-" + UUID.randomUUID()), injectId);

      // Assert
      assertThat(singleOccurrenceOf(created.getId()).get("finding_occurrence_inject_id"))
          .isEqualTo(injectId);
    }
  }

  // -- PRIVATE --

  private String saveAgentFinding(String value) {
    findingWriter.saveCompleteFinding(
        TxCtx.forTenant(tenantId),
        "hostname",
        ContractOutputType.Text.name(),
        value,
        new String[0],
        sentInject(),
        "occurrence probe",
        assetId,
        new String[0]);
    return jdbc.queryForObject(
        "SELECT finding_id FROM findings WHERE tenant_id = ? AND finding_value = ?",
        String.class,
        tenantId,
        value);
  }

  // The inject as the agent path holds it: loaded by the caller, with its status
  private Inject sentInject() {
    InjectStatus status = new InjectStatus();
    status.setTrackingSentDate(SENT_DATE);
    Inject inject = new Inject();
    inject.setId(injectId);
    inject.setStatus(status);
    return inject;
  }

  private static Finding finding(String value) {
    Finding finding = new Finding();
    finding.setType(ContractOutputType.Text);
    finding.setField("hostname");
    finding.setValue(value);
    finding.setName("occurrence probe");
    return finding;
  }

  private Map<String, Object> singleOccurrenceOf(String findingId) {
    return jdbc.queryForMap(
        "SELECT * FROM finding_occurrences WHERE finding_occurrence_finding_id = ?", findingId);
  }

  private int occurrenceCount(String findingId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM finding_occurrences WHERE finding_occurrence_finding_id = ?",
        Integer.class,
        findingId);
  }
}
