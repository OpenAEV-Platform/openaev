package io.openaev.rest.atomic_testing;

import static io.openaev.api.expectations.mapper.InjectExpectationMapper.toOutputs;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.expectations.ExpectationsDriftService;
import io.openaev.api.expectations.dto.ExpectationsDriftOutput;
import io.openaev.api.expectations.dto.ExpectationsRealignOutput;
import io.openaev.api.expectations.dto.InjectExpectationOutput;
import io.openaev.database.model.Action;
import io.openaev.database.model.Collector;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.atomic_testing.form.*;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.exception.UnprocessableContentException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.inject.form.InjectBulkProcessingInput;
import io.openaev.service.AtomicTestingService;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.InjectImportService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({AtomicTestingApi.ATOMIC_TESTING_URI, AtomicTestingApi.TENANT_ATOMIC_TESTING_URI})
@RequiredArgsConstructor
public class AtomicTestingApi extends RestBehavior {

  public static final String ATOMIC_TESTING_URI = "/api/atomic-testings";
  public static final String TENANT_ATOMIC_TESTING_URI = TENANT_PREFIX + "/atomic-testings";

  private final AtomicTestingService atomicTestingService;
  private final InjectExpectationService injectExpectationService;
  private final CollectorService collectorsService;
  private final InjectImportService injectImportService;
  private final ExpectationsDriftService expectationsDriftService;

  @LogExecutionTime
  @PostMapping("/search")
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ATOMIC_TESTING)
  @Transactional(readOnly = true)
  public Page<InjectResultOutput> findAllAtomicTestings(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return atomicTestingService.searchAtomicTestingsForCurrentUser(searchPaginationInput);
  }

  // some api use inject as resource type because they are actually used to retrieve inject data for
  // simulation and AT
  @LogExecutionTime
  @Transactional
  @GetMapping("/{injectId}")
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECT)
  public InjectResultOverviewOutput findAtomicTesting(@PathVariable String injectId) {
    return atomicTestingService.findById(injectId);
  }

  @LogExecutionTime
  @Transactional
  @GetMapping("/{injectId}/payload")
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECT)
  public StatusPayloadOutput findAtomicTestingPayload(@PathVariable String injectId) {
    return atomicTestingService.findPayloadOutputByInjectId(injectId);
  }

  @PostMapping()
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.ATOMIC_TESTING)
  @Transactional(rollbackFor = Exception.class)
  public InjectResultOverviewOutput createAtomicTesting(
      @Valid @RequestBody AtomicTestingInput input) {
    return this.atomicTestingService.createOrUpdate(input, null);
  }

  @PutMapping("/{injectId}")
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  @Transactional(rollbackFor = Exception.class)
  public InjectResultOverviewOutput updateAtomicTesting(
      @PathVariable @NotBlank final String injectId,
      @Valid @RequestBody final AtomicTestingInput input) {
    return atomicTestingService.createOrUpdate(input, injectId);
  }

  @DeleteMapping("/{injectId}")
  @Transactional
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.INJECT)
  public void deleteAtomicTesting(@PathVariable @NotBlank final String injectId) {
    atomicTestingService.deleteAtomicTesting(injectId);
  }

  @Operation(
      description = "Bulk delete of atomic testings",
      tags = {"Atomic testings"})
  @LogExecutionTime
  @DeleteMapping()
  // SUPPORTS (not REQUIRED): the service deletes in small independent chunk transactions with
  // deadlock retry; a request-wide transaction would force everything back into one transaction.
  @Transactional(propagation = Propagation.SUPPORTS)
  @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.ATOMIC_TESTING)
  public List<String> bulkDeleteAtomicTestings(
      @RequestBody @Valid final InjectBulkProcessingInput input) {
    return atomicTestingService.bulkDelete(input);
  }

  @PostMapping("/{atomicTestingId}/duplicate")
  @Transactional
  @AccessControl(
      resourceId = "#atomicTestingId",
      actionPerformed = Action.DUPLICATE,
      resourceType = ResourceType.ATOMIC_TESTING)
  public InjectResultOverviewOutput duplicateAtomicTesting(
      @PathVariable @NotBlank final String atomicTestingId) {
    return atomicTestingService.duplicate(atomicTestingId);
  }

  @Operation(
      summary = "Get the expectation drift report of an atomic testing",
      description =
          "Compares the predefined expectations of the injector contract with the expectations"
              + " stored inside the atomic testing")
  @GetMapping("/{injectId}/expectations-drift")
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECT)
  public ExpectationsDriftOutput atomicTestingExpectationsDrift(
      @PathVariable @NotBlank final String injectId) {
    return expectationsDriftService.injectDrift(injectId);
  }

  @Operation(
      summary = "Realign the expectations of an atomic testing onto its contract",
      description =
          "Overwrites the expectations of the atomic testing with the predefined expectations"
              + " currently exposed by its injector contract")
  // SUPPORTS (not REQUIRED) on purpose: the realignment runs in the service's own short
  // transactions, wrapped in a massive-operation scope that must cover the commit-time flush.
  @Transactional(propagation = Propagation.SUPPORTS)
  @PostMapping("/{injectId}/expectations-drift/realign")
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public ExpectationsRealignOutput realignAtomicTestingExpectations(
      @PathVariable @NotBlank final String injectId) {
    return expectationsDriftService.realignInject(injectId);
  }

  @PostMapping("/{atomicTestingId}/launch")
  @Transactional
  @AccessControl(
      resourceId = "#atomicTestingId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.INJECT)
  public InjectResultOverviewOutput launchAtomicTesting(
      @PathVariable @NotBlank final String atomicTestingId) {
    return atomicTestingService.launch(atomicTestingId);
  }

  @PostMapping("/{atomicTestingId}/relaunch")
  @Transactional
  @AccessControl(
      resourceId = "#atomicTestingId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.INJECT)
  public InjectResultOverviewOutput relaunchAtomicTesting(
      @PathVariable @NotBlank final String atomicTestingId) {
    return atomicTestingService.relaunch(atomicTestingId);
  }

  // -- RECURRENCE --

  @PutMapping("/{injectId}/recurrence")
  @Transactional
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.INJECT)
  public InjectResultOverviewOutput updateAtomicTestingRecurrence(
      @PathVariable @NotBlank final String injectId,
      @Valid @RequestBody final InjectRecurrenceInput input) {
    // Scheduling is a Community Edition feature: no Enterprise licence gate here.
    return atomicTestingService.updateRecurrence(injectId, input);
  }

  @GetMapping("/{injectId}/target_results/{targetId}/types/{targetType}")
  @Transactional
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECT)
  public List<InjectExpectationOutput> findTargetResult(
      @PathVariable String injectId,
      @PathVariable String targetId,
      @PathVariable String targetType,
      @RequestParam(required = false) String parentTargetId) {
    return toOutputs(
        injectExpectationService.findMergedExpectationsByInjectAndTargetAndTargetType(
            injectId, targetId, parentTargetId, targetType));
  }

  @GetMapping("/{injectId}/target_results/{targetId}/asset_with_agents")
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECT)
  @Operation(
      summary = "Get the agents injects expectations from an inject, asset and expectation type")
  @Transactional
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "The list of the agents injects expectations")
      })
  public List<InjectExpectationAgentOutput> findTargetResultAssetWithAgents(
      @PathVariable String injectId,
      @PathVariable String targetId,
      @RequestParam @NotBlank String expectationType) {
    return injectExpectationService.findMergedExpectationsWithAgentsByInjectAndAsset(
        injectId, targetId, expectationType);
  }

  /**
   * Returns expectations for inject target with results merged across all expectations of the same
   * type
   *
   * @param injectId ID of the inject owning the targets
   * @param targetId ID of the specific target
   * @param targetType Type of the specified target
   */
  @Operation(
      summary =
          "Fetch target expectations with merged results across all occurrences of each expectation type")
  @Transactional
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Expectation results fetched successfully"),
        @ApiResponse(responseCode = "400", description = "An invalid target type was specified")
      })
  @GetMapping("/{injectId}/target_results/{targetId}/types/{targetType}/merged")
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECT)
  public List<InjectExpectationOutput> findTargetResultMerged(
      @PathVariable String injectId,
      @PathVariable String targetId,
      @PathVariable String targetType) {
    return toOutputs(
        injectExpectationService
            .findMergedExpectationsByInjectAndTargetAndTargetType(injectId, targetId, targetType)
            .stream()
            .sorted(Comparator.comparing(expectation -> expectation.getType().name()))
            .toList());
  }

  @PutMapping("/{injectId}/tags")
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  @Transactional(rollbackFor = Exception.class)
  public InjectResultOverviewOutput updateAtomicTestingTags(
      @PathVariable @NotBlank final String injectId,
      @Valid @RequestBody final AtomicTestingUpdateTagsInput input) {
    return atomicTestingService.updateAtomicTestingTags(injectId, input);
  }

  @GetMapping("/{injectId}/collectors")
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECT)
  @Operation(summary = "Get the Collectors used in an atomic testing remediation")
  @Transactional
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "The list of Collectors used in an atomic testing remediation")
      })
  public List<Collector> collectorsFromAtomicTesting(@PathVariable String injectId) {
    return collectorsService.collectorsForAtomicTesting(injectId);
  }

  @PostMapping(
      path = "/import",
      consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  @Transactional
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.ATOMIC_TESTING)
  public void atomicTestingImport(
      @RequestPart("file") MultipartFile file, HttpServletResponse response) throws Exception {
    if (file == null || file.isEmpty()) {
      throw new UnprocessableContentException("Insufficient input: file is required");
    }

    this.injectImportService.importInjectsForAtomicTestings(file);
  }
}
