package io.openaev.rest.scenario;

import static io.openaev.database.specification.ScenarioSpecification.byName;
import static io.openaev.database.specification.TeamSpecification.fromScenario;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RBAC;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawPaginationScenario;
import io.openaev.database.raw.RawPlayer;
import io.openaev.database.repository.*;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.rest.asset.endpoint.form.EndpointOutput;
import io.openaev.rest.asset_group.form.AssetGroupOutput;
import io.openaev.rest.custom_dashboard.CustomDashboardService;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.form.LessonsInput;
import io.openaev.rest.exercise.form.ScenarioTeamPlayersEnableInput;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.scenario.form.*;
import io.openaev.rest.scenario.response.ScenarioOutput;
import io.openaev.rest.team.output.TeamOutput;
import io.openaev.service.*;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class ScenarioApi extends RestBehavior {

  public static final String SCENARIO_URI = "/api/scenarios";

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
  private final PlatformSettingsService platformSettingsService;

  @PostMapping(SCENARIO_URI)
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.SCENARIO)
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
          this.platformSettingsService
              .setting(SettingKeys.DEFAULT_SCENARIO_DASHBOARD.key())
              .map(Setting::getValue)
              .filter(v -> !v.isEmpty())
              .map(this.customDashboardService::customDashboard)
              .orElse(null));
    }
    return this.scenarioService.createScenario(scenario);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.DUPLICATE,
      resourceType = ResourceType.SCENARIO)
  public Scenario duplicateScenario(@PathVariable @NotBlank final String scenarioId) {
    return scenarioService.getDuplicateScenario(scenarioId);
  }

  @GetMapping(SCENARIO_URI)
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public List<ScenarioSimple> scenarios() {
    return this.scenarioService.scenarios();
  }

  @LogExecutionTime
  @PostMapping(SCENARIO_URI + "/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public Page<RawPaginationScenario> scenarios(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return this.scenarioService.scenarios(searchPaginationInput);
  }

  @LogExecutionTime
  @PostMapping(SCENARIO_URI + "/search-by-id")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  @Operation(
      summary = "Get scenarios by their id",
      description = "Get the scenarios with the specified ids if you have the right to see them")
  public List<ScenarioSimple> scenariosById(
      @RequestBody final GetScenariosInput getScenariosInput) {
    return this.scenarioService.scenarios(getScenariosInput.getScenarioIds());
  }

  @GetMapping(SCENARIO_URI + "/{scenarioId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public ScenarioOutput scenario(@PathVariable @NotBlank final String scenarioId) {
    return scenarioService.getScenarioById(scenarioId);
  }

  @GetMapping(SCENARIO_URI + "/{scenarioId}/healthchecks")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public List<HealthCheck> streamHealthChecks(@PathVariable @NotBlank final String scenarioId) {
    return scenarioService.runChecks(scenarioId);
  }

  @PutMapping(SCENARIO_URI + "/{scenarioId}")
  @RBAC(
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

  @DeleteMapping(SCENARIO_URI + "/{scenarioId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.SCENARIO)
  public void deleteScenario(@PathVariable @NotBlank final String scenarioId) {
    this.scenarioService.deleteScenario(scenarioId);
  }

  // -- TAGS --

  @PutMapping(SCENARIO_URI + "/{scenarioId}/tags")
  @RBAC(
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

  @GetMapping(SCENARIO_URI + "/{scenarioId}/export")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.SEARCH,
      resourceType = ResourceType.SCENARIO)
  public void exportScenario(
      @PathVariable @NotBlank final String scenarioId,
      @RequestParam(required = false) final boolean isWithTeams,
      @RequestParam(required = false) final boolean isWithPlayers,
      @RequestParam(required = false) final boolean isWithVariableValues,
      HttpServletResponse response)
      throws IOException {
    this.scenarioService.exportScenario(
        scenarioId, isWithTeams, isWithPlayers, isWithVariableValues, response);
  }

  // -- IMPORT --

  @PostMapping(SCENARIO_URI + "/import")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.SCENARIO)
  public void importScenario(@RequestPart("file") @NotNull MultipartFile file) throws Exception {
    this.importService.handleFileImport(file, null, null);
  }

  // -- TEAMS --
  @LogExecutionTime
  @GetMapping(SCENARIO_URI + "/{scenarioId}/teams")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public List<TeamOutput> scenarioTeams(@PathVariable @NotBlank final String scenarioId) {
    return this.teamService.find(fromScenario(scenarioId));
  }

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(SCENARIO_URI + "/{scenarioId}/teams/remove")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Iterable<TeamOutput> removeScenarioTeams(
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody final ScenarioUpdateTeamsInput input) {
    return this.scenarioService.removeTeams(scenarioId, input.getTeamIds());
  }

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(SCENARIO_URI + "/{scenarioId}/teams/replace")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public List<TeamOutput> replaceScenarioTeams(
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody final ScenarioUpdateTeamsInput input) {
    return this.scenarioService.replaceTeams(scenarioId, input.getTeamIds());
  }

  @GetMapping(SCENARIO_URI + "/{scenarioId}/players")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Iterable<RawPlayer> getPlayersByScenario(@PathVariable String scenarioId) {
    return userRepository.rawPlayersByScenarioId(scenarioId);
  }

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/enable")
  @RBAC(
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

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/disable")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Scenario disableScenarioTeamPlayers(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String teamId,
      @Valid @RequestBody final ScenarioTeamPlayersEnableInput input) {
    return this.scenarioService.disablePlayers(scenarioId, teamId, input.getPlayersIds());
  }

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/add")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Scenario addScenarioTeamPlayers(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String teamId,
      @Valid @RequestBody final ScenarioTeamPlayersEnableInput input) {
    return this.scenarioService.addScenarioPlayer(scenarioId, teamId, input.getPlayersIds());
  }

  @Transactional(rollbackOn = Exception.class)
  @PutMapping(SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/remove")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public Scenario removeScenarioTeamPlayers(
      @PathVariable @NotBlank final String scenarioId,
      @PathVariable @NotBlank final String teamId,
      @Valid @RequestBody final ScenarioTeamPlayersEnableInput input) {
    Team team = teamRepository.findById(teamId).orElseThrow(ElementNotFoundException::new);
    Iterable<User> teamUsers = userRepository.findAllById(input.getPlayersIds());
    team.getUsers().removeAll(fromIterable(teamUsers));
    teamRepository.save(team);
    return this.scenarioService.disablePlayers(scenarioId, teamId, input.getPlayersIds());
  }

  // -- RECURRENCE --

  @PutMapping(SCENARIO_URI + "/{scenarioId}/recurrence")
  @RBAC(
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

  @GetMapping(SCENARIO_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText) {
    return fromIterable(
            this.scenarioRepository.findAll(
                byName(searchText), Sort.by(Sort.Direction.ASC, "name")))
        .stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @PostMapping(SCENARIO_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.scenarioRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @GetMapping(SCENARIO_URI + "/category/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public List<FilterUtilsJpa.Option> categoryOptionsByName(
      @RequestParam(required = false) final String searchText) {
    return this.scenarioRepository
        .findDistinctCategoriesBySearchTerm(searchText, PageRequest.of(0, 10))
        .stream()
        .map(i -> new FilterUtilsJpa.Option(i, i))
        .toList();
  }

  // -- LESSON --
  @PutMapping(SCENARIO_URI + "/{scenarioId}/lessons")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackOn = Exception.class)
  public Scenario updateScenarioLessons(
      @PathVariable String scenarioId, @Valid @RequestBody LessonsInput input) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    scenario.setLessonsAnonymized(input.isLessonsAnonymized());
    return scenarioRepository.save(scenario);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/exercise/running")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.SCENARIO)
  public Exercise createRunningExerciseFromScenario(
      @PathVariable @NotBlank final String scenarioId) {
    Scenario scenario = this.scenarioService.scenario(scenarioId);
    this.scenarioService.throwIfScenarioNotLaunchable(scenario);
    return scenarioToExerciseService.toExercise(
        scenario, now().truncatedTo(MINUTES).plus(1, MINUTES), true);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/check-rules")
  @RBAC(
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
  @GetMapping(SCENARIO_URI + "/{scenarioId}/asset-groups")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Operation(
      summary =
          "Get asset groups. Can only be called if the user has access to the given scenario.",
      description = "Get all asset groups used by injects for a given scenario")
  public List<AssetGroup> assetGroups(@PathVariable String scenarioId) {
    return this.assetGroupService.assetGroupsForScenario(scenarioId);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/asset-groups/find")
  @RBAC(
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

  @GetMapping(SCENARIO_URI + "/{scenarioId}/channels")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Operation(
      summary = "Get channels. Can only be called if the user has access to the given scenario.",
      description = "Get all channels used by articles for a given scenario")
  public Iterable<Channel> channels(@PathVariable String scenarioId) {
    return this.channelService.channelsForScenario(scenarioId);
  }

  @GetMapping(SCENARIO_URI + "/{scenarioId}/endpoints")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Operation(
      summary = "Get endpoints. Can only be called if the user has access to the given scenario.",
      description = "Get all endpoints used by injects for a given scenario")
  public List<Endpoint> endpoints(@PathVariable String scenarioId) {
    return this.endpointService.endpointsForScenario(scenarioId);
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/endpoints/find")
  @RBAC(
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

  @GetMapping(SCENARIO_URI + "/{scenarioId}/documents")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  @Operation(
      summary = "Get documents. Can only be called if the user has access to the given scenario.",
      description = "Get all documents used by injects for a given scenario")
  public List<Document> documents(@PathVariable String scenarioId) {
    return this.documentService.documentsForScenario(scenarioId);
  }

  // end region
}
