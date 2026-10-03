package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.SecurityCoverageSendJob;
import io.openaev.database.model.Tenant;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.service.SecurityCoverageSendJobService;
import io.openaev.service.stix.SecurityCoverageHuntValidationService;
import io.openaev.service.stix.SecurityCoverageService;
import io.openaev.stix.objects.Bundle;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit coverage for the "skip push when the connector is not usable" path that keeps the scheduler
 * from logging a full ERROR stack every cycle while an OpenCTI connector is configured but not yet
 * registered (OpenCTI unreachable / token not authorized), and for the OpenCTI hunt validation
 * planning that follows a successful push.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityCoverageJob")
class SecurityCoverageJobTest {

  private static final String TENANT_ID = "tenant-1";
  private static final String SIMULATION_ID = "simulation-1";

  @Mock private SecurityCoverageSendJobService securityCoverageSendJobService;
  @Mock private SecurityCoverageService securityCoverageService;
  @Mock private OpenCTIConnectorService openCTIConnectorService;
  @Mock private SecurityCoverageHuntValidationService huntValidationService;
  @Mock private TenantScopedTransaction tenantTx;

  @InjectMocks private SecurityCoverageJob job;

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
    sendJob.setSimulation(simulation);
    return sendJob;
  }

  private void givenRegisteredConnectorAndBuiltBundle(Bundle bundle) throws Exception {
    when(tenantTx.execute(any(), ArgumentMatchers.<Supplier<Object>>any()))
        .thenAnswer(invocation -> invocation.<Supplier<Object>>getArgument(1).get());
    when(securityCoverageSendJobService.getPendingSecurityCoverageSendJobs())
        .thenReturn(List.of(pendingJobForTenant()));
    ConnectorBase connector = org.mockito.Mockito.mock(ConnectorBase.class);
    when(connector.isRegistered()).thenReturn(true);
    when(openCTIConnectorService.getConnectorBase(TENANT_ID)).thenReturn(Optional.of(connector));
    // getId() is only used for a log line; leaving the mock default (null) keeps the test focused.
    when(securityCoverageService.createBundleFromSendJobs(anyList())).thenReturn(bundle);
  }

  @Nested
  @DisplayName("Connector gate")
  class ConnectorGate {

    @Test
    @DisplayName("a configured but not-yet-registered connector short-circuits before any push")
    void given_connectorNotRegistered_should_skipBundleCreationAndPush() throws Exception {
      when(securityCoverageSendJobService.getPendingSecurityCoverageSendJobs())
          .thenReturn(List.of(pendingJobForTenant()));
      ConnectorBase connector = org.mockito.Mockito.mock(ConnectorBase.class);
      when(connector.isRegistered()).thenReturn(false);
      when(openCTIConnectorService.getConnectorBase(TENANT_ID)).thenReturn(Optional.of(connector));

      job.execute(null);

      // No bundle is built, nothing is pushed (so no ConnectorError -> no ERROR stack), and the
      // job stays pending until the connector registers.
      verify(securityCoverageService, never()).createBundleFromSendJobs(anyList());
      verify(openCTIConnectorService, never()).pushSecurityCoverageStixBundle(any(), any());
      verify(securityCoverageSendJobService, never()).consumeJobs(anyList());
      verify(huntValidationService, never()).planForSimulation(anyString());
    }

    @Test
    @DisplayName("no connector at all is also skipped without a push")
    void given_noConnector_should_skip() throws Exception {
      when(securityCoverageSendJobService.getPendingSecurityCoverageSendJobs())
          .thenReturn(List.of(pendingJobForTenant()));
      when(openCTIConnectorService.getConnectorBase(TENANT_ID)).thenReturn(Optional.empty());

      job.execute(null);

      verify(securityCoverageService, never()).createBundleFromSendJobs(anyList());
      verify(openCTIConnectorService, never()).pushSecurityCoverageStixBundle(any(), any());
      verify(securityCoverageSendJobService, never()).consumeJobs(anyList());
      verify(huntValidationService, never()).planForSimulation(anyString());
    }

    @Test
    @DisplayName("a registered connector still gets its bundle built, pushed and the job consumed")
    void given_registeredConnector_should_pushAndConsume() throws Exception {
      Bundle bundle = org.mockito.Mockito.mock(Bundle.class);
      givenRegisteredConnectorAndBuiltBundle(bundle);

      job.execute(null);

      verify(securityCoverageService).createBundleFromSendJobs(anyList());
      verify(openCTIConnectorService).pushSecurityCoverageStixBundle(eq(bundle), eq(TENANT_ID));
      verify(securityCoverageSendJobService).consumeJobs(anyList());
    }
  }

  @Nested
  @DisplayName("OpenCTI hunt validation planning")
  class HuntValidationPlanning {

    @Test
    @DisplayName(
        "given hunt validation enabled should plan after the push, in the simulation scope")
    void given_enabled_should_planAfterPushInSimulationScope() throws Exception {
      // Arrange
      Bundle bundle = org.mockito.Mockito.mock(Bundle.class);
      givenRegisteredConnectorAndBuiltBundle(bundle);
      when(huntValidationService.isEnabled()).thenReturn(true);
      when(huntValidationService.planForSimulation(SIMULATION_ID)).thenReturn(2);

      // Act
      job.execute(null);

      // Assert
      InOrder order = inOrder(openCTIConnectorService, huntValidationService);
      order.verify(openCTIConnectorService).pushSecurityCoverageStixBundle(bundle, TENANT_ID);
      order.verify(huntValidationService).planForSimulation(SIMULATION_ID);
      ArgumentCaptor<TxCtx> scopes = ArgumentCaptor.forClass(TxCtx.class);
      verify(tenantTx, times(2))
          .execute(scopes.capture(), ArgumentMatchers.<Supplier<Object>>any());
      assertThat(scopes.getAllValues()).containsOnly(TxCtx.forTenant(TENANT_ID));
      verify(securityCoverageSendJobService).consumeJobs(anyList());
    }

    @Test
    @DisplayName("given hunt validation disabled should never plan")
    void given_disabled_should_neverPlan() throws Exception {
      // Arrange
      givenRegisteredConnectorAndBuiltBundle(org.mockito.Mockito.mock(Bundle.class));
      when(huntValidationService.isEnabled()).thenReturn(false);

      // Act
      job.execute(null);

      // Assert
      verify(huntValidationService, never()).planForSimulation(anyString());
      verify(securityCoverageSendJobService).consumeJobs(anyList());
    }

    @Test
    @DisplayName("given the planning fails should push the bundle but keep the job pending")
    void given_planningFails_should_pushButKeepJobPending() throws Exception {
      // Arrange
      Bundle bundle = org.mockito.Mockito.mock(Bundle.class);
      givenRegisteredConnectorAndBuiltBundle(bundle);
      when(huntValidationService.isEnabled()).thenReturn(true);
      when(huntValidationService.planForSimulation(SIMULATION_ID))
          .thenThrow(new IllegalStateException("database hiccup"));

      // Act
      job.execute(null);

      // Assert
      verify(openCTIConnectorService).pushSecurityCoverageStixBundle(eq(bundle), eq(TENANT_ID));
      verify(securityCoverageSendJobService, never()).consumeJobs(anyList());
    }

    @Test
    @DisplayName("given the push fails should not plan, the job staying pending")
    void given_pushFails_should_notPlan() throws Exception {
      // Arrange
      Bundle bundle = org.mockito.Mockito.mock(Bundle.class);
      givenRegisteredConnectorAndBuiltBundle(bundle);
      doThrow(new ConnectorError("OpenCTI down"))
          .when(openCTIConnectorService)
          .pushSecurityCoverageStixBundle(bundle, TENANT_ID);

      // Act
      job.execute(null);

      // Assert
      verify(huntValidationService, never()).planForSimulation(anyString());
      verify(securityCoverageSendJobService, never()).consumeJobs(anyList());
    }
  }
}
