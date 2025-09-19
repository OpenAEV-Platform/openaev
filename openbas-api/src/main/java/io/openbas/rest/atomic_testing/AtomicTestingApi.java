package io.openbas.rest.atomic_testing;

import io.openbas.aop.LogExecutionTime;
import io.openbas.aop.RBAC;
import io.openbas.database.model.Action;
import io.openbas.database.model.Collector;
import io.openbas.database.model.InjectExpectation;
import io.openbas.database.model.ResourceType;
import io.openbas.rest.atomic_testing.form.*;
import io.openbas.rest.collector.service.CollectorService;
import io.openbas.rest.exception.UnprocessableContentException;
import io.openbas.rest.helper.RestBehavior;
import io.openbas.service.AtomicTestingService;
import io.openbas.service.InjectExpectationService;
import io.openbas.service.InjectImportService;
import io.openbas.utils.pagination.SearchPaginationInput;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(AtomicTestingApi.ATOMIC_TESTING_URI)
@RequiredArgsConstructor
public class AtomicTestingApi extends RestBehavior {

  public static final String ATOMIC_TESTING_URI = "/api/atomic-testings";

  private final AtomicTestingService atomicTestingService;
  private final InjectExpectationService injectExpectationService;
  private final CollectorService collectorsService;
  private final InjectImportService injectImportService;

  @LogExecutionTime
  @PostMapping("/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ATOMIC_TESTING)
  @Transactional(readOnly = true)
  public Page<InjectResultOutput> findAllAtomicTestings(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return atomicTestingService.searchAtomicTestingsForCurrentUser(searchPaginationInput);
  }

  // some api use inject as resource type because they are actually used to retrieve inject data for
  // simulation and AT
  @LogExecutionTime
  @GetMapping("/{injectId}")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public InjectResultOverviewOutput findAtomicTesting(@PathVariable String injectId) {
    return atomicTestingService.findById(injectId);
  }

  @LogExecutionTime
  @GetMapping("/{injectId}/payload")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public StatusPayloadOutput findAtomicTestingPayload(@PathVariable String injectId) {
    return atomicTestingService.findPayloadOutputByInjectId(injectId);
  }

  @PostMapping()
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.ATOMIC_TESTING)
  @Transactional(rollbackFor = Exception.class)
  public InjectResultOverviewOutput createAtomicTesting(
      @Valid @RequestBody AtomicTestingInput input) {
    return this.atomicTestingService.createOrUpdate(input, null);
  }

  @PutMapping("/{injectId}")
  @RBAC(
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
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.INJECT)
  public void deleteAtomicTesting(@PathVariable @NotBlank final String injectId) {
    atomicTestingService.deleteAtomicTesting(injectId);
  }

  @PostMapping("/{atomicTestingId}/duplicate")
  @RBAC(
      resourceId = "#atomicTestingId",
      actionPerformed = Action.DUPLICATE,
      resourceType = ResourceType.ATOMIC_TESTING)
  public InjectResultOverviewOutput duplicateAtomicTesting(
      @PathVariable @NotBlank final String atomicTestingId) {
    return atomicTestingService.duplicate(atomicTestingId);
  }

  @PostMapping("/{atomicTestingId}/launch")
  @RBAC(
      resourceId = "#atomicTestingId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.INJECT)
  public InjectResultOverviewOutput launchAtomicTesting(
      @PathVariable @NotBlank final String atomicTestingId) {
    return atomicTestingService.launch(atomicTestingId);
  }

  @PostMapping("/{atomicTestingId}/relaunch")
  @RBAC(
      resourceId = "#atomicTestingId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.INJECT)
  public InjectResultOverviewOutput relaunchAtomicTesting(
      @PathVariable @NotBlank final String atomicTestingId) {
    return atomicTestingService.relaunch(atomicTestingId);
  }

  @GetMapping("/{injectId}/target_results/{targetId}/types/{targetType}")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public List<InjectExpectation> findTargetResult(
      @PathVariable String injectId,
      @PathVariable String targetId,
      @PathVariable String targetType,
      @RequestParam(required = false) String parentTargetId) {
    return injectExpectationService.findMergedExpectationsByInjectAndTargetAndTargetType(
        injectId, targetId, parentTargetId, targetType);
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
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Expectation results fetched successfully"),
        @ApiResponse(responseCode = "400", description = "An invalid target type was specified")
      })
  @GetMapping("/{injectId}/target_results/{targetId}/types/{targetType}/merged")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  public List<InjectExpectation> findTargetResultMerged(
      @PathVariable String injectId,
      @PathVariable String targetId,
      @PathVariable String targetType) {
    return injectExpectationService
        .findMergedExpectationsByInjectAndTargetAndTargetType(injectId, targetId, targetType)
        .stream()
        .sorted(Comparator.comparing(InjectExpectation::getType))
        .toList();
  }

  @PutMapping("/{injectId}/tags")
  @RBAC(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  @Transactional(rollbackFor = Exception.class)
  public InjectResultOverviewOutput updateAtomicTestingTags(
      @PathVariable @NotBlank final String injectId,
      @Valid @RequestBody final AtomicTestingUpdateTagsInput input) {
    return atomicTestingService.updateAtomicTestingTags(injectId, input);
  }

  @GetMapping(ATOMIC_TESTING_URI + "/{injectId}/collectors")
  @RBAC(resourceId = "#injectId", actionPerformed = Action.READ, resourceType = ResourceType.INJECT)
  @Operation(summary = "Get the Collectors used in an atomic testing remediation")
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
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.ATOMIC_TESTING)
  public void atomicTestingImport(
      @RequestPart("file") MultipartFile file, HttpServletResponse response) throws Exception {
    if (file == null || file.isEmpty()) {
      throw new UnprocessableContentException("Insufficient input: file is required");
    }

    this.injectImportService.importInjectsForAtomicTestings(file);
  }
}
