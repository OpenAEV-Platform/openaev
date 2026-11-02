package io.openaev.service.readiness;

import static io.openaev.aop.audit_log.AuditEventOrigin.SYSTEM;
import static java.time.Instant.now;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Scenario;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.service.payload_approval.BlockedPayloadsException.BlockedPayload;
import io.openaev.service.payload_approval.PayloadApprovalGate;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Puts what would run on its own back in a not-ready state, so a user has to act on purpose before
 * it runs again:
 *
 * <ul>
 *   <li>a recurring scenario or atomic testing gets its schedule <b>paused</b> (kept, not cleared);
 *       a user re-enables it by saving or stopping the schedule, which is refused while it is still
 *       blocked;
 *   <li>a simulation planned for later loses its start date (it shows as Draft); the user plans or
 *       starts it again;
 *   <li>a running simulation is not touched: the executor refuses the injects whose payload is not
 *       approved (they end in Error) and runs the rest.
 * </ul>
 *
 * <p>Trigger: a sensitive change of an inject (see {@link InjectSensitiveFields}). A payload edit
 * never triggers it: the approved version keeps running while a new version is pending. Manual
 * launches are a deliberate action: they stay possible after a sensitive change, and are blocked
 * only while a payload has no approved version.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LaunchReadinessService {

  private final ScenarioRepository scenarioRepository;
  private final ExerciseRepository exerciseRepository;
  private final InjectRepository injectRepository;
  private final PayloadApprovalGate payloadApprovalGate;

  /** What an inject runs or targets changed (option A: pause on change). */
  public void onSensitiveChange(@NotNull final Inject inject) {
    Scenario scenario = inject.getScenario();
    if (scenario != null) {
      if (scenario.getRecurrence() != null && scenario.getRecurrencePausedAt() == null) {
        pause(scenario);
        log.info(
            "Schedule of scenario {} paused: inject {} changed", scenario.getId(), inject.getId());
      }
      return;
    }
    Exercise simulation = inject.getExercise();
    if (simulation != null) {
      if (isPlanned(simulation)) {
        unplan(simulation);
        log.info(
            "Simulation {} back to draft: inject {} changed", simulation.getId(), inject.getId());
      }
      return;
    }
    if (inject.getRecurrence() != null && inject.getRecurrencePausedAt() == null) {
      pause(inject);
      log.info("Schedule of atomic testing {} paused: it changed", inject.getId());
    }
  }

  /** The teams or players of a scenario changed: its schedule waits for a deliberate action. */
  public void onScenarioTargetsChanged(@NotNull final String scenarioId) {
    scenarioRepository
        .findById(scenarioId)
        .filter(s -> s.getRecurrence() != null && s.getRecurrencePausedAt() == null)
        .ifPresent(this::pause);
  }

  /** The teams or players of a simulation changed: a planned simulation goes back to Draft. */
  public void onSimulationTargetsChanged(@NotNull final String simulationId) {
    exerciseRepository
        .findById(simulationId)
        .filter(LaunchReadinessService::isPlanned)
        .ifPresent(this::unplan);
  }

  private static boolean isPlanned(Exercise simulation) {
    return simulation.getStatus() == ExerciseStatus.SCHEDULED && simulation.getStart().isPresent();
  }

  private void unplan(Exercise simulation) {
    simulation.setStart(null);
    simulation.setUpdatedAt(now());
    exerciseRepository.save(simulation);
  }

  private void pause(Scenario scenario) {
    scenario.setRecurrencePausedAt(now());
    scenario.setUpdatedAt(now());
    scenarioRepository.save(scenario);
  }

  private void pause(Inject atomicTesting) {
    atomicTesting.setRecurrencePausedAt(now());
    atomicTesting.setUpdatedAt(now());
    injectRepository.save(atomicTesting);
  }

  private void audit(String operation, List<BlockedPayload> reason, ResourceType type, String id) {
    payloadApprovalGate.auditBlocked(operation, reason, type, id, SYSTEM);
  }
}
