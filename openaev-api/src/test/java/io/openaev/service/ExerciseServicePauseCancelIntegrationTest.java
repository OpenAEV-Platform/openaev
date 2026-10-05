package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepActionClass;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.model.autonomous.AutonomousRun;
import io.openaev.database.model.autonomous.AutonomousRunStatus;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pausing a simulation and cancelling it from the pause, through the real {@link ExerciseService}.
 *
 * <p>A paused chained simulation keeps its workflow run in {@code STOP}, not {@code RUN}: the
 * cancellation must still end it, and pausing must never be mistaken for a cancellation.
 */
@SpringBootTest
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithMockUser(isAdmin = true)
@DisplayName("Exercise pause and cancel integration tests")
class ExerciseServicePauseCancelIntegrationTest extends IntegrationTest {

  @Autowired private ExerciseService exerciseService;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private StepRepository stepRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private AutonomousRunRepository autonomousRunRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;

  @BeforeEach
  void setUp() {
    exerciseComposer.reset();
    workflowComposer.reset();
    injectComposer.reset();
    injectStatusComposer.reset();
  }

  @Nested
  @DisplayName("Pausing a running simulation")
  class Pause {

    @Test
    @DisplayName("given_runningSimulation_when_paused_should_notSetEndDate")
    void given_runningSimulation_when_paused_should_notSetEndDate() throws ChainingException {
      // Arrange
      Exercise simulation = persistSimulation(ExerciseStatus.RUNNING);

      // Act
      exerciseService.changeExerciseStatus(ExerciseStatus.PAUSED, simulation.getId());
      Exercise result = reload(simulation);

      // Assert - pausing is not cancelling: the simulation is not over, it has no end date
      assertEquals(ExerciseStatus.PAUSED, result.getStatus());
      assertTrue(result.getEnd().isEmpty());
      assertTrue(result.getCurrentPause().isPresent());
    }

    @Test
    @DisplayName("given_runningChainedSimulation_when_paused_should_parkWorkflowWithoutEndingIt")
    void given_runningChainedSimulation_when_paused_should_parkWorkflowWithoutEndingIt()
        throws ChainingException {
      // Arrange
      Workflow run = persistChainedSimulation(ExerciseStatus.RUNNING);
      Step activeStep = persistStep(run, StepStatus.RUN);
      String simulationId = run.getSimulation().getId();

      // Act
      exerciseService.changeExerciseStatus(ExerciseStatus.PAUSED, simulationId);
      entityManager.flush();
      entityManager.clear();

      // Assert - STOP (parked, resumable), never END; its steps are left as they were
      assertEquals(
          WorkflowStatus.STOP, workflowRepository.findById(run.getId()).orElseThrow().getStatus());
      assertEquals(
          StepStatus.RUN, stepRepository.findById(activeStep.getId()).orElseThrow().getStatus());
      Exercise result = exerciseRepository.findById(simulationId).orElseThrow();
      assertEquals(ExerciseStatus.PAUSED, result.getStatus());
      assertTrue(result.getEnd().isEmpty());
    }
  }

  @Nested
  @DisplayName("Cancelling a simulation")
  class Cancel {

    @Test
    @DisplayName("given_pausedSimulation_when_canceled_should_setEndDate")
    void given_pausedSimulation_when_canceled_should_setEndDate() throws ChainingException {
      // Arrange
      Exercise simulation = persistSimulation(ExerciseStatus.PAUSED);

      // Act
      exerciseService.changeExerciseStatus(ExerciseStatus.CANCELED, simulation.getId());
      Exercise result = reload(simulation);

      // Assert
      assertEquals(ExerciseStatus.CANCELED, result.getStatus());
      assertTrue(result.getEnd().isPresent());
    }

    @Test
    @DisplayName("given_runningChainedSimulation_when_canceled_should_endWorkflowAndActiveSteps")
    void given_runningChainedSimulation_when_canceled_should_endWorkflowAndActiveSteps()
        throws ChainingException {
      // Arrange
      Workflow run = persistChainedSimulation(ExerciseStatus.RUNNING);
      Step activeStep = persistStep(run, StepStatus.RUN);
      String simulationId = run.getSimulation().getId();

      // Act
      exerciseService.changeExerciseStatus(ExerciseStatus.CANCELED, simulationId);
      entityManager.flush();
      entityManager.clear();

      // Assert
      assertEquals(
          WorkflowStatus.END, workflowRepository.findById(run.getId()).orElseThrow().getStatus());
      assertEquals(
          StepStatus.END, stepRepository.findById(activeStep.getId()).orElseThrow().getStatus());
      Exercise result = exerciseRepository.findById(simulationId).orElseThrow();
      assertEquals(ExerciseStatus.CANCELED, result.getStatus());
      assertTrue(result.getEnd().isPresent());
    }

    @Test
    @DisplayName("given_pausedChainedSimulation_when_canceled_should_endWorkflowAndActiveSteps")
    void given_pausedChainedSimulation_when_canceled_should_endWorkflowAndActiveSteps()
        throws ChainingException {
      // Arrange - pause through the service so the run is really parked in STOP, not RUN
      Workflow run = persistChainedSimulation(ExerciseStatus.RUNNING);
      Step activeStep = persistStep(run, StepStatus.RUN);
      Step readyStep = persistStep(run, StepStatus.READY);
      String simulationId = run.getSimulation().getId();
      exerciseService.changeExerciseStatus(ExerciseStatus.PAUSED, simulationId);
      entityManager.flush();
      entityManager.clear();
      assertEquals(
          WorkflowStatus.STOP, workflowRepository.findById(run.getId()).orElseThrow().getStatus());

      // Act
      exerciseService.changeExerciseStatus(ExerciseStatus.CANCELED, simulationId);
      entityManager.flush();
      entityManager.clear();

      // Assert - the parked run is ended too, not left in STOP behind a CANCELED simulation
      assertEquals(
          WorkflowStatus.END, workflowRepository.findById(run.getId()).orElseThrow().getStatus());
      assertEquals(
          StepStatus.END, stepRepository.findById(activeStep.getId()).orElseThrow().getStatus());
      assertEquals(
          StepStatus.END, stepRepository.findById(readyStep.getId()).orElseThrow().getStatus());
      Exercise result = exerciseRepository.findById(simulationId).orElseThrow();
      assertEquals(ExerciseStatus.CANCELED, result.getStatus());
      assertTrue(result.getEnd().isPresent());
    }

    @Test
    @DisplayName("given_pausedChainedSimulation_when_canceled_should_keepItsInjects")
    void given_pausedChainedSimulation_when_canceled_should_keepItsInjects()
        throws ChainingException {
      // Arrange - stopping is not resetting, also when the simulation was paused
      Workflow run = persistChainedSimulation(ExerciseStatus.RUNNING);
      String simulationId = run.getSimulation().getId();
      Inject neverStarted = persistInject(run.getSimulation(), false);
      Inject executed = persistInject(run.getSimulation(), true);
      exerciseService.changeExerciseStatus(ExerciseStatus.PAUSED, simulationId);

      // Act
      exerciseService.changeExerciseStatus(ExerciseStatus.CANCELED, simulationId);
      entityManager.flush();
      entityManager.clear();

      // Assert
      List<String> remaining =
          injectRepository.findByExerciseId(simulationId).stream().map(Inject::getId).toList();
      assertTrue(remaining.contains(neverStarted.getId()));
      assertTrue(remaining.contains(executed.getId()));
    }

    @Test
    @DisplayName(
        "given_pausedAutonomousSimulation_when_canceled_should_dropOnlyNeverStartedInjects")
    void given_pausedAutonomousSimulation_when_canceled_should_dropOnlyNeverStartedInjects()
        throws ChainingException {
      // Arrange
      Workflow run = persistChainedSimulation(ExerciseStatus.RUNNING);
      Exercise simulation = run.getSimulation();
      String simulationId = simulation.getId();
      persistAutonomousRun(simulation);
      Inject neverStarted = persistInject(simulation, false);
      Inject executed = persistInject(simulation, true);
      exerciseService.changeExerciseStatus(ExerciseStatus.PAUSED, simulationId);

      // Act
      exerciseService.changeExerciseStatus(ExerciseStatus.CANCELED, simulationId);
      entityManager.flush();
      entityManager.clear();

      // Assert - the orchestrator's queued-but-never-started injects go, the executed record stays
      List<String> remaining =
          injectRepository.findByExerciseId(simulationId).stream().map(Inject::getId).toList();
      assertFalse(remaining.contains(neverStarted.getId()));
      assertTrue(remaining.contains(executed.getId()));
      assertEquals(
          WorkflowStatus.END, workflowRepository.findById(run.getId()).orElseThrow().getStatus());
    }
  }

  // -- Helpers --

  private Exercise persistSimulation(ExerciseStatus status) {
    Exercise simulation = ExerciseFixture.createDefaultExercise();
    simulation.setStatus(status);
    simulation.setStart(Instant.now().minusSeconds(120));
    if (ExerciseStatus.PAUSED.equals(status)) {
      simulation.setCurrentPause(Instant.now().minusSeconds(30));
    }
    return exerciseComposer.forExercise(simulation).persist().get();
  }

  private Workflow persistChainedSimulation(ExerciseStatus status) {
    Exercise simulation = ExerciseFixture.createDefaultExercise();
    simulation.setStatus(status);
    simulation.setStart(Instant.now().minusSeconds(120));
    Workflow run = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);
    return workflowComposer
        .forWorkflow(run)
        .withSimulation(exerciseComposer.forExercise(simulation))
        .persist()
        .get();
  }

  private Step persistStep(Workflow workflow, StepStatus status) {
    return stepRepository.save(
        Step.builder()
            .stepAction(StepActionClass.INJECT_EXECUTION)
            .status(status)
            .workflow(workflow)
            .limitExecution(1)
            .build());
  }

  private Inject persistInject(Exercise simulation, boolean executed) {
    InjectComposer.Composer composer =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withExercise(exerciseComposer.forExercise(simulation));
    if (executed) {
      composer.withInjectStatus(
          injectStatusComposer.forInjectStatus(InjectStatusFixture.createSuccessStatus()));
    }
    return composer.persist().get();
  }

  private void persistAutonomousRun(Exercise simulation) {
    AutonomousRun autonomousRun = new AutonomousRun();
    autonomousRun.setTenant(simulation.getTenant());
    autonomousRun.setObjective("Pause then cancel");
    autonomousRun.setStatus(AutonomousRunStatus.PAUSED);
    autonomousRun.setSimulationId(simulation.getId());
    autonomousRunRepository.save(autonomousRun);
    entityManager.flush();
  }

  private Exercise reload(Exercise simulation) {
    entityManager.flush();
    entityManager.clear();
    return exerciseRepository.findById(simulation.getId()).orElseThrow();
  }
}
