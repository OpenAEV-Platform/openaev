package io.openaev.scheduler.jobs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.SecurityCoverageSendJob;
import io.openaev.database.model.Tenant;
import io.openaev.database.raw.RawSecurityCoverageSendJobTenant;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.service.SecurityCoverageSendJobService;
import io.openaev.service.stix.SecurityCoverageService;
import io.openaev.stix.objects.Bundle;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit coverage for the "skip push when the connector is not usable" path that keeps the scheduler
 * from logging a full ERROR stack every cycle while an OpenCTI connector is configured but not yet
 * registered (OpenCTI unreachable / token not authorized).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityCoverageJob skips jobs without an active, registered connector")
class SecurityCoverageJobTest {

  private static final String TENANT_ID = "tenant-1";
  private static final String SEND_JOB_ID = "send-job-1";
  private static final String SIMULATION_ID = "simulation-1";

  @Mock private SecurityCoverageSendJobService securityCoverageSendJobService;
  @Mock private SecurityCoverageService securityCoverageService;
  @Mock private OpenCTIConnectorService openCTIConnectorService;
  @Mock private TenantScopedTransaction tenantTx;

  @InjectMocks private SecurityCoverageJob job;

  @BeforeEach
  void runScopedWorkInline() {
    // The scopes themselves are proven on the real stack (SimulationLifecycleSweepScopeTest); here
    // the primitive just runs the work it is handed.
    lenient()
        .when(tenantTx.execute(any(), ArgumentMatchers.<Supplier<Object>>any()))
        .thenAnswer(invocation -> invocation.<Supplier<Object>>getArgument(1).get());
    lenient()
        .doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(1).run();
              return null;
            })
        .when(tenantTx)
        .execute(any(), any(Runnable.class));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clearCurrentTenant();
  }

  private SecurityCoverageSendJob pendingJobForTenant() {
    Tenant tenant = new Tenant();
    tenant.setId(TENANT_ID);
    Exercise simulation = new Exercise();
    simulation.setId(SIMULATION_ID);
    simulation.setTenant(tenant);
    SecurityCoverageSendJob sendJob = new SecurityCoverageSendJob();
    sendJob.setId(SEND_JOB_ID);
    sendJob.setSimulation(simulation);
    lenient()
        .when(securityCoverageSendJobService.securityCoverageSendJob(SEND_JOB_ID))
        .thenReturn(sendJob);
    RawSecurityCoverageSendJobTenant pending = mock(RawSecurityCoverageSendJobTenant.class);
    lenient().when(pending.getSecurity_coverage_send_job_id()).thenReturn(SEND_JOB_ID);
    lenient().when(pending.getExercise_id()).thenReturn(SIMULATION_ID);
    lenient().when(pending.getTenant_id()).thenReturn(TENANT_ID);
    when(securityCoverageSendJobService.getPendingSecurityCoverageSendJobs())
        .thenReturn(List.of(pending));
    return sendJob;
  }

  @Test
  @DisplayName("a configured but not-yet-registered connector short-circuits before any push")
  void given_connectorNotRegistered_should_skipBundleCreationAndPush() throws Exception {
    pendingJobForTenant();
    ConnectorBase connector = mock(ConnectorBase.class);
    when(connector.isRegistered()).thenReturn(false);
    when(openCTIConnectorService.getConnectorBase(TENANT_ID)).thenReturn(Optional.of(connector));

    job.execute(null);

    // No bundle is built, nothing is pushed (so no ConnectorError -> no ERROR stack), and the job
    // stays pending until the connector registers.
    verify(securityCoverageService, never()).createBundleFromSendJobs(anyList());
    verify(openCTIConnectorService, never()).pushSecurityCoverageStixBundle(any(), any());
    verify(securityCoverageSendJobService, never()).consumeJobs(anyList());
  }

  @Test
  @DisplayName("no connector at all is also skipped without a push")
  void given_noConnector_should_skip() throws Exception {
    pendingJobForTenant();
    when(openCTIConnectorService.getConnectorBase(TENANT_ID)).thenReturn(Optional.empty());

    job.execute(null);

    verify(securityCoverageService, never()).createBundleFromSendJobs(anyList());
    verify(openCTIConnectorService, never()).pushSecurityCoverageStixBundle(any(), any());
    verify(securityCoverageSendJobService, never()).consumeJobs(anyList());
  }

  @Test
  @DisplayName("a registered connector still gets its bundle built, pushed and the job consumed")
  void given_registeredConnector_should_pushAndConsume() throws Exception {
    SecurityCoverageSendJob sendJob = pendingJobForTenant();
    ConnectorBase connector = mock(ConnectorBase.class);
    when(connector.isRegistered()).thenReturn(true);
    when(openCTIConnectorService.getConnectorBase(TENANT_ID)).thenReturn(Optional.of(connector));
    // getId() is only used for a log line; leaving the mock default (null) keeps the test focused.
    Bundle bundle = mock(Bundle.class);
    when(securityCoverageService.createBundleFromSendJobs(anyList())).thenReturn(bundle);

    job.execute(null);

    verify(securityCoverageService).createBundleFromSendJobs(anyList());
    verify(openCTIConnectorService).pushSecurityCoverageStixBundle(eq(bundle), eq(TENANT_ID));
    verify(securityCoverageSendJobService).consumeJobs(List.of(sendJob));
  }
}
