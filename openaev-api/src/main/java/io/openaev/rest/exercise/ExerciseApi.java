package io.openaev.rest.exercise;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.database.specification.ExerciseSpecification.findGrantedFor;
import static io.openaev.database.specification.TeamSpecification.fromExercise;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.rest.exercise.form.SimulationDetails.fromRawExercise;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;
import static java.time.Duration.between;
import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RBAC;
import io.openaev.database.model.*;
import io.openaev.database.raw.*;
import io.openaev.database.repository.*;
import io.openaev.database.specification.*;
import io.openaev.rest.asset.endpoint.form.EndpointOutput;
import io.openaev.rest.asset_group.form.AssetGroupOutput;
import io.openaev.rest.custom_dashboard.CustomDashboardService;
import io.openaev.rest.document.DocumentService;
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
import io.openaev.rest.team.output.TeamOutput;
import io.openaev.service.*;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Join;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class ExerciseApi extends RestBehavior {

  public static final String EXERCISE_URI = "/api/exercises";

  // region repositories
  private final LogRepository logRepository;
  private final TagRepository tagRepository;
  private final UserRepository userRepository;
  private final PauseRepository pauseRepository;
  private final DocumentRepository documentRepository;
  private final ExerciseRepository exerciseRepository;
  private final TeamRepository teamRepository;
  private final ExerciseTeamUserRepository exerciseTeamUserRepository;
  private final LogRepository exerciseLogRepository;
  private final ComcheckRepository comcheckRepository;
  private final LessonsCategoryRepository lessonsCategoryRepository;
  private final LessonsQuestionRepository lessonsQuestionRepository;
  private final LessonsAnswerRepository lessonsAnswerRepository;
  private final InjectStatusRepository injectStatusRepository;
  private final InjectRepository injectRepository;
  private final ObjectiveRepository objectiveRepository;
  private final EvaluationRepository evaluationRepository;
  private final KillChainPhaseRepository killChainPhaseRepository;
  private final GrantRepository grantRepository;
  private final EntityManager entityManager;
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
  private final ActionMetricCollector actionMetricCollector;
  private final ChannelService channelService;
  private final DocumentService documentService;
  private final ScenarioService scenarioService;
  private final UserService userService;
  private final PlatformSettingsService platformSettingsService;

  // endregion

  // region logs
  @GetMapping(EXERCISE_URI + "/{exercise}/logs")
  @RBAC(
      resourceId = "#exercise",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<Log> logs(@PathVariable String exercise) {
    return exerciseLogRepository.findAll(ExerciseLogSpecification.fromExercise(exercise));
  }

  @PostMapping(EXERCISE_URI + "/{exerciseId}/logs")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Log createLog(@PathVariable String exerciseId, @Valid @RequestBody LogCreateInput input) {
    Exercise exercise =
        exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
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

  @PutMapping(EXERCISE_URI + "/{exerciseId}/logs/{logId}")
  @RBAC(
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

  @DeleteMapping(EXERCISE_URI + "/{exerciseId}/logs/{logId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public void deleteLog(@PathVariable String exerciseId, @PathVariable String logId) {
    logRepository.deleteById(logId);
  }

  // endregion

  // region comchecks
  @GetMapping(EXERCISE_URI + "/{exercise}/comchecks")
  @RBAC(
      resourceId = "#exercise",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<Comcheck> comchecks(@PathVariable String exercise) {
    return comcheckRepository.findAll(ComcheckSpecification.fromExercise(exercise));
  }

  @GetMapping(EXERCISE_URI + "/{exercise}/comchecks/{comcheck}")
  @RBAC(
      resourceId = "#exercise",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Comcheck comcheck(@PathVariable String exercise, @PathVariable String comcheck) {
    Specification<Comcheck> filters =
        ComcheckSpecification.fromExercise(exercise).and(ComcheckSpecification.id(comcheck));
    return comcheckRepository.findOne(filters).orElseThrow(ElementNotFoundException::new);
  }

  @GetMapping(EXERCISE_URI + "/{exercise}/comchecks/{comcheck}/statuses")
  @RBAC(
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
  @GetMapping(EXERCISE_URI + "/{exerciseId}/teams")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public List<TeamOutput> getExerciseTeams(@PathVariable String exerciseId) {
    return this.teamService.find(fromExercise(exerciseId));
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping(EXERCISE_URI + "/{exerciseId}/teams/remove")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Iterable<TeamOutput> removeExerciseTeams(
      @PathVariable String exerciseId, @Valid @RequestBody ExerciseUpdateTeamsInput input) {
    return this.exerciseService.removeTeams(exerciseId, input.getTeamIds());
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping(EXERCISE_URI + "/{exerciseId}/teams/replace")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Iterable<TeamOutput> replaceExerciseTeams(
      @PathVariable String exerciseId, @Valid @RequestBody ExerciseUpdateTeamsInput input) {
    return this.exerciseService.replaceTeams(exerciseId, input.getTeamIds());
  }

  @GetMapping(EXERCISE_URI + "/{exerciseId}/players")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<RawPlayer> getPlayersByExercise(@PathVariable String exerciseId) {
    return userRepository.rawPlayersByExerciseId(exerciseId);
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping(EXERCISE_URI + "/{exerciseId}/teams/{teamId}/players/enable")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Exercise enableExerciseTeamPlayers(
      @PathVariable String exerciseId,
      @PathVariable String teamId,
      @Valid @RequestBody ExerciseTeamPlayersEnableInput input) {
    Exercise exercise =
        exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
    Team team = teamRepository.findById(teamId).orElseThrow(ElementNotFoundException::new);
    input
        .getPlayersIds()
        .forEach(
            playerId -> {
              ExerciseTeamUser exerciseTeamUser = new ExerciseTeamUser();
              exerciseTeamUser.setExercise(exercise);
              exerciseTeamUser.setTeam(team);
              exerciseTeamUser.setUser(
                  userRepository.findById(playerId).orElseThrow(ElementNotFoundException::new));
              exerciseTeamUserRepository.save(exerciseTeamUser);
            });
    return exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping(EXERCISE_URI + "/{exerciseId}/teams/{teamId}/players/disable")
  @RBAC(
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
    return exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
  }

  @Transactional(rollbackFor = Exception.class)
  @PutMapping(EXERCISE_URI + "/{exerciseId}/teams/{teamId}/players/add")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Exercise addExerciseTeamPlayers(
      @PathVariable String exerciseId,
      @PathVariable String teamId,
      @Valid @RequestBody ExerciseTeamPlayersEnableInput input) {
    Exercise exercise =
        exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
    Team team = teamRepository.findById(teamId).orElseThrow(ElementNotFoundException::new);
    Iterable<User> teamUsers = userRepository.findAllById(input.getPlayersIds());
    team.getUsers().addAll(fromIterable(teamUsers));
    teamRepository.save(team);
    input
        .getPlayersIds()
        .forEach(
            playerId -> {
              ExerciseTeamUser exerciseTeamUser = new ExerciseTeamUser();
              exerciseTeamUser.setExercise(exercise);
              exerciseTeamUser.setTeam(team);
              exerciseTeamUser.setUser(
                  userRepository.findById(playerId).orElseThrow(ElementNotFoundException::new));
              exerciseTeamUserRepository.save(exerciseTeamUser);
            });
    return exercise;
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}/teams/{teamId}/players/remove")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Exercise removeExerciseTeamPlayers(
      @PathVariable String exerciseId,
      @PathVariable String teamId,
      @Valid @RequestBody ExerciseTeamPlayersEnableInput input) {
    Team team = teamRepository.findById(teamId).orElseThrow(ElementNotFoundException::new);
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
    return exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
  }

  // endregion

  // region exercises
  @PostMapping(EXERCISE_URI)
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.SIMULATION)
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
          this.platformSettingsService
              .setting(SettingKeys.DEFAULT_SIMULATION_DASHBOARD.key())
              .map(Setting::getValue)
              .filter(v -> !v.isEmpty())
              .map(this.customDashboardService::customDashboard)
              .orElse(null));
    }
    return this.exerciseService.createExercise(exercise);
  }

  @PostMapping(EXERCISE_URI + "/{exerciseId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.DUPLICATE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Exercise duplicateExercise(@PathVariable @NotBlank final String exerciseId) {
    return exerciseService.getDuplicateExercise(exerciseId);
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Exercise updateExerciseInformation(
      @PathVariable String exerciseId, @Valid @RequestBody UpdateExerciseInput input) {
    Exercise exercise =
        exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
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

  @PutMapping(EXERCISE_URI + "/{exerciseId}/start_date")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  @Deprecated(since = "1.16.0")
  public Exercise deprecatedUpdateExerciseStart(
      @PathVariable String exerciseId, @Valid @RequestBody ExerciseUpdateStartDateInput input)
      throws InputValidationException {
    return this.updateExerciseStart(exerciseId, input);
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}/start-date")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Exercise updateExerciseStart(
      @PathVariable String exerciseId, @Valid @RequestBody ExerciseUpdateStartDateInput input)
      throws InputValidationException {
    Exercise exercise =
        exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
    if (!exercise.getStatus().equals(ExerciseStatus.SCHEDULED)) {
      String message = "Change date is only possible in scheduling state";
      throw new InputValidationException("exercise_start_date", message);
    }
    exerciseService.throwIfExerciseNotLaunchable(exercise);
    exercise.setUpdateAttributes(input);
    return exerciseRepository.save(exercise);
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}/tags")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Exercise updateExerciseTags(
      @PathVariable String exerciseId, @Valid @RequestBody ExerciseUpdateTagsInput input) {
    Exercise exercise =
        exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
    Set<Tag> currentTagList = exercise.getTags();
    exercise.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    return exerciseService.updateExercice(exercise, currentTagList, input.isApplyTagRule());
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}/logos")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Exercise updateExerciseLogos(
      @PathVariable String exerciseId, @Valid @RequestBody ExerciseUpdateLogoInput input) {
    Exercise exercise =
        exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
    exercise.setLogoDark(documentRepository.findById(input.getLogoDark()).orElse(null));
    exercise.setLogoLight(documentRepository.findById(input.getLogoLight()).orElse(null));
    return exerciseRepository.save(exercise);
  }

  // -- OPTION --
  @LogExecutionTime
  @GetMapping(EXERCISE_URI + "/findings/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public List<FilterUtilsJpa.Option> optionsByNameLinkedToFindings(
      @RequestParam(required = false) final String searchText,
      @RequestParam(required = false) final String scenarioId) {
    return exerciseService.getOptionsByNameLinkedToFindings(
        searchText, scenarioId, PageRequest.of(0, 50));
  }

  @LogExecutionTime
  @PostMapping(EXERCISE_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.exerciseRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}/lessons")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Exercise updateExerciseLessons(
      @PathVariable String exerciseId, @Valid @RequestBody LessonsInput input) {
    Exercise exercise =
        exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
    exercise.setLessonsAnonymized(input.isLessonsAnonymized());
    return exerciseRepository.save(exercise);
  }

  @DeleteMapping(EXERCISE_URI + "/{exerciseId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public void deleteExercise(@PathVariable String exerciseId) {
    exerciseRepository.deleteById(exerciseId);
  }

  @GetMapping(EXERCISE_URI + "/{exerciseId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(readOnly = true)
  public SimulationDetails exercise(@PathVariable String exerciseId) {
    // We get the raw exercise
    RawSimulation rawSimulation = exerciseService.rawSimulation(exerciseId);
    // We get the injects linked to this exercise
    List<RawInject> rawInjects =
        injectRepository.findRawByIds(rawSimulation.getInject_ids().stream().distinct().toList());
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
    // We get all the kill chain phases
    List<KillChainPhase> killChainPhase =
        StreamSupport.stream(
                killChainPhaseRepository
                    .findAllById(
                        rawInjects.stream()
                            .flatMap(rawInject -> rawInject.getInject_kill_chain_phases().stream())
                            .toList())
                    .spliterator(),
                false)
            .collect(Collectors.toList());

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
    detail.setPlatforms(
        rawInjects.stream()
            .flatMap(inject -> inject.getInject_platforms().stream())
            .distinct()
            .toList());
    detail.setCommunicationsNumber(
        rawInjects.stream()
            .mapToLong(rawInject -> rawInject.getInject_communications().size())
            .sum());
    detail.setKillChainPhases(killChainPhase);
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
  @GetMapping(EXERCISE_URI + "/{exerciseId}/results")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public List<ExpectationResultsByType> globalResults(@NotBlank @PathVariable String exerciseId) {
    return exerciseService.getGlobalResults(exerciseId);
  }

  @LogExecutionTime
  @PostMapping(EXERCISE_URI + "/global-scores")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public ExercisesGlobalScoresOutput getExercisesGlobalScores(
      @Valid @RequestBody ExercisesGlobalScoresInput input) {
    return exerciseService.getExercisesGlobalScores(input);
  }

  @LogExecutionTime
  @GetMapping(EXERCISE_URI + "/{exerciseId}/injects/results-by-attack-patterns")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public List<InjectExpectationResultsByAttackPattern> injectResults(
      @NotBlank final @PathVariable String exerciseId) {
    return exerciseService.extractExpectationResultsByAttackPattern(exerciseId);
  }

  @DeleteMapping(EXERCISE_URI + "/{exerciseId}/{documentId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Exercise deleteDocument(@PathVariable String exerciseId, @PathVariable String documentId) {
    Exercise exercise =
        exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
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

  @PutMapping(EXERCISE_URI + "/{exerciseId}/status")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Exercise changeExerciseStatus(
      @PathVariable String exerciseId, @Valid @RequestBody ExerciseUpdateStatusInput input) {
    ExerciseStatus status = input.getStatus();
    Exercise exercise =
        this.exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
    // Check if next status is possible
    List<ExerciseStatus> nextPossibleStatus = exercise.nextPossibleStatus();
    if (!nextPossibleStatus.contains(status)) {
      throw new UnsupportedOperationException(
          "Exercise can't support moving to status " + status.name());
    }
    // In case of rescheduled of an exercise.
    boolean isCloseState =
        ExerciseStatus.CANCELED.equals(exercise.getStatus())
            || ExerciseStatus.FINISHED.equals(exercise.getStatus());
    if (isCloseState && ExerciseStatus.SCHEDULED.equals(status)) {
      exercise.setStart(null);
      exercise.setEnd(null);
      // Reset pauses
      exercise.setCurrentPause(null);
      pauseRepository.deleteAll(pauseRepository.findAllForExercise(exerciseId));
      // Reset injects outcome, communications and expectations
      this.injectStatusRepository.deleteAllById(
          exercise.getInjects().stream()
              .map(Inject::getStatus)
              .map(i -> i.map(InjectStatus::getId).orElse(""))
              .toList());
      exercise.getInjects().forEach(Inject::clean);
      // Reset lessons learned answers
      List<LessonsAnswer> lessonsAnswers =
          lessonsCategoryRepository
              .findAll(LessonsCategorySpecification.fromExercise(exerciseId))
              .stream()
              .flatMap(
                  lessonsCategory ->
                      lessonsQuestionRepository
                          .findAll(
                              LessonsQuestionSpecification.fromCategory(lessonsCategory.getId()))
                          .stream()
                          .flatMap(
                              lessonsQuestion ->
                                  lessonsAnswerRepository
                                      .findAll(
                                          LessonsAnswerSpecification.fromQuestion(
                                              lessonsQuestion.getId()))
                                      .stream()))
              .toList();
      lessonsAnswerRepository.deleteAll(lessonsAnswers);
      entityManager.flush();
      entityManager.clear();
      // Delete exercise transient files (communications, ...)
      fileService.deleteDirectory(exerciseId);
    }
    // In case of manual start
    if (ExerciseStatus.SCHEDULED.equals(exercise.getStatus())
        && ExerciseStatus.RUNNING.equals(status)) {
      exerciseService.throwIfExerciseNotLaunchable(exercise);
      Instant nextMinute = now().truncatedTo(MINUTES).plus(1, MINUTES);
      exercise.setStart(nextMinute);
      actionMetricCollector.addSimulationPlayedCount();
    }
    // If exercise move from pause to running state,
    // we log the pause date to be able to recompute inject dates.
    if (ExerciseStatus.PAUSED.equals(exercise.getStatus())
        && ExerciseStatus.RUNNING.equals(status)) {
      Instant lastPause = exercise.getCurrentPause().orElseThrow(ElementNotFoundException::new);
      exercise.setCurrentPause(null);
      Pause pause = new Pause();
      pause.setDate(lastPause);
      pause.setExercise(exercise);
      pause.setDuration(between(lastPause, now()).getSeconds());
      pauseRepository.save(pause);
    }
    // If pause is asked, just set the pause date.
    if (ExerciseStatus.RUNNING.equals(exercise.getStatus())
        && ExerciseStatus.PAUSED.equals(status)) {
      exercise.setCurrentPause(Instant.now());
    }
    // Cancelation
    if (ExerciseStatus.RUNNING.equals(exercise.getStatus())
        && ExerciseStatus.CANCELED.equals(status)) {
      exercise.setEnd(now());
    }
    exercise.setUpdatedAt(now());
    exercise.setStatus(status);
    return exerciseRepository.save(exercise);
  }

  @LogExecutionTime
  @GetMapping(EXERCISE_URI)
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public List<ExerciseSimple> exercises() {
    return exerciseService.exercises();
  }

  @LogExecutionTime
  @PostMapping(EXERCISE_URI + "/search-by-id")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  @Operation(
      summary = "Get simulations by their id",
      description = "Get the simulations with the specified ids if you have the right to see them")
  public List<ExerciseSimple> simulationsById(
      @RequestBody final GetExercisesInput getExercisesInput) {
    return exerciseService.exercises(getExercisesInput.getExerciseIds());
  }

  @LogExecutionTime
  @PostMapping(EXERCISE_URI + "/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
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
  @GetMapping(EXERCISE_URI + "/{exerciseId}/communications")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<Communication> exerciseCommunications(@PathVariable String exerciseId) {
    Exercise exercise =
        exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
    List<Communication> communications = new ArrayList<>();
    exercise
        .getInjects()
        .forEach(injectDoc -> communications.addAll(injectDoc.getCommunications()));
    return communications;
  }

  @GetMapping("/api/communications/attachment")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
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
  @GetMapping(EXERCISE_URI + "/{exerciseId}/export")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public void exerciseExport(
      @NotBlank @PathVariable final String exerciseId,
      @RequestParam(required = false) final boolean isWithTeams,
      @RequestParam(required = false) final boolean isWithPlayers,
      @RequestParam(required = false) final boolean isWithVariableValues,
      HttpServletResponse response)
      throws IOException {
    Exercise exercise =
        exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
    int exportOptionsMask = ExportOptions.mask(isWithPlayers, isWithTeams, isWithVariableValues);

    byte[] zippedExport = exportService.exportExerciseToZip(exercise, exportOptionsMask);
    String zipName = exportService.getZipFileName(exercise, exportOptionsMask);

    response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + zipName);
    response.addHeader(HttpHeaders.CONTENT_TYPE, "application/zip");
    response.setStatus(HttpServletResponse.SC_OK);
    ServletOutputStream outputStream = response.getOutputStream();
    outputStream.write(zippedExport);
    outputStream.close();
  }

  @PostMapping(EXERCISE_URI + "/import")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.SIMULATION)
  public void exerciseImport(@RequestPart("file") MultipartFile file) throws Exception {
    importService.handleFileImport(file, null, null);
  }

  @PostMapping(EXERCISE_URI + "/{exerciseId}/check-rules")
  @RBAC(
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
  @GetMapping(EXERCISE_URI + "/{exerciseId}/asset-groups")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(
      summary =
          "Get asset groups. Can only be called if the user has access to the given simulation.",
      description = "Get all asset groups used by injects for a given simulation")
  public List<AssetGroup> assetGroups(@PathVariable String exerciseId) {
    return this.assetGroupService.assetGroupsForSimulation(exerciseId);
  }

  @PostMapping(EXERCISE_URI + "/{exerciseId}/asset-groups/find")
  @RBAC(
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

  @GetMapping(EXERCISE_URI + "/{exerciseId}/channels")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(
      summary = "Get channels. Can only be called if the user has access to the given simulation.",
      description = "Get all channels used by articles for a given simulation")
  public Iterable<Channel> channels(@PathVariable String exerciseId) {
    return this.channelService.channelsForSimulation(exerciseId);
  }

  @GetMapping(EXERCISE_URI + "/{exerciseId}/endpoints")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(
      summary = "Get endpoints. Can only be called if the user has access to the given simulation.",
      description = "Get all endpoints used by injects for a given simulation")
  public List<Endpoint> endpoints(@PathVariable String exerciseId) {
    return this.endpointService.endpointsForSimulation(exerciseId);
  }

  @PostMapping(EXERCISE_URI + "/{exerciseId}/endpoints/find")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(
      summary =
          "Get endpoints by ids. Can only be called if the user has access to the given simulation.",
      description = "Get all endpoints by ids used by injects for a given simulation")
  public List<EndpointOutput> endpointsByIds(
      @PathVariable String exerciseId,
      @RequestBody @Valid @NotNull final List<String> endpointIds) {
    return this.endpointService.endpointsByIdsForSimulation(exerciseId, endpointIds);
  }

  @GetMapping(EXERCISE_URI + "/{exerciseId}/documents")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(
      summary = "Get documents. Can only be called if the user has access to the given simulation.",
      description = "Get all documents used by injects for a given simulation")
  public List<Document> documents(@PathVariable String exerciseId) {
    return this.documentService.documentsForSimulation(exerciseId);
  }

  @GetMapping(EXERCISE_URI + "/{simulationId}/scenario")
  @RBAC(
      resourceId = "#simulationId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(summary = "Get the Scenario linked to the simulation")
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
