package io.openaev.service.inject;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.repository.InjectRepository;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionCallback;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private InjectRepository injectRepository;
  @Autowired private EntityManager entityManager;

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
    // Resolve the shared contract once: getWellKnownSingleEmailContract find-or-creates, and
    // calling
    // it per tenant in this committed (non-transactional) test would leave duplicates behind.
    InjectorContract contract = injectorContractFixture.getWellKnownSingleEmailContract();
    tenantA = tenantHelper.createTenantWithCurrentUser("cb-scope-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("cb-scope-b").getId();

    String injectA = seedPendingInject(tenantA, contract);
    String injectB = seedPendingInject(tenantB, contract);

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
    assertThat(statusOf(injectA)).isNotEqualTo(ExecutionStatus.PENDING);
    assertThat(statusOf(injectB)).isNotEqualTo(ExecutionStatus.PENDING);
  }

  private ExecutionStatus statusOf(String injectId) {
    return injectRepository
        .findById(injectId)
        .flatMap(Inject::getStatus)
        .map(InjectStatus::getName)
        .orElseThrow();
  }

  private String seedPendingInject(String tenantId, InjectorContract contract) {
    TenantContext.setCurrentTenant(tenantId);
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
    return injectC.get().getId();
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
