package io.openaev.service.inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionCallback;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import io.openaev.rest.inject.service.InjectExecutionService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectorExecutionProcessingHandler;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RowTenantScope;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * #8115: an inject execution callback that fails inside another bean's {@code @Transactional}
 * method marks its transaction rollback-only, even though the callback processing catches the
 * failure. It must neither take the other callbacks of its batch down with it, nor hide behind an
 * {@code UnexpectedRollbackException} at commit.
 *
 * <p>The poisoned callback is made by the output post-processing of one inject: it looks up an
 * inject that does not exist through {@link InjectService#existsInScope}, a transactional method of
 * another bean, which is the exact shape of the defect.
 *
 * <p>Deliberately NOT {@code @Transactional}: the batch consumer opens its own transactions, and
 * the commit is where the defect shows. Committed rows are cleaned up with their tenant.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Inject execution callbacks that fail inside another transactional bean (#8115)")
class InjectCallbackHandledErrorCommitTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @MockitoSpyBean private InjectorExecutionProcessingHandler injectorHandler;

  @Autowired private BatchingInjectStatusService batchingService;
  @Autowired private InjectExecutionService injectExecutionService;
  @Autowired private InjectService injectService;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private InjectRepository injectRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private RowTenantScope rowTenantScope;
  @Autowired private TenantScopedTransaction tenantTx;

  private String tenantId;
  private String poisonedInjectId;
  private final List<String> brokenOutputInjectIds = new ArrayList<>();

  @BeforeEach
  void poisonOneInject() throws Exception {
    tenantId = tenantHelper.createTenantWithCurrentUser("cb-handled-error").getId();
    poisonedInjectId = seedPendingInject();
    Mockito.doAnswer(
            invocation -> {
              ExecutionProcessingContext context = invocation.getArgument(0);
              if (brokenOutputInjectIds.contains(context.inject().getId())) {
                throw new IllegalStateException("unparsable output");
              }
              if (poisonedInjectId.equals(context.inject().getId())) {
                injectService.existsInScope(
                    TxCtx.forTenant(tenantId), UUID.randomUUID().toString());
              }
              return invocation.callRealMethod();
            })
        .when(injectorHandler)
        .processContext(Mockito.any());
    TenantContext.clearCurrentTenant();
  }

  @AfterEach
  void cleanup() {
    brokenOutputInjectIds.clear();
    TenantContext.clearCurrentTenant();
    tenantHelper.deleteCommittedTenants(tenantId);
  }

  @Nested
  @DisplayName("Batched callbacks from the inject-trace queue")
  class BatchedCallbacks {

    @Test
    @DisplayName("given_onePoisonedCallback_should_commitTheOthersAndAcknowledgeOnlyThem")
    void given_onePoisonedCallback_should_commitTheOthersAndAcknowledgeOnlyThem() throws Exception {
      // -- ARRANGE --
      String healthyInjectId = seedPendingInject();
      TenantContext.clearCurrentTenant();

      // -- ACT --
      List<InjectExecutionCallback> acknowledged =
          batchingService.handleInjectExecutionCallback(
              List.of(completeCallback(poisonedInjectId), completeCallback(healthyInjectId)));

      // -- ASSERT --
      assertThat(acknowledged)
          .extracting(InjectExecutionCallback::getInjectId)
          .as("only the callback that was committed is acknowledged")
          .containsExactly(healthyInjectId);
      entityManager.clear();
      assertThat(statusOf(healthyInjectId))
          .as("the healthy callback is not lost with the poisoned one")
          .isNotEqualTo(ExecutionStatus.PENDING);
      assertThat(statusOf(poisonedInjectId)).isEqualTo(ExecutionStatus.PENDING);
    }

    @Test
    @DisplayName(
        "given_postProcessingFailingOutsideAnyTransactionalBean_should_stillRecordTheStatus")
    void given_postProcessingFailingOutsideAnyTransactionalBean_should_stillRecordTheStatus()
        throws Exception {
      // -- ARRANGE --
      // The output post-processing is best-effort: a failure that leaves the transaction sound
      // must not cost the callback its status.
      String brokenOutputInjectId = seedPendingInject();
      brokenOutputInjectIds.add(brokenOutputInjectId);
      TenantContext.clearCurrentTenant();

      // -- ACT --
      List<InjectExecutionCallback> acknowledged =
          batchingService.handleInjectExecutionCallback(
              List.of(completeCallback(brokenOutputInjectId)));

      // -- ASSERT --
      assertThat(acknowledged)
          .extracting(InjectExecutionCallback::getInjectId)
          .containsExactly(brokenOutputInjectId);
      entityManager.clear();
      assertThat(statusOf(brokenOutputInjectId)).isNotEqualTo(ExecutionStatus.PENDING);
    }
  }

  @Nested
  @DisplayName("Callback processed right away (legacy ingestion, no queue)")
  class DirectCallback {

    @Test
    @DisplayName("given_poisonedCallback_should_answerTheFailureInsteadOfDyingAtCommit")
    void given_poisonedCallback_should_answerTheFailureInsteadOfDyingAtCommit() {
      // -- ACT & ASSERT --
      // The endpoint's transaction is the outermost one, as tenantTx's is here.
      assertThatThrownBy(
              () ->
                  tenantTx.execute(
                      TxCtx.forTenant(tenantId),
                      () ->
                          injectExecutionService.handleInjectExecutionCallback(
                              poisonedInjectId, null, completeInput())))
          .as("the failure itself, not an UnexpectedRollbackException at commit")
          .isInstanceOf(ElementNotFoundException.class);
    }

    @Test
    @DisplayName("given_unknownInject_should_handleTheErrorAndCommit")
    void given_unknownInject_should_handleTheErrorAndCommit() {
      // -- ACT --
      tenantTx.execute(
          TxCtx.forTenant(tenantId),
          () ->
              injectExecutionService.handleInjectExecutionCallback(
                  UUID.randomUUID().toString(), null, completeInput()));

      // -- ASSERT --
      // Reaching this line is the assertion: the not-found branch did not poison the commit.
      assertThat(statusOf(poisonedInjectId)).isEqualTo(ExecutionStatus.PENDING);
    }
  }

  // -- HELPERS --

  private String seedPendingInject() {
    TenantContext.setCurrentTenant(tenantId);
    String[] injectId = new String[1];
    rowTenantScope.write(
        tenantId,
        () -> {
          InjectorContract contract = injectorContractFixture.getWellKnownSingleEmailContract();
          InjectComposer.Composer injectC =
              injectComposer
                  .forInject(InjectFixture.createInject(contract, "cb-handled-error"))
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

  private ExecutionStatus statusOf(String injectId) {
    return tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () ->
            injectRepository
                .findById(injectId)
                .flatMap(Inject::getStatus)
                .map(InjectStatus::getName)
                .orElseThrow());
  }

  private static InjectExecutionInput completeInput() {
    InjectExecutionInput input = new InjectExecutionInput();
    input.setAction(InjectExecutionAction.complete);
    input.setMessage("done");
    input.setStatus("SUCCESS");
    return input;
  }

  private static InjectExecutionCallback completeCallback(String injectId) {
    return InjectExecutionCallback.builder()
        .injectId(injectId)
        .injectExecutionInput(completeInput())
        .emissionDate(Instant.now().toEpochMilli())
        .build();
  }
}
