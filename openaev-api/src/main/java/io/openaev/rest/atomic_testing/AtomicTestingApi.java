package io.openaev.rest.atomic_testing;

import static io.openaev.api.expectations.mapper.InjectExpectationMapper.toOutputs;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.asset.dto.SecurityPlatformSimpleOutput;
import io.openaev.api.expectations.ExpectationsDriftService;
import io.openaev.api.expectations.dto.ExpectationsDriftDismissInput;
import io.openaev.api.expectations.dto.ExpectationsDriftOutput;
import io.openaev.api.expectations.dto.ExpectationsRealignOutput;
import io.openaev.api.expectations.dto.InjectExpectationOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.atomic_testing.form.*;
import io.openaev.rest.exception.UnprocessableContentException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.inject.form.InjectBulkProcessingInput;
import io.openaev.service.AtomicTestingService;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.InjectImportService;
import io.openaev.service.detection_remediation.DetectionRemediationService;
import io.openaev.utils.mapper.SecurityPlatformMapper;
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
  private final DetectionRemediationService detectionRemediationService;
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
  public InjectResultOverviewOutput findAtomicTesting(
      // The TxCtx parameter is not used directly; it signals the transaction aspect to set the
      // tenant scope for this read. InjectResultOverviewOutput serializes Inject#getType(), which
      // resolves the inject's injector through the contract's (eager) injector link on the
      // v2-scoped injectors table. Without the scope the read fails closed and inject_type comes
      // back null, which changes how the frontend renders the atomic-testing result panel.
      TxCtx ctx, @PathVariable String injectId) {
    return atomicTestingService.findById(injectId);
  }

  @LogExecutionTime
  @Transactional
  @GetMapping("/{injectId}/payload")
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECT)
  public StatusPayloadOutput findAtomicTestingPayload(
      // Signals the transaction aspect to set the tenant scope: resolving the payload output reads
      // the inject's injector contract / injector on the v2-scoped injectors table.
      TxCtx ctx, @PathVariable String injectId) {
    return atomicTestingService.findPayloadOutputByInjectId(injectId);
  }

  @PostMapping()
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.ATOMIC_TESTING)
  @Transactional(rollbackFor = Exception.class)
  public InjectResultOverviewOutput createAtomicTesting(
      // The TxCtx parameter is not used directly; it signals the transaction aspect to set the
      // tenant scope for this write (createOrUpdate resolves the Injector via
      // InjectUtils#resolveInjector, which reads the v2-scoped injectors table).
      TxCtx ctx, @Valid @RequestBody AtomicTestingInput input) {
    return this.atomicTestingService.createOrUpdate(input, null);
  }

  @PutMapping("/{injectId}")
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  @Transactional(rollbackFor = Exception.class)
  public InjectResultOverviewOutput updateAtomicTesting(
      // The TxCtx parameter is not used directly; it signals the transaction aspect to set the
      // tenant scope for this write (createOrUpdate resolves the Injector via
      // InjectUtils#resolveInjector, which reads the v2-scoped injectors table).
      TxCtx ctx,
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
      // The TxCtx parameter is not used directly; it signals the transaction aspect to set the
      // tenant scope for this read (duplicate reads Inject#getInjector(), a lazy association on
      // the v2-scoped injectors table).
      TxCtx ctx, @PathVariable @NotBlank final String atomicTestingId) {
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

  @Operation(
      summary = "Dismiss or restore the expectation drift warning of an atomic testing",
      description =
          "Acknowledges that the drifted expectations were customized on purpose: the warning is"
              + " downgraded to a discreet indicator. Persisted in database so the dismissal is"
              + " shared between users, and reset on realignment")
  @PutMapping("/{injectId}/expectations-drift/dismiss")
  @Transactional
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.INJECT)
  public ExpectationsDriftOutput dismissAtomicTestingExpectationsDrift(
      @PathVariable @NotBlank final String injectId,
      @Valid @RequestBody final ExpectationsDriftDismissInput input) {
    return expectationsDriftService.dismissInjectDrift(injectId, input.dismissed());
  }

  @PostMapping("/{atomicTestingId}/launch")
  @Transactional
  @AccessControl(
      resourceId = "#atomicTestingId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.INJECT)
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // executors table (the Enterprise executor gate reads each targeted agent's executor).
  public InjectResultOverviewOutput launchAtomicTesting(
      TxCtx ctx, @PathVariable @NotBlank final String atomicTestingId) {
    return atomicTestingService.launch(atomicTestingId);
  }

  @PostMapping("/{atomicTestingId}/relaunch")
  @Transactional
  @AccessControl(
      resourceId = "#atomicTestingId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.INJECT)
  // ctx is unused directly: the aspect reads it to scope this transaction against the v2-active
  // executors table (the Enterprise executor gate reads each targeted agent's executor).
  public InjectResultOverviewOutput relaunchAtomicTesting(
      // The TxCtx parameter is not used directly; it signals the transaction aspect to set the
      // tenant scope for this read (relaunch duplicates the inject, reading Inject#getInjector(),
      // a lazy association on the v2-scoped injectors table).
      TxCtx ctx, @PathVariable @NotBlank final String atomicTestingId) {
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
      // ctx is unused directly: the aspect reads it to scope this transaction against the
      // v2-active executors table (see the comment below on the Enterprise executor gate).
      TxCtx ctx,
      @PathVariable @NotBlank final String injectId,
      @Valid @RequestBody final InjectRecurrenceInput input) {
    // Scheduling is a Community Edition feature, but setting a schedule still goes through the
    // Enterprise executor gate (see AtomicTestingService#updateRecurrence).
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
      TxCtx ctx,
      @PathVariable @NotBlank final String injectId,
      @Valid @RequestBody final AtomicTestingUpdateTagsInput input) {
    return atomicTestingService.updateAtomicTestingTags(injectId, input);
  }

  @GetMapping("/{injectId}/security-platforms")
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECT)
  @Operation(summary = "Get the Security platforms used in an atomic testing remediation")
  @Transactional
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "The list of Security platforms used in an atomic testing remediation")
      })
  public List<SecurityPlatformSimpleOutput> securityPlatformsFromAtomicTesting(
      @PathVariable String injectId) {
    return SecurityPlatformMapper.toSimpleOutputs(
        detectionRemediationService.securityPlatformsForInject(injectId));
  }

  @PostMapping(
      path = "/import",
      consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  @Transactional
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.ATOMIC_TESTING)
  public void atomicTestingImport(
      // The TxCtx parameter is not used directly; it signals the transaction aspect to set the
      // tenant scope for this read/write (the import reads InjectorContract#getFirstInjector()).
      // The handler does not use it directly.
      TxCtx ctx, @RequestPart("file") MultipartFile file, HttpServletResponse response)
      throws Exception {
    if (file == null || file.isEmpty()) {
      throw new UnprocessableContentException("Insufficient input: file is required");
    }

    this.injectImportService.importInjectsForAtomicTestings(ctx, file);
  }
}
