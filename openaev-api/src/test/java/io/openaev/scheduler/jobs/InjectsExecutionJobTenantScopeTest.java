package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Injection;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectDependenciesRepository;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.helper.InjectHelper;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Scheduled inject execution must carry the inject's tenant in BOTH scopes: the v2 primitive and
 * the v1 thread-local {@link TenantContext}, which {@code HibernateFilterTransactionAspect} turns
 * into the Hibernate {@code tenantFilter}.
 *
 * <p>Regression: only the primitive was set, so {@link TenantContext#getCurrentTenant()} fell back
 * to the DEFAULT tenant on the executor thread. Everything the execution resolves through a
 * Criteria query - asset groups, endpoints, agents - therefore came from the default tenant, and a
 * customer simulation created its expectations against another tenant's endpoints while its own
 * targets got none. Invisible in single-tenant deployments, where the fallback is the right tenant:
 * these assertions are the guard, since a unit test can name the tenant the default is not.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Scheduled inject execution runs under the inject's tenant (v1 + v2 scopes)")
class InjectsExecutionJobTenantScopeTest {

  private static final String INJECT_TENANT = "11111111-2222-3333-4444-555555555555";

  @Mock private InjectHelper injectHelper;
  @Mock private InjectService injectService;
  @Mock private ExerciseRepository exerciseRepository;
  @Mock private InjectDependenciesRepository injectDependenciesRepository;
  @Mock private InjectExpectationRepository injectExpectationRepository;
  @Mock private InjectStatusService injectStatusService;
  @Mock private io.openaev.executors.Executor executor;
  @Mock private ActionMetricCollector actionMetricCollector;
  @Mock private EntityManager entityManager;
  @Mock private TenantScopedTransaction tenantTx;
  @Mock private TenantScopedJobRunner tenantScopedJobRunner;
  @Mock private HealthCheckUtils healthCheckUtils;

  @InjectMocks private InjectsExecutionJob job;

  /** The tenant the executor saw while the inject was running. */
  private final AtomicReference<String> tenantDuringExecution = new AtomicReference<>();

  /** The scope the v2 primitive was opened with. */
  private final AtomicReference<TxCtx> primitiveScope = new AtomicReference<>();

  private final AtomicReference<String> tenantDuringFailStatus = new AtomicReference<>();

  @BeforeEach
  void setUp() throws Exception {
    ReflectionTestUtils.setField(job, "auditLogger", Optional.empty());
    when(entityManager.unwrap(Session.class)).thenReturn(mock(Session.class));

    // The scoping is what this suite asserts, so the runner is the real one over the mocked
    // primitive - a stub would run the work with no scope opened at all.
    TenantScopedJobRunner scopedRunner = new TenantScopedJobRunner(tenantTx);
    doAnswer(
            invocation -> {
              scopedRunner.runInTenant(
                  invocation.getArgument(0), invocation.getArgument(1, Runnable.class));
              return null;
            })
        .when(tenantScopedJobRunner)
        .runInTenant(anyString(), any(Runnable.class));

    // Every sweep around the execution is a no-op here: this test is about the execution scope.
    when(exerciseRepository.findAllShouldBeInRunningState(any())).thenReturn(List.of());
    doReturn(List.of()).when(exerciseRepository).saveAll(any());
    when(injectService.resolveAllAssetsToExecute(any(Inject.class))).thenReturn(List.of());
    when(healthCheckUtils.runContentChecks(any(Inject.class))).thenReturn(List.of());

    // Built before the when(): the helper stubs its own mocks, and nesting that inside
    // thenReturn(...) argument evaluation trips Mockito's unfinished-stubbing detection.
    ExecutableInject injectToRun = atomicInjectOfTenant(INJECT_TENANT);
    when(injectHelper.getInjectsToRun()).thenReturn(List.of(injectToRun));

    // Stand in for the real primitive: record the scope, run the work on this thread.
    doAnswer(
            invocation -> {
              primitiveScope.set(invocation.getArgument(0));
              invocation.getArgument(1, Runnable.class).run();
              return null;
            })
        .when(tenantTx)
        .execute(any(TxCtx.class), any(Runnable.class));

    when(executor.execute(any(ExecutableInject.class), any()))
        .thenAnswer(
            invocation -> {
              tenantDuringExecution.set(TenantContext.getCurrentTenant());
              return null;
            });
    doAnswer(
            invocation -> {
              tenantDuringFailStatus.set(TenantContext.getCurrentTenant());
              return null;
            })
        .when(injectStatusService)
        .failInjectStatus(anyString(), anyString());
  }

  @AfterEach
  void clearScope() {
    TenantContext.clearCurrentTenant();
  }

  /** An atomic inject (no exercise) so the run needs no dependency or exercise bookkeeping. */
  private static ExecutableInject atomicInjectOfTenant(String tenantId) {
    return injectOfTenant(tenantId, null);
  }

  /**
   * An inject of the given tenant, optionally attached to an exercise. When {@code exercise} is
   * null the inject is atomic (no exercise bookkeeping needed).
   */
  private static ExecutableInject injectOfTenant(String tenantId, Exercise exercise) {
    Inject inject = new Inject();
    inject.setId(UUID.randomUUID().toString());
    inject.setTitle("Nuclei - CVE scan");
    inject.setTenant(new Tenant(tenantId));

    Injection injection = mock(Injection.class);
    when(injection.getInject()).thenReturn(inject);
    when(injection.getId()).thenReturn(inject.getId());
    when(injection.getExercise()).thenReturn(exercise);

    ExecutableInject executableInject = mock(ExecutableInject.class);
    when(executableInject.getInjection()).thenReturn(injection);
    // Computed before the when(): calling exercise.getId() (itself a mock) inside a when(...)
    // argument nests a stub inside another's finishing call and trips Mockito's unfinished-
    // stubbing detection.
    String exerciseId = exercise == null ? null : exercise.getId();
    when(executableInject.getExerciseId()).thenReturn(exerciseId);
    return executableInject;
  }

  @Test
  @DisplayName("the v1 tenant filter scope is the inject's tenant, never the default fallback")
  void executionRunsUnderTheInjectTenant() throws Exception {
    job.execute(null);

    assertThat(tenantDuringExecution.get())
        .as("asset groups, endpoints and agents must resolve in the inject's tenant")
        .isEqualTo(INJECT_TENANT);
    assertThat(tenantDuringExecution.get())
        .as("the default-tenant fallback is exactly the cross-tenant bug being guarded against")
        .isNotEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  @Test
  @DisplayName("the v2 primitive is opened on the same tenant")
  void primitiveIsScopedToTheInjectTenant() throws Exception {
    job.execute(null);

    assertThat(primitiveScope.get()).isNotNull();
    assertThat(primitiveScope.get().toGuc()).isEqualTo(INJECT_TENANT);
  }

  @Test
  @DisplayName("the thread-local scope is restored once the inject is done")
  void scopeDoesNotLeakOntoThePooledThread() throws Exception {
    // The job borrows shared ForkJoinPool threads (including this caller): a scope the thread
    // carried before the sweep must survive it (restore semantics, not a blanket clear). Note
    // DefaultTenantExtension pre-sets the default tenant on every test thread, so "starts empty"
    // is not a premise this suite can rely on.
    String preexistingScope = "99999999-8888-7777-6666-555555555555";
    TenantContext.setCurrentTenant(preexistingScope);

    job.execute(null);

    assertThat(TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null)
        .as("the pre-existing scope of the borrowed thread must be restored, not overwritten")
        .isEqualTo(preexistingScope);

    // And a thread that had no scope at all must end with none.
    TenantContext.clearCurrentTenant();
    job.execute(null);

    assertThat(TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null)
        .as("a scope-less thread must leave the sweep scope-less")
        .isNull();
  }

  @Test
  @DisplayName("failed inject status update runs under the inject tenant scope")
  void failedStatusUpdateRunsUnderInjectTenant() throws Exception {
    when(executor.execute(any(ExecutableInject.class), any()))
        .thenThrow(new RuntimeException("boom"));

    job.execute(null);

    verify(injectStatusService).failInjectStatus(anyString(), anyString());
    assertThat(tenantDuringFailStatus.get()).isEqualTo(INJECT_TENANT);
  }

  @Test
  @DisplayName(
      "an exercise inject and two atomic injects of different tenants in the same sweep each"
          + " execute under their own tenant, never a sibling's")
  void mixedBatchExecutesEachInjectUnderItsOwnTenant() throws Exception {
    // Regression for the "atomic" constant batch key: before the fix, every exercise-less inject
    // shared one synthetic batch key regardless of tenant, so the batch's tenant was resolved
    // once (from whichever inject happened to be first) and reused for every atomic inject in it
    // - a customer's atomic inject could silently execute (and resolve its injector) under
    // another tenant's scope.
    String exerciseTenant = "22222222-2222-2222-2222-222222222222";
    String atomicTenantA = "33333333-3333-3333-3333-333333333333";
    String atomicTenantB = "44444444-4444-4444-4444-444444444444";

    Exercise exercise = mock(Exercise.class);
    when(exercise.getId()).thenReturn("exercise-1");

    ExecutableInject exerciseInject = injectOfTenant(exerciseTenant, exercise);
    ExecutableInject atomicInjectA = injectOfTenant(atomicTenantA, null);
    ExecutableInject atomicInjectB = injectOfTenant(atomicTenantB, null);

    when(injectHelper.getInjectsToRun())
        .thenReturn(List.of(exerciseInject, atomicInjectA, atomicInjectB));

    // Exercise batch bookkeeping (updateExercise), reached only for the exercise-linked inject.
    when(exerciseRepository.findById(exercise.getId())).thenReturn(Optional.of(exercise));
    when(exerciseRepository.save(any(Exercise.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Record the tenant scope actually active while each inject executes, keyed by inject id.
    Map<String, String> tenantSeenPerInject = new ConcurrentHashMap<>();
    when(executor.execute(any(ExecutableInject.class), any()))
        .thenAnswer(
            invocation -> {
              ExecutableInject arg = invocation.getArgument(0);
              String injectId = arg.getInjection().getInject().getId();
              tenantSeenPerInject.put(injectId, TenantContext.getCurrentTenant());
              return null;
            });

    job.execute(null);

    assertThat(tenantSeenPerInject)
        .as("each inject must resolve/execute under its own tenant scope, never a sibling's")
        .containsEntry(exerciseInject.getInjection().getInject().getId(), exerciseTenant)
        .containsEntry(atomicInjectA.getInjection().getInject().getId(), atomicTenantA)
        .containsEntry(atomicInjectB.getInjection().getInject().getId(), atomicTenantB);
  }
}
