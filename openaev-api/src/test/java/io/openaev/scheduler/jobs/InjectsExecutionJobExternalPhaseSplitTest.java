package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.openaev.IntegrationTest;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.helper.ExecutionTraceRepositoryHelper;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectStatusRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.executors.Executor;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.service.RabbitmqService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utilstest.RowTenantScope;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The external inject path phases the scheduler job into short scoped transactions (#6968): the
 * publish runs with no transaction active, and the outcome trace is written only after the
 * publish's real result is known.
 *
 * <p>Each test opens its own scoped transactions via {@code job.execute(null)} (through {@code
 * TenantScopedTransaction}), which refuses to run inside an already-active transaction, so the
 * methods below run with {@code @Transactional(NOT_SUPPORTED)} and sweep their committed rows by
 * hand, matching {@code InjectsExecutionJobTest}.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InjectsExecutionJobExternalPhaseSplitTest extends IntegrationTest {

  @Autowired private InjectsExecutionJob job;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private RowTenantScope rowTenantScope;

  @MockitoSpyBean private RabbitmqService rabbitmqService;
  @MockitoSpyBean private ConnectorInstanceService connectorInstanceService;
  @MockitoSpyBean private HealthCheckUtils healthCheckUtils;
  @MockitoSpyBean private Executor executor;
  @Autowired private InjectStatusRepository injectStatusRepository;
  @MockitoSpyBean private ExecutionTraceRepositoryHelper executionTraceRepositoryHelper;
  @MockitoSpyBean private InjectStatusService injectStatusService;
  @Autowired private InjectEventCapture eventCapture;

  private void inTransaction(Runnable work) {
    rowTenantScope.write(Tenant.DEFAULT_TENANT_UUID, work);
  }

  /** An external injector with a contract, and an atomic (no exercise) inject queued to run. */
  private record DueExternalInject(String injectId, String injectorContractId) {}

  private DueExternalInject persistDueAtomicInject() {
    String uniqueSuffix = java.util.UUID.randomUUID().toString();
    Injector externalInjector =
        InjectorFixture.createInjector(
            "ext-injector-phase-split-" + uniqueSuffix,
            "External Phase Split Injector",
            "external_phase_split_" + uniqueSuffix);
    externalInjector.setExternal(true);

    InjectorContract injectorContract = InjectorContractFixture.createDefaultInjectorContract();
    injectorContract.clearInjectors();
    injectorContract.addInjector(externalInjector);

    String[] ids = new String[2]; // injectId, injectorContractId
    inTransaction(
        () -> {
          InjectorContract savedContract =
              injectorContractComposer.forInjectorContract(injectorContract).persist().get();
          Inject inject =
              injectComposer
                  .forInject(
                      InjectFixture.createInject(savedContract, "External phase-split inject"))
                  .withInjectStatus(
                      injectStatusComposer.forInjectStatus(
                          InjectStatusFixture.createQueuingInjectStatus()))
                  .persist()
                  .get();
          ids[0] = inject.getId();
          ids[1] = savedContract.getId();
        });
    return new DueExternalInject(ids[0], ids[1]);
  }

  private void sweep(DueExternalInject due) {
    inTransaction(
        () -> {
          injectRepository.deleteById(due.injectId());
          injectorContractRepository
              .findById(due.injectorContractId())
              .ifPresent(injectorContractRepository::delete);
        });
    injectComposer.reset();
    injectStatusComposer.reset();
    injectorContractComposer.reset();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName(
      "the publish runs with no transaction active, and the trace follows a successful publish")
  void given_externalInject_should_publishWithNoTransactionActive_then_recordSuccess()
      throws JobExecutionException, IOException, TimeoutException {
    // Arrange
    doReturn(true)
        .when(connectorInstanceService)
        .hasStartedConnectorInstanceForInjector(any(String.class));
    doReturn(List.of()).when(healthCheckUtils).runContentChecks(any(Inject.class));
    AtomicBoolean transactionActiveDuringPublish = new AtomicBoolean(true);
    doAnswer(
            invocation -> {
              transactionActiveDuringPublish.set(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return null;
            })
        .when(rabbitmqService)
        .publish(any(String.class), any(String.class));
    DueExternalInject due = persistDueAtomicInject();

    try {
      // Act
      job.execute(null);

      // Assert
      assertThat(transactionActiveDuringPublish.get())
          .as("phase 1 must have committed and released its connection before the publish")
          .isFalse();

      InjectStatus status = injectStatusRepository.findByInjectId(due.injectId()).orElseThrow();
      assertThat(status.getTraces())
          .as("the confirmed-publish trace is written only after the publish succeeded")
          .anyMatch(
              trace ->
                  trace.getMessage() != null
                      && trace.getMessage().contains("waiting to be consumed"));
    } finally {
      sweep(due);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("a failed publish records the real error instead of a trace that claims success")
  void given_publishFailure_should_recordErrorStatus_not_publishedTrace()
      throws JobExecutionException, IOException, TimeoutException {
    // Arrange
    doReturn(true)
        .when(connectorInstanceService)
        .hasStartedConnectorInstanceForInjector(any(String.class));
    doReturn(List.of()).when(healthCheckUtils).runContentChecks(any(Inject.class));
    doThrow(new IOException("broker unreachable"))
        .when(rabbitmqService)
        .publish(any(String.class), any(String.class));
    DueExternalInject due = persistDueAtomicInject();

    try {
      // Act
      job.execute(null);

      // Assert
      InjectStatus status = injectStatusRepository.findByInjectId(due.injectId()).orElseThrow();
      assertThat(status.getTraces())
          .as("a failed publish must never leave a trace claiming it was published")
          .noneMatch(
              trace ->
                  trace.getMessage() != null
                      && trace.getMessage().contains("waiting to be consumed"));
      assertThat(status.getTraces())
          .as("the real publish failure is recorded instead")
          .anyMatch(
              trace ->
                  trace.getMessage() != null && trace.getMessage().contains("broker unreachable"));
      assertThat(status.getName()).isEqualTo(ExecutionStatus.ERROR);
    } finally {
      sweep(due);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("an unchecked exception from publish is recorded as a failure, not a batch abort")
  void given_publishRuntimeException_should_recordErrorStatus_notThrow()
      throws JobExecutionException, IOException, TimeoutException {
    // Arrange
    doReturn(true)
        .when(connectorInstanceService)
        .hasStartedConnectorInstanceForInjector(any(String.class));
    doReturn(List.of()).when(healthCheckUtils).runContentChecks(any(Inject.class));
    doThrow(new RuntimeException("malformed inject"))
        .when(rabbitmqService)
        .publish(any(String.class), any(String.class));
    DueExternalInject due = persistDueAtomicInject();

    try {
      // Act: must not throw even though publish raised an unchecked exception
      job.execute(null);

      // Assert
      InjectStatus status = injectStatusRepository.findByInjectId(due.injectId()).orElseThrow();
      assertThat(status.getTraces())
          .as("the real failure is recorded instead of a trace claiming success")
          .anyMatch(
              trace ->
                  trace.getMessage() != null && trace.getMessage().contains("malformed inject"));
      assertThat(status.getName()).isEqualTo(ExecutionStatus.ERROR);
    } finally {
      sweep(due);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("a failure recording one inject's outcome does not abort the rest of the batch")
  void given_recordOutcomeFailure_should_notAbortTheRestOfTheBatch()
      throws JobExecutionException, IOException, TimeoutException {
    // Arrange
    doReturn(true)
        .when(connectorInstanceService)
        .hasStartedConnectorInstanceForInjector(any(String.class));
    doReturn(List.of()).when(healthCheckUtils).runContentChecks(any(Inject.class));
    // The broker is not under test: a healthy publish, so the recording is what fails
    doNothing().when(rabbitmqService).publish(any(String.class), any(String.class));
    DueExternalInject failing = persistDueAtomicInject();
    DueExternalInject healthy = persistDueAtomicInject();
    doAnswer(
            invocation -> {
              Executor.ExternalPublish pending = invocation.getArgument(0);
              if (pending.injectId().equals(failing.injectId())) {
                throw new RuntimeException("recording the outcome failed");
              }
              return invocation.callRealMethod();
            })
        .when(executor)
        .recordPublishSuccess(any());

    try {
      // Act: must not throw even though one inject's outcome recording fails
      job.execute(null);

      // Assert: the other inject of the same batch still gets its real trace
      InjectStatus healthyStatus =
          injectStatusRepository.findByInjectId(healthy.injectId()).orElseThrow();
      assertThat(healthyStatus.getTraces())
          .as("the rest of the batch still runs despite the other inject's recording failure")
          .anyMatch(
              trace ->
                  trace.getMessage() != null
                      && trace.getMessage().contains("waiting to be consumed"));
    } finally {
      sweep(failing);
      sweep(healthy);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName(
      "a completion committed by the injector's callback while the outcome is recorded is kept,"
          + " and no stale status is streamed")
  void given_callbackCompletesBeforeTheOutcomeIsRecorded_should_keepItsStatus()
      throws JobExecutionException, IOException, TimeoutException {
    // Arrange
    doReturn(true)
        .when(connectorInstanceService)
        .hasStartedConnectorInstanceForInjector(any(String.class));
    doReturn(List.of()).when(healthCheckUtils).runContentChecks(any(Inject.class));
    AtomicBoolean published = new AtomicBoolean(false);
    doAnswer(
            invocation -> {
              published.set(true);
              return null;
            })
        .when(rabbitmqService)
        .publish(any(String.class), any(String.class));
    DueExternalInject due = persistDueAtomicInject();
    String statusId = injectStatusRepository.findByInjectId(due.injectId()).orElseThrow().getId();
    AtomicBoolean callbackDone = new AtomicBoolean(false);
    // The callback commits from its own thread once the publish is out, just before the outcome
    // write reaches the database
    Runnable callback =
        () -> {
          if (published.get() && callbackDone.compareAndSet(false, true)) {
            CompletableFuture.runAsync(
                    () ->
                        executionTraceRepositoryHelper.updateInjectStatus(
                            statusId, ExecutionStatus.EXECUTED.name(), Instant.now()))
                .join();
            eventCapture.streamedStatuses().clear();
          }
        };
    doAnswer(
            invocation -> {
              callback.run();
              return invocation.callRealMethod();
            })
        .when(executionTraceRepositoryHelper)
        .saveExecutionTrace(any(ExecutionTrace.class));
    doAnswer(
            invocation -> {
              callback.run();
              return invocation.callRealMethod();
            })
        .when(injectStatusService)
        .saveAndStreamInject(any(InjectStatus.class));

    try {
      // Act
      job.execute(null);

      // Assert
      assertThat(callbackDone).as("the outcome write ran").isTrue();
      InjectStatus status = injectStatusRepository.findByInjectId(due.injectId()).orElseThrow();
      assertThat(status.getName())
          .as("the callback's completion is not overwritten")
          .isEqualTo(ExecutionStatus.EXECUTED);
      assertThat(status.getTraces())
          .as("the published trace is still recorded")
          .anyMatch(
              trace ->
                  trace.getMessage() != null
                      && trace.getMessage().contains("waiting to be consumed"));
      assertThat(eventCapture.streamedStatuses())
          .as("no status event is sent after the completion")
          .isEmpty();
    } finally {
      sweep(due);
    }
  }

  /** Records the status each inject carried when its update event was published. */
  @TestConfiguration
  static class InjectEventCapture {
    private final List<String> streamedStatuses = new CopyOnWriteArrayList<>();

    List<String> streamedStatuses() {
      return streamedStatuses;
    }

    @EventListener
    void on(BaseEvent event) {
      if (event.getInstance() instanceof Inject inject) {
        streamedStatuses.add(
            inject.getStatus().map(status -> status.getName().name()).orElse("none"));
      }
    }
  }
}
