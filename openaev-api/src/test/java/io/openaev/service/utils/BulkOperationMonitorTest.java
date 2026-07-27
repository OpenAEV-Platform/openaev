package io.openaev.service.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.openaev.service.utils.BulkOperationMonitor.BulkOperation;
import io.openaev.service.utils.BulkOperationMonitor.BulkOperationEvent;
import io.openaev.service.utils.BulkOperationMonitor.BulkOperationStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

class BulkOperationMonitorTest {

  private ApplicationEventPublisher eventPublisher;
  private BulkOperationMonitor monitor;

  @BeforeEach
  void setUp() {
    eventPublisher = mock(ApplicationEventPublisher.class);
    // Mocked journal: queries return empty lists, so findForUser only sees the in-memory
    // registry - which is exactly what these unit tests exercise.
    monitor = new BulkOperationMonitor(eventPublisher, mock(JdbcTemplate.class));
  }

  @Test
  @DisplayName("Given a full lifecycle, should track progress and publish an event per step")
  void given_fullLifecycle_should_trackProgressAndPublishEvents() {
    String operationId = monitor.start("delete", "scenarios", 60);
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
    String operationId = monitor.start("delete", "teams", 50);
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
    // Started without a tenant context: tenantId is whatever TenantContext resolves to here.
    String operationId = monitor.start("delete", "players", 10);
    List<BulkOperation> visible = monitor.findForUser(null, null);
    assertThat(visible).extracting(BulkOperation::id).contains(operationId);
    assertThat(monitor.findForUser(null, "another-tenant"))
        .extracting(BulkOperation::id)
        .doesNotContain(operationId);
  }

  @Test
  @DisplayName("Given operations of another user, should not list them for a user")
  void given_operationsOfAnotherUser_should_notListThemForAUser() {
    // Started without an authenticated user: userId is null.
    String operationId = monitor.start("delete", "assets", 10);
    assertThat(monitor.findForUser("another-user", null))
        .extracting(BulkOperation::id)
        .doesNotContain(operationId);
  }

  @Test
  @DisplayName("Given running operations, should list them before finished ones")
  void given_runningOperations_should_listThemFirst() {
    String finishedId = monitor.start("delete", "teams", 5);
    monitor.complete(finishedId);
    String runningId = monitor.start("delete", "scenarios", 50);

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
