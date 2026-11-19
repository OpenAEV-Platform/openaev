package io.openaev.executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.model.Injector;
import io.openaev.database.repository.InjectStatusRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutableInjectDTO;
import io.openaev.execution.ExecutableInjectDTOMapper;
import io.openaev.execution.ExecutionExecutorService;
import io.openaev.execution.UnexecutableInjectException;
import io.openaev.integration.ManagerFactory;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.rest.inject.service.ResolvedAssetsToExecute;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.RabbitmqService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.payload.PayloadRevocationService;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Regression coverage for the legacy-inject injector-resolution fallback in {@link
 * Executor#execute(ExecutableInject)}: when {@code Inject.injector} is not populated, the injector
 * must be re-resolved through a fresh, explicitly tenant-scoped query rather than through the
 * inject's cached, EAGER {@code @Filter}-guarded association graph (which may have been initialized
 * under the wrong tenant context by a prior cross-tenant batch read, e.g. {@code
 * InjectHelper#getInjectsToRun}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutorTest {

  @Mock private InjectStatusRepository injectStatusRepository;
  @Mock private InjectorRepository injectorRepository;
  @Mock private RabbitmqService rabbitmqService;
  @Mock private ActionMetricCollector actionMetricCollector;
  @Mock private ManagerFactory managerFactory;
  @Mock private ExecutionExecutorService executionExecutorService;
  @Mock private InjectStatusService injectStatusService;
  @Mock private InjectService injectService;
  @Mock private ExecutableInjectDTOMapper executableInjectDTOMapper;
  @Mock private ConnectorInstanceService connectorInstanceService;
  @Mock private InjectExpectationService injectExpectationService;
  @Mock private PayloadRevocationService payloadRevocationService;
  @Mock private TenantScopedJobRunner tenantScopedJobRunner;
  @Mock private TenantScopedTransaction tenantTx;

  @InjectMocks private Executor executor;

  private static final String TENANT_ID = "tenant-001";
  private static final String CONTRACT_ID = "contract-001";
  private static final String INJECT_ID = "inject-001";

  private Inject inject;
  private InjectorContract injectorContract;
  private ExecutableInject executableInject;

  @BeforeEach
  void setUp() {
    // executor.mapper is a @Resource field, not constructor-injected: @InjectMocks never touches
    // it, so it must be wired manually to avoid a NullPointerException in executeExternal.
    ObjectMapper mapper = new ObjectMapper();
    ReflectionTestUtils.setField(executor, "mapper", mapper);

    Tenant tenant = new Tenant(TENANT_ID);

    injectorContract = mock(InjectorContract.class);
    when(injectorContract.getId()).thenReturn(CONTRACT_ID);
    when(injectorContract.getNeedsExecutor()).thenReturn(false);

    inject = mock(Inject.class);
    when(inject.getId()).thenReturn(INJECT_ID);
    when(inject.getTenant()).thenReturn(tenant);
    when(inject.getInjectorContract()).thenReturn(Optional.of(injectorContract));

    Injection injection = mock(Injection.class);
    when(injection.getInject()).thenReturn(inject);

    executableInject = mock(ExecutableInject.class);
    when(executableInject.getInjection()).thenReturn(injection);
    // By default the targets were already resolved by the caller, as scheduled execution does
    when(executableInject.getResolvedAssetsToExecute())
        .thenReturn(new ResolvedAssetsToExecute(List.of(), Set.of()));
  }

  @Test
  @DisplayName("execute refuses an inject whose payload version is revoked, with the reason")
  void given_revokedPayload_should_refuseTheInjectWithTheReason() {
    // Arrange
    when(payloadRevocationService.refusal(inject))
        .thenReturn(Optional.of("Payload revoked: Deletes user files"));

    // Act & Assert
    assertThatThrownBy(() -> executor.execute(executableInject))
        .isInstanceOf(UnexecutableInjectException.class)
        .hasMessage("Payload revoked: Deletes user files");
    verifyNoInteractions(injectorRepository, injectStatusService);
  }

  @Nested
  @DisplayName("execute - legacy inject injector fallback resolution")
  class LegacyInjectorFallback {

    @Test
    @DisplayName(
        "Given inject without a populated injector field, execute should resolve it through a fresh tenant-scoped query")
    void given_injectWithoutInjectorField_should_resolveInjectorViaFreshTenantScopedQuery()
        throws Exception {
      // -------- Arrange --------
      when(inject.getInjector()).thenReturn(null);
      when(inject.getType()).thenReturn("openaev_test_injector");

      Injector injector = new Injector();
      injector.setId("injector-001");
      injector.setTenantId(TENANT_ID);
      injector.setType("openaev_test_injector");
      injector.setExternal(true);

      when(injectorRepository.findFirstByContractsCompositeIdIdAndTenantId(CONTRACT_ID, TENANT_ID))
          .thenReturn(Optional.of(injector));
      when(connectorInstanceService.hasStartedConnectorInstanceForInjector("injector-001"))
          .thenReturn(true);
      when(injectStatusService.initializeInjectStatus(INJECT_ID, ExecutionStatus.EXECUTING))
          .thenReturn(mock(InjectStatus.class));
      InjectStatus injectStatus = mock(InjectStatus.class);
      when(injectStatus.getId()).thenReturn("status-001");
      when(injectStatusRepository.findByInjectId(INJECT_ID)).thenReturn(Optional.of(injectStatus));
      when(injectStatusRepository.findById("status-001")).thenReturn(Optional.of(injectStatus));
      when(injectService.resolveAllAssetsToExecute(inject)).thenReturn(List.of());

      // -------- Act --------
      executor.execute(executableInject);

      // -------- Assert --------
      verify(injectorRepository)
          .findFirstByContractsCompositeIdIdAndTenantId(CONTRACT_ID, TENANT_ID);
    }

    @Test
    @DisplayName(
        "Given inject without a populated injector field and no linked injector for the tenant, execute should fail with a clear error")
    void given_noLinkedInjectorForTenant_should_throwUnexecutableInjectException() {
      // -------- Arrange --------
      when(inject.getInjector()).thenReturn(null);
      when(injectorRepository.findFirstByContractsCompositeIdIdAndTenantId(CONTRACT_ID, TENANT_ID))
          .thenReturn(Optional.empty());

      // -------- Act / Assert --------
      assertThatThrownBy(() -> executor.execute(executableInject))
          .isInstanceOf(UnexecutableInjectException.class)
          .hasMessage("No injector is registered for the injector contract " + CONTRACT_ID);
    }

    @Test
    @DisplayName("Given inject with a populated injector field, execute should use it directly")
    void given_injectWithPopulatedInjectorField_should_useItDirectlyWithoutExtraQuery()
        throws Exception {
      // -------- Arrange --------
      Injector injector = new Injector();
      injector.setId("injector-002");
      injector.setTenantId(TENANT_ID);
      injector.setType("openaev_test_injector");
      injector.setExternal(true);
      when(inject.getInjector()).thenReturn(injector);

      when(connectorInstanceService.hasStartedConnectorInstanceForInjector("injector-002"))
          .thenReturn(true);
      when(injectStatusService.initializeInjectStatus(INJECT_ID, ExecutionStatus.EXECUTING))
          .thenReturn(mock(InjectStatus.class));
      InjectStatus injectStatus = mock(InjectStatus.class);
      when(injectStatus.getId()).thenReturn("status-002b");
      when(injectStatusRepository.findByInjectId(INJECT_ID)).thenReturn(Optional.of(injectStatus));
      when(injectStatusRepository.findById("status-002b")).thenReturn(Optional.of(injectStatus));
      when(injectService.resolveAllAssetsToExecute(inject)).thenReturn(List.of());

      // -------- Act --------
      executor.execute(executableInject);

      // -------- Assert --------
      verifyNoInteractions(injectorRepository);
    }
  }

  @Nested
  @DisplayName("execute - final failures are reported as unexecutable")
  class FinalFailures {

    @Test
    @DisplayName(
        "Given an injector whose connector instance is not started, execute should fail as unexecutable before any status change")
    void given_stoppedConnectorInstance_should_throwUnexecutableInjectException() {
      // -------- Arrange --------
      Injector injector = new Injector();
      injector.setId("injector-stopped");
      injector.setType("openaev_nmap");
      when(inject.getInjector()).thenReturn(injector);
      when(connectorInstanceService.hasStartedConnectorInstanceForInjector("injector-stopped"))
          .thenReturn(false);

      // -------- Act / Assert --------
      assertThatThrownBy(() -> executor.execute(executableInject))
          .isInstanceOf(UnexecutableInjectException.class)
          .hasMessage("No started connector instance found for injector type: openaev_nmap");
      verifyNoInteractions(injectStatusService, executionExecutorService, rabbitmqService);
    }

    @Test
    @DisplayName("Given an inject without contract, execute should fail as unexecutable")
    void given_injectWithoutContract_should_throwUnexecutableInjectException() {
      // -------- Arrange --------
      when(inject.getInjectorContract()).thenReturn(Optional.empty());

      // -------- Act / Assert --------
      assertThatThrownBy(() -> executor.execute(executableInject))
          .isInstanceOf(UnexecutableInjectException.class)
          .hasMessage("Inject does not have a contract");
    }

    @Test
    @DisplayName("Given an inject without content, directExecute should fail as unexecutable")
    void given_injectWithoutContent_should_throwUnexecutableInjectException() {
      // -------- Arrange --------
      when(executableInject.isDirect()).thenReturn(true);
      when(inject.getContent()).thenReturn(null);

      // -------- Act / Assert --------
      assertThatThrownBy(() -> executor.directExecute(executableInject))
          .isInstanceOf(UnexecutableInjectException.class)
          .hasMessage("Inject is empty");
    }
  }

  @Nested
  @DisplayName("execute - inject injector authorisation and attachments")
  class InjectAuthorisationAndAttachments {
    @Test
    @DisplayName("External inject execution should publish attachments and persist authorisation")
    void
        given_externalInjectWithSecretReferences_should_publishAttachments_and_persistAuthorisation()
            throws Exception {
      Injector injector = new Injector();
      injector.setId("injector-002");
      injector.setTenantId(TENANT_ID);
      injector.setType("openaev_test_injector");
      injector.setExternal(true);
      when(inject.getInjector()).thenReturn(injector);
      when(executableInject.getSecretReferenceIds()).thenReturn(List.of("secret-ref-1"));
      ExecutableInjectDTO dto =
          ExecutableInjectDTO.builder()
              .injection(null)
              .assets(java.util.Set.of())
              .assetGroups(java.util.Set.of())
              .attachments(
                  ExecutableInjectDTO.Attachments.builder()
                      .credentialReferences(List.of("secret-ref-1"))
                      .authorisationCode("auth-code")
                      .build())
              .build();

      when(connectorInstanceService.hasStartedConnectorInstanceForInjector("injector-002"))
          .thenReturn(true);
      when(injectStatusService.initializeInjectStatus(INJECT_ID, ExecutionStatus.EXECUTING))
          .thenReturn(mock(InjectStatus.class));
      InjectStatus injectStatus = mock(InjectStatus.class);
      when(injectStatus.getId()).thenReturn("status-002");
      when(injectStatusRepository.findByInjectId(INJECT_ID)).thenReturn(Optional.of(injectStatus));
      when(injectStatusRepository.findById("status-002")).thenReturn(Optional.of(injectStatus));
      when(injectService.resolveAllAssetsToExecute(inject)).thenReturn(List.of());
      when(injectService.getAuthorisationCodeIfNeeded(executableInject)).thenReturn("auth-code");
      when(executableInjectDTOMapper.toExecutableInjectDTO(executableInject, "auth-code"))
          .thenReturn(dto);

      ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);

      executor.execute(executableInject);

      verify(injectService).getAuthorisationCodeIfNeeded(executableInject);
      verify(rabbitmqService).publish(any(), jsonCaptor.capture());

      JsonNode payload = new ObjectMapper().readTree(jsonCaptor.getValue());
      assertThat(payload.path("attachments").path("credential_references").toString())
          .contains("secret-ref-1");
      assertThat(payload.path("attachments").path("authorisation_code").asText())
          .isEqualTo("auth-code");
    }
  }

  @Nested
  @DisplayName("execute - not targeted trace for assets outside the launcher's clearance")
  class AssetsWithoutClearanceTraces {

    private void givenStartedExternalInjector() {
      Injector injector = new Injector();
      injector.setId("injector-003");
      injector.setTenantId(TENANT_ID);
      injector.setType("openaev_test_injector");
      injector.setExternal(true);
      when(inject.getInjector()).thenReturn(injector);
      when(connectorInstanceService.hasStartedConnectorInstanceForInjector("injector-003"))
          .thenReturn(true);
      InjectStatus injectStatus = mock(InjectStatus.class);
      when(injectStatus.getId()).thenReturn("status-003");
      when(injectStatusRepository.findByInjectId(INJECT_ID)).thenReturn(Optional.of(injectStatus));
      when(injectStatusRepository.findById("status-003")).thenReturn(Optional.of(injectStatus));
    }

    @Test
    @DisplayName(
        "Given an injector without agent routing, execute should trace the restricted targets as"
            + " not targeted")
    void given_injectorWithoutAgentRouting_should_traceRestrictedTargets() throws Exception {
      // -------- Arrange --------
      givenStartedExternalInjector();
      InjectStatus status = mock(InjectStatus.class);
      when(injectStatusService.initializeInjectStatus(INJECT_ID, ExecutionStatus.EXECUTING))
          .thenReturn(status);
      Asset assetRed = new Asset();
      assetRed.setId("asset-red");
      ResolvedAssetsToExecute resolved = new ResolvedAssetsToExecute(List.of(), Set.of(assetRed));
      when(injectService.resolveAssetsToExecuteByClearance(inject)).thenReturn(resolved);
      // Nothing cached yet (direct execution), then the resolution the executor caches
      when(executableInject.getResolvedAssetsToExecute()).thenReturn(null, resolved);

      // -------- Act --------
      executor.execute(executableInject);

      // -------- Assert --------
      verify(injectService, times(1)).resolveAssetsToExecuteByClearance(inject);
      verify(executableInject).cacheAssetsToExecute(resolved);
      verify(executionExecutorService).saveAssetsWithoutClearanceTraces(Set.of(assetRed), status);
      verify(executionExecutorService, never()).launchExecutorContext(any(), any());
    }

    @Test
    @DisplayName(
        "Given targets already resolved by the caller, execute should trace the cached restricted"
            + " targets without resolving them again")
    void given_targetsAlreadyResolved_should_traceCachedRestrictedTargets() throws Exception {
      // -------- Arrange --------
      givenStartedExternalInjector();
      InjectStatus status = mock(InjectStatus.class);
      when(injectStatusService.initializeInjectStatus(INJECT_ID, ExecutionStatus.EXECUTING))
          .thenReturn(status);
      Asset assetRed = new Asset();
      assetRed.setId("asset-red");
      when(executableInject.getResolvedAssetsToExecute())
          .thenReturn(new ResolvedAssetsToExecute(List.of(), Set.of(assetRed)));

      // -------- Act --------
      executor.execute(executableInject);

      // -------- Assert --------
      verify(injectService, never()).resolveAssetsToExecuteByClearance(inject);
      verify(executionExecutorService).saveAssetsWithoutClearanceTraces(Set.of(assetRed), status);
    }

    @Test
    @DisplayName(
        "Given an injector with agent routing, execute should hand the cached resolution to the"
            + " executor context without resolving the targets again")
    void given_injectorWithAgentRouting_should_launchExecutorContextFromCachedResolution()
        throws Exception {
      // -------- Arrange --------
      givenStartedExternalInjector();
      when(injectorContract.getNeedsExecutor()).thenReturn(true);
      when(injectStatusService.initializeInjectStatus(INJECT_ID, ExecutionStatus.EXECUTING))
          .thenReturn(mock(InjectStatus.class));
      Asset assetRed = new Asset();
      assetRed.setId("asset-red");
      ResolvedAssetsToExecute cached = new ResolvedAssetsToExecute(List.of(), Set.of(assetRed));
      when(executableInject.getResolvedAssetsToExecute()).thenReturn(cached);

      // -------- Act --------
      executor.execute(executableInject);

      // -------- Assert --------
      verify(executionExecutorService).launchExecutorContext(inject, cached);
      verify(executionExecutorService, never()).saveAssetsWithoutClearanceTraces(any(), any());
      verify(injectService, never()).resolveAssetsToExecuteByClearance(inject);
    }

    @Test
    @DisplayName(
        "Given an injector with agent routing and nothing cached, execute should resolve the"
            + " targets once and hand that resolution to the executor context")
    void given_injectorWithAgentRoutingAndNothingCached_should_resolveOnceForExecutorContext()
        throws Exception {
      // -------- Arrange --------
      givenStartedExternalInjector();
      when(injectorContract.getNeedsExecutor()).thenReturn(true);
      when(injectStatusService.initializeInjectStatus(INJECT_ID, ExecutionStatus.EXECUTING))
          .thenReturn(mock(InjectStatus.class));
      ResolvedAssetsToExecute resolved = new ResolvedAssetsToExecute(List.of(), Set.of());
      when(injectService.resolveAssetsToExecuteByClearance(inject)).thenReturn(resolved);
      when(executableInject.getResolvedAssetsToExecute()).thenReturn(null, resolved);

      // -------- Act --------
      executor.execute(executableInject);

      // -------- Assert --------
      verify(injectService, times(1)).resolveAssetsToExecuteByClearance(inject);
      verify(executableInject).cacheAssetsToExecute(resolved);
      verify(executionExecutorService).launchExecutorContext(inject, resolved);
    }
  }

  @Nested
  @DisplayName("execute - publish timing relative to the caller's transaction")
  class PublishTiming {

    /** An external injector with everything execute() needs to reach the publish step. */
    private void givenExternalInjectorReadyToPublish() {
      Injector injector = new Injector();
      injector.setId("injector-timing");
      injector.setTenantId(TENANT_ID);
      injector.setType("openaev_test_injector");
      injector.setExternal(true);
      when(inject.getInjector()).thenReturn(injector);
      when(connectorInstanceService.hasStartedConnectorInstanceForInjector("injector-timing"))
          .thenReturn(true);
      when(injectStatusService.initializeInjectStatus(INJECT_ID, ExecutionStatus.EXECUTING))
          .thenReturn(mock(InjectStatus.class));
      InjectStatus injectStatus = mock(InjectStatus.class);
      when(injectStatus.getId()).thenReturn("status-timing");
      when(injectStatusRepository.findByInjectId(INJECT_ID)).thenReturn(Optional.of(injectStatus));
      when(injectStatusRepository.findById("status-timing")).thenReturn(Optional.of(injectStatus));
      when(injectService.resolveAllAssetsToExecute(inject)).thenReturn(List.of());
      ExecutableInjectDTO dto =
          ExecutableInjectDTO.builder()
              .injection(null)
              .assets(Set.of())
              .assetGroups(Set.of())
              .build();
      when(executableInjectDTOMapper.toExecutableInjectDTO(eq(executableInject), any()))
          .thenReturn(dto);
    }

    @AfterEach
    void clearSynchronization() {
      if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.clearSynchronization();
      }
    }

    @Test
    @DisplayName(
        "with the caller's transaction synchronization active, the publish is deferred to"
            + " afterCommit and never runs before it")
    void given_transactionSynchronizationActive_should_deferPublishPastCommit() throws Exception {
      // -------- Arrange --------
      givenExternalInjectorReadyToPublish();
      TransactionSynchronizationManager.initSynchronization();

      // -------- Act --------
      executor.execute(executableInject);

      // -------- Assert: the publish has not run yet, the trace already has --------
      verifyNoInteractions(rabbitmqService);
      verify(injectStatusService).saveAndStreamInject(any(InjectStatus.class));
      List<TransactionSynchronization> registered =
          TransactionSynchronizationManager.getSynchronizations();
      assertThat(registered).hasSize(1);

      // -------- Act: simulate the caller's transaction committing --------
      registered.get(0).afterCommit();

      // -------- Assert: only now does the publish actually run --------
      verify(rabbitmqService).publish(any(), any());
    }

    @Test
    @DisplayName(
        "a publish deferred past commit that then fails records the error in a new transaction"
            + " of the inject's tenant, then throws as before")
    void given_deferredPublishFails_should_recordErrorInNewTransactionThenThrow() throws Exception {
      // -------- Arrange --------
      givenExternalInjectorReadyToPublish();
      doThrow(new IOException("broker unreachable")).when(rabbitmqService).publish(any(), any());
      ArgumentCaptor<TxCtx> scope = ArgumentCaptor.forClass(TxCtx.class);
      doAnswer(
              invocation -> {
                Object work = invocation.getArgument(1);
                return work instanceof Supplier<?> supplier ? supplier.get() : null;
              })
          .when(tenantTx)
          .executeNew(scope.capture(), any(Supplier.class));
      TransactionSynchronizationManager.initSynchronization();
      executor.execute(executableInject);
      TransactionSynchronization registered =
          TransactionSynchronizationManager.getSynchronizations().get(0);

      // -------- Act & Assert --------
      assertThatThrownBy(registered::afterCommit)
          .isInstanceOf(IllegalStateException.class)
          .hasCauseInstanceOf(IOException.class);
      assertThat(scope.getValue().toGuc()).isEqualTo(TENANT_ID);
      verify(injectStatusService).persistErrorStatusInTransaction(INJECT_ID, "broker unreachable");
    }

    @Test
    @DisplayName("a failed deferred publish still throws when its error status cannot be recorded")
    void given_deferredPublishAndRecordingFail_should_stillThrowThePublishFailure()
        throws Exception {
      // -------- Arrange --------
      givenExternalInjectorReadyToPublish();
      doThrow(new IOException("broker unreachable")).when(rabbitmqService).publish(any(), any());
      doThrow(new IllegalStateException("no connection"))
          .when(tenantTx)
          .executeNew(any(TxCtx.class), any(Supplier.class));
      TransactionSynchronizationManager.initSynchronization();
      executor.execute(executableInject);
      TransactionSynchronization registered =
          TransactionSynchronizationManager.getSynchronizations().get(0);

      // -------- Act & Assert --------
      assertThatThrownBy(registered::afterCommit)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Failed to publish inject after commit");
    }

    @Test
    @DisplayName(
        "with no transaction, a failed publish records the error in a transaction of the"
            + " inject's tenant and rethrows the publish failure")
    void given_noTransactionAndPublishFails_should_recordErrorThenRethrow() throws Exception {
      // -------- Arrange --------
      givenExternalInjectorReadyToPublish();
      doThrow(new IOException("broker unreachable")).when(rabbitmqService).publish(any(), any());
      doAnswer(
              invocation -> {
                invocation.getArgument(1, Runnable.class).run();
                return null;
              })
          .when(tenantScopedJobRunner)
          .runInTenant(eq(TENANT_ID), any(Runnable.class));

      // -------- Act & Assert --------
      assertThatThrownBy(() -> executor.execute(executableInject))
          .isInstanceOf(IOException.class)
          .hasMessage("broker unreachable");
      // The status write is MANDATORY: it must run in a transaction opened for it
      verify(tenantScopedJobRunner).runInTenant(eq(TENANT_ID), any(Runnable.class));
      verify(injectStatusService).persistErrorStatusInTransaction(INJECT_ID, "broker unreachable");
    }

    @Test
    @DisplayName("with no transaction synchronization active, the publish runs inline")
    void given_noTransactionSynchronizationActive_should_publishInline() throws Exception {
      // -------- Arrange --------
      givenExternalInjectorReadyToPublish();
      assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();

      // -------- Act --------
      executor.execute(executableInject);

      // -------- Assert: no deferral, the publish already ran --------
      verify(rabbitmqService).publish(any(), any());
    }
  }

  @Nested
  @DisplayName("outcome of the external publish")
  class PublishOutcome {

    @Test
    @DisplayName("a confirmed publish saves the trace through the streaming save")
    void given_publishConfirmed_should_saveAndStreamTheTrace() {
      // -------- Arrange --------
      InjectStatus injectStatus = mock(InjectStatus.class);
      when(injectStatusRepository.findById("status-1")).thenReturn(Optional.of(injectStatus));

      // -------- Act --------
      executor.recordPublishSuccess(
          new Executor.ExternalPublish(TENANT_ID, INJECT_ID, "status-1", "injector-1", "{}"));

      // -------- Assert --------
      verify(injectStatusService).saveAndStreamInject(injectStatus);
      verify(injectStatusRepository, never()).save(any(InjectStatus.class));
    }

    @Test
    @DisplayName("a publish failure with no message is recorded with the exception type")
    void given_failureWithoutMessage_should_recordTheExceptionType() {
      // -------- Act --------
      executor.recordPublishFailure(
          new Executor.ExternalPublish(TENANT_ID, INJECT_ID, "status-1", "injector-1", "{}"),
          new TimeoutException());

      // -------- Assert --------
      verify(injectStatusService)
          .persistErrorStatusInTransaction(
              INJECT_ID, "Failed to publish the inject: TimeoutException");
    }

    @Test
    @DisplayName("a batch may keep half the publisher pool in flight, at least one")
    void given_publisherPool_should_leaveHalfOfItFree() {
      // -------- Arrange & Act & Assert --------
      when(rabbitmqService.getPublishThreads()).thenReturn(20);
      assertThat(executor.maxConcurrentPublishes()).isEqualTo(10);
      when(rabbitmqService.getPublishThreads()).thenReturn(1);
      assertThat(executor.maxConcurrentPublishes()).isEqualTo(1);
    }
  }
}
