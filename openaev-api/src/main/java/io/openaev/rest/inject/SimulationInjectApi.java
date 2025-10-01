package io.openaev.rest.inject;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.database.specification.CommunicationSpecification.fromInject;
import static io.openaev.database.specification.InjectSpecification.fromSimulation;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;
import static java.time.Instant.now;

import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RBAC;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutionContext;
import io.openaev.execution.ExecutionContextService;
import io.openaev.executors.Executor;
import io.openaev.rest.atomic_testing.form.InjectResultOutput;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.inject.form.*;
import io.openaev.rest.inject.output.InjectOutput;
import io.openaev.rest.inject.service.InjectDuplicateService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.rest.inject.service.SimulationInjectService;
import io.openaev.service.InjectSearchService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.persistence.criteria.Join;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SimulationInjectApi extends RestBehavior {

  private final InjectSearchService injectSearchService;
  private final Executor executor;
  private final InjectorContractRepository injectorContractRepository;
  private final CommunicationRepository communicationRepository;
  private final ExerciseRepository exerciseRepository;
  private final UserRepository userRepository;
  private final InjectRepository injectRepository;
  private final TeamRepository teamRepository;
  private final ExecutionContextService executionContextService;
  private final InjectService injectService;
  private final InjectDuplicateService injectDuplicateService;
  private final InjectStatusService injectStatusService;
  private final SimulationInjectService simulationInjectService;

  @Operation(summary = "Retrieved injects for an exercise")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Retrieved injects for an exercise",
            content = {
              @Content(
                  mediaType = "application/json",
                  schema = @Schema(implementation = InjectOutput.class))
            }),
      })
  @GetMapping(EXERCISE_URI + "/{exerciseId}/injects/simple")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(readOnly = true)
  public Iterable<InjectOutput> exerciseInjectsSimple(
      @PathVariable @NotBlank final String exerciseId) {
    return injectSearchService.injects(fromSimulation(exerciseId));
  }

  @PostMapping(EXERCISE_URI + "/{exerciseId}/injects/simple")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(readOnly = true)
  public Iterable<InjectOutput> exerciseInjectsSimple(
      @PathVariable @NotBlank final String exerciseId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    Map<String, Join<Base, Base>> joinMap = new HashMap<>();
    return buildPaginationCriteriaBuilder(
        (Specification<Inject> specification,
            Specification<Inject> specificationCount,
            Pageable pageable) ->
            this.injectSearchService.injects(
                fromSimulation(exerciseId).and(specification),
                fromSimulation(exerciseId).and(specificationCount),
                pageable,
                joinMap),
        searchPaginationInput,
        Inject.class,
        joinMap);
  }

  @LogExecutionTime
  @GetMapping(EXERCISE_URI + "/{exerciseId}/injects")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<Inject> exerciseInjects(@PathVariable @NotBlank final String exerciseId) {
    return injectRepository.findByExerciseId(exerciseId).stream()
        .sorted(Inject.executionComparator)
        .toList();
  }

  @LogExecutionTime
  @PostMapping(EXERCISE_URI + "/{exerciseId}/injects/search")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(readOnly = true)
  public Page<InjectResultOutput> searchExerciseInjects(
      @PathVariable final String exerciseId,
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return injectSearchService.getPageOfInjectResults(exerciseId, searchPaginationInput);
  }

  @LogExecutionTime
  @GetMapping(EXERCISE_URI + "/{exerciseId}/injects/results")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Transactional(readOnly = true)
  public List<InjectResultOutput> exerciseInjectsResults(@PathVariable final String exerciseId) {
    return injectSearchService.getListOfInjectResults(exerciseId);
  }

  @GetMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Inject exerciseInject(@PathVariable String exerciseId, @PathVariable String injectId) {
    return injectRepository.findById(injectId).orElseThrow(ElementNotFoundException::new);
  }

  @GetMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}/teams")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<Team> exerciseInjectTeams(
      @PathVariable String exerciseId, @PathVariable String injectId) {
    return injectRepository
        .findById(injectId)
        .orElseThrow(ElementNotFoundException::new)
        .getTeams();
  }

  @GetMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}/communications")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Iterable<Communication> exerciseInjectCommunications(
      @PathVariable String exerciseId, @PathVariable String injectId) {
    List<Communication> coms =
        communicationRepository.findAll(
            fromInject(injectId), Sort.by(Sort.Direction.DESC, "receivedAt"));
    List<Communication> ackComs = coms.stream().peek(com -> com.setAck(true)).toList();
    return communicationRepository.saveAll(ackComs);
  }

  @PostMapping(EXERCISE_URI + "/{exerciseId}/injects")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackFor = Exception.class)
  public Inject createInjectForExercise(
      @PathVariable String exerciseId, @Valid @RequestBody InjectInput input) {
    Exercise exercise =
        exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
    return this.injectService.createInject(exercise, null, input);
  }

  @PostMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Inject duplicateInjectForExercise(
      @PathVariable @NotBlank final String exerciseId,
      @PathVariable @NotBlank final String injectId) {
    return injectDuplicateService.duplicateInjectForExerciseWithDuplicateWordInTitle(
        exerciseId, injectId);
  }

  @Transactional(rollbackFor = Exception.class)
  @PostMapping(value = EXERCISE_URI + "/{exerciseId}/inject")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.SIMULATION)
  public InjectStatus executeInject(
      @PathVariable @NotBlank final String exerciseId,
      @Valid @RequestPart("input") DirectInjectInput input,
      @RequestPart("file") Optional<MultipartFile> file) {
    Inject inject =
        input.toInject(
            this.injectorContractRepository
                .findById(input.getInjectorContract())
                .orElseThrow(() -> new ElementNotFoundException("Injector contract not found")));
    inject.setUser(
        this.userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found")));
    inject.setExercise(
        this.exerciseRepository
            .findById(exerciseId)
            .orElseThrow(() -> new ElementNotFoundException("Exercise not found")));
    inject.setDependsDuration(0L);
    Inject savedInject = this.injectRepository.save(inject);
    Iterable<User> users = this.userRepository.findAllById(input.getUserIds());
    List<ExecutionContext> userInjectContexts =
        fromIterable(users).stream()
            .map(
                user ->
                    this.executionContextService.executionContext(
                        user, savedInject, "Direct execution"))
            .collect(Collectors.toList());
    ExecutableInject injection =
        new ExecutableInject(
            true,
            true,
            savedInject,
            List.of(),
            savedInject.getAssets(),
            savedInject.getAssetGroups(),
            userInjectContexts);
    file.ifPresent(injection::addDirectAttachment);
    try {
      return executor.directExecute(injection);
    } catch (Exception e) {
      log.warn(e.getMessage(), e);
      return injectStatusService.failInjectStatus(inject.getId(), e.getMessage());
    }
  }

  @Transactional(rollbackFor = Exception.class)
  @DeleteMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public void deleteInject(@PathVariable String exerciseId, @PathVariable String injectId) {
    this.simulationInjectService.deleteInject(exerciseId, injectId);
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}/activation")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Inject updateInjectActivationForExercise(
      @PathVariable String exerciseId,
      @PathVariable String injectId,
      @Valid @RequestBody InjectUpdateActivationInput input) {
    return injectService.updateInjectActivation(injectId, input);
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}/trigger")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Inject updateInjectTrigger(
      @PathVariable String exerciseId, @PathVariable String injectId) {
    Inject inject = injectRepository.findById(injectId).orElseThrow(ElementNotFoundException::new);
    inject.setTriggerNowDate(now());
    inject.setUpdatedAt(now());
    return injectRepository.save(inject);
  }

  @Transactional(rollbackFor = Exception.class)
  @PostMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}/status")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Inject setInjectStatus(
      @PathVariable String exerciseId,
      @PathVariable String injectId,
      @Valid @RequestBody InjectUpdateStatusInput input) {
    return injectStatusService.updateInjectStatus(injectId, input);
  }

  @PutMapping(EXERCISE_URI + "/{exerciseId}/injects/{injectId}/teams")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Inject updateInjectTeams(
      @PathVariable String exerciseId,
      @PathVariable String injectId,
      @Valid @RequestBody InjectTeamsInput input) {
    Inject inject = injectRepository.findById(injectId).orElseThrow(ElementNotFoundException::new);
    Iterable<Team> injectTeams = teamRepository.findAllById(input.getTeamIds());
    inject.setTeams(fromIterable(injectTeams));
    return injectRepository.save(inject);
  }
}
