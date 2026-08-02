package io.openaev.service.autonomous;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.autonomous.AutonomousRun;
import io.openaev.database.model.autonomous.AutonomousRunStatus;
import io.openaev.database.repository.autonomous.AutonomousRunRepository;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.chaining.WorkflowService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/**
 * Focused unit tests for the dry-run ("plan mode") branches of {@link AutonomousRunService}. Kept
 * as a plain Mockito unit test (no Spring context / Docker) so it verifies the suppression + guard
 * logic in isolation: a plan run never dispatches, and only a settled plan can be promoted.
 */
@ExtendWith(MockitoExtension.class)
class AutonomousRunServiceTest {

  @Mock private AutonomousRunRepository runRepository;
  @Mock private WorkflowService workflowService;
  @Mock private PreviewFeatureService previewFeatureService;

  @InjectMocks private AutonomousRunService service;

  @Test
  @DisplayName("evaluateAttackPath is a no-op in plan mode (never touches the run workflow)")
  void evaluateAttackPathIsNoOpInPlanMode() {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    AutonomousRun run = new AutonomousRun();
    run.setPlanMode(true);
    run.setStatus(AutonomousRunStatus.PLANNING);
    run.setSimulationId("sim-1");
    when(runRepository.findById("run-1")).thenReturn(Optional.of(run));

    service.evaluateAttackPath("run-1");

    // A dry-run never evaluates its workflow, so nothing can be readied or dispatched.
    verify(workflowService, never()).findWorkflowRunBySimulationId(anyString());
  }

  @Test
  @DisplayName("promoteToRealRun refuses a run that is not a dry-run")
  void promoteRejectsNonPlanRun() {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    AutonomousRun run = new AutonomousRun();
    run.setPlanMode(false);
    run.setStatus(AutonomousRunStatus.RUNNING);
    when(runRepository.findById("run-1")).thenReturn(Optional.of(run));

    assertThatThrownBy(() -> service.promoteToRealRun("run-1"))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  @DisplayName("promoteToRealRun refuses a plan that is still being designed (PLANNING)")
  void promoteRejectsUnsettledPlan() {
    when(previewFeatureService.isAutonomousAttackPathEnabled()).thenReturn(true);
    AutonomousRun run = new AutonomousRun();
    run.setPlanMode(true);
    run.setStatus(AutonomousRunStatus.PLANNING);
    when(runRepository.findById("run-1")).thenReturn(Optional.of(run));

    assertThatThrownBy(() -> service.promoteToRealRun("run-1"))
        .isInstanceOf(ResponseStatusException.class);
  }
}
