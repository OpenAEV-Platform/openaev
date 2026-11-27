package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.SecurityCoverageRepository;
import io.openaev.database.repository.SecurityCoverageSendJobRepository;
import io.openaev.database.repository.SettingRepository;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utilstest.RowTenantScope;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InjectsFinalizationJobTest extends IntegrationTest {

  @Autowired private InjectsFinalizationJob job;

  @Autowired private InjectRepository injectRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private SecurityCoverageSendJobRepository securityCoverageSendJobRepository;
  @Autowired private SecurityCoverageRepository securityCoverageRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private RowTenantScope rowTenantScope;
  @Autowired private SettingRepository settingRepository;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;

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
  /**
   * Runs fixture writes, clean-ups and reads in the default tenant's scope, where every fixture of
   * this class lives: in a transaction of their own when the test runs outside one, in the test's
   * transaction otherwise.
   */
  private void inTransaction(Runnable work) {
    rowTenantScope.write(Tenant.DEFAULT_TENANT_UUID, work);
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

    /**
     * These run {@code @Transactional(NOT_SUPPORTED)} for the same reason the auto-closing tests
     * above do: {@code handlePendingInject} now finalizes each inject inside its own tenant scope
     * through {@code TenantScopedJobRunner}, and that primitive refuses to open inside an already
     * active transaction. Rows are committed for real, hence the explicit sweep in each finally.
     */
    private String seedPendingInject(java.util.function.Supplier<Inject> arrange) {
      String[] injectId = new String[1];
      inTransaction(
          () -> {
            injectId[0] = arrange.get().getId();
            entityManager.flush();
          });
      return injectId[0];
    }

    private void sweep(String injectId) {
      inTransaction(() -> injectRepository.deleteById(injectId));
      injectComposer.reset();
      injectStatusComposer.reset();
      executionTraceComposer.reset();
      endpointComposer.reset();
      agentComposer.reset();
    }

    private InjectStatus reload(String injectId) {
      return injectRepository.findById(injectId).orElseThrow().getStatus().orElseThrow();
    }

    @Test
    @DisplayName("given pending inject without traces should mark status as error with timeout")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void given_pendingInjectWithoutTraces_should_markStatusAsMaybePrevented() {
      String injectId =
          seedPendingInject(
              () -> {
                InjectStatus statusToSave = InjectStatusFixture.createPendingInjectStatus();
                statusToSave.setTrackingSentDate(Instant.now().minus(20, ChronoUnit.MINUTES));
                return injectComposer
                    .forInject(InjectFixture.getDefaultInject())
                    .withInjectStatus(injectStatusComposer.forInjectStatus(statusToSave))
                    .persist()
                    .get();
              });
      try {
        job.handlePendingInject();
        inTransaction(() -> assertEquals(ExecutionStatus.ERROR, reload(injectId).getName()));
      } finally {
        sweep(injectId);
      }
    }

    @Test
    @DisplayName(
        "given agentless pending inject should mark status error with an explicit agentless timeout trace")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void given_agentlessPendingInject_should_addAgentlessTimeoutTrace() {
      // An inject with no endpoint or agent (network scanner style, e.g. Nuclei) stuck PENDING past
      // the threshold: getAgentsByInject returns empty, so the per-agent timeout loop records
      // nothing and the agentless trace is what explains the red inject.
      String injectId =
          seedPendingInject(
              () -> {
                InjectStatus statusToSave = InjectStatusFixture.createPendingInjectStatus();
                statusToSave.setTrackingSentDate(Instant.now().minus(20, ChronoUnit.MINUTES));
                return injectComposer
                    .forInject(InjectFixture.getDefaultInject())
                    .withInjectStatus(injectStatusComposer.forInjectStatus(statusToSave))
                    .persist()
                    .get();
              });
      try {
        job.handlePendingInject();
        inTransaction(
            () -> {
              InjectStatus savedStatus = reload(injectId);
              assertEquals(ExecutionStatus.ERROR, savedStatus.getName());
              assertTrue(
                  savedStatus.getTraces().stream()
                      .anyMatch(
                          trace ->
                              ExecutionTraceStatus.TIMEOUT.equals(trace.getStatus())
                                  && ExecutionTraceAction.COMPLETE.equals(trace.getAction())
                                  && trace.getAgent() == null
                                  && trace.getMessage().contains("did not complete within the")));
            });
      } finally {
        sweep(injectId);
      }
    }

    @Test
    @DisplayName(
        "given agentless pending inject with only a not targeted trace should still add the"
            + " agentless timeout trace")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void given_agentlessPendingInjectWithNotTargetedTrace_should_addAgentlessTimeoutTrace() {
      // The not targeted trace is a COMPLETE trace written at dispatch: it must not be taken for
      // a terminal outcome reported by the injector.
      String injectId =
          seedPendingInject(
              () -> {
                InjectStatus statusToSave = InjectStatusFixture.createPendingInjectStatus();
                statusToSave.setTrackingSentDate(Instant.now().minus(20, ChronoUnit.MINUTES));
                return injectComposer
                    .forInject(InjectFixture.getDefaultInject())
                    .withInjectStatus(
                        injectStatusComposer
                            .forInjectStatus(statusToSave)
                            .withExecutionTrace(
                                executionTraceComposer.forExecutionTrace(
                                    new ExecutionTrace(
                                        null,
                                        ExecutionTraceStatus.ASSET_CLEARANCE_RESTRICTED,
                                        List.of("asset-red"),
                                        "Asset red not targeted",
                                        ExecutionTraceAction.COMPLETE,
                                        null,
                                        null))))
                    .persist()
                    .get();
              });
      try {
        job.handlePendingInject();
        inTransaction(
            () ->
                assertTrue(
                    reload(injectId).getTraces().stream()
                        .anyMatch(
                            trace ->
                                ExecutionTraceStatus.TIMEOUT.equals(trace.getStatus())
                                    && trace.getAgent() == null)));
      } finally {
        sweep(injectId);
      }
    }

    @Test
    @DisplayName(
        "given pending inject without complete traces should mark status as error with timeout")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void given_pendingInjectWithoutCompleteTraces_should_markStatusAsMaybePrevented() {
      String injectId =
          seedPendingInject(
              () -> {
                AgentComposer.Composer agentComposerRef =
                    agentComposer.forAgent(AgentFixture.createDefaultAgentService());
                InjectStatus statusToSave = InjectStatusFixture.createPendingInjectStatus();
                statusToSave.setTrackingSentDate(Instant.now().minus(20, ChronoUnit.MINUTES));
                return injectComposer
                    .forInject(InjectFixture.getDefaultInject())
                    .withEndpoint(
                        endpointComposer
                            .forEndpoint(EndpointFixture.createEndpoint())
                            .withAgent(agentComposerRef))
                    .withInjectStatus(
                        injectStatusComposer
                            .forInjectStatus(statusToSave)
                            .withExecutionTrace(
                                executionTraceComposer
                                    .forExecutionTrace(
                                        ExecutionTraceFixture.createDefaultExecutionTraceStart())
                                    .withAgent(agentComposerRef)))
                    .persist()
                    .get();
              });
      try {
        job.handlePendingInject();
        inTransaction(
            () -> {
              InjectStatus savedStatus = reload(injectId);
              assertEquals(ExecutionStatus.ERROR, savedStatus.getName());
              // Launched with no expected count: the timed-out agent is counted on close
              assertEquals(1, savedStatus.getExpectedAgentCount());
              assertTrue(
                  savedStatus.getTraces().stream()
                      .anyMatch(
                          trace ->
                              ExecutionTraceStatus.TIMEOUT.equals(trace.getStatus())
                                  && trace.getMessage().contains("did not respond within the")));
            });
      } finally {
        sweep(injectId);
      }
    }

    private String seedAgentlessPendingInject(int minutesAgo) {
      return seedPendingInject(
          () -> {
            InjectStatus statusToSave = InjectStatusFixture.createPendingInjectStatus();
            statusToSave.setTrackingSentDate(Instant.now().minus(minutesAgo, ChronoUnit.MINUTES));
            return injectComposer
                .forInject(InjectFixture.getDefaultInject())
                .withInjectStatus(injectStatusComposer.forInjectStatus(statusToSave))
                .persist()
                .get();
          });
    }

    /** Writes (or, with null, removes) the platform row of the inject execution threshold. */
    private void setThresholdSetting(String value) {
      String key = SettingKeys.INJECT_EXECUTION_THRESHOLD_MINUTES.key();
      tenantScopedTransaction.executeAsPlatformWrite(
          TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
          () -> {
            Optional<Setting> current = settingRepository.findByKeyAndTenantIsNull(key);
            if (value == null) {
              current.ifPresent(settingRepository::delete);
              return;
            }
            Setting setting = current.orElseGet(() -> new Setting(key, value));
            setting.setValue(value);
            settingRepository.save(setting);
          });
    }

    private boolean hasAgentlessTimeoutTraceNaming(InjectStatus status, int minutes) {
      return status.getTraces().stream()
          .anyMatch(
              trace ->
                  ExecutionTraceStatus.TIMEOUT.equals(trace.getStatus())
                      && trace.getAgent() == null
                      && trace.getMessage().contains("within the " + minutes + " minutes"));
    }

    @Test
    @DisplayName(
        "given threshold setting unset should time out an agentless inject past the configured"
            + " threshold")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void given_thresholdSettingUnset_should_useConfiguredThreshold() {
      setThresholdSetting(null);
      String injectId = seedAgentlessPendingInject(11);
      try {
        job.handlePendingInject();
        inTransaction(
            () -> {
              InjectStatus savedStatus = reload(injectId);
              assertEquals(ExecutionStatus.ERROR, savedStatus.getName());
              assertTrue(
                  hasAgentlessTimeoutTraceNaming(
                      savedStatus,
                      InjectsExecutionJob.DEFAULT_EXECUTION_THRESHOLD_TIME_IN_MINUTES));
            });
      } finally {
        sweep(injectId);
      }
    }

    @Test
    @DisplayName(
        "given threshold setting changed at runtime should keep a younger inject pending and name"
            + " the new threshold in the timeout trace")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void given_thresholdSettingChangedAtRuntime_should_applyItAtTheNextRun() {
      String youngInjectId = seedAgentlessPendingInject(11);
      String oldInjectId = seedAgentlessPendingInject(61);
      try {
        setThresholdSetting("60");

        job.handlePendingInject();

        inTransaction(
            () -> {
              assertEquals(ExecutionStatus.PENDING, reload(youngInjectId).getName());
              InjectStatus oldStatus = reload(oldInjectId);
              assertEquals(ExecutionStatus.ERROR, oldStatus.getName());
              assertTrue(hasAgentlessTimeoutTraceNaming(oldStatus, 60));
            });
      } finally {
        setThresholdSetting(null);
        sweep(youngInjectId);
        sweep(oldInjectId);
      }
    }

    @Test
    @DisplayName("given pending inject with complete traces should compute final status")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void given_pendingInjectWithCompleteTraces_should_computeFinalStatus() {
      String injectId =
          seedPendingInject(
              () -> {
                AgentComposer.Composer agentComposerRef =
                    agentComposer.forAgent(AgentFixture.createDefaultAgentService());
                InjectStatus statusToSave = InjectStatusFixture.createPendingInjectStatus();
                statusToSave.setTrackingSentDate(Instant.now().minus(20, ChronoUnit.MINUTES));
                return injectComposer
                    .forInject(InjectFixture.getDefaultInject())
                    .withEndpoint(
                        endpointComposer
                            .forEndpoint(EndpointFixture.createEndpoint())
                            .withAgent(agentComposerRef))
                    .withInjectStatus(
                        injectStatusComposer
                            .forInjectStatus(statusToSave)
                            .withExecutionTrace(
                                executionTraceComposer
                                    .forExecutionTrace(
                                        ExecutionTraceFixture.createDefaultExecutionTraceComplete())
                                    .withAgent(agentComposerRef)))
                    .persist()
                    .get();
              });
      try {
        job.handlePendingInject();
        inTransaction(
            () -> {
              InjectStatus savedStatus = reload(injectId);
              assertEquals(ExecutionStatus.EXECUTED, savedStatus.getName());
              assertTrue(
                  savedStatus.getTraces().stream()
                      .noneMatch(trace -> ExecutionTraceStatus.WARNING.equals(trace.getStatus())));
            });
      } finally {
        sweep(injectId);
      }
    }
  }
}
