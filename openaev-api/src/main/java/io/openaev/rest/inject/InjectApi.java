package io.openaev.rest.inject;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.helper.StreamHelper.fromIterable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RBAC;
import io.openaev.aop.lock.Lock;
import io.openaev.aop.lock.LockResourceType;
import io.openaev.config.OpenAEVConfig;
import io.openaev.config.RabbitmqConfig;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawDocument;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.database.specification.InjectSpecification;
import io.openaev.database.specification.SpecificationUtils;
import io.openaev.rest.atomic_testing.form.ExecutionTraceOutput;
import io.openaev.rest.atomic_testing.form.InjectStatusOutput;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.exports.ExportOptions;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.helper.queue.BatchQueueService;
import io.openaev.rest.helper.queue.executor.BatchExecutionTraceExecutor;
import io.openaev.rest.inject.form.*;
import io.openaev.rest.inject.service.ExecutableInjectService;
import io.openaev.rest.inject.service.InjectExecutionService;
import io.openaev.rest.inject.service.InjectExportService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.payload.form.DetectionRemediationOutput;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.UserService;
import io.openaev.service.targets.TargetService;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.TargetType;
import io.openaev.utils.mapper.PayloadMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@Setter
public class InjectApi extends RestBehavior {

  public static final String INJECT_URI = "/api/injects";

  private static final int MAX_NEXT_INJECTS = 6;

  private final ExecutableInjectService executableInjectService;
  private final ExerciseRepository exerciseRepository;
  private final InjectRepository injectRepository;
  private final InjectService injectService;
  private final InjectExecutionService injectExecutionService;
  private final InjectExportService injectExportService;
  private final TargetService targetService;
  private final UserRepository userRepository;
  private final PayloadMapper payloadMapper;
  private final UserService userService;
  private final DocumentService documentService;
  private final BatchExecutionTraceExecutor batchExecutionTraceExecutor;

  private final RabbitmqConfig rabbitmqConfig;
  private final OpenAEVConfig openAEVConfig;
  private final ObjectMapper objectMapper;

  private final PreviewFeatureService previewFeatureService;

  // For testing purpose, we add a setter
  @Setter private BatchQueueService<InjectExecutionCallback> injectTraceQueueService;

  @PostConstruct
  public void init() throws IOException, TimeoutException {
    if (openAEVConfig.getQueueConfig().get("inject-trace") != null) {
      // Initializing the queue for batching the inject execution trace
      injectTraceQueueService =
          new BatchQueueService<>(
              InjectExecutionCallback.class,
              batchExecutionTraceExecutor::handleInjectExecutionCallbackList,
              rabbitmqConfig,
              objectMapper,
              openAEVConfig.getQueueConfig().get("inject-trace"));
    }
  }

  // -- INJECTS --

  @GetMapping(INJECT_URI + "/{injectId}")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public Inject inject(@PathVariable @NotBlank final String injectId) {
    return this.injectRepository.findById(injectId).orElseThrow(ElementNotFoundException::new);
  }

  @LogExecutionTime
  @PostMapping(INJECT_URI + "/search/export")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECT)
  public void injectsExportFromSearch(
      @RequestBody @Valid InjectExportFromSearchRequestInput input, HttpServletResponse response)
      throws IOException {

    // Control and format inputs
    List<Inject> injects =
        getInjectsAndCheckInputForBulkProcessing(input, Grant.GRANT_TYPE.OBSERVER);

    if (injects.isEmpty()) {
      throw new ElementNotFoundException("No injects to export");
    }

    runInjectExport(
        injects,
        ExportOptions.mask(
            input.getExportOptions().isWithPlayers(),
            input.getExportOptions().isWithTeams(),
            input.getExportOptions().isWithVariableValues()),
        response);
  }

  @PostMapping(INJECT_URI + "/export")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECT)
  public void injectsExport(
      @RequestBody @Valid final InjectExportRequestInput injectExportRequestInput,
      HttpServletResponse response)
      throws IOException {
    List<String> targetIds = injectExportRequestInput.getTargetsIds();
    User currentUser = userService.currentUser();
    List<Inject> injects =
        injectRepository.findAll(
            Specification.where(SpecificationUtils.<Inject>hasIdIn(targetIds))
                .and(
                    SpecificationUtils.hasGrantAccess(
                        currentUser.getId(),
                        currentUser.isAdminOrBypass(),
                        currentUser.getCapabilities().contains(Capability.ACCESS_ASSESSMENT),
                        Grant.GRANT_TYPE.OBSERVER)));
    List<String> foundIds = injects.stream().map(Inject::getId).toList();
    List<String> missedIds =
        new ArrayList<>(targetIds.stream().filter(id -> !foundIds.contains(id)).toList());

    if (!missedIds.isEmpty()) {
      throw new ElementNotFoundException(String.join(", ", missedIds));
    }

    int exportOptionsMask =
        ExportOptions.mask(
            injectExportRequestInput.getExportOptions().isWithPlayers(),
            injectExportRequestInput.getExportOptions().isWithTeams(),
            injectExportRequestInput.getExportOptions().isWithVariableValues());
    runInjectExport(injects, exportOptionsMask, response);
  }

  @Operation(summary = "Export an inject")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Inject exported successfully"),
        @ApiResponse(responseCode = "404", description = "The inject was not found")
      })
  @PostMapping(INJECT_URI + "/{injectId}/inject_export")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public void injectsIndividualExport(
      @PathVariable @NotBlank final String injectId,
      @RequestBody @Valid
          final InjectIndividualExportRequestInput injectIndividualExportRequestInput,
      HttpServletResponse response)
      throws IOException {

    Inject inject = injectRepository.findById(injectId).orElseThrow(ElementNotFoundException::new);
    int exportOptionsMask =
        ExportOptions.mask(
            injectIndividualExportRequestInput.getExportOptions().isWithPlayers(),
            injectIndividualExportRequestInput.getExportOptions().isWithTeams(),
            injectIndividualExportRequestInput.getExportOptions().isWithVariableValues());
    runInjectExport(List.of(inject), exportOptionsMask, response);
  }

  private void runInjectExport(
      List<Inject> injects, int exportOptionsMask, HttpServletResponse response)
      throws IOException {
    byte[] zippedExport = injectExportService.exportInjectsToZip(injects, exportOptionsMask);
    String zipName = injectExportService.getZipFileName(exportOptionsMask);

    response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + zipName);
    response.addHeader(HttpHeaders.CONTENT_TYPE, "application/zip");
    response.setStatus(HttpServletResponse.SC_OK);
    ServletOutputStream outputStream = response.getOutputStream();
    outputStream.write(zippedExport);
    outputStream.close();
  }

  /**
   * Returns a page of inject target results based on search parameters
   *
   * @param injectId ID of the inject owning the targets
   * @param targetType Type of the searched targets
   * @param input Search terms specification
   */
  @Operation(summary = "Search inject targets by inject and by target type")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "A page of inject target results is fetched successfully"),
        @ApiResponse(responseCode = "404", description = "The inject ID was not found"),
        @ApiResponse(responseCode = "400", description = "An invalid target type was specified")
      })
  @LogExecutionTime
  @PostMapping(path = INJECT_URI + "/{injectId}/targets/{targetType}/search")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public Page<InjectTarget> injectTargetSearch(
      @PathVariable String injectId,
      @PathVariable String targetType,
      @Valid @RequestBody SearchPaginationInput input) {
    TargetType injectTargetTypeEnum;

    try {
      injectTargetTypeEnum = TargetType.valueOf(targetType);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(String.format("Invalid target type %s", targetType));
    }

    Inject inject = injectService.inject(injectId);

    return targetService.searchTargets(injectTargetTypeEnum, inject, input);
  }

  /**
   * Returns all possible filter value options for the given target type and inject
   *
   * @param injectId ID of the inject owning the potential options
   * @param targetType Type of the desired targets as options
   * @param searchText Additional filter on target label
   */
  @Operation(summary = "Get filter values options from possible targets by target type and inject")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Target as option values fetched successfully"),
        @ApiResponse(responseCode = "404", description = "The inject ID was not found"),
        @ApiResponse(responseCode = "400", description = "An invalid target type was specified")
      })
  @LogExecutionTime
  @GetMapping(path = INJECT_URI + "/{injectId}/targets/{targetType}/options")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public List<FilterUtilsJpa.Option> targetOptions(
      @PathVariable String injectId,
      @PathVariable String targetType,
      @RequestParam(required = false) final String searchText) {
    TargetType injectTargetTypeEnum;

    try {
      injectTargetTypeEnum = TargetType.valueOf(targetType);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(String.format("Invalid target type %s", targetType));
    }

    Inject inject = injectService.inject(injectId);

    return targetService.getTargetOptions(
        injectTargetTypeEnum, inject, StringUtils.trimToEmpty(searchText));
  }

  /**
   * Returns possible filter value options for the given target ids
   *
   * @param targetType Type of the desired targets as options
   * @param ids IDs of the target options to fetch
   */
  @Operation(summary = "Get filter values options from possible targets by IDs")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Target as option values fetched successfully"),
        @ApiResponse(responseCode = "400", description = "An invalid target type was specified")
      })
  @LogExecutionTime
  @PostMapping(path = INJECT_URI + "/targets/{targetType}/options")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public List<FilterUtilsJpa.Option> targetOptionsById(
      @PathVariable String targetType, @RequestBody final List<String> ids) {
    TargetType injectTargetTypeEnum;

    try {
      injectTargetTypeEnum = TargetType.valueOf(targetType);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(String.format("Invalid target type %s", targetType));
    }
    return targetService.getTargetOptionsByIds(injectTargetTypeEnum, ids);
  }

  @PostMapping(INJECT_URI + "/execution/reception/{injectId}")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public Inject injectExecutionReception(
      @PathVariable String injectId, @Valid @RequestBody InjectReceptionInput input) {
    Inject inject = injectRepository.findById(injectId).orElseThrow(ElementNotFoundException::new);
    InjectStatus injectStatus = inject.getStatus().orElseThrow(ElementNotFoundException::new);
    injectStatus.setName(ExecutionStatus.PENDING);
    return injectRepository.save(inject);
  }

  @PostMapping(INJECT_URI + "/execution/callback/{injectId}")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public void injectExecutionCallback(
      @PathVariable String injectId, @Valid @RequestBody InjectExecutionInput input)
      throws IOException {
    injectExecutionCallback(null, injectId, input);
  }

  @PostMapping(INJECT_URI + "/execution/{agentId}/callback/{injectId}")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  @Lock(type = LockResourceType.INJECT, key = "#injectId")
  @Operation(
      summary = "Inject execution callback for implants",
      description =
          "This endpoint is invoked by implants to report the result of an inject execution. "
              + "It is used to update the inject status and execution traces based on the implant's execution result."
              + " If the requested action is 'complete', the inject must be in the 'PENDING' state. otherwise a 409"
              + " is returned. This can sometimes happen if the payload executed by the implant was executed too quickly. ")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Execution callback was successful"),
        @ApiResponse(
            responseCode = "409",
            description =
                "The inject to update was not in a valid state in regards to the requested action. Retry in a few seconds."),
      })
  public void injectExecutionCallback(
      @PathVariable
          String agentId, // must allow null because http injector used also this method to work.
      @PathVariable String injectId,
      @Valid @RequestBody InjectExecutionInput input)
      throws IOException {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.LEGACY_INGESTION_EXECUTION_TRACE)
        && injectTraceQueueService != null) {
      InjectExecutionCallback injectExecutionCallback =
          InjectExecutionCallback.builder()
              .injectExecutionInput(input)
              .agentId(agentId)
              .injectId(injectId)
              .emissionDate(Instant.now().toEpochMilli())
              .build();

      // Publishing the parameters into a queue for later ingestion
      injectTraceQueueService.publish(injectExecutionCallback);
    } else {
      injectExecutionService.handleInjectExecutionCallback(injectId, agentId, input);
    }
  }

  @GetMapping(INJECT_URI + "/{injectId}/{agentId}/executable-payload")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  @Operation(
      summary = "Get the payload ready to be executed",
      description =
          "This endpoint is invoked by implants to retrieve a payload command that's pre-configured and ready for execution.")
  public Payload getExecutablePayloadInject(
      @PathVariable @NotBlank final String injectId, @PathVariable @NotBlank final String agentId)
      throws Exception {
    return executableInjectService.getExecutablePayloadAndUpdateInjectStatus(injectId, agentId);
  }

  // -- EXERCISES --

  @Transactional(rollbackFor = Exception.class)
  @PutMapping(INJECT_URI + "/{exerciseId}/{injectId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public Inject updateInject(
      @PathVariable String exerciseId,
      @PathVariable String injectId,
      @Valid @RequestBody InjectInput input) {
    Exercise exercise =
        exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
    Inject inject = injectService.updateInject(injectId, input);

    // It should not be possible to add a EE executor on inject when the exercise is already
    // started.
    if (exercise.getStart().isPresent()) {
      this.injectService.throwIfInjectNotLaunchable(inject);
    }

    // If Documents not yet linked directly to the exercise, attached it
    inject
        .getDocuments()
        .forEach(
            document -> {
              if (!document.getDocument().getExercises().contains(exercise)) {
                exercise.getDocuments().add(document.getDocument());
              }
            });
    this.exerciseRepository.save(exercise);
    return injectRepository.save(inject);
  }

  @GetMapping(INJECT_URI + "/next")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECT)
  public List<Inject> nextInjectsToExecute(@RequestParam Optional<Integer> size) {
    return injectRepository.findAll(InjectSpecification.next()).stream()
        // Keep only injects visible by the user
        .filter(inject -> inject.getDate().isPresent())
        .filter(
            inject ->
                inject
                    .getExercise()
                    .isUserHasAccess(
                        userRepository
                            .findById(currentUser().getId())
                            .orElseThrow(
                                () -> new ElementNotFoundException("Current user not found"))))
        // Order by near execution
        .sorted(Inject.executionComparator)
        // Keep only the expected size
        .limit(size.orElse(MAX_NEXT_INJECTS))
        // Collect the result
        .toList();
  }

  @Operation(
      description = "Bulk update of injects",
      tags = {"Injects"})
  @Transactional(rollbackFor = Exception.class)
  @PutMapping(INJECT_URI)
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.INJECT)
  @LogExecutionTime
  public List<Inject> bulkUpdateInject(@RequestBody @Valid final InjectBulkUpdateInputs input) {

    // Control and format inputs
    List<Inject> injectsToUpdate =
        getInjectsAndCheckInputForBulkProcessing(input, Grant.GRANT_TYPE.PLANNER);

    // Bulk update
    return this.injectService.bulkUpdateInject(injectsToUpdate, input.getUpdateOperations());
  }

  @Operation(
      description = "Bulk delete of injects",
      tags = {"injects-api"})
  @Transactional(rollbackFor = Exception.class)
  @DeleteMapping(INJECT_URI)
  @RBAC(actionPerformed = Action.DELETE, resourceType = ResourceType.INJECT)
  @LogExecutionTime
  public List<Inject> bulkDelete(@RequestBody @Valid final InjectBulkProcessingInput input) {

    // Control and format inputs
    List<Inject> injectsToDelete =
        getInjectsAndCheckInputForBulkProcessing(input, Grant.GRANT_TYPE.PLANNER);

    // FIXME: This is a workaround to prevent the GUI from blocking when deleting elements
    injectsToDelete.forEach(inject -> inject.setListened(false));

    // Bulk delete
    this.injectService.deleteAll(injectsToDelete);
    return injectsToDelete;
  }

  // -- OPTION --

  @GetMapping(INJECT_URI + "/findings/options")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public List<FilterUtilsJpa.Option> optionsByTitleLinkedToFindings(
      @RequestParam(required = false) final String searchText,
      @RequestParam(required = false) final String sourceId) {
    return injectService.getOptionsByNameLinkedToFindings(
        searchText, sourceId, PageRequest.of(0, 50));
  }

  @PostMapping(INJECT_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECT)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.injectRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getTitle()))
        .toList();
  }

  /**
   * Retrieve injects that match the search input and check that the user is allowed to bulk process
   * them
   *
   * @param input The input for the bulk processing
   * @return The list of injects to process
   * @throws BadRequestException If the input is not correctly formatted
   */
  private List<Inject> getInjectsAndCheckInputForBulkProcessing(
      InjectBulkProcessingInput input, Grant.GRANT_TYPE requested_grant_level) {
    // Control and format inputs
    if ((CollectionUtils.isEmpty(input.getInjectIDsToProcess())
            && (input.getSearchPaginationInput() == null))
        || (!CollectionUtils.isEmpty(input.getInjectIDsToProcess())
            && (input.getSearchPaginationInput() != null))) {
      throw new BadRequestException(
          "Either inject_ids_to_process or search_pagination_input must be provided, and not both at the same time");
    }

    // Retrieve injects that match the search input and check that the user is allowed to bulk
    // process them
    return this.injectService.getInjectsAndCheckPermission(input, requested_grant_level);
  }

  // -- Execution Traces
  @Operation(
      description =
          "Get ExecutionTraces from a specific inject and target (asset, agent, team, player)")
  @GetMapping(INJECT_URI + "/execution-traces")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  @LogExecutionTime
  public List<ExecutionTraceOutput> getInjectTracesFromInjectAndTarget(
      @RequestParam String injectId,
      @RequestParam String targetId,
      @RequestParam TargetType targetType) {
    return this.injectService.getInjectTracesOutputFromInjectAndTarget(
        injectId, targetId, targetType);
  }

  @Operation(description = "Get InjectStatus with global execution traces")
  @GetMapping(INJECT_URI + "/status")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  @LogExecutionTime
  public InjectStatusOutput getInjectStatusWithGlobalExecutionTraces(
      @RequestParam String injectId) {
    return this.injectService.getInjectStatusWithGlobalExecutionTraces(injectId);
  }

  @Operation(description = "Get detection remediation by inject based on the payload definition")
  @GetMapping(INJECT_URI + "/detection-remediations/{injectId}")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public List<DetectionRemediationOutput> getPayloadDetectionRemediations(
      @PathVariable String injectId) {
    return payloadMapper.toDetectionRemediationOutputs(
        injectService.fetchDetectionRemediationsByInjectId(injectId));
  }

  @Operation(description = "Get documents by inject and payload id")
  @GetMapping(INJECT_URI + "/{injectId}/payload/{payloadId}/documents")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public List<RawDocument> getPayloadDocumentsByInjectIdAndPayloadId(
      @PathVariable String injectId, @PathVariable String payloadId) {
    Payload payload = injectService.getPayloadByInjectId(injectId);

    if (!payloadId.equals(payload.getId())) {
      throw new BadRequestException("provided payload id mismatch with provided inject id");
    }

    return documentService.documentsForPayload(payloadId);
  }

  @VisibleForTesting
  public BatchQueueService<InjectExecutionCallback> getInjectTraceQueueService() {
    return injectTraceQueueService;
  }
}
