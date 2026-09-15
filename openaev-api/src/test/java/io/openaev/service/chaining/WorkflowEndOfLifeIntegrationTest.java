package io.openaev.service.chaining;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.StepDelayQueueRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.database.repository.WorkflowStateRepository;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests exercising the three real workflow interruption causes documented in ADR-007
 * (TIMEOUT, CANCELED, NO_MORE_PROGRESS). Each test drives the real orchestration services
 * end-to-end and asserts both the resulting DB state and the exact operational log lines an
 * operator sees in production for that cause.
 *
 * @see WorkflowEndService
 * @see WorkflowService
 */
@SpringBootTest
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithMockUser(isAdmin = true)
@DisplayName("Workflow End-Of-Life Integration Tests (ADR-007)")
class WorkflowEndOfLifeIntegrationTest extends IntegrationTest {

  @Autowired private WorkflowEndService workflowEndService;
  @Autowired private WorkflowService workflowService;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private StepRepository stepRepository;
  @Autowired private StepDelayQueueRepository stepDelayQueueRepository;
  @Autowired private WorkflowStateRepository workflowStateRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;

  private ListAppender<ILoggingEvent> appender;
  private Logger chainingLogger;
  private Level originalLevel;

  @BeforeEach
  void setUp() {
    workflowComposer.reset();
    exerciseComposer.reset();
    stepComposer.reset();
    injectComposer.reset();
    injectStatusComposer.reset();

    // The CI test logback config caps io.openaev above WARN by default (see
    // application.properties): force the parent package level to INFO so the log-content
    // assertions below see the events, and restore it afterwards.
    chainingLogger = (Logger) LoggerFactory.getLogger("io.openaev.service.chaining");
    originalLevel = chainingLogger.getLevel();
    chainingLogger.setLevel(Level.INFO);
    appender = new ListAppender<>();
    appender.start();
    chainingLogger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    chainingLogger.detachAppender(appender);
    appender.stop();
    chainingLogger.setLevel(originalLevel);
  }

  private List<String> errorMessages() {
    return appender.list.stream()
        .filter(e -> e.getLevel() == Level.ERROR)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  private boolean anyLogContains(String fragment) {
    return appender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .anyMatch(msg -> msg.contains(fragment));
  }

  // ========================================================================
  // TIMEOUT
  // ========================================================================
  @Nested
  @DisplayName("TIMEOUT")
  class TimeoutTests {

    @Test
    @DisplayName(
        "given_runningWorkflowWithActiveStepsDelayQueueAndWorkflowState_should_forceCompleteAndLogEveryStage")
    void
        given_runningWorkflowWithActiveStepsDelayQueueAndWorkflowState_should_forceCompleteAndLogEveryStage() {
      // Arrange
      Workflow workflowRun = createPersistedRunWorkflow();
      Step stepReady = createPersistedStep(workflowRun, StepStatus.READY);
      Step stepRun = createPersistedStep(workflowRun, StepStatus.RUN);
      Step stepTemplate = createPersistedStep(workflowRun, StepStatus.TEMPLATE);
      createPersistedDelayQueueEntry(workflowRun, stepTemplate);
      createPersistedDelayQueueEntry(workflowRun, stepTemplate);
      createPersistedWorkflowState(workflowRun, stepTemplate);

      // Act
      workflowEndService.forceCompleteWorkflowByTimeout(workflowRun);

      // Assert - DB state
      Workflow result = workflowRepository.findById(workflowRun.getId()).orElseThrow();
      assertEquals(WorkflowStatus.END, result.getStatus());
      assertEquals(
          StepStatus.END, stepRepository.findById(stepReady.getId()).orElseThrow().getStatus());
      assertEquals(
          StepStatus.END, stepRepository.findById(stepRun.getId()).orElseThrow().getStatus());
      assertTrue(stepDelayQueueRepository.findAllByWorkflowRun(workflowRun).isEmpty());
      assertNull(
          workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(
              stepTemplate.getId(), workflowRun.getId()));
      assertEquals(
          ExerciseStatus.FINISHED,
          exerciseRepository
              .findById(workflowRun.getSimulation().getId())
              .orElseThrow()
              .getStatus());

      // Assert - exact operational log lines an operator sees in production (see ADR-007)
      assertTrue(anyLogContains("Timeout expired for workflow run"));
      assertTrue(anyLogContains("Stop 2 active step(s)"));
      assertTrue(anyLogContains("force-completed due to TIMEOUT"));
      assertTrue(anyLogContains("2 step delay queue entries"));
      assertTrue(anyLogContains("have been deleted due to TIMEOUT"));
      assertTrue(anyLogContains("Stop 0 active inject(s)"));
      assertTrue(anyLogContains("due to workflow TIMEOUT"));
      assertTrue(anyLogContains("asset agent jobs"));
      assertTrue(anyLogContains("have been deleted due to TIMEOUT"));
      assertTrue(anyLogContains("1 workflow states"));
      assertTrue(anyLogContains("finished due to workflow TIMEOUT"));
      assertTrue(errorMessages().isEmpty(), "No error expected for a clean TIMEOUT end");
    }
  }

  // ========================================================================
  // CANCELED
  // ========================================================================
  @Nested
  @DisplayName("CANCELED")
  class CanceledTests {

    @Test
    @DisplayName(
        "given_runningWorkflowWithActiveStepsDelayQueueAndActiveInject_should_forceCompleteWithoutFinishingSimulation")
    void
        given_runningWorkflowWithActiveStepsDelayQueueAndActiveInject_should_forceCompleteWithoutFinishingSimulation() {
      // Arrange - the exercise is already CANCELED by the user before this cleanup runs (real
      // production sequencing documented in ADR-007: ExerciseService.changeExerciseStatus sets
      // the exercise to CANCELED first, then calls WorkflowService.cancelSimulationEndWorkflowRun)
      Workflow workflowRun = createPersistedRunWorkflow();
      workflowRun.getSimulation().setStatus(ExerciseStatus.CANCELED);
      exerciseRepository.save(workflowRun.getSimulation());

      Step stepReady = createPersistedStep(workflowRun, StepStatus.READY);
      Step stepRun = createPersistedStep(workflowRun, StepStatus.RUN);
      Step stepTemplate = createPersistedStep(workflowRun, StepStatus.TEMPLATE);
      createPersistedDelayQueueEntry(workflowRun, stepTemplate);
      createPersistedDelayQueueEntry(workflowRun, stepTemplate);
      createPersistedDelayQueueEntry(workflowRun, stepTemplate);
      createPersistedWorkflowState(workflowRun, stepTemplate);
      createPersistedWorkflowState(workflowRun, null);
      Inject activeInject = createPersistedActiveInject(workflowRun.getSimulation());

      // Act
      workflowService.cancelSimulationEndWorkflowRun(List.of(workflowRun));

      // Assert - DB state
      Workflow result = workflowRepository.findById(workflowRun.getId()).orElseThrow();
      assertEquals(WorkflowStatus.END, result.getStatus());
      assertEquals(
          StepStatus.END, stepRepository.findById(stepReady.getId()).orElseThrow().getStatus());
      assertEquals(
          StepStatus.END, stepRepository.findById(stepRun.getId()).orElseThrow().getStatus());
      assertTrue(stepDelayQueueRepository.findAllByWorkflowRun(workflowRun).isEmpty());
      assertNull(
          workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(
              stepTemplate.getId(), workflowRun.getId()));
      assertEquals(
          ExecutionStatus.ERROR,
          injectRepository
              .findById(activeInject.getId())
              .orElseThrow()
              .getStatus()
              .orElseThrow()
              .getName());
      // The simulation was already stopped by the user: cancellation must NOT flip it to
      // FINISHED (stopSimulationByEndWorkflow no-ops for CANCELED, per ADR-007).
      assertEquals(
          ExerciseStatus.CANCELED,
          exerciseRepository
              .findById(workflowRun.getSimulation().getId())
              .orElseThrow()
              .getStatus());

      // Assert - exact operational log lines an operator sees in production (see ADR-007)
      assertTrue(anyLogContains("Stop 2 active step(s)"));
      assertTrue(anyLogContains("force-completed due to CANCELED"));
      assertTrue(anyLogContains("3 step delay queue entries"));
      assertTrue(anyLogContains("have been deleted due to CANCELED"));
      assertTrue(anyLogContains("Stop 1 active inject(s)"));
      assertTrue(anyLogContains("due to workflow CANCELED"));
      assertTrue(anyLogContains("asset agent jobs"));
      assertTrue(anyLogContains("have been deleted due to CANCELED"));
      assertTrue(anyLogContains("2 workflow states"));
      assertFalse(
          anyLogContains("finished due to workflow CANCELED"),
          "CANCELED must never log a simulation-finished line: the user already stopped it");
      assertTrue(errorMessages().isEmpty(), "No error expected for a clean CANCELED end");
    }
  }

  // ========================================================================
  // NO_MORE_PROGRESS (natural end)
  // ========================================================================
  @Nested
  @DisplayName("NO_MORE_PROGRESS")
  class NoMoreProgressTests {

    @Test
    @DisplayName(
        "given_runningWorkflowWithNoStepTemplateLeftAndNoActiveWork_should_endNaturallyWithoutStepOrDelayQueueLogs")
    void
        given_runningWorkflowWithNoStepTemplateLeftAndNoActiveWork_should_endNaturallyWithoutStepOrDelayQueueLogs()
            throws Exception {
      // Arrange - a workflow template with zero step templates is the simplest, still-realistic
      // way to reach the "natural end" branch of evaluateWorkflowProgress (empty stepsTemplate),
      // exactly like the last step of a real chain finishing with no follow-up defined.
      Workflow workflowRun = createPersistedRunWorkflow();

      Workflow template = WorkflowFixture.getDefaultWorkflowTemplate();
      workflowComposer
          .forWorkflow(template)
          .withSimulation(exerciseComposer.forExercise(workflowRun.getSimulation()))
          .persist();

      workflowRun.setWorkflowTemplate(template);
      workflowRun = workflowRepository.save(workflowRun);

      // Act
      Workflow result = workflowService.evaluateWorkflowProgress(workflowRun);

      // Assert - DB state
      assertEquals(WorkflowStatus.END, result.getStatus());
      assertEquals(
          ExerciseStatus.FINISHED,
          exerciseRepository
              .findById(workflowRun.getSimulation().getId())
              .orElseThrow()
              .getStatus());

      // Assert - exact operational log lines an operator sees in production (see ADR-007):
      // with zero active steps and an empty delay queue, endActiveStepsByWorkflowId and
      // deleteAllByWorkflowRun stay completely silent (no INFO, no ERROR) - only the
      // active-inject/asset-agent-jobs/workflow-states/simulation-finished stages log.
      assertTrue(anyLogContains("No step template for workflow template"));
      assertTrue(anyLogContains("Stop 0 active inject(s)"));
      assertTrue(anyLogContains("due to workflow NO_MORE_PROGRESS"));
      assertTrue(anyLogContains("asset agent jobs"));
      assertTrue(anyLogContains("have been deleted due to NO_MORE_PROGRESS"));
      assertTrue(anyLogContains("0 workflow states"));
      assertTrue(anyLogContains("finished due to workflow NO_MORE_PROGRESS"));
      assertTrue(anyLogContains("Stop 0 active step(s)."));
      assertTrue(anyLogContains("0 step delay queue entries"));
      assertTrue(errorMessages().isEmpty(), "No error expected for a clean natural end");
    }
  }

  // ========================================================================
  // Helpers
  // ========================================================================

  private Workflow createPersistedRunWorkflow() {
    Workflow workflowRun = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);
    workflowRun.setTimeoutEnabled(true);
    workflowRun.setTimeoutSeconds(3600L);

    ExerciseComposer.Composer simComposer =
        exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise());

    return workflowComposer.forWorkflow(workflowRun).withSimulation(simComposer).persist().get();
  }

  private Step createPersistedStep(Workflow workflow, StepStatus status) {
    Step step =
        Step.builder()
            .stepAction(StepActionClass.INJECT_EXECUTION)
            .status(status)
            .workflow(workflow)
            .limitExecution(1)
            .build();
    return stepRepository.save(step);
  }

  private StepDelayQueue createPersistedDelayQueueEntry(Workflow workflowRun, Step stepTemplate) {
    StepDelayQueue delayEntry =
        StepDelayQueue.builder()
            .workflowRun(workflowRun)
            .stepTemplate(stepTemplate)
            .input("{}")
            .now(Instant.now())
            .goal(Instant.now().plus(1, ChronoUnit.HOURS))
            .delay(3600000L)
            .build();
    return stepDelayQueueRepository.save(delayEntry);
  }

  private WorkflowState createPersistedWorkflowState(Workflow workflowRun, Step stepTemplate) {
    WorkflowState state =
        WorkflowState.builder()
            .workflowExecution(workflowRun)
            .stepTemplate(stepTemplate)
            .entries("{}")
            .build();
    return workflowStateRepository.save(state);
  }

  private Inject createPersistedActiveInject(Exercise simulation) {
    Inject inject = InjectFixture.getDefaultInject();
    return injectComposer
        .forInject(inject)
        .withExercise(exerciseComposer.forExercise(simulation))
        .withInjectStatus(
            injectStatusComposer.forInjectStatus(InjectStatusFixture.createQueuingInjectStatus()))
        .persist()
        .get();
  }
}
