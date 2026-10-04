package io.openaev.service.stix;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.repository.IocValidationRepository;
import io.openaev.database.repository.IocValidationRepository.IocValidationRef;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * The results-push outbox polls finished validations whose result bundle OpenCTI has not received.
 * Rejected, awaiting and running validations never push results: they must be neither returned by
 * the poll nor kept in its partial index, or the index would grow with the whole history.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=ioc_validations")
@WithMockUser(isAdmin = true)
@DisplayName("IOC validation results-push outbox")
class IocValidationResultsPushOutboxTest extends IntegrationTest {

  @Autowired private IocValidationRepository iocValidationRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private JdbcTemplate jdbc;

  private String tenantId;

  @BeforeEach
  void setUp() throws Exception {
    tenantId = tenantHelper.createTenantWithCurrentUser("ioc-validation-outbox").getId();
  }

  @AfterEach
  void cleanUp() {
    jdbc.update("DELETE FROM ioc_validations WHERE tenant_id = ?", tenantId);
    tenantHelper.deleteCommittedTenants(tenantId);
  }

  @Test
  @DisplayName("given validations in every status should poll only the unpushed finished ones")
  void given_everyStatus_should_pollOnlyUnpushedFinishedValidations() {
    String completed = insertValidation("COMPLETED", false);
    String partial = insertValidation("PARTIAL", false);
    String failed = insertValidation("FAILED", false);
    insertValidation("COMPLETED", true);
    insertValidation("REJECTED", false);
    insertValidation("AWAITING_APPROVAL", false);
    insertValidation("RUNNING", false);

    List<String> polled =
        tenantTx
            .execute(TxCtx.allTenants(), iocValidationRepository::findRefsWithPendingResultsPush)
            .stream()
            .filter(ref -> tenantId.equals(ref.getTenantId()))
            .map(IocValidationRef::getId)
            .toList();

    assertThat(polled).containsExactlyInAnyOrder(completed, partial, failed);
  }

  @Test
  @DisplayName("given the outbox index should cover only the statuses whose results are pushed")
  void given_outboxIndex_should_coverOnlyResultStatuses() {
    String predicate =
        jdbc.queryForObject(
            "SELECT pg_get_expr(i.indpred, i.indrelid) FROM pg_index i"
                + " JOIN pg_class c ON c.oid = i.indexrelid"
                + " WHERE c.relname = 'idx_ioc_validations_results_push_pending'",
            String.class);

    assertThat(predicate)
        .contains("ioc_validation_results_pushed_at IS NULL")
        .contains("'COMPLETED'")
        .contains("'PARTIAL'")
        .contains("'FAILED'")
        .doesNotContain("'REJECTED'");
    IocValidationService.RESULT_STATUSES.forEach(
        status -> assertThat(predicate).contains("'" + status.name() + "'"));
  }

  private String insertValidation(String status, boolean pushed) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO ioc_validations (ioc_validation_id, ioc_validation_external_id,"
            + " ioc_validation_name, ioc_validation_status, ioc_validation_results_pushed_at,"
            + " tenant_id) VALUES (?, ?, ?, ?, CASE WHEN ? THEN now() END, ?)",
        id,
        "ioc-validation-request--" + id,
        "Outbox " + status,
        status,
        pushed,
        tenantId);
    return id;
  }
}
