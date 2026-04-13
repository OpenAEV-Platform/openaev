package io.openaev.scheduler.jobs;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectDependenciesRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.SecurityCoverageSendJobRepository;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.scheduler.jobs.exception.ErrorMessagesPreExecutionException;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InjectsExecutionJobTest extends IntegrationTest {

  @Autowired private InjectsExecutionJob job;

  @Autowired private ExerciseService exerciseService;
  @Autowired private InjectRepository injectRepository;

  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private EntityManager entityManager;
  @Autowired private SecurityCoverageSendJobRepository securityCoverageSendJobRepository;
  @Autowired private SecurityCoverageComposer securityCoverageComposer;
  @Autowired private ExecutionTraceComposer executionTraceComposer;

  @Mock private InjectDependenciesRepository injectDependenciesRepository;

  @BeforeAll
  public void init() {
    MockitoAnnotations.openMocks(this);
  }

  @DisplayName("Not start children injects at the same time as parent injects")
  @Test
  void given_cron_in_one_minute_should_not_start_children_injects() throws JobExecutionException {
    // -- PREPARE --
    Exercise exercise = ExerciseFixture.getExercise();
    exercise.setStart(Instant.now().minus(1, ChronoUnit.MINUTES));
    Exercise exerciseSaved = this.exerciseService.createExercise(exercise);
    Inject injectParent =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withEndpoint(
                endpointComposer
                    .forEndpoint(EndpointFixture.createEndpoint())
                    .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
                    .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentSession())))
            .withInjectStatus(
                injectStatusComposer.forInjectStatus(
                    InjectStatusFixture.createPendingInjectStatus()))
            .persist()
            .get();
    Inject injectChildren =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withEndpoint(
                endpointComposer
                    .forEndpoint(EndpointFixture.createEndpoint())
                    .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
                    .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentSession())))
            .withInjectStatus(
                injectStatusComposer.forInjectStatus(
                    InjectStatusFixture.createPendingInjectStatus()))
            .withDependsOn(injectParent)
            .persist()
            .get();
    entityManager.flush();

    injectParent.setExercise(exerciseSaved);
    injectChildren.setExercise(exerciseSaved);
    injectParent.setStatus(null);
    injectChildren.setStatus(null);
    exerciseSaved.setInjects(new ArrayList<>(List.of(injectParent, injectChildren)));
    String exerciseId = exerciseSaved.getId();

    injectRepository.saveAll(new ArrayList<>(List.of(injectParent, injectChildren)));
    entityManager.flush();
    // -- EXECUTE --
    this.job.execute(null);
    entityManager.flush();
    entityManager.clear();

    // -- ASSERT --
    List<Inject> injectsSaved = injectRepository.findByExerciseId(exerciseId);
    Optional<Inject> savedInjectParent =
        injectsSaved.stream()
            .filter(inject -> inject.getId().equals(injectParent.getId()))
            .findFirst();
    Optional<Inject> savedInjectChildren =
        injectsSaved.stream()
            .filter(inject -> inject.getId().equals(injectChildren.getId()))
            .findFirst();
    // Checking that only the parent inject has a status
    assertTrue(savedInjectParent.isPresent());
    assertTrue(savedInjectChildren.isPresent());

    assertTrue(savedInjectParent.get().getStatus().isPresent());
    assertTrue(savedInjectChildren.get().getStatus().isEmpty());

    assertNotNull(savedInjectParent.get().getStatus().get().getName());
  }

  @Test
  @DisplayName("When auto closing of stix-created simulation, trigger stix coverage job")
  public void whenAutoClosingStixCreatedSimulation_TriggerStixCoverageJob()
      throws JobExecutionException {
    ExerciseComposer.Composer exerciseWrapper =
        exerciseComposer
            .forExercise(ExerciseFixture.createDefaultExercise())
            .withSecurityCoverage(
                securityCoverageComposer.forSecurityCoverage(
                    SecurityCoverageFixture.createDefaultSecurityCoverage()))
            .withInject(
                injectComposer
                    .forInject(InjectFixture.getDefaultInject())
                    .withInjectStatus(
                        injectStatusComposer.forInjectStatus(
                            InjectStatusFixture.createSuccessStatus())))
            .withInject(
                injectComposer
                    .forInject(InjectFixture.getDefaultInject())
                    .withInjectStatus(
                        injectStatusComposer.forInjectStatus(
                            InjectStatusFixture.createSuccessStatus())));

    injectComposer.generatedItems.forEach(
        i -> i.setCollectExecutionStatus(CollectExecutionStatus.COMPLETED));
    exerciseWrapper.get().setStatus(ExerciseStatus.RUNNING);
    exerciseWrapper.persist();
    entityManager.flush();

    this.job.execute(null);
    entityManager.flush();
    entityManager.clear();

    // assert
    Optional<SecurityCoverageSendJob> job =
        securityCoverageSendJobRepository.findBySimulation(exerciseWrapper.get());
    assertThat(job).isNotEmpty();
  }

  @Test
  @DisplayName(
      "When auto closing of NON stix-created simulation, DOES NOT trigger stix coverage job")
  public void whenAutoClosingNONStixCreatedSimulation_DoesNotTriggerStixCoverageJob()
      throws JobExecutionException {
    ExerciseComposer.Composer exerciseWrapper =
        exerciseComposer
            .forExercise(ExerciseFixture.createDefaultExercise())
            .withInject(
                injectComposer
                    .forInject(InjectFixture.getDefaultInject())
                    .withInjectStatus(
                        injectStatusComposer.forInjectStatus(
                            InjectStatusFixture.createSuccessStatus())))
            .withInject(
                injectComposer
                    .forInject(InjectFixture.getDefaultInject())
                    .withInjectStatus(
                        injectStatusComposer.forInjectStatus(
                            InjectStatusFixture.createSuccessStatus())));

    injectComposer.generatedItems.forEach(
        i -> i.setCollectExecutionStatus(CollectExecutionStatus.COMPLETED));
    exerciseWrapper.get().setStatus(ExerciseStatus.RUNNING);
    exerciseWrapper.persist();
    entityManager.flush();

    this.job.execute(null);
    entityManager.flush();
    entityManager.clear();

    // assert
    Optional<SecurityCoverageSendJob> job =
        securityCoverageSendJobRepository.findBySimulation(exerciseWrapper.get());
    assertThat(job).isEmpty();
  }

  @Test
  @DisplayName(
      "When auto closing of NON stix-created simulation, DOES NOT trigger stix coverage job")
  public void shouldRaiseExceptionIfExpectationMalicious() {
    ReflectionTestUtils.setField(job, "injectDependenciesRepository", injectDependenciesRepository);
    Inject inject = injectComposer.forInject(InjectFixture.getDefaultInject()).get();
    inject.setId(UUID.randomUUID().toString());
    InjectDependency injectDependency = new InjectDependency();
    injectDependency
        .getCompositeId()
        .setInjectParent(
            InjectFixture.createInjectWithManualExpectation(
                InjectorContractFixture.createDefaultInjectorContract(),
                "parent",
                "T(java.lang.Runtime).getRuntime().exec('gedit');"));
    injectDependency.getCompositeId().setInjectChildren(InjectFixture.getDefaultInject());
    injectDependency.setInjectDependencyCondition(
        new InjectDependencyConditions.InjectDependencyCondition());
    InjectDependencyConditions.Condition condition = new InjectDependencyConditions.Condition();
    condition.setOperator(InjectDependencyConditions.DependencyOperator.eq);
    condition.setValue(true);
    condition.setKey("T(java.lang.Runtime).getRuntime().exec('gedit');");
    injectDependency.getInjectDependencyCondition().setConditions(List.of(condition));
    when(injectDependenciesRepository.findParents(any())).thenReturn(List.of(injectDependency));
    try {
      this.job.checkErrorMessagesPreExecution(UUID.randomUUID().toString(), inject);
      fail("Should have raised an exception");
    } catch (Exception e) {
      assertThat(e).isInstanceOf(ErrorMessagesPreExecutionException.class);
      assertThat(e.getMessage())
          .isEqualTo("There was an error during the evaluation of the condition of the inject");
    }
  }

  @Nested
  @DisplayName("handlePendingInject")
  class HandlePendingInjectTest {

    @Test
    @DisplayName("given pending inject without traces should mark status as maybe prevented")
    void given_pendingInjectWithoutTraces_should_markStatusAsMaybePrevented() {
      // Arrange
      InjectStatus statusToSave = InjectStatusFixture.createPendingInjectStatus();
      statusToSave.setTrackingSentDate(Instant.now().minus(20, ChronoUnit.MINUTES));
      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectStatus(injectStatusComposer.forInjectStatus(statusToSave))
              .persist()
              .get();
      entityManager.flush();

      // Act
      job.handlePendingInject();
      entityManager.flush();
      entityManager.clear();

      // Assert
      Inject savedInject = injectRepository.findById(inject.getId()).orElseThrow();
      InjectStatus savedStatus = savedInject.getStatus().orElseThrow();
      assertEquals(ExecutionStatus.MAYBE_PREVENTED, savedStatus.getName());
      assertTrue(
          savedStatus.getTraces().stream()
              .anyMatch(
                  trace ->
                      ExecutionTraceStatus.WARNING.equals(trace.getStatus())
                          && trace
                              .getMessage()
                              .contains("Execution delay detected: Inject exceeded the")));
    }

    @Test
    @DisplayName(
        "given pending inject without complete traces should mark status as maybe prevented")
    void given_pendingInjectWithoutCompleteTraces_should_markStatusAsMaybePrevented() {
      // Arrange
      AgentComposer.Composer agentComposerRef =
          agentComposer.forAgent(AgentFixture.createDefaultAgentService());
      InjectStatus statusToSave = InjectStatusFixture.createPendingInjectStatus();
      statusToSave.setTrackingSentDate(Instant.now().minus(20, ChronoUnit.MINUTES));
      InjectStatusComposer.Composer statusComposer =
          injectStatusComposer
              .forInjectStatus(statusToSave)
              .withExecutionTrace(
                  executionTraceComposer
                      .forExecutionTrace(ExecutionTraceFixture.createDefaultExecutionTraceStart())
                      .withAgent(agentComposerRef));

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(
                  endpointComposer
                      .forEndpoint(EndpointFixture.createEndpoint())
                      .withAgent(agentComposerRef))
              .withInjectStatus(statusComposer)
              .persist()
              .get();
      entityManager.flush();

      // Act
      job.handlePendingInject();
      entityManager.flush();
      entityManager.clear();

      // Assert
      Inject savedInject = injectRepository.findById(inject.getId()).orElseThrow();
      InjectStatus savedStatus = savedInject.getStatus().orElseThrow();
      assertEquals(ExecutionStatus.MAYBE_PREVENTED, savedStatus.getName());
      assertTrue(
          savedStatus.getTraces().stream()
              .anyMatch(
                  trace ->
                      ExecutionTraceStatus.WARNING.equals(trace.getStatus())
                          && trace
                              .getMessage()
                              .contains("Execution delay detected: Inject exceeded the")));
    }

    @Test
    @DisplayName("given pending inject with complete traces should compute final status")
    void given_pendingInjectWithCompleteTraces_should_computeFinalStatus() {
      // Arrange
      AgentComposer.Composer agentComposerRef =
          agentComposer.forAgent(AgentFixture.createDefaultAgentService());
      InjectStatus statusToSave = InjectStatusFixture.createPendingInjectStatus();
      statusToSave.setTrackingSentDate(Instant.now().minus(20, ChronoUnit.MINUTES));
      InjectStatusComposer.Composer statusComposer =
          injectStatusComposer
              .forInjectStatus(statusToSave)
              .withExecutionTrace(
                  executionTraceComposer
                      .forExecutionTrace(
                          ExecutionTraceFixture.createDefaultExecutionTraceComplete())
                      .withAgent(agentComposerRef));

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(
                  endpointComposer
                      .forEndpoint(EndpointFixture.createEndpoint())
                      .withAgent(agentComposerRef))
              .withInjectStatus(statusComposer)
              .persist()
              .get();
      entityManager.flush();

      // Act
      job.handlePendingInject();
      entityManager.flush();
      entityManager.clear();

      // Assert
      Inject savedInject = injectRepository.findById(inject.getId()).orElseThrow();
      InjectStatus savedStatus = savedInject.getStatus().orElseThrow();
      assertEquals(ExecutionStatus.SUCCESS, savedStatus.getName());
      assertTrue(
          savedStatus.getTraces().stream()
              .noneMatch(trace -> ExecutionTraceStatus.WARNING.equals(trace.getStatus())));
    }
  }
}
