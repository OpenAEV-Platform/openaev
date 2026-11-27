package io.openaev.service.inject;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionCallback;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExecutionTraceFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.composers.ExecutionTraceComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RowTenantScope;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * #6357: end-to-end proof that a cross-tenant batch of inject-execution callbacks is processed with
 * each callback scoped to its own inject's tenant, through the real primitive (no mocks).
 *
 * <p>Deliberately NOT {@code @Transactional}: the consumer commits each tenant group in a {@code
 * REQUIRES_NEW} transaction, which would not see rows seeded inside a rolled-back test transaction
 * (mirrors {@code AttackPathRunWiringTest}); committed rows are cleaned up in {@code @AfterEach}.
 *
 * <p>The assertion is scope-dependent on purpose: a wrong or missing scope would filter each inject
 * out of its group's read, the callback would fall into the not-found branch, and the PENDING
 * status would never transition. Both statuses leaving PENDING proves each inject was found and
 * processed under its own tenant.
 */
@WithMockUser(isAdmin = true)
@DisplayName("batched callbacks are processed under each inject's own tenant (#6357)")
class BatchingInjectStatusServiceTenantScopeTest extends IntegrationTest {

  @Autowired private BatchingInjectStatusService service;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private ExecutionTraceComposer executionTraceComposer;
  @Autowired private DataSource dataSource;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private InjectRepository injectRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private RowTenantScope rowTenantScope;
  @Autowired private TenantScopedTransaction tenantTx;

  private String tenantA;
  private String tenantB;

  @AfterEach
  void cleanup() {
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
  }

  @Test
  @DisplayName(
      "each tenant's PENDING inject transitions, proving it was found and processed in scope")
  void crossTenantBatchScopedPerTenant() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("cb-scope-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("cb-scope-b").getId();

    // injectors_contracts is tenant-active: the built-in email contract is per-tenant now, so each
    // tenant resolves (find-or-creates) its own row rather than sharing one.
    String injectA = seedPendingInject(tenantA);
    String injectB = seedPendingInject(tenantB);

    // No ambient scope, like the batch worker: the service must resolve and scope each callback by
    // its inject's tenant on its own.
    TenantContext.clearCurrentTenant();
    List<InjectExecutionCallback> processed =
        service.handleInjectExecutionCallback(
            List.of(completeCallback(injectA), completeCallback(injectB)));

    assertThat(processed)
        .extracting(InjectExecutionCallback::getInjectId)
        .containsExactlyInAnyOrder(injectA, injectB);

    entityManager.clear();
    assertThat(statusOf(tenantA, injectA)).isNotEqualTo(ExecutionStatus.PENDING);
    assertThat(statusOf(tenantB, injectB)).isNotEqualTo(ExecutionStatus.PENDING);
  }

  @Test
  @DisplayName(
      "each tenant's batch merges the traces already stored for its inject and stamps the new"
          + " trace with that tenant")
  void crossTenantBatchMergesExistingTracesPerTenant() throws Exception {
    // Arrange
    tenantA = tenantHelper.createTenantWithCurrentUser("cb-traces-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("cb-traces-b").getId();
    String injectA = seedPendingInjectWithStoredError(tenantA);
    String injectB = seedPendingInjectWithStoredError(tenantB);

    // Act
    TenantContext.clearCurrentTenant();
    service.handleInjectExecutionCallback(
        List.of(completeCallback(injectA), completeCallback(injectB)));

    // Assert: the final status is computed from the stored error trace and the reported success,
    // so it holds only when the stored trace is read in the inject's own tenant scope.
    entityManager.clear();
    assertThat(statusOf(tenantA, injectA)).isEqualTo(ExecutionStatus.PARTIAL);
    assertThat(statusOf(tenantB, injectB)).isEqualTo(ExecutionStatus.PARTIAL);
    assertThat(traceTenants(injectA)).containsExactly(tenantA, tenantA);
    assertThat(traceTenants(injectB)).containsExactly(tenantB, tenantB);
  }

  /** The tenant of every trace of the inject's status, read with raw JDBC on its own connection. */
  private List<String> traceTenants(String injectId) {
    return new JdbcTemplate(dataSource)
        .queryForList(
            "SELECT t.tenant_id FROM execution_traces t JOIN injects_statuses s"
                + " ON s.status_id = t.execution_inject_status_id WHERE s.status_inject = ?",
            String.class,
            injectId);
  }

  private String seedPendingInjectWithStoredError(String tenantId) {
    String[] injectId = new String[1];
    rowTenantScope.write(
        tenantId,
        () -> {
          InjectorContract contract =
              injectorContractFixture.getWellKnownSingleEmailContract(tenantId);
          Inject inject = InjectFixture.createInject(contract, "cb-traces-probe");
          inject.setTenant(new Tenant(tenantId));
          InjectComposer.Composer injectC =
              injectComposer
                  .forInject(inject)
                  .withInjectStatus(
                      injectStatusComposer
                          .forInjectStatus(InjectStatusFixture.createPendingInjectStatus())
                          .withExecutionTrace(
                              executionTraceComposer.forExecutionTrace(
                                  ExecutionTraceFixture.createDefaultExecutionTraceError())));
          Exercise exercise = ExerciseFixture.createDefaultExercise();
          exercise.setTenant(new Tenant(tenantId));
          exerciseComposer.forExercise(exercise).withInjects(List.of(injectC)).persist();
          injectId[0] = injectC.get().getId();
        });
    return injectId[0];
  }

  private ExecutionStatus statusOf(String tenantId, String injectId) {
    return tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () ->
            injectRepository
                .findById(injectId)
                .flatMap(Inject::getStatus)
                .map(InjectStatus::getName)
                .orElseThrow());
  }

  private String seedPendingInject(String tenantId) {
    TenantContext.setCurrentTenant(tenantId);
    String[] injectId = new String[1];
    rowTenantScope.write(
        tenantId,
        () -> {
          InjectorContract contract = injectorContractFixture.getWellKnownSingleEmailContract();
          InjectComposer.Composer injectC =
              injectComposer
                  .forInject(InjectFixture.createInject(contract, "cb-scope-probe"))
                  .withInjectStatus(
                      injectStatusComposer.forInjectStatus(
                          InjectStatusFixture.createPendingInjectStatus()));
          exerciseComposer
              .forExercise(ExerciseFixture.createDefaultExercise())
              .withInjects(List.of(injectC))
              .persist();
          injectId[0] = injectC.get().getId();
        });
    return injectId[0];
  }

  private InjectExecutionCallback completeCallback(String injectId) {
    InjectExecutionInput input = new InjectExecutionInput();
    input.setAction(InjectExecutionAction.complete);
    input.setMessage("done");
    input.setStatus("SUCCESS");
    return InjectExecutionCallback.builder()
        .injectId(injectId)
        .injectExecutionInput(input)
        .emissionDate(Instant.now().toEpochMilli())
        .build();
  }
}
