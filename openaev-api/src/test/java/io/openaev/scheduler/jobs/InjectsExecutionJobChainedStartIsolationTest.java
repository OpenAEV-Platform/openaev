package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.executors.Executor;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.helper.InjectHelper;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
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
 * One chained simulation whose workflow cannot start must not take the rest of the tick with it.
 *
 * <p>{@code autoStartDueExercises} promotes every due simulation to RUNNING in its own transaction
 * and commits, and {@code findAllShouldBeInRunningState} never hands a started simulation back. The
 * tick that starts the workflows is therefore the only chance the batch gets: before this
 * isolation, the first {@link ChainingException} left the loop, skipped {@code
 * executeClassicalInjects}, and left every other simulation of the tick RUNNING without a workflow
 * run - permanently, since the next tick does not see them.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("A failed chained start is isolated to its own simulation")
class InjectsExecutionJobChainedStartIsolationTest {

  private static final String TENANT_ID = "11111111-2222-3333-4444-555555555555";
  private static final String FAILING_SIMULATION_ID = "aaaaaaaa-1111-4111-8111-111111111111";
  private static final String HEALTHY_SIMULATION_ID = "bbbbbbbb-2222-4222-8222-222222222222";

  @Mock private InjectHelper injectHelper;
  @Mock private InjectService injectService;
  @Mock private ExerciseRepository exerciseRepository;
  @Mock private io.openaev.database.repository.InjectDependenciesRepository dependencyRepository;
  @Mock private InjectStatusService injectStatusService;
  @Mock private Executor executor;
  @Mock private ActionMetricCollector actionMetricCollector;
  @Mock private io.openaev.service.chaining.WorkflowService workflowService;
  @Mock private TenantScopedJobRunner tenantScopedJobRunner;
  @Mock private HealthCheckUtils healthCheckUtils;

  @InjectMocks private InjectsExecutionJob job;

  private Exercise failing;
  private Exercise healthy;

  @BeforeEach
  void setUp() throws Exception {
    ReflectionTestUtils.setField(job, "auditLogger", Optional.empty());
    failing = simulation(FAILING_SIMULATION_ID);
    healthy = simulation(HEALTHY_SIMULATION_ID);

    // Pass-through runner: this suite asserts the loop, not the scope it opens
    doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(1).run();
              return null;
            })
        .when(tenantScopedJobRunner)
        .runInTenant(anyString(), any(Runnable.class));
    doAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get())
        .when(tenantScopedJobRunner)
        .supplyInTenant(anyString(), any(Supplier.class));

    doThrow(new ChainingException("workflow template is not usable"))
        .when(workflowService)
        .startWorkflowBySimulationIdIfPresent(FAILING_SIMULATION_ID);
  }

  private static Exercise simulation(String id) {
    Exercise exercise = new Exercise();
    exercise.setId(id);
    exercise.setName("simulation-" + id);
    exercise.setTenant(new Tenant(TENANT_ID));
    return exercise;
  }

  @Test
  @DisplayName("given_one_failing_simulation, should_start_the_workflow_of_the_next_one")
  void given_oneFailingSimulation_should_startTheWorkflowOfTheNextOne() throws Exception {
    // Act
    ReflectionTestUtils.invokeMethod(
        job, "executeChainedSimulations", new ArrayList<>(List.of(failing, healthy)));

    // Assert
    verify(workflowService).startWorkflowBySimulationIdIfPresent(FAILING_SIMULATION_ID);
    verify(workflowService).startWorkflowBySimulationIdIfPresent(HEALTHY_SIMULATION_ID);
  }

  @Test
  @DisplayName("given_one_failing_simulation, should_still_run_the_classical_inject_phase")
  void given_oneFailingSimulation_should_stillRunTheClassicalInjectPhase() throws Exception {
    // Arrange: a full tick, one due chained simulation whose start fails
    when(exerciseRepository.findAllShouldBeInRunningState(any()))
        .thenReturn(List.of(failing, healthy));
    when(tenantScopedJobRunner.supplyAcrossTenants(any(Supplier.class)))
        .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
    when(exerciseRepository.findAllById(any())).thenReturn(List.of(failing, healthy));
    when(exerciseRepository.saveAll(any())).thenReturn(List.of(failing, healthy));
    when(injectHelper.getInjectsToRun()).thenReturn(List.of());

    // Act / Assert: the job completes instead of aborting on the failed start
    assertThatCode(() -> job.execute(null)).doesNotThrowAnyException();
    verify(injectHelper).getInjectsToRun();
  }
}
