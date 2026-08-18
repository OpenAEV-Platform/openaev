package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.IntegrationTest;
import io.openaev.aop.audit_log.AuditEvent;
import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.database.model.*;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.ComcheckRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.SecurityCoverageRepository;
import io.openaev.database.repository.SecurityCoverageSendJobRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.helper.InjectHelper;
import io.openaev.integration.Manager;
import io.openaev.integration.ManagerFactory;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = {"openaev.audit-logs.transports=console"})
class InjectsExecutionJobTest extends IntegrationTest {

  @Autowired private InjectsExecutionJob job;

  @Autowired private ExerciseService exerciseService;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectHelper injectHelper;

  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private TeamComposer teamComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private EntityManager entityManager;
  @Autowired private SecurityCoverageSendJobRepository securityCoverageSendJobRepository;
  @Autowired private SecurityCoverageComposer securityCoverageComposer;
  @Autowired private ExecutionTraceComposer executionTraceComposer;

  @Autowired private ComchecksExecutionJob comchecksExecutionJob;
  @Autowired private ComcheckRepository comcheckRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private AgentRepository agentRepository;
  @Autowired private SecurityCoverageRepository securityCoverageRepository;
  @Autowired private PlatformTransactionManager transactionManager;
  @MockitoSpyBean private ManagerFactory managerFactory;

  @MockitoSpyBean private AuditLogger auditLogger;
  @MockitoSpyBean private HealthCheckUtils healthCheckUtils;

  @BeforeEach
  void setUpAuditLogger() {
    doNothing().when(auditLogger).logEvent(any(AuditEvent.class));
  }

  @AfterEach
  void resetMocks() {
    Mockito.reset(managerFactory, auditLogger, healthCheckUtils);
  }

  /**
   * These 3 tests below call {@code job.execute(null)} which internally opens its own transaction
   * via {@code TenantScopedTransaction} (needed since {@code InjectHelper.getInjectsToRun()} now
   * uses it to set the v2 tenant-scope GUC for the cross-tenant executor join). That primitive
   * refuses to run inside an already-active transaction, so these methods are declared
   * {@code @Transactional(NOT_SUPPORTED)} to suspend the class-level {@code @Transactional} for
   * their duration. Setup/assertions that still need a transaction (entity relations, explicit
   * flush) run in their own short-lived transaction via this helper; rows are committed for real,
   * hence the manual cleanup in each test's {@code finally} block.
   */
  private void inTransaction(Runnable work) {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
  }

  @DisplayName("Not start children injects at the same time as parent injects")
  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void given_cron_in_one_minute_should_not_start_children_injects() throws JobExecutionException {
    // -- PREPARE --
    String[] ids = new String[3]; // exerciseId, injectParentId, injectChildrenId
    inTransaction(
        () -> {
          Exercise exercise = ExerciseFixture.getExercise();
          exercise.setStart(Instant.now().minus(1, ChronoUnit.MINUTES));
          Exercise exerciseSaved = this.exerciseService.createExercise(exercise);
          Inject injectParent =
              injectComposer
                  .forInject(InjectFixture.getDefaultInject())
                  .withEndpoint(
                      endpointComposer
                          .forEndpoint(EndpointFixture.createEndpoint())
                          .withAgent(
                              agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
                          .withAgent(
                              agentComposer.forAgent(AgentFixture.createDefaultAgentSession())))
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
                          .withAgent(
                              agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
                          .withAgent(
                              agentComposer.forAgent(AgentFixture.createDefaultAgentSession())))
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

          injectRepository.saveAll(new ArrayList<>(List.of(injectParent, injectChildren)));
          entityManager.flush();

          ids[0] = exerciseSaved.getId();
          ids[1] = injectParent.getId();
          ids[2] = injectChildren.getId();
        });

    try {
      // -- EXECUTE --
      this.job.execute(null);

      // -- ASSERT --
      inTransaction(
          () -> {
            List<Inject> injectsSaved = injectRepository.findByExerciseId(ids[0]);
            Optional<Inject> savedInjectParent =
                injectsSaved.stream().filter(inject -> inject.getId().equals(ids[1])).findFirst();
            Optional<Inject> savedInjectChildren =
                injectsSaved.stream().filter(inject -> inject.getId().equals(ids[2])).findFirst();
            // Checking that only the parent inject has a status
            assertTrue(savedInjectParent.isPresent());
            assertTrue(savedInjectChildren.isPresent());

            assertTrue(savedInjectParent.get().getStatus().isPresent());
            assertTrue(savedInjectChildren.get().getStatus().isEmpty());

            assertNotNull(savedInjectParent.get().getStatus().get().getName());
          });
    } finally {
      // Committed rows (job opens its own transactions via TenantScopedTransaction, which refuses
      // to run inside the class-level @Transactional): sweep them explicitly, no auto-rollback.
      inTransaction(
          () -> {
            exerciseRepository.deleteById(ids[0]);
            endpointRepository.deleteAll(endpointComposer.generatedItems);
          });
      injectComposer.reset();
      endpointComposer.reset();
      agentComposer.reset();
      injectStatusComposer.reset();
    }
  }

  @Test
  @DisplayName("When auto closing of stix-created simulation, trigger stix coverage job")
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void whenAutoClosingStixCreatedSimulation_TriggerStixCoverageJob()
      throws JobExecutionException {
    String[] exerciseId = new String[1];
    inTransaction(
        () -> {
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

          exerciseId[0] = exerciseWrapper.get().getId();
        });

    try {
      this.job.execute(null);

      // assert
      inTransaction(
          () -> {
            Optional<SecurityCoverageSendJob> job =
                securityCoverageSendJobRepository.findBySimulation(
                    exerciseRepository.getReferenceById(exerciseId[0]));
            assertThat(job).isNotEmpty();
          });
    } finally {
      // Committed rows (job opens its own transactions via TenantScopedTransaction, which refuses
      // to run inside the class-level @Transactional): sweep them explicitly, no auto-rollback.
      inTransaction(
          () -> {
            exerciseRepository.deleteById(exerciseId[0]);
            securityCoverageRepository.deleteAll(securityCoverageComposer.generatedItems);
          });
      exerciseComposer.reset();
      injectComposer.reset();
      injectStatusComposer.reset();
      securityCoverageComposer.reset();
    }
  }

  @Test
  @DisplayName(
      "When auto closing of NON stix-created simulation, DOES NOT trigger stix coverage job")
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void whenAutoClosingNONStixCreatedSimulation_DoesNotTriggerStixCoverageJob()
      throws JobExecutionException {
    String[] exerciseId = new String[1];
    inTransaction(
        () -> {
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

          exerciseId[0] = exerciseWrapper.get().getId();
        });

    try {
      this.job.execute(null);

      // assert
      inTransaction(
          () -> {
            Optional<SecurityCoverageSendJob> job =
                securityCoverageSendJobRepository.findBySimulation(
                    exerciseRepository.getReferenceById(exerciseId[0]));
            assertThat(job).isEmpty();
          });
    } finally {
      // Committed rows (job opens its own transactions via TenantScopedTransaction, which refuses
      // to run inside the class-level @Transactional): sweep them explicitly, no auto-rollback.
      inTransaction(() -> exerciseRepository.deleteById(exerciseId[0]));
      exerciseComposer.reset();
      injectComposer.reset();
      injectStatusComposer.reset();
    }
  }

  @Test
  @DisplayName("buildComcheckEmail should set injector from email contract on the inject")
  void givenComcheckNeedingExecution_shouldBuildInjectWithInjectorSet()
      throws JobExecutionException {
    // -- ARRANGE --
    // Ensure the email injector contract exists in the database
    injectorContractFixture.getWellKnownSingleEmailContract();

    Exercise exercise = ExerciseFixture.getExercise();
    exercise.setStart(Instant.now().minus(1, ChronoUnit.MINUTES));
    Exercise exerciseSaved = exerciseService.createExercise(exercise);

    User user = userRepository.findAll().getFirst();

    Comcheck comcheck = new Comcheck();
    comcheck.setName("Test comcheck");
    comcheck.setStart(Instant.now().minus(1, ChronoUnit.HOURS));
    comcheck.setEnd(Instant.now().plus(1, ChronoUnit.HOURS));
    comcheck.setState(Comcheck.COMCHECK_STATUS.RUNNING);
    comcheck.setSubject("Comcheck subject");
    comcheck.setMessage("Comcheck message");
    comcheck.setExercise(exerciseSaved);

    ComcheckStatus status = new ComcheckStatus(user);
    status.setComcheck(comcheck);
    // lastSent = null, receiveDate = null => matches thatNeedExecution()
    comcheck.setComcheckStatus(new ArrayList<>(List.of(status)));
    comcheckRepository.save(comcheck);
    entityManager.flush();

    // Mock the email executor to capture the ExecutableInject
    io.openaev.executors.Injector mockEmailExecutor = mock(io.openaev.executors.Injector.class);
    Execution successExecution = new Execution(false);
    when(mockEmailExecutor.executeInjection(any())).thenReturn(successExecution);

    Manager mockManager = mock(Manager.class);
    when(mockManager.requestEmailInjector()).thenReturn(mockEmailExecutor);
    doReturn(mockManager).when(managerFactory).getManager(anyString());

    // -- ACT --
    comchecksExecutionJob.execute(null);

    // -- ASSERT --
    ArgumentCaptor<ExecutableInject> captor = ArgumentCaptor.forClass(ExecutableInject.class);
    verify(mockEmailExecutor).executeInjection(captor.capture());

    Inject emailInject = captor.getValue().getInjection().getInject();
    assertNotNull(
        emailInject.getInjectorContract().orElse(null), "Injector contract should be set");
    assertNotNull(emailInject.getInjector(), "Injector should be set from the email contract");
  }

  @Nested
  @DisplayName("Audit logging for scheduled simulations and inject target resolution")
  class AuditLoggingTest {

    @Test
    @DisplayName(
        "given scheduled simulation from scenario should log SCHEDULED_LAUNCH with scenario context")
    void given_scheduledSimulationFromScenario_should_logScheduledLaunchWithScenarioContext() {
      // Arrange
      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
              .persist()
              .get();
      Exercise exercise =
          ExerciseFixture.createDefaultIncidentResponseExercise(Instant.now().minusSeconds(60));
      exercise.setScenario(scenario);
      exerciseComposer.forExercise(exercise).persist();
      entityManager.flush();
      clearInvocations(auditLogger);

      // Act
      job.handleAutoStartExercises();

      // Assert
      ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
      verify(auditLogger, atLeastOnce()).logEvent(eventCaptor.capture());
      AuditEvent event =
          eventCaptor.getAllValues().stream()
              .filter(e -> e.getEventScope() == AuditEventScope.SCHEDULED_LAUNCH)
              .findFirst()
              .orElseThrow();

      assertThat(event.getEventType()).isEqualTo(EventType.SYSTEM);
      assertThat(event.getResourceId()).isEqualTo(exercise.getId());
      assertThat(event.getContextData())
          .containsEntry("simulation_id", exercise.getId())
          .containsEntry("initiator", "scheduler")
          .containsEntry("scenario_id", scenario.getId())
          .containsEntry("scenario_name", scenario.getName());
    }

    @Test
    @DisplayName(
        "given scheduled simulation without scenario should log SCHEDULED_LAUNCH without scenario context")
    void
        given_scheduledSimulationWithoutScenario_should_logScheduledLaunchWithoutScenarioContext() {
      // Arrange
      Exercise exercise =
          ExerciseFixture.createDefaultIncidentResponseExercise(Instant.now().minusSeconds(60));
      exerciseComposer.forExercise(exercise).persist();
      entityManager.flush();
      clearInvocations(auditLogger);

      // Act
      job.handleAutoStartExercises();

      // Assert
      ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
      verify(auditLogger, atLeastOnce()).logEvent(eventCaptor.capture());
      AuditEvent event =
          eventCaptor.getAllValues().stream()
              .filter(e -> e.getEventScope() == AuditEventScope.SCHEDULED_LAUNCH)
              .findFirst()
              .orElseThrow();

      assertThat(event.getContextData())
          .containsEntry("simulation_id", exercise.getId())
          .containsEntry("initiator", "scheduler");
      assertThat(event.getContextData()).doesNotContainKeys("scenario_id", "scenario_name");
    }

    @Test
    @DisplayName("given inject targets should log TARGET_RESOLUTION with endpoint agent statuses")
    @SuppressWarnings("unchecked")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void given_injectTargets_should_logTargetResolutionWithEndpointStatuses() throws Exception {
      String[] ids = new String[3]; // exerciseId, injectId, endpointWithoutAgentId
      try {
        // Arrange
        inTransaction(
            () -> {
              Exercise savedExercise =
                  exerciseComposer
                      .forExercise(
                          ExerciseFixture.createRunningAttackExercise(
                              Instant.now().minus(1, ChronoUnit.MINUTES)))
                      .persist()
                      .get();

              AgentComposer.Composer activeAgentComposer =
                  agentComposer.forAgent(AgentFixture.createDefaultAgentService());
              activeAgentComposer.get().setLastSeen(Instant.now());
              activeAgentComposer.get().setStatus(AgentStatus.ACTIVE);
              AgentComposer.Composer inactiveAgentComposer =
                  agentComposer.forAgent(AgentFixture.createDefaultAgentSession());
              inactiveAgentComposer.get().setLastSeen(Instant.now().minus(2, ChronoUnit.HOURS));
              inactiveAgentComposer.get().setStatus(AgentStatus.INACTIVE);

              EndpointComposer.Composer endpointWithoutAgentComposer =
                  endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
              EndpointComposer.Composer endpointWithAgentsComposer =
                  endpointComposer
                      .forEndpoint(EndpointFixture.createEndpoint())
                      .withAgent(activeAgentComposer)
                      .withAgent(inactiveAgentComposer);

              InjectorContract injectorContract =
                  injectorContractFixture.getWellKnownSingleManualContract();

              Inject inject =
                  injectComposer
                      .forInject(InjectFixture.getInjectForEmailContract(injectorContract))
                      .withEndpoint(endpointWithoutAgentComposer)
                      .withEndpoint(endpointWithAgentsComposer)
                      .persist()
                      .get();

              inject.setExercise(savedExercise);
              injectRepository.save(inject);
              entityManager.flush();

              ids[0] = savedExercise.getId();
              ids[1] = inject.getId();
              ids[2] = endpointWithoutAgentComposer.get().getId();
            });

        clearInvocations(auditLogger);
        doReturn(List.of()).when(healthCheckUtils).runContentChecks(any(Inject.class));

        // Act
        job.executeInject(getExecutableInject(ids[1]));

        // Assert
        AuditEvent targetResolutionEvent = captureTargetResolutionEvent(ids[1]);

        assertThat(targetResolutionEvent.getEventType()).isEqualTo(EventType.EXECUTION);
        assertThat(targetResolutionEvent.getContextData())
            .containsEntry("inject_id", ids[1])
            .containsEntry("total_endpoints", 2);

        List<Map<String, Object>> endpoints =
            (List<Map<String, Object>>) targetResolutionEvent.getContextData().get("endpoints");
        Map<String, Object> agentlessEndpoint =
            endpoints.stream()
                .filter(endpoint -> ids[2].equals(endpoint.get("endpoint_id")))
                .findFirst()
                .orElseThrow();
        assertThat(agentlessEndpoint).containsEntry("status", "ASSET_AGENTLESS");

        Map<String, Object> endpointWithAgents =
            endpoints.stream()
                .filter(endpoint -> !ids[2].equals(endpoint.get("endpoint_id")))
                .findFirst()
                .orElseThrow();
        List<Map<String, Object>> agents =
            (List<Map<String, Object>>) endpointWithAgents.get("agents");
        Set<String> statuses =
            agents.stream()
                .map(agent -> String.valueOf(agent.get("status")))
                .collect(java.util.stream.Collectors.toSet());

        assertThat(statuses).containsExactlyInAnyOrder("AGENT_ACTIVE", "AGENT_INACTIVE");
      } finally {
        inTransaction(
            () -> {
              exerciseRepository.deleteById(ids[0]);
              endpointRepository.deleteAll(endpointComposer.generatedItems);
            });
        exerciseComposer.reset();
        injectComposer.reset();
        endpointComposer.reset();
        agentComposer.reset();
      }
    }

    @Test
    @DisplayName(
        "given inject team and player should log TARGET_RESOLUTION with team_ids and player_ids")
    @SuppressWarnings("unchecked")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void given_injectTeamAndPlayer_should_logTargetResolutionWithTeamAndPlayerIds()
        throws Exception {
      String[] ids = new String[3]; // exerciseId, injectId, teamId
      try {
        // Arrange
        inTransaction(
            () -> {
              UserComposer.Composer userComposerRef =
                  userComposer.forUser(UserFixture.getUserWithDefaultEmail());
              TeamComposer.Composer teamComposerRef =
                  teamComposer.forTeam(TeamFixture.getDefaultTeam()).withUser(userComposerRef);

              Exercise exercise =
                  exerciseComposer
                      .forExercise(
                          ExerciseFixture.createRunningAttackExercise(
                              Instant.now().minus(1, ChronoUnit.MINUTES)))
                      .withTeam(teamComposerRef)
                      .withTeamUsers()
                      .persist()
                      .get();

              InjectorContract injectorContract =
                  injectorContractFixture.getWellKnownSingleEmailContract();
              Inject inject =
                  injectComposer
                      .forInject(InjectFixture.getInjectForEmailContract(injectorContract))
                      .withTeam(teamComposerRef)
                      .persist()
                      .get();
              inject.setExercise(exercise);
              injectRepository.save(inject);
              entityManager.flush();

              ids[0] = exercise.getId();
              ids[1] = inject.getId();
              ids[2] = teamComposerRef.get().getId();
            });

        clearInvocations(auditLogger);
        doReturn(List.of()).when(healthCheckUtils).runContentChecks(any(Inject.class));

        // Act
        job.executeInject(getExecutableInject(ids[1]));

        // Assert
        AuditEvent targetResolutionEvent = captureTargetResolutionEvent(ids[1]);

        List<String> teamIds =
            (List<String>) targetResolutionEvent.getContextData().get("team_ids");
        assertThat(teamIds).contains(ids[2]);
      } finally {
        inTransaction(() -> exerciseRepository.deleteById(ids[0]));
        exerciseComposer.reset();
        injectComposer.reset();
        teamComposer.reset();
        userComposer.reset();
      }
    }

    private AuditEvent captureTargetResolutionEvent(String injectId) {
      ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
      verify(auditLogger, atLeastOnce()).logEvent(eventCaptor.capture());
      return eventCaptor.getAllValues().stream()
          .filter(e -> e.getEventScope() == AuditEventScope.TARGET_RESOLUTION)
          .filter(e -> injectId.equals(e.getResourceId()))
          .findFirst()
          .orElseThrow();
    }

    private ExecutableInject getExecutableInject(String injectId) {
      return injectHelper.getInjectsToRun().stream()
          .filter(execInject -> injectId.equals(execInject.getInjection().getInject().getId()))
          .findFirst()
          .orElseThrow();
    }
  }

  @Nested
  @DisplayName("handlePendingInject")
  class HandlePendingInjectTest {

    @Test
    @DisplayName("given pending inject without traces should mark status as error with timeout")
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
      assertEquals(ExecutionStatus.ERROR, savedStatus.getName());
    }

    @Test
    @DisplayName(
        "given agentless pending inject should mark status error with an explicit agentless timeout trace")
    void given_agentlessPendingInject_should_addAgentlessTimeoutTrace() {
      // Arrange: an inject with no endpoint/agent (network scanner style, e.g. Nuclei) stuck
      // PENDING past the threshold. getAgentsByInject returns empty, so the per-agent timeout loop
      // records nothing.
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

      // Assert: the inject is finalized ERROR, but now carries a clear agentless timeout trace
      // instead of an empty COMPLETE-trace list.
      Inject savedInject = injectRepository.findById(inject.getId()).orElseThrow();
      InjectStatus savedStatus = savedInject.getStatus().orElseThrow();
      assertEquals(ExecutionStatus.ERROR, savedStatus.getName());
      assertTrue(
          savedStatus.getTraces().stream()
              .anyMatch(
                  trace ->
                      ExecutionTraceStatus.TIMEOUT.equals(trace.getStatus())
                          && ExecutionTraceAction.COMPLETE.equals(trace.getAction())
                          && trace.getAgent() == null
                          && trace.getMessage().contains("did not complete within the")));
    }

    @Test
    @DisplayName(
        "given pending inject without complete traces should mark status as error with timeout")
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
      assertEquals(ExecutionStatus.ERROR, savedStatus.getName());
      assertTrue(
          savedStatus.getTraces().stream()
              .anyMatch(
                  trace ->
                      ExecutionTraceStatus.TIMEOUT.equals(trace.getStatus())
                          && trace.getMessage().contains("did not respond within the")));
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
      assertEquals(ExecutionStatus.EXECUTED, savedStatus.getName());
      assertTrue(
          savedStatus.getTraces().stream()
              .noneMatch(trace -> ExecutionTraceStatus.WARNING.equals(trace.getStatus())));
    }
  }
}
