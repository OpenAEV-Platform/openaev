package io.openaev.service.scenario;

import static io.openaev.config.SessionHelper.currentUser;
import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.MINUTES;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.User;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.FaithfulCopyTransaction;
import io.openaev.service.ScenarioToExerciseService;
import io.openaev.service.autonomous.AutonomousRunService;
import io.openaev.service.catalog.scenario.targeting.CatalogScenarioTargets;
import io.openaev.service.catalog.scenario.targeting.TargetChoice;
import io.openaev.service.chaining.WorkflowOwnerScope;
import io.openaev.service.chaining.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Starts a simulation from a scenario, now. */
@Service
@RequiredArgsConstructor
public class ScenarioLaunchService {

  private final ScenarioService scenarioService;
  private final ScenarioToExerciseService scenarioToExerciseService;
  private final WorkflowService workflowService;
  private final WorkflowOwnerScope workflowOwnerScope;
  private final AutonomousRunService autonomousRunService;
  private final FaithfulCopyTransaction faithfulCopy;
  private final UserRepository userRepository;
  private final InjectService injectService;
  private final CatalogScenarioTargets catalogScenarioTargets;

  /** The id of the simulation started; the targets go to its injects, never to the scenario. */
  @Transactional
  public String launch(TxCtx ctx, String scenarioId, TargetChoice targets)
      throws ChainingException {
    Scenario scenario = this.scenarioService.scenario(ctx, scenarioId);
    String launchedById = currentUser().getId();
    // The scenario's workflow is read, and its simulation's started, in the scenario's own tenant
    // whatever the caller's scope: access to the scenario was granted, its workflow follows it.
    boolean chaining =
        workflowOwnerScope.call(scenario, () -> workflowService.isScenarioChaining(scenarioId));

    if (chaining) {
      // A normal (operator-driven) launch makes any prior autonomous AI outcome on this scenario
      // stale: clear a settled run so the scenario reverts to its normal overview / hero (the AI
      // plan or run outcome is no longer the latest activity). No-op when the scenario carries no
      // run or the feature is off, and never tears down a still-active run. Read and written in
      // the scenario's own tenant, like its workflow: the run follows its scenario, whatever the
      // caller's scope.
      workflowOwnerScope.run(
          scenario, () -> autonomousRunService.supersedeSettledRunOnManualLaunch(scenarioId));
    } else {
      this.scenarioService.throwIfScenarioNotLaunchable(scenario);
    }

    // Resolved before the copy, which sees every marking: a target the user cannot see is dropped.
    TargetChoice visibleTargets =
        catalogScenarioTargets.visible(scenario.getTenant().getId(), targets);

    // The simulation is a faithful copy of the scenario: it keeps the targets the user is not
    // cleared for, which the dispatch will not execute. The copy starts from a cleared persistence
    // context, so the scenario and the launcher are reloaded inside it; the caller then reads the
    // simulation back under the user's clearance.
    return faithfulCopy.execute(
        ctx,
        () -> {
          User launchedBy =
              this.userRepository.findById(launchedById).orElseThrow(ElementNotFoundException::new);
          Exercise simulation =
              scenarioToExerciseService.toExercise(
                  this.scenarioService.scenario(ctx, scenarioId),
                  now().truncatedTo(MINUTES).plus(1, MINUTES),
                  true,
                  launchedBy);
          if (!visibleTargets.isEmpty()) {
            catalogScenarioTargets.addTo(
                simulation.getTenant().getId(),
                injectService.findBySimulationId(simulation.getId()),
                visibleTargets);
          }
          if (chaining) {
            workflowOwnerScope.run(
                scenario,
                () ->
                    workflowService.startWorkflowByScenarioIdAndSimulation(scenarioId, simulation));
          }
          return simulation.getId();
        });
  }
}
