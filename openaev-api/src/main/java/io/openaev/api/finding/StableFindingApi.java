package io.openaev.api.finding;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.finding.StableFindingReadService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping({StableFindingApi.STABLE_FINDING_URI, TENANT_PREFIX + "/stable-findings"})
public class StableFindingApi {

  public static final String STABLE_FINDING_URI = "/api/stable-findings";

  private final StableFindingReadService stableFindingReadService;

  // -- SEARCH --

  @PostMapping("/search")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.FINDING)
  @LogExecutionTime
  @Operation(summary = "Search stable findings")
  public Page<StableFindingOutput> search(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput input) {
    return stableFindingReadService.search(ctx, input);
  }

  @PostMapping("/facet-counts")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.FINDING)
  @LogExecutionTime
  @Operation(summary = "Get Finding sidebar facet counts")
  public StableFindingFacetCountsOutput facetCounts(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput input) {
    return stableFindingReadService.facetCounts(ctx, input);
  }

  @PostMapping("/{id}/occurrences/search")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.FINDING)
  @LogExecutionTime
  @Operation(summary = "Search a stable finding's occurrences")
  public Page<FindingOccurrenceOutput> searchOccurrences(
      TxCtx ctx,
      @PathVariable @NotNull String id,
      @RequestBody @Valid SearchPaginationInput input) {
    return stableFindingReadService.searchOccurrences(ctx, id, input);
  }

  // -- READ --

  @GetMapping("/{id}")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.FINDING)
  @LogExecutionTime
  @Operation(summary = "Get a stable finding")
  public StableFindingOutput findById(TxCtx ctx, @PathVariable @NotNull String id) {
    return stableFindingReadService.findById(ctx, id);
  }

  @GetMapping("/{id}/summary")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.FINDING)
  @LogExecutionTime
  @Operation(summary = "Get a stable finding summary")
  public StableFindingSummaryOutput summary(TxCtx ctx, @PathVariable @NotNull String id) {
    return stableFindingReadService.summary(ctx, id);
  }

  @GetMapping("/{id}/locations")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.FINDING)
  @LogExecutionTime
  @Operation(summary = "Get the locations observed for a stable finding")
  public List<FindingLocationOutput> locations(TxCtx ctx, @PathVariable @NotNull String id) {
    return stableFindingReadService.locations(ctx, id);
  }
}
