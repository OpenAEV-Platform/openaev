package io.openaev.rest.finding;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Finding;
import io.openaev.database.model.ResourceType;
import io.openaev.database.specification.FindingSpecification;
import io.openaev.rest.finding.form.FindingOutput;
import io.openaev.rest.finding.form.FindingRetestInput;
import io.openaev.rest.finding.form.FindingRetestOutput;
import io.openaev.rest.finding.form.FindingSimple;
import io.openaev.rest.finding.form.FindingSummaryOutput;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.finding.FindingOccurrenceService;
import io.openaev.service.finding.FindingRetestService;
import io.openaev.utils.TxCtxScopeUtils;
import io.openaev.utils.mapper.FindingMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class FindingApi extends RestBehavior {

  public static final String FINDING_URI = "/api/findings";
  public static final String TENANT_FINDING_URI = TENANT_PREFIX + "/findings";

  private static final String DISTINCT_DEPRECATION =
      "Deprecated and ignored: a finding is already one row per type, value and location.";

  private final FindingService findingService;
  private final FindingMapper findingMapper;
  private final FindingOccurrenceService findingOccurrenceService;
  private final InjectService injectService;
  private final FindingRetestService findingRetestService;

  // -- READ --

  /*
   * The output is built inside the transaction: its location reaches the v2-active assets table,
   * which Jackson can no longer read once the transaction has closed (#7856).
   * FindingLocationSinkTest pins it from a non-transactional test.
   */

  @GetMapping({FINDING_URI + "/{id}", TENANT_FINDING_URI + "/{id}"})
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.READ,
      resourceType = ResourceType.FINDING)
  public ResponseEntity<FindingOutput> finding(TxCtx ctx, @PathVariable @NotNull final String id) {
    return ResponseEntity.ok(
        this.findingMapper.toFindingOutput(this.findingService.finding(ctx, id)));
  }

  @GetMapping({FINDING_URI + "/{id}/summary", TENANT_FINDING_URI + "/{id}/summary"})
  @Transactional
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.READ,
      resourceType = ResourceType.FINDING)
  @Operation(
      summary = "Get the group-wide summary of a finding",
      description = "Deprecated: read the finding and its occurrences instead.",
      deprecated = true)
  public ResponseEntity<FindingSummaryOutput> findingSummary(
      TxCtx ctx, @PathVariable @NotNull final String id) {
    return ResponseEntity.ok(this.findingService.findingSummary(ctx, id));
  }

  // -- SEARCH --

  @LogExecutionTime
  @PostMapping({FINDING_URI + "/search", TENANT_FINDING_URI + "/search"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.FINDING)
  public Page<FindingSimple> findings(
      TxCtx ctx,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput,
      @Parameter(deprecated = true, description = DISTINCT_DEPRECATION)
          @RequestParam(value = "distinct", required = false)
          final Boolean distinct) {
    return search(Specification.unrestricted(), searchPaginationInput);
  }

  @LogExecutionTime
  @PostMapping({
    FINDING_URI + "/injects/{injectId}/search",
    TENANT_FINDING_URI + "/injects/{injectId}/search"
  })
  @Transactional
  @AccessControl(
      resourceId = "#injectId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.INJECT)
  public Page<FindingSimple> findingsByInject(
      TxCtx ctx,
      @PathVariable @NotNull final String injectId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput,
      @Parameter(deprecated = true, description = DISTINCT_DEPRECATION)
          @RequestParam(value = "distinct", required = false)
          final Boolean distinct) {
    injectService.existsInScope(ctx, injectId);
    return search(FindingSpecification.findFindingsForInject(injectId), searchPaginationInput);
  }

  @LogExecutionTime
  @PostMapping({
    FINDING_URI + "/exercises/{simulationId}/search",
    TENANT_FINDING_URI + "/exercises/{simulationId}/search"
  })
  @Transactional
  @AccessControl(
      resourceId = "#simulationId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public Page<FindingSimple> findingsBySimulation(
      TxCtx ctx,
      @PathVariable @NotNull final String simulationId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput,
      @Parameter(deprecated = true, description = DISTINCT_DEPRECATION)
          @RequestParam(value = "distinct", required = false)
          final Boolean distinct) {
    return search(
        FindingSpecification.findFindingsForSimulation(simulationId), searchPaginationInput);
  }

  @LogExecutionTime
  @PostMapping({
    FINDING_URI + "/scenarios/{scenarioId}/search",
    TENANT_FINDING_URI + "/scenarios/{scenarioId}/search"
  })
  @Transactional
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Page<FindingSimple> findingsByScenario(
      TxCtx ctx,
      @PathVariable @NotNull final String scenarioId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput,
      @Parameter(deprecated = true, description = DISTINCT_DEPRECATION)
          @RequestParam(value = "distinct", required = false)
          final Boolean distinct) {
    return search(FindingSpecification.findFindingsForScenario(scenarioId), searchPaginationInput);
  }

  @LogExecutionTime
  @PostMapping({
    FINDING_URI + "/endpoints/{endpointId}/search",
    TENANT_FINDING_URI + "/endpoints/{endpointId}/search"
  })
  @Transactional
  @AccessControl(
      resourceId = "#endpointId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ASSET)
  public Page<FindingSimple> findingsByEndpoint(
      TxCtx ctx,
      @PathVariable @NotNull final String endpointId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput,
      @Parameter(deprecated = true, description = DISTINCT_DEPRECATION)
          @RequestParam(value = "distinct", required = false)
          final Boolean distinct) {
    return search(FindingSpecification.findFindingsForEndpoint(endpointId), searchPaginationInput);
  }

  private Page<FindingSimple> search(
      Specification<Finding> scope, SearchPaginationInput searchPaginationInput) {
    return this.findingMapper.toFindingSimplePage(
        this.findingService.searchFindings(scope, searchPaginationInput));
  }

  // -- LIFECYCLE --

  /**
   * Re-tests findings: queues a copy of the inject that last produced each of them, on the same
   * targets and under the same contract, and returns the queued inject per finding. When a copy
   * finishes, the finding stays OPEN if it was detected again, else it turns NOT_REPRODUCED. The
   * caller needs the launch right on each inject re-run, checked in the service.
   */
  @PostMapping({FINDING_URI + "/retest", TENANT_FINDING_URI + "/retest"})
  @Transactional
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.FINDING)
  public ResponseEntity<List<FindingRetestOutput>> retestFindings(
      TxCtx ctx, @Valid @RequestBody final FindingRetestInput input) {
    return ResponseEntity.ok(
        this.findingRetestService
            .retest(ctx, TxCtxScopeUtils.requireOneTenantOfScope(ctx), input.getFindingIds())
            .stream()
            .map(retest -> new FindingRetestOutput(retest.findingId(), retest.retestInjectId()))
            .toList());
  }
}
