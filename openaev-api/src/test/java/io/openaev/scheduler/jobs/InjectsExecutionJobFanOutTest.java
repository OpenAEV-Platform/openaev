package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import io.openaev.service.NotificationEventService;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.SecurityCoverageSendJobService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import org.hibernate.Session;
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
 * The scheduled inject fan-out runs on the dedicated {@code injectExecutionExecutor} pool (issue
 * #236). These tests pin the semantics the executor migration must preserve: per-inject error
 * isolation (one failing inject never takes down the rest of the batch) and the exercise update
 * running only after every inject of that exercise has completed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Scheduled inject fan-out on the dedicated executor")
class InjectsExecutionJobFanOutTest {

  private static final String TENANT = "11111111-2222-3333-4444-555555555555";

  @Mock private InjectHelper injectHelper;
  @Mock private InjectService injectService;
  @Mock private ExerciseRepository exerciseRepository;
  @Mock private InjectDependenciesRepository injectDependenciesRepository;
  @Mock private InjectExpectationRepository injectExpectationRepository;
  @Mock private InjectStatusService injectStatusService;
  @Mock private io.openaev.executors.Executor executor;
  @Mock private ActionMetricCollector actionMetricCollector;
  @Mock private NotificationEventService notificationEventService;
  @Mock private SecurityCoverageSendJobService securityCoverageSendJobService;
  @Mock private EntityManager entityManager;
  @Mock private TenantScopedTransaction tenantTx;
  @Mock private PreviewFeatureService previewFeatureService;
  @Mock private WorkflowService workflowService;
  @Mock private HealthCheckUtils healthCheckUtils;

  // Stubbed in setUp() to run each inject on the calling thread (deterministic ordering).
  @Mock private Executor injectExecutionExecutor;

  @InjectMocks private InjectsExecutionJob job;

  /** Ordered trace of what ran, to assert completion ordering. */
  private final List<String> events = new CopyOnWriteArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    ReflectionTestUtils.setField(job, "injectExecutionThreshold", 15);
    ReflectionTestUtils.setField(job, "auditLogger", Optional.empty());
    when(entityManager.unwrap(Session.class)).thenReturn(mock(Session.class));

    // Every sweep around the fan-out is a no-op here.
    when(exerciseRepository.findAllShouldBeInRunningState(any())).thenReturn(List.of());
    when(exerciseRepository.thatMustBeFinished()).thenReturn(List.of());
    doReturn(List.of()).when(exerciseRepository).saveAll(any());
    when(previewFeatureService.isFeatureEnabled(any())).thenReturn(false);
    when(injectService.getExecutedAndNotFinished()).thenReturn(List.of());
    when(injectService.resolveAllAssetsToExecute(any(Inject.class))).thenReturn(List.of());
    when(injectHelper.getAllPendingInjectsWithThresholdMinutes(anyInt())).thenReturn(List.of());
    when(healthCheckUtils.runContentChecks(any(Inject.class))).thenReturn(List.of());
    when(injectDependenciesRepository.findParents(any())).thenReturn(List.of());

    // The dedicated inject pool runs each task on this thread (deterministic ordering).
    doAnswer(
            invocation -> {
              invocation.getArgument(0, Runnable.class).run();
              return null;
            })
        .when(injectExecutionExecutor)
        .execute(any(Runnable.class));

    // Stand in for the tenant-scoped transaction primitive: run the work on this thread.
    doAnswer(
            invocation -> {
              invocation.getArgument(1, Runnable.class).run();
              return null;
            })
        .when(tenantTx)
        .execute(any(TxCtx.class), any(Runnable.class));
  }

  private ExecutableInject injectOf(String injectId, Exercise exercise) {
    Inject inject = new Inject();
    inject.setId(injectId);
    inject.setTitle("Inject " + injectId);
    inject.setTenant(new Tenant(TENANT));

    // Resolved before the when() calls: invoking a mocked getter inside a thenReturn() argument
    // trips Mockito's unfinished-stubbing detection.
    String exerciseId = exercise == null ? null : exercise.getId();

    Injection injection = mock(Injection.class);
    when(injection.getInject()).thenReturn(inject);
    when(injection.getId()).thenReturn(injectId);
    when(injection.getExercise()).thenReturn(exercise);

    ExecutableInject executableInject = mock(ExecutableInject.class);
    when(executableInject.getInjection()).thenReturn(injection);
    when(executableInject.getExerciseId()).thenReturn(exerciseId);
    return executableInject;
  }

  @Test
  @DisplayName("a failing inject is isolated: the other injects of the batch still execute")
  void failingInjectDoesNotTakeDownTheBatch() throws Exception {
    ExecutableInject failing = injectOf("failing-inject", null);
    ExecutableInject healthy = injectOf("healthy-inject", null);
    when(injectHelper.getInjectsToRun()).thenReturn(List.of(failing, healthy));

    when(executor.execute(any(ExecutableInject.class), any()))
        .thenAnswer(
            invocation -> {
              ExecutableInject argument = invocation.getArgument(0);
              String id = argument.getInjection().getInject().getId();
              events.add("executed:" + id);
              if ("failing-inject".equals(id)) {
                throw new RuntimeException("boom");
              }
              return null;
            });

    job.execute(null);

    assertThat(events).contains("executed:failing-inject", "executed:healthy-inject");
    verify(injectStatusService).failInjectStatus(eq("failing-inject"), anyString());
    verify(injectStatusService, never()).failInjectStatus(eq("healthy-inject"), anyString());
  }

  @Test
  @DisplayName("the exercise is updated only after all its injects have completed")
  void exerciseIsUpdatedAfterAllItsInjectsCompleted() throws Exception {
    String exerciseId = UUID.randomUUID().toString();
    Exercise exercise = mock(Exercise.class);
    when(exercise.getId()).thenReturn(exerciseId);
    when(exerciseRepository.findById(exerciseId)).thenReturn(Optional.of(exercise));
    doAnswer(
            invocation -> {
              events.add("exercise-updated");
              return invocation.getArgument(0);
            })
        .when(exerciseRepository)
        .save(any(Exercise.class));

    ExecutableInject first = injectOf("first-inject", exercise);
    ExecutableInject second = injectOf("second-inject", exercise);
    when(injectHelper.getInjectsToRun()).thenReturn(List.of(first, second));

    when(executor.execute(any(ExecutableInject.class), any()))
        .thenAnswer(
            invocation -> {
              ExecutableInject argument = invocation.getArgument(0);
              events.add("executed:" + argument.getInjection().getInject().getId());
              return null;
            });

    job.execute(null);

    assertThat(events)
        .containsExactly("executed:first-inject", "executed:second-inject", "exercise-updated");
  }
}
