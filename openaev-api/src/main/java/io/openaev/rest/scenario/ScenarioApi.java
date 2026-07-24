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
import io.openaev.context.BulkOperationContext;
import io.openaev.context.TenantContext;
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
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public List<HealthCheck> streamHealthChecks(@PathVariable @NotBlank final String scenarioId) {
    return scenarioService.runChecks(scenarioId);
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
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody final ScenarioRecurrenceInput input) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    if (input.getRecurrenceStart() != null) {
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
  public Exercise createRunningExerciseFromScenario(@PathVariable @NotBlank final String scenarioId)
      throws ChainingException {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    Exercise simulation;

    if (previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING)
        && workflowService.isScenarioChaining(scenarioId)) {
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
  public List<Endpoint> endpoints(@PathVariable String scenarioId) {
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
  public List<EndpointOutput> endpointsByIds(
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
