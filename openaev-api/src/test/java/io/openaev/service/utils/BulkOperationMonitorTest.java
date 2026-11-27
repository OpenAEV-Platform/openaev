package io.openaev.service.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.config.OpenAEVPrincipal;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.service.utils.BulkOperationMonitor.BulkOperation;
import io.openaev.service.utils.BulkOperationMonitor.BulkOperationEvent;
import io.openaev.service.utils.BulkOperationMonitor.BulkOperationStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class BulkOperationMonitorTest {

  private static final String TENANT_B = "bulk-monitor-tenant-b";
  private static final String LAUNCHER_ID = "bulk-monitor-launcher";

  private ApplicationEventPublisher eventPublisher;
  private BulkOperationMonitor monitor;

  @BeforeEach
  void setUp() {
    eventPublisher = mock(ApplicationEventPublisher.class);
    // Mocked journal: queries return empty lists, so findForUser only sees the in-memory
    // registry - which is exactly what these unit tests exercise.
    monitor = new BulkOperationMonitor(eventPublisher, mock(JdbcTemplate.class));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Given a full lifecycle, should track progress and publish an event per step")
  void given_fullLifecycle_should_trackProgressAndPublishEvents() {
    String operationId = monitor.start(TxCtx.missing(), "delete", "scenarios", 60);
    monitor.progress(operationId, 25);
    monitor.progress(operationId, 25);
    monitor.progress(operationId, 10);
    monitor.complete(operationId);

    ArgumentCaptor<BulkOperationEvent> captor = ArgumentCaptor.forClass(BulkOperationEvent.class);
    verify(eventPublisher, times(5)).publishEvent(captor.capture());
    List<BulkOperation> snapshots =
        captor.getAllValues().stream().map(BulkOperationEvent::operation).toList();
    assertThat(snapshots.get(0).status()).isEqualTo(BulkOperationStatus.RUNNING);
    assertThat(snapshots.get(0).processed()).isZero();
    assertThat(snapshots.get(1).processed()).isEqualTo(25);
    assertThat(snapshots.get(3).processed()).isEqualTo(60);
    assertThat(snapshots.get(4).status()).isEqualTo(BulkOperationStatus.COMPLETED);
    assertThat(snapshots.get(4).finishedAt()).isNotNull();
  }

  @Test
  @DisplayName("Given a failed operation, should keep the partial progress and mark it failed")
  void given_failedOperation_should_keepPartialProgressAndMarkFailed() {
    String operationId = monitor.start(TxCtx.missing(), "delete", "teams", 50);
    monitor.progress(operationId, 25);
    monitor.fail(operationId);

    List<BulkOperation> operations = monitor.findForUser(null, null);
    assertThat(operations).hasSize(1);
    assertThat(operations.get(0).status()).isEqualTo(BulkOperationStatus.FAILED);
    assertThat(operations.get(0).processed()).isEqualTo(25);
  }

  @Test
  @DisplayName("Given operations of another tenant, should not list them for a tenant consumer")
  void given_operationsOfSeveralTenants_should_onlyListRequestedTenant() {
    // Arrange & Act
    String operationId = monitor.start(TxCtx.forTenant(TENANT_B), "delete", "players", 10);

    // Assert
    assertThat(monitor.findForUser(null, null)).extracting(BulkOperation::id).contains(operationId);
    assertThat(monitor.findForUser(null, Set.of(TENANT_B)))
        .extracting(BulkOperation::id)
        .contains(operationId);
    assertThat(monitor.findForUser(null, Set.of("another-tenant")))
        .extracting(BulkOperation::id)
        .doesNotContain(operationId);
  }

  @Test
  @DisplayName("Given a scope pinning one tenant, should attribute the operation to that tenant")
  void given_singleTenantScope_should_attributeOperationToThatTenant() {
    // Arrange
    TenantContext.clearCurrentTenant();

    // Act
    monitor.start(TxCtx.forTenant(TENANT_B), "delete", "players", 10);

    // Assert
    assertThat(publishedOperation().tenantId()).isEqualTo(TENANT_B);
  }

  @Test
  @DisplayName("Given a scope spanning several tenants, should attribute the operation to none")
  void given_multiTenantScope_should_attributeOperationToNoTenant() {
    // Arrange
    TenantContext.clearCurrentTenant();

    // Act
    monitor.start(
        TxCtx.forTenants(List.of(TENANT_B, Tenant.DEFAULT_TENANT_UUID)), "delete", "players", 10);

    // Assert
    assertThat(publishedOperation().tenantId()).isNull();
  }

  @Test
  @DisplayName(
      "Given an operation with no tenant, should list it for its launcher under any tenant filter")
  void given_operationWithNoTenant_should_listItForItsLauncherOnly() {
    // Arrange
    authenticateAs(LAUNCHER_ID);
    String operationId =
        monitor.start(
            TxCtx.forTenants(List.of(TENANT_B, Tenant.DEFAULT_TENANT_UUID)), "delete", "teams", 3);
    Set<String> immutableScope = Set.copyOf(List.of(TENANT_B));

    // Act
    List<BulkOperation> ofLauncher = monitor.findForUser(LAUNCHER_ID, immutableScope);
    List<BulkOperation> ofAnotherUser = monitor.findForUser("another-user", immutableScope);
    List<BulkOperation> ofNoUser = monitor.findForUser(null, immutableScope);

    // Assert
    assertThat(ofLauncher).extracting(BulkOperation::id).contains(operationId);
    assertThat(ofAnotherUser).extracting(BulkOperation::id).doesNotContain(operationId);
    assertThat(ofNoUser).extracting(BulkOperation::id).doesNotContain(operationId);
  }

  private static void authenticateAs(String userId) {
    OpenAEVPrincipal principal = mock(OpenAEVPrincipal.class);
    when(principal.getId()).thenReturn(userId);
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken(principal, null));
  }

  private BulkOperation publishedOperation() {
    ArgumentCaptor<BulkOperationEvent> captor = ArgumentCaptor.forClass(BulkOperationEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    return captor.getValue().operation();
  }

  @Test
  @DisplayName("Given operations of another user, should not list them for a user")
  void given_operationsOfAnotherUser_should_notListThemForAUser() {
    // Started without an authenticated user: userId is null.
    String operationId = monitor.start(TxCtx.missing(), "delete", "assets", 10);
    assertThat(monitor.findForUser("another-user", null))
        .extracting(BulkOperation::id)
        .doesNotContain(operationId);
  }

  @Test
  @DisplayName("Given running operations, should list them before finished ones")
  void given_runningOperations_should_listThemFirst() {
    String finishedId = monitor.start(TxCtx.missing(), "delete", "teams", 5);
    monitor.complete(finishedId);
    String runningId = monitor.start(TxCtx.missing(), "delete", "scenarios", 50);

    List<BulkOperation> operations = monitor.findForUser(null, null);
    assertThat(operations).extracting(BulkOperation::id).containsExactly(runningId, finishedId);
  }

  @Test
  @DisplayName("Given progress on an unknown operation, should not publish anything")
  void given_progressOnUnknownOperation_should_notPublish() {
    monitor.progress("unknown", 10);
    monitor.complete("unknown");
    monitor.fail("unknown");
    verify(eventPublisher, times(0)).publishEvent(org.mockito.ArgumentMatchers.any());
  }
}
