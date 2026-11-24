package io.openaev.api.findings;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.api.findings.dto.FindingOccurrenceOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.finding.FindingOccurrenceService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(FindingOccurrenceApi.TENANT_FINDING_OCCURRENCES_URI)
public class FindingOccurrenceApi extends RestBehavior {

  public static final String TENANT_FINDING_OCCURRENCES_URI =
      TENANT_PREFIX + "/findings/{findingId}/occurrences";

  private final FindingOccurrenceService findingOccurrenceService;
  private final FindingOccurrenceMapper findingOccurrenceMapper;

  // -- SEARCH --

  @PostMapping("/search")
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#findingId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.FINDING)
  @Operation(summary = "Search the occurrences of a finding")
  public Page<FindingOccurrenceOutput> searchOccurrences(
      TxCtx ctx,
      @PathVariable @NotNull final String findingId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return findingOccurrenceService
        .searchOccurrences(findingId, searchPaginationInput)
        .map(findingOccurrenceMapper::toOutput);
  }
}
