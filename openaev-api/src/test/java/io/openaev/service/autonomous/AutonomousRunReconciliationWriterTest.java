package io.openaev.service.autonomous;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.autonomous.AutonomousEventType;
import io.openaev.database.model.autonomous.AutonomousRun;
import io.openaev.database.model.autonomous.AutonomousRunStatus;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the isolated reconcile writer: the terminal flip is claimed atomically with an
 * explicit tenant predicate, the re-load stays tenant-scoped (consistent with the write - a bare PK
 * lookup is not covered by tenant filters), and the STATUS timeline event is narrated exactly once,
 * by the claimer only.
 */
@ExtendWith(MockitoExtension.class)
class AutonomousRunReconciliationWriterTest {

  @Mock private AutonomousRunRepository runRepository;
  @Mock private AutonomousEventService eventService;

  @InjectMocks private AutonomousRunReconciliationWriter writer;

  private static AutonomousRun settledRun() {
    AutonomousRun run = new AutonomousRun();
    run.setId("run-1");
    run.setSimulationId("sim-1");
    run.setStatus(AutonomousRunStatus.CANCELED);
    return run;
  }

  @Test
  @DisplayName("the claimer of the terminal flip narrates it once, through a tenant-scoped reload")
  void claimerNarratesOnce() {
    when(runRepository.settleTerminalStatusIfActive(
            eq("run-1"), eq("tenant-1"), eq(AutonomousRunStatus.CANCELED), any(Instant.class)))
        .thenReturn(1);
    when(runRepository.findByIdAndTenantId("run-1", "tenant-1"))
        .thenReturn(Optional.of(settledRun()));

    AutonomousRun result =
        writer.settleRunStatus("run-1", "tenant-1", AutonomousRunStatus.CANCELED, "detail");

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(AutonomousRunStatus.CANCELED);
    verify(eventService)
        .append(
            eq("run-1"),
            eq("sim-1"),
            eq(AutonomousEventType.STATUS),
            eq("Run canceled"),
            eq("detail"),
            eq(null));
  }

  @Test
  @DisplayName("a loser of the settle race returns the terminal run but stays silent")
  void loserStaysSilent() {
    when(runRepository.settleTerminalStatusIfActive(
            eq("run-1"), eq("tenant-1"), eq(AutonomousRunStatus.CANCELED), any(Instant.class)))
        .thenReturn(0);
    when(runRepository.findByIdAndTenantId("run-1", "tenant-1"))
        .thenReturn(Optional.of(settledRun()));

    AutonomousRun result =
        writer.settleRunStatus("run-1", "tenant-1", AutonomousRunStatus.CANCELED, "detail");

    assertThat(result).isNotNull();
    verify(eventService, never()).append(any(), any(), any(), anyString(), anyString(), any());
  }

  @Test
  @DisplayName("a run invisible in the caller's tenant scope degrades to null, never cross-tenant")
  void crossTenantRunIsInvisible() {
    when(runRepository.settleTerminalStatusIfActive(
            eq("run-1"), eq("tenant-1"), eq(AutonomousRunStatus.CANCELED), any(Instant.class)))
        .thenReturn(0);
    when(runRepository.findByIdAndTenantId("run-1", "tenant-1")).thenReturn(Optional.empty());

    AutonomousRun result =
        writer.settleRunStatus("run-1", "tenant-1", AutonomousRunStatus.CANCELED, "detail");

    assertThat(result).isNull();
    verify(eventService, never()).append(any(), any(), any(), anyString(), anyString(), any());
  }
}
