package io.openaev.scheduler.jobs;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.integration.Manager;
import io.openaev.integration.ManagerFactory;
import io.openaev.service.tenants.TenantService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
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
  @Mock private Manager tenantAManager;
  @Mock private Manager tenantBManager;

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
          new ManagerIntegrationsSyncJob(managerFactory, tenantService, DIRECT_EXECUTOR);

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
          new ManagerIntegrationsSyncJob(managerFactory, tenantService, queueingExecutor);

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
          new ManagerIntegrationsSyncJob(managerFactory, tenantService, DIRECT_EXECUTOR);

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
          new ManagerIntegrationsSyncJob(managerFactory, tenantService, DIRECT_EXECUTOR);

      // Act / Assert
      assertDoesNotThrow(() -> job.execute(null));
      assertDoesNotThrow(() -> job.execute(null));
      verify(tenantAManager, times(2)).monitorIntegrations();
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
