package io.openaev.rest.scenario;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.database.specification.ScenarioSpecification.byName;
import static io.openaev.database.specification.TeamSpecification.fromScenario;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.expectations.ExpectationsDriftService;
import io.openaev.api.expectations.dto.ExpectationsDriftDismissInput;
import io.openaev.api.expectations.dto.ExpectationsDriftOutput;
import io.openaev.api.expectations.dto.ExpectationsRealignOutput;
import io.openaev.context.BulkOperationContext;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.model.TenantSettingKeys;
import io.openaev.database.raw.RawPaginationScenario;
import io.openaev.database.raw.RawPlayer;
import io.openaev.database.repository.*;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.rest.asset.endpoint.form.EndpointOutput;
import io.openaev.rest.asset_group.form.AssetGroupOutput;
import io.openaev.rest.custom_dashboard.CustomDashboardService;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.form.LessonsInput;
import io.openaev.rest.exercise.form.ScenarioTeamPlayersEnableInput;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.scenario.form.*;
import io.openaev.rest.scenario.response.ScenarioOutput;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.rest.team.output.TeamOutput;
import io.openaev.service.*;
import io.openaev.service.autonomous.AutonomousRunService;
import io.openaev.service.chaining.StepService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.service.settings.TenantSettingsService;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class ScenarioApi extends RestBehavior {

  public static final String SCENARIO_URI = "/api/scenarios";
  public static final String TENANT_SCENARIO_URI = TENANT_PREFIX + "/scenarios";

  private final CustomDashboardService customDashboardService;
  private final TagRepository tagRepository;
  private final TeamRepository teamRepository;
  private final UserRepository userRepository;
  private final ScenarioRepository scenarioRepository;
  private final ScenarioToExerciseService scenarioToExerciseService;
  private final ImportService importService;
  private final ScenarioService scenarioService;
  private final TeamService teamService;
  private final AssetGroupService assetGroupService;
  private final EndpointService endpointService;
  private final ChannelService channelService;
  private final DocumentService documentService;
  private final TenantSettingsService tenantSettingsService;
  private final WorkflowService workflowService;
  private final StepService stepService;
  private final PreviewFeatureService previewFeatureService;
  private final ExpectationsDriftService expectationsDriftService;
  private final AutonomousRunService autonomousRunService;

  @PostMapping({SCENARIO_URI, TENANT_SCENARIO_URI})
  @Transactional
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.SCENARIO)
  public Scenario createScenario(@Valid @RequestBody final ScenarioInput input) {
    if (input == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scenario input cannot be null");
    }
    Scenario scenario = new Scenario();
    scenario.setUpdateAttributes(input);
    scenario.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    if (hasText(input.getCustomDashboard())) {
      scenario.setCustomDashboard(
          this.customDashboardService.customDashboard(input.getCustomDashboard()));
    } else {
      scenario.setCustomDashboard(
          this.tenantSettingsService
              .findSetting(
                  TenantContext.getCurrentTenant(),
                  TenantSettingKeys.TENANT_SCENARIO_DASHBOARD.key())
              .map(Setting::getValue)
              .filter(v -> !v.isEmpty())
              .map(this.customDashboardService::customDashboard)
              .orElse(null));
    }
    Scenario savedScenario = this.scenarioService.createScenario(scenario);

    // If the chaining feature flag is enabled and the engine is "chaining", create and link a
    // workflow to the scenario
    if (previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING)
        && Boolean.TRUE.equals(input.getIsChaining())) {
      workflowService.creationWorkflow(savedScenario);
    }

    return savedScenario;
  }

  @PostMapping({
    SCENARIO_URI + "/with-injector-contracts",
    TENANT_SCENARIO_URI + "/with-injector-contracts"
  })
  // SUPPORTS (not REQUIRED) on purpose: the creation runs in the service's own transaction,
  // wrapped in a massive-operation scope (per-entity stream event suppression) that must cover the
  // commit-time flush - the arsenal selection can create thousands of injects.
  @Transactional(propagation = Propagation.SUPPORTS)
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.SCENARIO)
  public Scenario createScenarioWithInjectorContracts(
      @Valid @RequestBody final ScenarioAndInjectorContractsInputs inputs) {
    return BulkOperationContext.runSuppressed(
        () ->
            this.scenarioService.createScenarioWithInjectorContracts(
                TenantContext.getCurrentTenant(),
                inputs.getScenarioInput(),
                inputs.getInjectorContractSearchPaginationInput(),
                inputs.getLocale()));
  }

  @PutMapping({
    SCENARIO_URI + "/with-injector-contracts",
    TENANT_SCENARIO_URI + "/with-injector-contracts"
  })
  // SUPPORTS (not REQUIRED) on purpose: the update runs in the service's own transaction, wrapped
  // in a massive-operation scope (per-entity stream event suppression) that must cover the
  // commit-time flush - the arsenal selection can create thousands of injects.
  @Transactional(propagation = Propagation.SUPPORTS)
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.SCENARIO)
  public List<Scenario> updateScenariosWithInjectorContracts(
      @Valid @RequestBody final ScenarioIdsAndInjectorContractsInputs inputs) {
    return BulkOperationContext.runSuppressed(
        () ->
            this.scenarioService.updateScenariosWithInjectorContracts(
                inputs.getScenarioIds(),
                inputs.getInjectorContractSearchPaginationInput(),
                inputs.getLocale()));
  }

  @PostMapping({SCENARIO_URI + "/{scenarioId}", TENANT_SCENARIO_URI + "/{scenarioId}"})
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.DUPLICATE,
      resourceType = ResourceType.SCENARIO)
  public Scenario duplicateScenario(@PathVariable @NotBlank final String scenarioId) {
    return scenarioService.getDuplicateScenario(scenarioId);
  }

  @GetMapping({SCENARIO_URI, TENANT_SCENARIO_URI})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public List<ScenarioSimple> scenarios() {
    return this.scenarioService.scenarios();
  }

  @LogExecutionTime
  @PostMapping({SCENARIO_URI + "/search", TENANT_SCENARIO_URI + "/search"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public Page<RawPaginationScenario> scenarios(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return this.scenarioService.scenarios(searchPaginationInput);
  }

  @LogExecutionTime
  @PostMapping({SCENARIO_URI + "/search-by-id", TENANT_SCENARIO_URI + "/search-by-id"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  @Operation(
      summary = "Get scenarios by their id",
      description = "Get the scenarios with the specified ids if you have the right to see them")
  public List<ScenarioSimple> scenariosById(
      @RequestBody final GetScenariosInput getScenariosInput) {
    return this.scenarioService.scenarios(getScenariosInput.getScenarioIds());
  }

  @GetMapping({SCENARIO_URI + "/{scenarioId}", TENANT_SCENARIO_URI + "/{scenarioId}"})
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public ScenarioOutput scenario(@PathVariable @NotBlank final String scenarioId) {
    return scenarioService.getScenarioById(scenarioId);
  }

  @GetMapping({
    SCENARIO_URI + "/{scenarioId}/healthchecks",
    TENANT_SCENARIO_URI + "/{scenarioId}/healthchecks"
  })
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public List<HealthCheck> streamHealthChecks(
      // The TxCtx parameter is not used directly; it signals the transaction aspect to set
      // the tenant scope in the DB session so the v2 inspector can resolve can_access_tenant.
      TxCtx ctx, @PathVariable @NotBlank final String scenarioId) {
    return scenarioService.runChecks(scenarioId);
  }

  @Operation(
      summary = "Get the expectation drift report of a scenario",
      description =
          "Compares the predefined expectations of the injector contracts with the expectations"
              + " stored inside the scenario injects")
  @GetMapping({
    SCENARIO_URI + "/{scenarioId}/expectations-drift",
    TENANT_SCENARIO_URI + "/{scenarioId}/expectations-drift"
  })
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public ExpectationsDriftOutput scenarioExpectationsDrift(
      @PathVariable @NotBlank final String scenarioId) {
    return expectationsDriftService.scenarioDrift(scenarioId);
  }

  @Operation(
      summary = "Realign the expectations of the scenario injects onto their contracts",
      description =
          "Overwrites the expectations of every drifted inject with the predefined expectations"
              + " currently exposed by its injector contract, as a tracked massive operation")
  // SUPPORTS (not REQUIRED) on purpose: the realignment runs chunk by chunk in the service's own
  // short transactions, wrapped in a massive-operation scope (header progress indicator +
  // per-entity stream event suppression) that must cover each commit-time flush.
  @Transactional(propagation = Propagation.SUPPORTS)
  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/expectations-drift/realign",
    TENANT_SCENARIO_URI + "/{scenarioId}/expectations-drift/realign"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public ExpectationsRealignOutput realignScenarioExpectations(
      @PathVariable @NotBlank final String scenarioId) {
    return expectationsDriftService.realignScenario(scenarioId);
  }

  @Operation(
      summary = "Dismiss or restore the expectation drift warning of a scenario",
      description =
          "Acknowledges that the drifted expectations were customized on purpose: the warning is"
              + " downgraded to a discreet indicator. Persisted in database so the dismissal is"
              + " shared between users, and reset on realignment")
  @PutMapping({
    SCENARIO_URI + "/{scenarioId}/expectations-drift/dismiss",
    TENANT_SCENARIO_URI + "/{scenarioId}/expectations-drift/dismiss"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public ExpectationsDriftOutput dismissScenarioExpectationsDrift(
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody final ExpectationsDriftDismissInput input) {
    return expectationsDriftService.dismissScenarioDrift(scenarioId, input.dismissed());
  }

  @PutMapping({SCENARIO_URI + "/{scenarioId}", TENANT_SCENARIO_URI + "/{scenarioId}"})
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Scenario updateScenario(
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody final UpdateScenarioInput input) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    Set<Tag> currentTagList = scenario.getTags();
    scenario.setUpdateAttributes(input);
    scenario.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    if (hasText(input.getCustomDashboard())) {
      scenario.setCustomDashboard(
          this.customDashboardService.customDashboard(input.getCustomDashboard()));
    } else {
      scenario.setCustomDashboard(null);
    }
    return this.scenarioService.updateScenario(scenario, currentTagList, input.isApplyTagRule());
  }

  @DeleteMapping({SCENARIO_URI + "/{scenarioId}", TENANT_SCENARIO_URI + "/{scenarioId}"})
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.SCENARIO)
  public void deleteScenario(@PathVariable @NotBlank final String scenarioId) {
    // Tear down the autonomous run's coordination first (409 if it is still active), then delete the
    // scenario. Finished simulations are NOT deleted - they detach and remain as history, like any
    // chained simulation. No-op for manual scenarios.
    this.autonomousRunService.deleteForScenario(scenarioId);
    this.scenarioService.deleteScenario(scenarioId);
  }

  @Operation(
      description = "Bulk delete of scenarios",
      tags = {"Scenarios"})
  @LogExecutionTime
  @DeleteMapping({SCENARIO_URI, TENANT_SCENARIO_URI})
  // SUPPORTS (not REQUIRED) on purpose: the service deletes in small independent transactions
  // (chunked, with deadlock retry) - a request-wide transaction would defeat that and used to
  // deadlock in production against concurrent inject expectation updates.
  @Transactional(propagation = Propagation.SUPPORTS)
  @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.SCENARIO)
  public List<String> bulkDeleteScenarios(
      @RequestBody @Valid final ScenarioBulkProcessingInput input) {
    return this.scenarioService.bulkDeleteScenarios(input);
  }

  // -- TAGS --

  @PutMapping({SCENARIO_URI + "/{scenarioId}/tags", TENANT_SCENARIO_URI + "/{scenarioId}/tags"})
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Scenario updateScenarioTags(
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody final ScenarioUpdateTagsInput input) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    Set<Tag> currentTagList = scenario.getTags();
    scenario.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    return this.scenarioService.updateScenario(scenario, currentTagList, input.isApplyTagRule());
  }

  // -- EXPORT --

  @GetMapping({SCENARIO_URI + "/{scenarioId}/export", TENANT_SCENARIO_URI + "/{scenarioId}/export"})
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.SEARCH,
      resourceType = ResourceType.SCENARIO)
  public void exportScenario(
      @PathVariable @NotBlank final String scenarioId,
      @RequestParam(required = false) final boolean isWithTeams,
      @RequestParam(required = false) final boolean isWithPlayers,
      @RequestParam(required = false) final boolean isWithVariableValues,
      @RequestParam(required = false, defaultValue = "true") final boolean isWithScopeDefinition,
      HttpServletResponse response)
      throws IOException {
    this.scenarioService.exportScenario(
        scenarioId,
        isWithTeams,
        isWithPlayers,
        isWithVariableValues,
        isWithScopeDefinition,
        response);
  }

  // -- IMPORT --

  @PostMapping({SCENARIO_URI + "/import", TENANT_SCENARIO_URI + "/import"})
  @Transactional
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.SCENARIO)
  public void importScenario(@RequestPart("file") @NotNull MultipartFile file) throws Exception {
    this.importService.handleFileImport(file, null, null);
  }

  // -- TEAMS --
  @LogExecutionTime
  @Transactional
  @GetMapping({SCENARIO_URI + "/{scenarioId}/teams", TENANT_SCENARIO_URI + "/{scenarioId}/teams"})
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public List<TeamOutput> scenarioTeams(@PathVariable @NotBlank final String scenarioId) {
    return this.teamService.find(fromScenario(scenarioId));
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping({
    SCENARIO_URI + "/{scenarioId}/teams/remove",
    TENANT_SCENARIO_URI + "/{scenarioId}/teams/remove"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Iterable<TeamOutput> removeScenarioTeams(
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody final ScenarioUpdateTeamsInput input) {
    return this.scenarioService.removeTeams(scenarioId, input.getTeamIds());
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping({
    SCENARIO_URI + "/{scenarioId}/teams/replace",
    TENANT_SCENARIO_URI + "/{scenarioId}/teams/replace"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public List<TeamOutput> replaceScenarioTeams(
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody final ScenarioUpdateTeamsInput input) {
    return this.scenarioService.replaceTeams(scenarioId, input.getTeamIds());
  }

  @GetMapping({
    SCENARIO_URI + "/{scenarioId}/players",
    TENANT_SCENARIO_URI + "/{scenarioId}/players"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<RawPlayer> getPlayersByScenario(@PathVariable String scenarioId) {
    return userRepository.rawPlayersByScenarioId(scenarioId);
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping({
    SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/enable",
    TENANT_SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/enable"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Scenario enableScenarioTeamPlayers(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String teamId,
      @Valid @RequestBody final ScenarioTeamPlayersEnableInput input) {
    return this.scenarioService.enableAddScenarioTeamPlayer(
        scenarioId, teamId, input.getPlayersIds());
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping({
    SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/disable",
    TENANT_SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/disable"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Scenario disableScenarioTeamPlayers(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String teamId,
      @Valid @RequestBody final ScenarioTeamPlayersEnableInput input) {
    return this.scenarioService.disablePlayers(scenarioId, teamId, input.getPlayersIds());
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping({
    SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/add",
    TENANT_SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/add"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Scenario addScenarioTeamPlayers(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String teamId,
      @Valid @RequestBody final ScenarioTeamPlayersEnableInput input) {
    return this.scenarioService.addScenarioPlayer(scenarioId, teamId, input.getPlayersIds());
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping({
    SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/remove",
    TENANT_SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/remove"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Scenario removeScenarioTeamPlayers(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String teamId,
      @Valid @RequestBody final ScenarioTeamPlayersEnableInput input) {
    Team team =
        teamRepository
            .findByIdAndTenantId(teamId, TenantContext.getCurrentTenant())
            .orElseThrow(ElementNotFoundException::new);
    Iterable<User> teamUsers = userRepository.findAllById(input.getPlayersIds());
    team.getUsers().removeAll(fromIterable(teamUsers));
    teamRepository.save(team);
    return this.scenarioService.disablePlayers(scenarioId, teamId, input.getPlayersIds());
  }

  // -- RECURRENCE --

  @PutMapping({
    SCENARIO_URI + "/{scenarioId}/recurrence",
    TENANT_SCENARIO_URI + "/{scenarioId}/recurrence"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.SCENARIO)
  public Scenario updateScenarioRecurrence(
      // ctx is unused directly: the aspect reads it to scope this transaction against the
      // v2-active executors table (throwIfScenarioNotLaunchable's Enterprise gate reads each
      // targeted agent's executor).
      TxCtx ctx,
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody final ScenarioRecurrenceInput input) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    // Scheduling itself is a Community Edition feature, but the Enterprise executor gate still
    // applies: without it, scheduling would bypass the licence check enforced on manual launches
    // (scheduled executions deliberately skip the gate at run time). Gate on both fields: a
    // recurrence expression with a null start date still triggers scheduled execution.
    boolean schedules =
        input.getRecurrenceStart() != null
            || (input.getRecurrence() != null && !input.getRecurrence().isBlank());
    if (schedules) {
      this.scenarioService.throwIfScenarioNotLaunchable(scenario);
    }
    scenario.setUpdateAttributes(input);
    return this.scenarioService.updateScenario(scenario);
  }

  // -- OPTION --

  @GetMapping({SCENARIO_URI + "/options", TENANT_SCENARIO_URI + "/options"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText) {
    return fromIterable(
            this.scenarioRepository.findAll(
                byName(searchText), Sort.by(Sort.Direction.ASC, "name")))
        .stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @PostMapping({SCENARIO_URI + "/options", TENANT_SCENARIO_URI + "/options"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.scenarioRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @GetMapping({SCENARIO_URI + "/category/options", TENANT_SCENARIO_URI + "/category/options"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public List<FilterUtilsJpa.Option> categoryOptionsByName(
      @RequestParam(required = false) final String searchText) {
    return this.scenarioRepository
        .findDistinctCategoriesBySearchTerm(searchText, PageRequest.of(0, 10))
        .stream()
        .map(i -> new FilterUtilsJpa.Option(i, i))
        .toList();
  }

  // -- LESSON --
  @PutMapping({
    SCENARIO_URI + "/{scenarioId}/lessons",
    TENANT_SCENARIO_URI + "/{scenarioId}/lessons"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackFor = Exception.class)
  public Scenario updateScenarioLessons(
      @PathVariable String scenarioId, @Valid @RequestBody LessonsInput input) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    // Partial update: absent fields keep their current value (older API consumers
    // only send lessons_anonymized and must not reset the enabled flag).
    if (input.getLessonsAnonymized() != null) {
      scenario.setLessonsAnonymized(input.getLessonsAnonymized());
    }
    if (input.getLessonsEnabled() != null) {
      scenario.setLessonsEnabled(input.getLessonsEnabled());
    }
    return scenarioRepository.save(scenario);
  }

  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/exercise/running",
    TENANT_SCENARIO_URI + "/{scenarioId}/exercise/running"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.SCENARIO)
  public Exercise createRunningExerciseFromScenario(
      // ctx is unused directly: the aspect reads it to scope this transaction against the
      // v2-active executors table (throwIfScenarioNotLaunchable's Enterprise gate reads each
      // targeted agent's executor).
      TxCtx ctx, @PathVariable @NotBlank final String scenarioId) throws ChainingException {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    Exercise simulation;

    if (previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING)
        && workflowService.isScenarioChaining(scenarioId)) {
      // A normal (operator-driven) launch makes any prior autonomous AI outcome on this scenario
      // stale: clear a settled run so the scenario reverts to its normal overview / hero (the AI
      // plan or run outcome is no longer the latest activity). No-op when the scenario carries no
      // run or the feature is off, and never tears down a still-active run.
      autonomousRunService.supersedeSettledRunOnManualLaunch(scenarioId);
      simulation =
          scenarioToExerciseService.toExercise(
              scenario, now().truncatedTo(MINUTES).plus(1, MINUTES), true);
      workflowService.startWorkflowByScenarioIdAndSimulation(scenarioId, simulation);

    } else {
      this.scenarioService.throwIfScenarioNotLaunchable(scenario);
      simulation =
          scenarioToExerciseService.toExercise(
              scenario, now().truncatedTo(MINUTES).plus(1, MINUTES), true);
    }

    return simulation;
  }

  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/check-rules",
    TENANT_SCENARIO_URI + "/{scenarioId}/check-rules"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Returns whether or not the rules apply")
      })
  @Operation(summary = "Check rules", description = "Check if the rules apply to a scenario update")
  public CheckScenarioRulesOutput checkIfRuleApplies(
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody final CheckScenarioRulesInput input) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    return CheckScenarioRulesOutput.builder()
        .rulesFound(this.scenarioService.checkIfTagRulesApplies(scenario, input.getNewTags()))
        .build();
  }

  // region asset groups, endpoints, documents and channels
  @GetMapping({
    SCENARIO_URI + "/{scenarioId}/asset-groups",
    TENANT_SCENARIO_URI + "/{scenarioId}/asset-groups"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Operation(
      summary =
          "Get asset groups. Can only be called if the user has access to the given scenario.",
      description = "Get all asset groups used by injects for a given scenario")
  @Transactional
  public List<AssetGroup> assetGroups(@PathVariable String scenarioId) {
    return this.assetGroupService.assetGroupsForScenario(scenarioId);
  }

  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/asset-groups/find",
    TENANT_SCENARIO_URI + "/{scenarioId}/asset-groups/find"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Operation(
      summary =
          "Get asset groups by ids. Can only be called if the user has access to the given scenario.",
      description = "Get all asset groups by ids and used by injects for a given scenario")
  public List<AssetGroupOutput> assetGroupsByIds(
      @PathVariable String scenarioId,
      @RequestBody @Valid @NotNull final List<String> assetGroupIds) {
    return this.assetGroupService.assetGroupsByIdsForScenario(scenarioId, assetGroupIds);
  }

  @GetMapping({
    SCENARIO_URI + "/{scenarioId}/channels",
    TENANT_SCENARIO_URI + "/{scenarioId}/channels"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Operation(
      summary = "Get channels. Can only be called if the user has access to the given scenario.",
      description = "Get all channels used by articles for a given scenario")
  @Transactional
  public Iterable<Channel> channels(@PathVariable String scenarioId) {
    return this.channelService.channelsForScenario(scenarioId);
  }

  @GetMapping({
    SCENARIO_URI + "/{scenarioId}/endpoints",
    TENANT_SCENARIO_URI + "/{scenarioId}/endpoints"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Operation(
      summary = "Get endpoints. Can only be called if the user has access to the given scenario.",
      description = "Get all endpoints used by injects for a given scenario")
  @Transactional
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // executors table (each endpoint's agents eager-load their executor).
  public List<Endpoint> endpoints(TxCtx ctx, @PathVariable String scenarioId) {
    return this.endpointService.endpointsForScenario(scenarioId);
  }

  @PostMapping({
    SCENARIO_URI + "/{scenarioId}/endpoints/find",
    TENANT_SCENARIO_URI + "/{scenarioId}/endpoints/find"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Operation(
      summary =
          "Get endpoints by ids. Can only be called if the user has access to the given scenario.",
      description = "Get all endpoints by ids used by injects for a given scenario")
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // executors table (each endpoint's agents eager-load their executor).
  public List<EndpointOutput> endpointsByIds(
      TxCtx ctx,
      @PathVariable String scenarioId,
      @RequestBody @Valid @NotNull final List<String> endpointIds) {
    return this.endpointService.endpointsByIdsForScenario(scenarioId, endpointIds);
  }

  @GetMapping({
    SCENARIO_URI + "/{scenarioId}/documents",
    TENANT_SCENARIO_URI + "/{scenarioId}/documents"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Operation(
      summary = "Get documents. Can only be called if the user has access to the given scenario.",
      description = "Get all documents used by injects for a given scenario")
  @Transactional
  public List<Document> documents(@PathVariable String scenarioId) {
    return this.documentService.documentsForScenario(scenarioId);
  }

  // end region
}
