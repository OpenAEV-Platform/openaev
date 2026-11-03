package io.openaev.service.chaining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Step;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.service.queue.BatchQueueService;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * A chaining event carries the tenant of the row it names, whatever the run's owner is. The
 * thread-local is set to a third tenant throughout, so neither it nor the consumer's default-tenant
 * fallback can pass for the right answer. Runs and steps are saved without an explicit flush, as
 * the chaining services do, so the stamp must see rows still pending in the session. The broker is
 * replaced by a captor for each test and restored after it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Chaining events: tenant stamped from the row")
class QueueChainingServiceTenantStampTest extends IntegrationTest {

  @Autowired private TenantRepository tenantRepository;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private StepRepository stepRepository;
  @Autowired private QueueChainingService queueChainingService;

  private BatchQueueService<StepEvent> readyQueue;
  private BatchQueueService<ExternalUpdateEvent> updateQueue;
  private BatchQueueService<StepEvent> originalReadyQueue;
  private BatchQueueService<ExternalUpdateEvent> originalUpdateQueue;
  private Tenant simulationTenant;
  private Tenant scenarioTenant;
  private Exercise simulation;
  private Scenario scenario;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    originalReadyQueue =
        (BatchQueueService<StepEvent>)
            ReflectionTestUtils.getField(queueChainingService, "readyQueueService");
    originalUpdateQueue =
        (BatchQueueService<ExternalUpdateEvent>)
            ReflectionTestUtils.getField(queueChainingService, "updateQueueService");
    readyQueue = mock(BatchQueueService.class);
    updateQueue = mock(BatchQueueService.class);
    queueChainingService.setReadyQueueService(readyQueue);
    queueChainingService.setUpdateQueueService(updateQueue);

    exerciseComposer.reset();
    scenarioComposer.reset();
    simulationTenant = persistTenant("stamp-simulation");
    scenarioTenant = persistTenant("stamp-scenario");
    Tenant threadTenant = persistTenant("stamp-thread");
    simulation = ExerciseFixture.createDefaultExercise();
    simulation.setTenant(simulationTenant);
    exerciseComposer.forExercise(simulation).persist();
    scenario = ScenarioFixture.getScenario();
    scenario.setTenant(scenarioTenant);
    scenarioComposer.forScenario(scenario).persist();
    TenantContext.setCurrentTenant(threadTenant.getId());
  }

  @AfterEach
  void restore() {
    TenantContext.clearCurrentTenant();
    queueChainingService.setReadyQueueService(originalReadyQueue);
    queueChainingService.setUpdateQueueService(originalUpdateQueue);
  }

  @Nested
  @DisplayName("Ready event")
  class ReadyEvent {

    @Test
    @DisplayName("A run attached to a scenario stamps the scenario's tenant")
    void given_scenarioRun_should_stampReadyEventWithScenarioTenant() throws Exception {
      // Arrange
      Workflow run = run(null, scenario);
      Step step = persistStep(run);

      // Act
      queueChainingService.readyStep(step, run);

      // Assert
      assertThat(publishedReadyEvent().getTenantId()).isEqualTo(scenarioTenant.getId());
    }

    @Test
    @DisplayName("A run attached to a simulation stamps the simulation's tenant")
    void given_simulationRun_should_stampReadyEventWithSimulationTenant() throws Exception {
      // Arrange
      Workflow run = run(simulation, null);
      Step step = persistStep(run);

      // Act
      queueChainingService.readyStep(step, run);

      // Assert
      assertThat(publishedReadyEvent().getTenantId()).isEqualTo(simulationTenant.getId());
    }
  }

  @Nested
  @DisplayName("Update event")
  class UpdateEvent {

    @Test
    @DisplayName("A step of a run attached to a scenario stamps the scenario's tenant")
    void given_stepOfScenarioRun_should_stampUpdateEventWithScenarioTenant() throws Exception {
      // Arrange
      Step step = persistStep(run(null, scenario));

      // Act
      queueChainingService.updateStep(step.getId());

      // Assert
      assertThat(publishedUpdateEvent().getTenantId()).isEqualTo(scenarioTenant.getId());
    }

    @Test
    @DisplayName("A step of a run attached to a simulation stamps the simulation's tenant")
    void given_stepOfSimulationRun_should_stampUpdateEventWithSimulationTenant() throws Exception {
      // Arrange
      Step step = persistStep(run(simulation, null));

      // Act
      queueChainingService.updateStep(step.getId());

      // Assert
      assertThat(publishedUpdateEvent().getTenantId()).isEqualTo(simulationTenant.getId());
    }

    @Test
    @DisplayName("An unknown step publishes an event with no tenant")
    void given_unknownStep_should_stampNoTenant() throws Exception {
      // Act
      queueChainingService.updateStep(UUID.randomUUID().toString());

      // Assert
      assertThat(publishedUpdateEvent().getTenantId()).isNull();
    }
  }

  // -- helpers --

  private Tenant persistTenant(String name) {
    return tenantRepository.save(
        TenantFixture.getTenant(name + "-" + UUID.randomUUID().toString().substring(0, 8)));
  }

  private Workflow run(Exercise owningSimulation, Scenario owningScenario) {
    Workflow run = WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN);
    run.setSimulation(owningSimulation);
    run.setScenario(owningScenario);
    return workflowRepository.save(run);
  }

  private Step persistStep(Workflow workflow) {
    Step step = StepFixture.getDefaultStepTemplate();
    step.setWorkflow(workflow);
    return stepRepository.save(step);
  }

  private StepEvent publishedReadyEvent() throws Exception {
    ArgumentCaptor<StepEvent> captor = ArgumentCaptor.forClass(StepEvent.class);
    verify(readyQueue).publish(captor.capture());
    return captor.getValue();
  }

  private ExternalUpdateEvent publishedUpdateEvent() throws Exception {
    ArgumentCaptor<ExternalUpdateEvent> captor = ArgumentCaptor.forClass(ExternalUpdateEvent.class);
    verify(updateQueue).publish(captor.capture());
    return captor.getValue();
  }
}
