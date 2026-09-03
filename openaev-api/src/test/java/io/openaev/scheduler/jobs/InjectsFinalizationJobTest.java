package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.SecurityCoverageRepository;
import io.openaev.database.repository.SecurityCoverageSendJobRepository;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InjectsFinalizationJobTest extends IntegrationTest {

  @Autowired private InjectsFinalizationJob job;

  @Autowired private InjectRepository injectRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private SecurityCoverageSendJobRepository securityCoverageSendJobRepository;
  @Autowired private SecurityCoverageRepository securityCoverageRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private SecurityCoverageComposer securityCoverageComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private ExecutionTraceComposer executionTraceComposer;

  /**
   * Auto-closing tests call {@code job.execute(null)}, which opens its own transaction via {@code
   * TenantScopedTransaction}; that primitive refuses to run inside an already-active transaction,
   * so they are declared {@code @Transactional(NOT_SUPPORTED)} and their setup/assertions run in
   * their own short-lived transaction here. Rows are committed for real, hence the manual cleanup
   * in each test's {@code finally} block.
   */
  private void inTransaction(Runnable work) {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
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
