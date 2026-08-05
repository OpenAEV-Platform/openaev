package io.openaev.rest.exercise;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.database.specification.ExerciseSpecification.findGrantedFor;
import static io.openaev.database.specification.TeamSpecification.fromExercise;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.rest.exercise.form.SimulationDetails.fromRawExercise;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;
import static java.time.Instant.now;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.expectations.ExpectationsDriftService;
import io.openaev.api.expectations.dto.ExpectationsDriftDismissInput;
import io.openaev.api.expectations.dto.ExpectationsDriftOutput;
import io.openaev.api.expectations.dto.ExpectationsRealignOutput;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.model.TenantSettingKeys;
import io.openaev.database.raw.*;
import io.openaev.database.repository.*;
import io.openaev.database.specification.ComcheckSpecification;
import io.openaev.database.specification.ExerciseLogSpecification;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.rest.asset.endpoint.form.EndpointOutput;
import io.openaev.rest.asset_group.form.AssetGroupOutput;
import io.openaev.rest.custom_dashboard.CustomDashboardService;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exception.InputValidationException;
import io.openaev.rest.exercise.exports.ExportOptions;
import io.openaev.rest.exercise.form.*;
import io.openaev.rest.exercise.response.ExercisesGlobalScoresOutput;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.exercise.service.ExportService;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.inject.form.InjectExpectationResultsByAttackPattern;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.rest.team.output.TeamOutput;
import io.openaev.service.*;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.service.settings.TenantSettingsService;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.persistence.criteria.Join;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class ExerciseApi extends RestBehavior {

  public static final String EXERCISE_URI = "/api/exercises";
  public static final String TENANT_EXERCISE_URI = TENANT_PREFIX + "/exercises";

  // region repositories
  private final LogRepository logRepository;
  private final TagRepository tagRepository;
  private final UserRepository userRepository;
  private final DocumentRepository documentRepository;
  private final ExerciseRepository exerciseRepository;
  private final TeamRepository teamRepository;
  private final ExerciseTeamUserRepository exerciseTeamUserRepository;
  private final LogRepository exerciseLogRepository;
  private final ComcheckRepository comcheckRepository;
  private final InjectRepository injectRepository;
  private final ObjectiveRepository objectiveRepository;
  private final EvaluationRepository evaluationRepository;
  private final KillChainPhaseRepository killChainPhaseRepository;
  private final GrantRepository grantRepository;
  private final CommunicationRepository communicationRepository;
  private final InjectorContractRepository injectorContractRepository;
  // endregion

  // region services
  private final AssetGroupService assetGroupService;
  private final CustomDashboardService customDashboardService;
  private final EndpointService endpointService;
  private final FileService fileService;
  private final InjectService injectService;
  private final ImportService importService;
  private final ExerciseService exerciseService;
  private final TeamService teamService;
  private final ExportService exportService;
  private final ChannelService channelService;
  private final DocumentService documentService;
  private final ScenarioService scenarioService;
  private final UserService userService;
  private final TenantSettingsService tenantSettingsService;
  private final WorkflowService workflowService;
  private final PreviewFeatureService previewFeatureService;
  private final ExpectationsDriftService expectationsDriftService;

  // endregion

  // region healthchecks
  @GetMapping({
    EXERCISE_URI + "/{exerciseId}/healthchecks",
    TENANT_EXERCISE_URI + "/{exerciseId}/healthchecks"
  })
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public List<HealthCheck> streamHealthChecks(
      // The TxCtx parameter is not used directly; it signals the transaction aspect to set
      // the tenant scope in the DB session so the v2 inspector can resolve can_access_tenant.
      TxCtx ctx, @PathVariable @NotBlank final String exerciseId) {
    return exerciseService.runChecks(exerciseId);
  }

  // endregion

  // region expectations drift
  @Operation(
      summary = "Get the expectation drift report of a simulation",
      description =
          "Compares the predefined expectations of the injector contracts with the expectations"
              + " stored inside the simulation injects")
  @GetMapping({
    EXERCISE_URI + "/{exerciseId}/expectations-drift",
    TENANT_EXERCISE_URI + "/{exerciseId}/expectations-drift"
  })
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public ExpectationsDriftOutput exerciseExpectationsDrift(
      @PathVariable @NotBlank final String exerciseId) {
    return expectationsDriftService.exerciseDrift(exerciseId);
  }

  @Operation(
      summary = "Realign the expectations of the simulation injects onto their contracts",
      description =
          "Overwrites the expectations of every drifted inject with the predefined expectations"
              + " currently exposed by its injector contract, as a tracked massive operation")
  // SUPPORTS (not REQUIRED) on purpose: the realignment runs chunk by chunk in the service's own
  // short transactions, wrapped in a massive-operation scope (header progress indicator +
  // per-entity stream event suppression) that must cover each commit-time flush.
  @Transactional(propagation = Propagation.SUPPORTS)
  @PostMapping({
    EXERCISE_URI + "/{exerciseId}/expectations-drift/realign",
    TENANT_EXERCISE_URI + "/{exerciseId}/expectations-drift/realign"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public ExpectationsRealignOutput realignExerciseExpectations(
      @PathVariable @NotBlank final String exerciseId) {
    return expectationsDriftService.realignExercise(exerciseId);
  }

  @Operation(
      summary = "Dismiss or restore the expectation drift warning of a simulation",
      description =
          "Acknowledges that the drifted expectations were customized on purpose: the warning is"
              + " downgraded to a discreet indicator. Persisted in database so the dismissal is"
              + " shared between users, and reset on realignment")
  @PutMapping({
    EXERCISE_URI + "/{exerciseId}/expectations-drift/dismiss",
    TENANT_EXERCISE_URI + "/{exerciseId}/expectations-drift/dismiss"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public ExpectationsDriftOutput dismissExerciseExpectationsDrift(
      @PathVariable @NotBlank final String exerciseId,
      @Valid @RequestBody final ExpectationsDriftDismissInput input) {
    return expectationsDriftService.dismissExerciseDrift(exerciseId, input.dismissed());
  }

  // endregion

  // region logs
  @GetMapping({EXERCISE_URI + "/{exercise}/logs", TENANT_EXERCISE_URI + "/{exercise}/logs"})
  @Transactional
  @AccessControl(
      resourceId = "#exercise",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<Log> logs(@PathVariable String exercise) {
    return exerciseLogRepository.findAll(ExerciseLogSpecification.fromExercise(exercise));
  }

  @PostMapping({EXERCISE_URI + "/{exerciseId}/logs", TENANT_EXERCISE_URI + "/{exerciseId}/logs"})
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Log createLog(@PathVariable String exerciseId, @Valid @RequestBody LogCreateInput input) {
    Exercise exercise = exerciseService.exercise(exerciseId);
    Log log = new Log();
    log.setUpdateAttributes(input);
    log.setExercise(exercise);
    log.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    log.setUser(
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found")));
    return exerciseLogRepository.save(log);
  }

  @PutMapping({
    EXERCISE_URI + "/{exerciseId}/logs/{logId}",
    TENANT_EXERCISE_URI + "/{exerciseId}/logs/{logId}"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Log updateLog(
      @PathVariable String exerciseId,
      @PathVariable String logId,
      @Valid @RequestBody LogCreateInput input) {
    Log log = logRepository.findById(logId).orElseThrow(ElementNotFoundException::new);
    log.setUpdateAttributes(input);
    log.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    return logRepository.save(log);
  }

  @DeleteMapping({
    EXERCISE_URI + "/{exerciseId}/logs/{logId}",
    TENANT_EXERCISE_URI + "/{exerciseId}/logs/{logId}"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public void deleteLog(@PathVariable String exerciseId, @PathVariable String logId) {
    logRepository.deleteById(logId);
  }

  // endregion

  // region comchecks
  @GetMapping({
    EXERCISE_URI + "/{exercise}/comchecks",
    TENANT_EXERCISE_URI + "/{exercise}/comchecks"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exercise",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<Comcheck> comchecks(@PathVariable String exercise) {
    return comcheckRepository.findAll(ComcheckSpecification.fromExercise(exercise));
  }

  @GetMapping({
    EXERCISE_URI + "/{exercise}/comchecks/{comcheck}",
    TENANT_EXERCISE_URI + "/{exercise}/comchecks/{comcheck}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exercise",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Comcheck comcheck(@PathVariable String exercise, @PathVariable String comcheck) {
    Specification<Comcheck> filters =
        ComcheckSpecification.fromExercise(exercise).and(ComcheckSpecification.id(comcheck));
    return comcheckRepository.findOne(filters).orElseThrow(ElementNotFoundException::new);
  }

  @GetMapping({
    EXERCISE_URI + "/{exercise}/comchecks/{comcheck}/statuses",
    TENANT_EXERCISE_URI + "/{exercise}/comchecks/{comcheck}/statuses"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exercise",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public List<ComcheckStatus> comcheckStatuses(
      @PathVariable String exercise, @PathVariable String comcheck) {
    return comcheck(exercise, comcheck).getComcheckStatus();
  }

  // endregion

  // region teams
  @LogExecutionTime
  @Transactional
  @GetMapping({EXERCISE_URI + "/{exerciseId}/teams", TENANT_EXERCISE_URI + "/{exerciseId}/teams"})
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public List<TeamOutput> getExerciseTeams(@PathVariable String exerciseId) {
    return this.teamService.find(fromExercise(exerciseId));
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping({
    EXERCISE_URI + "/{exerciseId}/teams/remove",
    TENANT_EXERCISE_URI + "/{exerciseId}/teams/remove"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Iterable<TeamOutput> removeExerciseTeams(
      @PathVariable String exerciseId, @Valid @RequestBody ExerciseUpdateTeamsInput input) {
    return this.exerciseService.removeTeams(exerciseId, input.getTeamIds());
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping({
    EXERCISE_URI + "/{exerciseId}/teams/replace",
    TENANT_EXERCISE_URI + "/{exerciseId}/teams/replace"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Iterable<TeamOutput> replaceExerciseTeams(
      @PathVariable String exerciseId, @Valid @RequestBody ExerciseUpdateTeamsInput input) {
    return this.exerciseService.replaceTeams(exerciseId, input.getTeamIds());
  }

  @GetMapping({
    EXERCISE_URI + "/{exerciseId}/players",
    TENANT_EXERCISE_URI + "/{exerciseId}/players"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<RawPlayer> getPlayersByExercise(@PathVariable String exerciseId) {
    return userRepository.rawPlayersByExerciseId(exerciseId);
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping({
    EXERCISE_URI + "/{exerciseId}/teams/{teamId}/players/enable",
    TENANT_EXERCISE_URI + "/{exerciseId}/teams/{teamId}/players/enable"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Exercise enableExerciseTeamPlayers(
      @PathVariable String exerciseId,
      @PathVariable String teamId,
      @Valid @RequestBody ExerciseTeamPlayersEnableInput input) {
    Team team =
        teamRepository
            .findByIdAndTenantId(teamId, TenantContext.getCurrentTenant())
            .orElseThrow(ElementNotFoundException::new);
    return exerciseService.enablePlayers(exerciseId, team, input.getPlayersIds());
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping({
    EXERCISE_URI + "/{exerciseId}/teams/{teamId}/players/disable",
    TENANT_EXERCISE_URI + "/{exerciseId}/teams/{teamId}/players/disable"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Exercise disableExerciseTeamPlayers(
      @PathVariable String exerciseId,
      @PathVariable String teamId,
      @Valid @RequestBody ExerciseTeamPlayersEnableInput input) {
    input
        .getPlayersIds()
        .forEach(
            playerId -> {
              ExerciseTeamUserId exerciseTeamUserId = new ExerciseTeamUserId();
              exerciseTeamUserId.setExerciseId(exerciseId);
              exerciseTeamUserId.setTeamId(teamId);
              exerciseTeamUserId.setUserId(playerId);
              exerciseTeamUserRepository.deleteById(exerciseTeamUserId);
            });
    return exerciseService.exercise(exerciseId);
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping({
    EXERCISE_URI + "/{exerciseId}/teams/{teamId}/players/add",
    TENANT_EXERCISE_URI + "/{exerciseId}/teams/{teamId}/players/add"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Exercise addExerciseTeamPlayers(
      @PathVariable String exerciseId,
      @PathVariable String teamId,
      @Valid @RequestBody ExerciseTeamPlayersEnableInput input) {
    Team team =
        teamRepository
            .findByIdAndTenantId(teamId, TenantContext.getCurrentTenant())
            .orElseThrow(ElementNotFoundException::new);
    Iterable<User> teamUsers = userRepository.findAllById(input.getPlayersIds());
    team.getUsers().addAll(fromIterable(teamUsers));
    teamRepository.save(team);
    return exerciseService.enablePlayers(exerciseId, team, input.getPlayersIds());
  }

  @PutMapping({
    EXERCISE_URI + "/{exerciseId}/teams/{teamId}/players/remove",
    TENANT_EXERCISE_URI + "/{exerciseId}/teams/{teamId}/players/remove"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Exercise removeExerciseTeamPlayers(
      @PathVariable String exerciseId,
      @PathVariable String teamId,
      @Valid @RequestBody ExerciseTeamPlayersEnableInput input) {
    Team team =
        teamRepository
            .findByIdAndTenantId(teamId, TenantContext.getCurrentTenant())
            .orElseThrow(ElementNotFoundException::new);
    Iterable<User> teamUsers = userRepository.findAllById(input.getPlayersIds());
    team.getUsers().removeAll(fromIterable(teamUsers));
    teamRepository.save(team);
    input
        .getPlayersIds()
        .forEach(
            playerId -> {
              ExerciseTeamUserId exerciseTeamUserId = new ExerciseTeamUserId();
              exerciseTeamUserId.setExerciseId(exerciseId);
              exerciseTeamUserId.setTeamId(teamId);
              exerciseTeamUserId.setUserId(playerId);
              exerciseTeamUserRepository.deleteById(exerciseTeamUserId);
            });
    return exerciseService.exercise(exerciseId);
  }

  // endregion

  // region exercises
  @PostMapping({EXERCISE_URI, TENANT_EXERCISE_URI})
  @Transactional
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.SIMULATION)
  public Exercise createExercise(@Valid @RequestBody CreateExerciseInput input) {
    if (input == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exercise input cannot be null");
    }
    Exercise exercise = new Exercise();
    exercise.setUpdateAttributes(input);
    exercise.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    if (hasText(input.getCustomDashboard())) {
      exercise.setCustomDashboard(
          this.customDashboardService.customDashboard(input.getCustomDashboard()));
    } else {
      exercise.setCustomDashboard(
          this.tenantSettingsService
              .findSetting(
                  TenantContext.getCurrentTenant(),
                  TenantSettingKeys.TENANT_SIMULATION_DASHBOARD.key())
              .map(Setting::getValue)
              .filter(v -> !v.isEmpty())
              .map(this.customDashboardService::customDashboard)
              .orElse(null));
    }
    Exercise savedExercise = this.exerciseService.createExercise(exercise);

    // If the chaining feature flag is enabled and the engine is "chaining", create and link a
    // workflow to the simulation
    if (previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING)
        && Boolean.TRUE.equals(input.getIsChaining())) {
      workflowService.creationWorkflow(savedExercise);
    }

    return savedExercise;
  }

  @PostMapping({EXERCISE_URI + "/{exerciseId}", TENANT_EXERCISE_URI + "/{exerciseId}"})
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.DUPLICATE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Exercise duplicateExercise(@PathVariable @NotBlank final String exerciseId) {
    return exerciseService.getDuplicateExercise(exerciseId);
  }

  @PutMapping({EXERCISE_URI + "/{exerciseId}", TENANT_EXERCISE_URI + "/{exerciseId}"})
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Exercise updateExerciseInformation(
      @PathVariable String exerciseId, @Valid @RequestBody UpdateExerciseInput input) {
    Exercise exercise = exerciseService.exercise(exerciseId);
    Set<Tag> currentTagList = exercise.getTags();
    exercise.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    exercise.setUpdateAttributes(input);
    if (hasText(input.getCustomDashboard())) {
      exercise.setCustomDashboard(
          this.customDashboardService.customDashboard(input.getCustomDashboard()));
    } else {
      exercise.setCustomDashboard(null);
    }
    return exerciseService.updateExercice(exercise, currentTagList, input.isApplyTagRule());
  }

  @PutMapping({
    EXERCISE_URI + "/{exerciseId}/start_date",
    TENANT_EXERCISE_URI + "/{exerciseId}/start_date"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  @Deprecated(since = "1.16.0")
  public Exercise deprecatedUpdateExerciseStart(
      // ctx is unused directly: the aspect reads it to scope this transaction against the
      // v2-active executors table (throwIfExerciseNotLaunchable's Enterprise gate reads each
      // targeted agent's executor).
      TxCtx ctx,
      @PathVariable String exerciseId,
      @Valid @RequestBody ExerciseUpdateStartDateInput input)
      throws InputValidationException {
    // Calls the shared, non-@Transactional helper directly rather than the sibling endpoint
    // method: an intra-class call to a @Transactional method would bypass the Spring proxy
    // (self-invocation), silently losing this transaction/scope.
    return doUpdateExerciseStart(exerciseId, input);
  }

  @PutMapping({
    EXERCISE_URI + "/{exerciseId}/start-date",
    TENANT_EXERCISE_URI + "/{exerciseId}/start-date"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Exercise updateExerciseStart(
      // ctx is unused directly: the aspect reads it to scope this transaction against the
      // v2-active executors table (throwIfExerciseNotLaunchable's Enterprise gate reads each
      // targeted agent's executor).
      TxCtx ctx,
      @PathVariable String exerciseId,
      @Valid @RequestBody ExerciseUpdateStartDateInput input)
      throws InputValidationException {
    return doUpdateExerciseStart(exerciseId, input);
  }

  private Exercise doUpdateExerciseStart(String exerciseId, ExerciseUpdateStartDateInput input)
      throws InputValidationException {
    Exercise exercise = exerciseService.exercise(exerciseId);
    if (!exercise.getStatus().equals(ExerciseStatus.SCHEDULED)) {
      String message = "Change date is only possible in scheduling state";
      throw new InputValidationException("exercise_start_date", message);
    }
    exerciseService.throwIfExerciseNotLaunchable(exercise);
    exercise.setUpdateAttributes(input);
    return exerciseRepository.save(exercise);
  }

  @PutMapping({EXERCISE_URI + "/{exerciseId}/tags", TENANT_EXERCISE_URI + "/{exerciseId}/tags"})
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Exercise updateExerciseTags(
      @PathVariable String exerciseId, @Valid @RequestBody ExerciseUpdateTagsInput input) {
    Exercise exercise = exerciseService.exercise(exerciseId);
    Set<Tag> currentTagList = exercise.getTags();
    exercise.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    return exerciseService.updateExercice(exercise, currentTagList, input.isApplyTagRule());
  }

  @PutMapping({EXERCISE_URI + "/{exerciseId}/logos", TENANT_EXERCISE_URI + "/{exerciseId}/logos"})
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Exercise updateExerciseLogos(
      @PathVariable String exerciseId, @Valid @RequestBody ExerciseUpdateLogoInput input) {
    Exercise exercise = exerciseService.exercise(exerciseId);
    exercise.setLogoDark(documentRepository.findById(input.getLogoDark()).orElse(null));
    exercise.setLogoLight(documentRepository.findById(input.getLogoLight()).orElse(null));
    return exerciseRepository.save(exercise);
  }

  // -- OPTION --
  @LogExecutionTime
  @Transactional
  @GetMapping({EXERCISE_URI + "/findings/options", TENANT_EXERCISE_URI + "/findings/options"})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public List<FilterUtilsJpa.Option> optionsByNameLinkedToFindings(
      @RequestParam(required = false) final String searchText,
      @RequestParam(required = false) final String scenarioId) {
    return exerciseService.getOptionsByNameLinkedToFindings(
        searchText, scenarioId, PageRequest.of(0, 50));
  }

  @LogExecutionTime
  @PostMapping({EXERCISE_URI + "/options", TENANT_EXERCISE_URI + "/options"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.exerciseRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @PutMapping({
    EXERCISE_URI + "/{exerciseId}/lessons",
    TENANT_EXERCISE_URI + "/{exerciseId}/lessons"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Exercise updateExerciseLessons(
      @PathVariable String exerciseId, @Valid @RequestBody LessonsInput input) {
    Exercise exercise = exerciseService.exercise(exerciseId);
    // Partial update: absent fields keep their current value (older API consumers
    // only send lessons_anonymized and must not reset the enabled flag).
    if (input.getLessonsAnonymized() != null) {
      exercise.setLessonsAnonymized(input.getLessonsAnonymized());
    }
    if (input.getLessonsEnabled() != null) {
      exercise.setLessonsEnabled(input.getLessonsEnabled());
    }
    return exerciseRepository.save(exercise);
  }

  @DeleteMapping({EXERCISE_URI + "/{exerciseId}", TENANT_EXERCISE_URI + "/{exerciseId}"})
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.SIMULATION)
  @Transactional
  public void deleteExercise(@PathVariable String exerciseId) {
    exerciseService.deleteById(exerciseId);
  }

  @Operation(
      description = "Bulk delete of simulations",
      tags = {"Simulations"})
  @LogExecutionTime
  @DeleteMapping({EXERCISE_URI, TENANT_EXERCISE_URI})
  // SUPPORTS (not REQUIRED) on purpose: the service deletes in small independent transactions
  // (chunked, with deadlock retry) - a request-wide transaction would defeat that and deadlock
  // against concurrent inject expectation updates.
  @Transactional(propagation = Propagation.SUPPORTS)
  @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.SIMULATION)
  public List<String> bulkDeleteExercises(
      @RequestBody @Valid final ExerciseBulkProcessingInput input) {
    return exerciseService.bulkDelete(input);
  }

  @GetMapping({EXERCISE_URI + "/{exerciseId}", TENANT_EXERCISE_URI + "/{exerciseId}"})
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(readOnly = true)
  public SimulationDetails exercise(@PathVariable String exerciseId) {
    // We get the raw exercise
    RawSimulationIndexing rawSimulation = exerciseService.rawSimulation(exerciseId);
    // We get aggregated inject metadata: platforms, comms count, kill chain phases
    long communicationsNumber = communicationRepository.countByExerciseId(exerciseId);
    List<KillChainPhase> killChainPhases =
        killChainPhaseRepository.findDistinctByExerciseId(exerciseId);
    List<String> platforms =
        injectorContractRepository.findDistinctPlatformsByExerciseId(exerciseId).stream()
            .filter(Objects::nonNull)
            .flatMap(Arrays::stream)
            .distinct()
            .map(Enum::name)
            .toList();
    // We get the tuple exercise/team/user
    List<RawExerciseTeamUser> listRawExerciseTeamUsers =
        exerciseTeamUserRepository.rawByExerciseIds(List.of(exerciseId));
    // We get the objectives of this exercise
    List<RawObjective> rawObjectives = objectiveRepository.rawByExerciseIds(List.of(exerciseId));
    // We make a map of the Evaluations by objective
    Map<String, List<RawEvaluation>> mapEvaluationsByObjective =
        evaluationRepository
            .rawByObjectiveIds(rawObjectives.stream().map(RawObjective::getObjective_id).toList())
            .stream()
            .collect(Collectors.groupingBy(RawEvaluation::getEvaluation_objective));
    // We make a map of grants of users id by type of grant (Planner, Observer)
    Map<String, List<RawGrant>> rawGrants =
        grantRepository.rawByExerciseIds(List.of(exerciseId)).stream()
            .collect(Collectors.groupingBy(RawGrant::getGrant_name));

    // We create objectives and fill them with evaluations
    List<Objective> objectives =
        rawObjectives.stream()
            .map(
                rawObjective -> {
                  Objective objective = new Objective();
                  if (mapEvaluationsByObjective.get(rawObjective.getObjective_id()) != null) {
                    objective.setEvaluations(
                        mapEvaluationsByObjective.get(rawObjective.getObjective_id()).stream()
                            .map(
                                rawEvaluation -> {
                                  Evaluation evaluation = new Evaluation();
                                  evaluation.setId(rawEvaluation.getEvaluation_id());
                                  evaluation.setScore(rawEvaluation.getEvaluation_score());
                                  return evaluation;
                                })
                            .toList());
                  }
                  return objective;
                })
            .toList();

    List<ExerciseTeamUser> listExerciseTeamUsers =
        listRawExerciseTeamUsers.stream().map(ExerciseTeamUser::fromRawExerciseTeamUser).toList();

    // We create an ExerciseDetails object and populate it
    SimulationDetails detail = fromRawExercise(rawSimulation, listExerciseTeamUsers, objectives);
    detail.setPlatforms(platforms);
    detail.setCommunicationsNumber(communicationsNumber);
    detail.setKillChainPhases(killChainPhases);
    if (rawGrants.get(Grant.GRANT_TYPE.OBSERVER.name()) != null) {
      detail.setObservers(
          rawGrants.get(Grant.GRANT_TYPE.OBSERVER.name()).stream()
              .map(RawGrant::getUser_id)
              .collect(Collectors.toSet()));
    }
    if (rawGrants.get(Grant.GRANT_TYPE.PLANNER.name()) != null) {
      detail.setPlanners(
          rawGrants.get(Grant.GRANT_TYPE.PLANNER.name()).stream()
              .map(RawGrant::getUser_id)
              .collect(Collectors.toSet()));
    }

    return detail;
  }

  @LogExecutionTime
  @Transactional
  @GetMapping({
    EXERCISE_URI + "/{exerciseId}/results",
    TENANT_EXERCISE_URI + "/{exerciseId}/results"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public List<ExpectationResultsByType> globalResults(@NotBlank @PathVariable String exerciseId) {
    // Validate tenant isolation before querying cross-tenant-safe repository method
    exerciseService.existsByIdAndTenantId(exerciseId);
    return exerciseService.getGlobalResults(exerciseId);
  }

  @LogExecutionTime
  @PostMapping({EXERCISE_URI + "/global-scores", TENANT_EXERCISE_URI + "/global-scores"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public ExercisesGlobalScoresOutput getExercisesGlobalScores(
      @Valid @RequestBody ExercisesGlobalScoresInput input) {
    return exerciseService.getExercisesGlobalScores(input);
  }

  @LogExecutionTime
  @Transactional
  @GetMapping({
    EXERCISE_URI + "/{exerciseId}/injects/results-by-attack-patterns",
    TENANT_EXERCISE_URI + "/{exerciseId}/injects/results-by-attack-patterns"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public List<InjectExpectationResultsByAttackPattern> injectResults(
      @NotBlank final @PathVariable String exerciseId) {
    return exerciseService.extractExpectationResultsByAttackPattern(exerciseId);
  }

  @DeleteMapping({
    EXERCISE_URI + "/{exerciseId}/{documentId}",
    TENANT_EXERCISE_URI + "/{exerciseId}/{documentId}"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Exercise deleteDocument(@PathVariable String exerciseId, @PathVariable String documentId) {
    Exercise exercise = exerciseService.exercise(exerciseId);
    exercise.setUpdatedAt(now());
    Document doc =
        documentRepository.findById(documentId).orElseThrow(ElementNotFoundException::new);
    Set<Exercise> docExercises =
        doc.getExercises().stream()
            .filter(ex -> !ex.getId().equals(exerciseId))
            .collect(Collectors.toSet());
    if (docExercises.isEmpty()) {
      // Document is no longer associate to any exercise, delete it
      documentRepository.delete(doc);
      // All associations with this document will be automatically cleanup.
    } else {
      // Document associated to other exercise, cleanup
      doc.setExercises(docExercises);
      documentRepository.save(doc);
      // Delete document from all exercise injects
      injectService.cleanInjectsDocExercise(exerciseId, documentId);
    }
    return exerciseRepository.save(exercise);
  }

  @PutMapping({EXERCISE_URI + "/{exerciseId}/status", TENANT_EXERCISE_URI + "/{exerciseId}/status"})
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.SIMULATION)
  public Exercise changeExerciseStatus(
      @PathVariable String exerciseId, @Valid @RequestBody ExerciseUpdateStatusInput input)
      throws ChainingException {
    ExerciseStatus status = input.getStatus();
    return exerciseService.changeExerciseStatus(status, exerciseId);
  }

  @LogExecutionTime
  @Transactional
  @GetMapping({EXERCISE_URI, TENANT_EXERCISE_URI})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public List<ExerciseSimple> exercises() {
    return exerciseService.exercises();
  }

  @LogExecutionTime
  @PostMapping({EXERCISE_URI + "/search-by-id", TENANT_EXERCISE_URI + "/search-by-id"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  @Operation(
      summary = "Get simulations by their id",
      description = "Get the simulations with the specified ids if you have the right to see them")
  public List<ExerciseSimple> simulationsById(
      @RequestBody final GetExercisesInput getExercisesInput) {
    return exerciseService.exercises(getExercisesInput.getExerciseIds());
  }

  @LogExecutionTime
  @PostMapping({EXERCISE_URI + "/search", TENANT_EXERCISE_URI + "/search"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public Page<ExerciseSimple> exercises(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    Map<String, Join<Base, Base>> joinMap = new HashMap<>();
    User currentUser = userService.currentUser();
    if (currentUser.isAdminOrBypass()
        || currentUser.getCapabilities().contains(Capability.ACCESS_ASSESSMENT)) {
      return buildPaginationCriteriaBuilder(
          (Specification<Exercise> specification,
              Specification<Exercise> specificationCount,
              Pageable pageable) ->
              this.exerciseService.exercises(specification, specificationCount, pageable, joinMap),
          searchPaginationInput,
          Exercise.class,
          joinMap);

    } else {
      return buildPaginationCriteriaBuilder(
          (Specification<Exercise> specification,
              Specification<Exercise> specificationCount,
              Pageable pageable) ->
              this.exerciseService.exercises(
                  findGrantedFor(currentUser().getId()).and(specification),
                  findGrantedFor(currentUser().getId()).and(specificationCount),
                  pageable,
                  joinMap),
          searchPaginationInput,
          Exercise.class,
          joinMap);
    }
  }

  // endregion

  // region communication
  @GetMapping({
    EXERCISE_URI + "/{exerciseId}/communications",
    TENANT_EXERCISE_URI + "/{exerciseId}/communications"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<Communication> exerciseCommunications(@PathVariable String exerciseId) {
    Exercise exercise = exerciseService.exercise(exerciseId);
    List<Communication> communications = new ArrayList<>();
    exercise
        .getInjects()
        .forEach(injectDoc -> communications.addAll(injectDoc.getCommunications()));
    return communications;
  }

  @GetMapping("/api/communications/attachment")
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  //
  public void downloadAttachment(@RequestParam String file, HttpServletResponse response)
      throws IOException {
    FileContainer fileContainer =
        fileService.getFileContainer(file).orElseThrow(ElementNotFoundException::new);
    response.addHeader(
        HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileContainer.getName());
    response.addHeader(HttpHeaders.CONTENT_TYPE, fileContainer.getContentType());
    response.setStatus(HttpServletResponse.SC_OK);
    fileContainer.getInputStream().transferTo(response.getOutputStream());
  }

  // endregion

  // region import/export
  @GetMapping({EXERCISE_URI + "/{exerciseId}/export", TENANT_EXERCISE_URI + "/{exerciseId}/export"})
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public void exerciseExport(
      @NotBlank @PathVariable final String exerciseId,
      @RequestParam(required = false) final boolean isWithTeams,
      @RequestParam(required = false) final boolean isWithPlayers,
      @RequestParam(required = false) final boolean isWithVariableValues,
      @RequestParam(required = false, defaultValue = "true") final boolean isWithScopeDefinition,
      HttpServletResponse response)
      throws IOException {
    Exercise exercise = exerciseService.exercise(exerciseId);
    int exportOptionsMask = ExportOptions.mask(isWithPlayers, isWithTeams, isWithVariableValues);
    boolean isChaining = workflowService.isSimulationChaining(exerciseId);

    byte[] zippedExport =
        exportService.exportExerciseToZip(exercise, exportOptionsMask, isWithScopeDefinition);
    String zipName =
        exportService.getZipFileName(
            exercise, exportOptionsMask, isChaining, isWithScopeDefinition);

    response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + zipName);
    response.addHeader(HttpHeaders.CONTENT_TYPE, "application/zip");
    response.setStatus(HttpServletResponse.SC_OK);
    ServletOutputStream outputStream = response.getOutputStream();
    outputStream.write(zippedExport);
    outputStream.close();
  }

  @PostMapping({EXERCISE_URI + "/import", TENANT_EXERCISE_URI + "/import"})
  @Transactional
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.SIMULATION)
  public void exerciseImport(@RequestPart("file") MultipartFile file) throws Exception {
    importService.handleFileImport(file, null, null);
  }

  @PostMapping({
    EXERCISE_URI + "/{exerciseId}/check-rules",
    TENANT_EXERCISE_URI + "/{exerciseId}/check-rules"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Returns whether or not the rules apply")
      })
  @Operation(
      summary = "Check rules",
      description = "Check if the rules apply to a simulation update")
  public CheckExerciseRulesOutput checkIfRuleApplies(
      @PathVariable @NotBlank final String exerciseId,
      @Valid @RequestBody final CheckExerciseRulesInput input) {
    Exercise exercise = this.exerciseService.exercise(exerciseId);
    return CheckExerciseRulesOutput.builder()
        .rulesFound(this.exerciseService.checkIfTagRulesApplies(exercise, input.getNewTags()))
        .build();
  }

  // endregion

  // region asset groups, endpoints, documents and channels
  @GetMapping({
    EXERCISE_URI + "/{exerciseId}/asset-groups",
    TENANT_EXERCISE_URI + "/{exerciseId}/asset-groups"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(
      summary =
          "Get asset groups. Can only be called if the user has access to the given simulation.",
      description = "Get all asset groups used by injects for a given simulation")
  @Transactional
  public List<AssetGroup> assetGroups(@PathVariable String exerciseId) {
    return this.assetGroupService.assetGroupsForSimulation(exerciseId);
  }

  @PostMapping({
    EXERCISE_URI + "/{exerciseId}/asset-groups/find",
    TENANT_EXERCISE_URI + "/{exerciseId}/asset-groups/find"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(
      summary =
          "Get asset groups by ids. Can only be called if the user has access to the given simulation.",
      description = "Get all asset groups by ids used by injects for a given simulation")
  public List<AssetGroupOutput> assetGroupsByIds(
      @PathVariable String exerciseId,
      @RequestBody @Valid @NotNull final List<String> assetGroupIds) {
    return this.assetGroupService.assetGroupsByIdsForSimulation(exerciseId, assetGroupIds);
  }

  @GetMapping({
    EXERCISE_URI + "/{exerciseId}/channels",
    TENANT_EXERCISE_URI + "/{exerciseId}/channels"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(
      summary = "Get channels. Can only be called if the user has access to the given simulation.",
      description = "Get all channels used by articles for a given simulation")
  @Transactional
  public Iterable<Channel> channels(@PathVariable String exerciseId) {
    return this.channelService.channelsForSimulation(exerciseId);
  }

  @GetMapping({
    EXERCISE_URI + "/{exerciseId}/endpoints",
    TENANT_EXERCISE_URI + "/{exerciseId}/endpoints"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(
      summary = "Get endpoints. Can only be called if the user has access to the given simulation.",
      description = "Get all endpoints used by injects for a given simulation")
  @Transactional
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // executors table (each endpoint's agents eager-load their executor).
  public List<Endpoint> endpoints(TxCtx ctx, @PathVariable String exerciseId) {
    return this.endpointService.endpointsForSimulation(exerciseId);
  }

  @PostMapping({
    EXERCISE_URI + "/{exerciseId}/endpoints/find",
    TENANT_EXERCISE_URI + "/{exerciseId}/endpoints/find"
  })
  @Transactional
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(
      summary =
          "Get endpoints by ids. Can only be called if the user has access to the given simulation.",
      description = "Get all endpoints by ids used by injects for a given simulation")
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // executors table (each endpoint's agents eager-load their executor).
  public List<EndpointOutput> endpointsByIds(
      TxCtx ctx,
      @PathVariable String exerciseId,
      @RequestBody @Valid @NotNull final List<String> endpointIds) {
    return this.endpointService.endpointsByIdsForSimulation(exerciseId, endpointIds);
  }

  @GetMapping({
    EXERCISE_URI + "/{exerciseId}/documents",
    TENANT_EXERCISE_URI + "/{exerciseId}/documents"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(
      summary = "Get documents. Can only be called if the user has access to the given simulation.",
      description = "Get all documents used by injects for a given simulation")
  @Transactional
  public List<Document> documents(@PathVariable String exerciseId) {
    return this.documentService.documentsForSimulation(exerciseId);
  }

  @GetMapping({
    EXERCISE_URI + "/{simulationId}/scenario",
    TENANT_EXERCISE_URI + "/{simulationId}/scenario"
  })
  @AccessControl(
      resourceId = "#simulationId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(summary = "Get the Scenario linked to the simulation")
  @Transactional
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "The Scenario related to the given simulation"),
        @ApiResponse(responseCode = "404", description = "Simulation or Scenario not found")
      })
  public Scenario scenarioFromSimulation(
      @PathVariable @NotBlank @Schema(description = "ID of the simulation")
          final String simulationId) {
    return scenarioService.scenarioFromSimulationId(simulationId);
  }

  // end region
}
