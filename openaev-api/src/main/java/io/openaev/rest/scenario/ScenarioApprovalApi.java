package io.openaev.rest.scenario;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.helper.UrlHelper.buildFrontScenarioUrl;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;

import io.openaev.aop.AccessControl;
import io.openaev.config.OpenAEVConfig;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Inject;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.User;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.notification.handler.ScenarioApprovalNotifier;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.scenario.form.ScenarioAssetGroupOrderInput;
import io.openaev.service.scenario.ScenarioApprovalService;
import io.openaev.service.scenario.ScenarioService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Approval and asset group run order of a scenario (coverage-built scenarios in particular). */
@RestController
@RequiredArgsConstructor
public class ScenarioApprovalApi extends RestBehavior {

  private final ScenarioService scenarioService;
  private final ScenarioApprovalService scenarioApprovalService;
  private final ScenarioApprovalNotifier scenarioApprovalNotifier;
  private final InjectRepository injectRepository;
  private final UserRepository userRepository;
  private final OpenAEVConfig openAEVConfig;

  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/approve",
    TENANT_SCENARIO_URI + "/{scenarioId}/approve"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.SCENARIO)
  @Operation(
      summary = "Approve a scenario waiting for approval",
      description =
          "Records the approval with its author and time, turns the held injects back on and lets"
              + " the scheduled run start. Needs the launch right on the scenario.")
  public Scenario approveScenario(TxCtx ctx, @PathVariable @NotBlank final String scenarioId) {
    Scenario scenario = scenarioService.scenario(ctx, scenarioId);
    if (!scenario.isWaitingForApproval()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Scenario is not waiting for approval");
    }
    User approver =
        userRepository.findById(currentUser().getId()).orElseThrow(ElementNotFoundException::new);
    Set<Inject> injects = injectRepository.findByScenarioId(scenarioId);
    List<Inject> released = scenarioApprovalService.approve(scenario, injects, approver);
    injectRepository.saveAll(released);
    Scenario saved = scenarioService.updateScenario(scenario);
    scenarioApprovalNotifier.approved(
        saved,
        released,
        buildFrontScenarioUrl(
            openAEVConfig.getBaseUrl(), saved.getTenant().getId(), saved.getId()));
    return saved;
  }

  @PutMapping({
    SCENARIO_URI + "/{scenarioId}/asset-group-order",
    TENANT_SCENARIO_URI + "/{scenarioId}/asset-group-order"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Operation(
      summary = "Set the run order of the scenario's target asset groups",
      description =
          "Injects targeting the first asset groups start first: each rank adds one minute to the"
              + " inject's start delay. Every id must be an asset group the scenario targets.")
  public Scenario updateAssetGroupOrder(
      TxCtx ctx,
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody final ScenarioAssetGroupOrderInput input) {
    Scenario scenario = scenarioService.scenario(ctx, scenarioId);
    Set<Inject> injects = injectRepository.findByScenarioId(scenarioId);
    try {
      scenarioApprovalService.applyAssetGroupOrder(scenario, input.getAssetGroupIds(), injects);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
    injectRepository.saveAll(injects);
    return scenarioService.updateScenario(scenario);
  }
}
