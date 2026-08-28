package io.openaev.scheduler.jobs;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.integration.Manager;
import io.openaev.integration.ManagerFactory;
import io.openaev.service.tenants.TenantService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ManagerIntegrationsSyncJob unit tests")
class ManagerIntegrationsSyncJobTest {

  private static final Executor DIRECT_EXECUTOR = Runnable::run;

  @Mock private ManagerFactory managerFactory;
  @Mock private TenantService tenantService;
  @Mock private TenantScopedTransaction tenantTx;
  @Mock private Manager tenantAManager;
  @Mock private Manager tenantBManager;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() throws Exception {
    // Make tenantTx.execute() just run the supplier directly (no real transaction).
    // Lenient: the shouldLogSlowTenantExecution tests do not invoke execute().
    org.mockito.Mockito.lenient()
        .doAnswer(
            invocation -> {
              Supplier<?> supplier = invocation.getArgument(1);
              return supplier.get();
            })
        .when(tenantTx)
        .execute(any(TxCtx.class), any(Supplier.class));
  }

  @Nested
  @DisplayName("execute")
  class ExecuteTests {

    @Test
    void given_activeTenants_should_scheduleAndMonitorEachTenant() throws Exception {
      // Arrange
      when(tenantService.findActiveTenantIds()).thenReturn(List.of("tenant-a", "tenant-b"));
      when(managerFactory.getManager("tenant-a")).thenReturn(tenantAManager);
      when(managerFactory.getManager("tenant-b")).thenReturn(tenantBManager);
      ManagerIntegrationsSyncJob job =
          new ManagerIntegrationsSyncJob(managerFactory, tenantService, tenantTx, DIRECT_EXECUTOR);

      // Act
      job.execute(null);

      // Assert
      verify(tenantAManager, times(1)).monitorIntegrations();
      verify(tenantBManager, times(1)).monitorIntegrations();
    }

    @Test
    void given_duplicateTenantInSameRun_should_skipOverlapAndMonitorOnce() throws Exception {
      // Arrange
      when(tenantService.findActiveTenantIds()).thenReturn(List.of("tenant-a", "tenant-a"));
      when(managerFactory.getManager("tenant-a")).thenReturn(tenantAManager);
      QueueingExecutor queueingExecutor = new QueueingExecutor();
      ManagerIntegrationsSyncJob job =
          new ManagerIntegrationsSyncJob(managerFactory, tenantService, tenantTx, queueingExecutor);

      // Act
      job.execute(null);
      queueingExecutor.runAll();

      // Assert
      assertEquals(1, queueingExecutor.size());
      verify(tenantAManager, times(1)).monitorIntegrations();
    }

    @Test
    void given_completedTenantSync_should_allowNextExecutionForSameTenant() throws Exception {
      // Arrange
      when(tenantService.findActiveTenantIds()).thenReturn(List.of("tenant-a"));
      when(managerFactory.getManager("tenant-a")).thenReturn(tenantAManager);
      ManagerIntegrationsSyncJob job =
          new ManagerIntegrationsSyncJob(managerFactory, tenantService, tenantTx, DIRECT_EXECUTOR);

      // Act
      job.execute(null);
      job.execute(null);

      // Assert
      verify(tenantAManager, times(2)).monitorIntegrations();
    }

    @Test
    void given_monitorFailure_should_cleanupInflightMarkerAndAllowNextExecution() throws Exception {
      // Arrange
      when(tenantService.findActiveTenantIds()).thenReturn(List.of("tenant-a"));
      when(managerFactory.getManager("tenant-a")).thenReturn(tenantAManager);
      doThrow(new RuntimeException("boom")).when(tenantAManager).monitorIntegrations();
      ManagerIntegrationsSyncJob job =
          new ManagerIntegrationsSyncJob(managerFactory, tenantService, tenantTx, DIRECT_EXECUTOR);

      // Act / Assert
      assertDoesNotThrow(() -> job.execute(null));
      assertDoesNotThrow(() -> job.execute(null));
      verify(tenantAManager, times(2)).monitorIntegrations();
    }
  }

  @Nested
  @DisplayName("shouldLogSlowTenantExecution")
  class ShouldLogSlowTenantExecutionTests {

    @Test
    void given_firstSyncForTenant_should_notLogSlowCall() {
      // Arrange
      ManagerIntegrationsSyncJob job =
          new ManagerIntegrationsSyncJob(managerFactory, tenantService, tenantTx, DIRECT_EXECUTOR);

      // Act
      boolean shouldLog = job.shouldLogSlowTenantExecution("tenant-a");

      // Assert
      assertFalse(shouldLog);
    }

    @Test
    void given_secondSyncForTenant_should_logSlowCall() {
      // Arrange
      ManagerIntegrationsSyncJob job =
          new ManagerIntegrationsSyncJob(managerFactory, tenantService, tenantTx, DIRECT_EXECUTOR);

      // Act
      boolean firstCall = job.shouldLogSlowTenantExecution("tenant-a");
      boolean secondCall = job.shouldLogSlowTenantExecution("tenant-a");

      // Assert
      assertFalse(firstCall);
      assertTrue(secondCall);
    }
  }

  private static final class QueueingExecutor implements Executor {
    private final List<Runnable> tasks = new ArrayList<>();

    @Override
    public void execute(Runnable command) {
      tasks.add(command);
    }

    int size() {
      return tasks.size();
    }

    void runAll() {
      tasks.forEach(Runnable::run);
    }
  }
}
