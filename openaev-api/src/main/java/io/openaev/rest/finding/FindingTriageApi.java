package io.openaev.rest.finding;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.finding.form.FindingTriageBulkInput;
import io.openaev.rest.finding.form.FindingTriageBulkItemOutput;
import io.openaev.rest.finding.form.FindingTriageHistoryOutput;
import io.openaev.rest.finding.form.FindingTriageInput;
import io.openaev.rest.finding.form.FindingTriageOutput;
import io.openaev.rest.helper.RestBehavior;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class FindingTriageApi extends RestBehavior {

  public static final String FINDING_TRIAGE_URI = "/api/findings/{id}/triage";
  public static final String TENANT_FINDING_TRIAGE_URI = TENANT_PREFIX + "/findings/{id}/triage";
  public static final String FINDING_TRIAGE_BULK_URI = "/api/findings/triage/bulk";
  public static final String TENANT_FINDING_TRIAGE_BULK_URI =
      TENANT_PREFIX + "/findings/triage/bulk";
  public static final String FINDING_TRIAGE_HISTORY_URI = "/api/findings/{id}/triage/history";
  public static final String TENANT_FINDING_TRIAGE_HISTORY_URI =
      TENANT_PREFIX + "/findings/{id}/triage/history";

  private final FindingTriageService findingTriageService;

  // Gated on READ (not TRIAGE): fetching the current status is a plain read of the finding's
  // sub-resource, available to anyone who can see the finding - the stricter TRIAGE capability
  // is reserved for the state-changing endpoints below (PATCH single/bulk).
  @GetMapping({FINDING_TRIAGE_URI, TENANT_FINDING_TRIAGE_URI})
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.READ,
      resourceType = ResourceType.FINDING)
  public ResponseEntity<FindingTriageOutput> getFindingTriage(
      @PathVariable @NotNull final String id) {
    return ResponseEntity.ok(findingTriageService.getCurrentStatus(id));
  }

  // Gated directly on the target Finding (id is already in the path) - unlike FindingComment's
  // update/delete, there is no parent-permission indirection needed here since every triage
  // sub-resource is always addressed via its finding id.
  @PatchMapping({FINDING_TRIAGE_URI, TENANT_FINDING_TRIAGE_URI})
  @Transactional
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.TRIAGE,
      resourceType = ResourceType.FINDING)
  public ResponseEntity<FindingTriageOutput> triageFinding(
      @PathVariable @NotNull final String id,
      @RequestBody @Valid @NotNull final FindingTriageInput input) {
    return ResponseEntity.ok(
        findingTriageService.triage(id, input.getStatus(), input.getJustification()));
  }

  // No single resourceId to resolve (the target findings are in the request body, as a list) -
  // mirrors FindingApi#createFinding's capability-only (no resourceId) @AccessControl usage,
  // since ResourceType.FINDING permission for MANAGE_FINDING_TRIAGE is a plain capability check
  // (FINDING is not in PermissionService's RESOURCES_USING_PARENT_PERMISSION or
  // RESOURCES_MANAGED_BY_GRANTS sets), not a per-instance grant/parent resolution.
  @PatchMapping({FINDING_TRIAGE_BULK_URI, TENANT_FINDING_TRIAGE_BULK_URI})
  @Transactional
  @AccessControl(actionPerformed = Action.TRIAGE, resourceType = ResourceType.FINDING)
  public ResponseEntity<List<FindingTriageBulkItemOutput>> triageFindingsBulk(
      @RequestBody @Valid @NotNull final FindingTriageBulkInput input) {
    return ResponseEntity.ok(
        findingTriageService.triageBulk(
            input.getFindingIds(), input.getStatus(), input.getJustification()));
  }

  @GetMapping({FINDING_TRIAGE_HISTORY_URI, TENANT_FINDING_TRIAGE_HISTORY_URI})
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.TRIAGE,
      resourceType = ResourceType.FINDING)
  public ResponseEntity<List<FindingTriageHistoryOutput>> findingTriageHistory(
      @PathVariable @NotNull final String id) {
    return ResponseEntity.ok(findingTriageService.history(id));
  }
}
