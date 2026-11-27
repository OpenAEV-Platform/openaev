package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;

import io.openaev.aop.AccessControl;
import io.openaev.api.threat_arsenal.dto.PayloadDraftInput;
import io.openaev.api.threat_arsenal.dto.PayloadDraftOutput;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalAction;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionCreateInput;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionUpdateInput;
import io.openaev.config.RequireTenantSelector;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.threat_arsenal.ThreatArsenalDraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Drafts of a Threat Arsenal action's payload: versions not published yet, never usable. Everyone
 * who can update the action sees and changes them.
 */
@RestController
@RequiredArgsConstructor
public class ThreatArsenalDraftApi {

  private static final String DRAFTS = "/{actionId}/drafts";
  private static final String DRAFT = DRAFTS + "/{draftId}";
  private static final String NEW_ACTION_DRAFTS = "/new_action_drafts";
  private static final String NEW_ACTION_DRAFT = NEW_ACTION_DRAFTS + "/{draftId}";

  private final ThreatArsenalDraftService threatArsenalDraftService;
  private final TenantWriteScopeResolver writeScopeResolver;

  @Operation(summary = "Drafts of the action's payload (of all its versions), last changed first")
  @GetMapping({THREAT_ARSENAL_URL + DRAFTS, TENANT_THREAT_ARSENAL_URL + DRAFTS})
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.THREAT_ARSENAL)
  public List<PayloadDraftOutput> drafts(
      // Sets the tenant scope of the transaction (payloads and payload_drafts are v2
      // tenant-scoped).
      TxCtx ctx, @NotBlank @PathVariable final String actionId) {
    return threatArsenalDraftService.drafts(actionId);
  }

  @Operation(
      summary = "Start a draft from the action's version",
      description =
          "A draft of a rejected version fixes that version: publishing it sends the same version"
              + " for approval again. At most one draft fixes a rejected version.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "The draft"),
    @ApiResponse(
        responseCode = "400",
        description =
            "The action has no payload, is read-only here, or its rejected version already has a"
                + " draft")
  })
  @PostMapping({THREAT_ARSENAL_URL + DRAFTS, TENANT_THREAT_ARSENAL_URL + DRAFTS})
  @Transactional
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.THREAT_ARSENAL)
  public PayloadDraftOutput createDraft(
      @RequireTenantSelector TxCtx ctx,
      @NotBlank @PathVariable final String actionId,
      @Valid @RequestBody PayloadDraftInput input) {
    writeScopeResolver.tenantForWrite(ctx, null);
    return threatArsenalDraftService.create(actionId, input.content());
  }

  @Operation(summary = "Save a draft: no version number changes")
  @PutMapping({THREAT_ARSENAL_URL + DRAFT, TENANT_THREAT_ARSENAL_URL + DRAFT})
  @Transactional
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.THREAT_ARSENAL)
  public PayloadDraftOutput saveDraft(
      @RequireTenantSelector TxCtx ctx,
      @NotBlank @PathVariable final String actionId,
      @NotBlank @PathVariable final String draftId,
      @Valid @RequestBody PayloadDraftInput input) {
    writeScopeResolver.tenantForWrite(ctx, null);
    return threatArsenalDraftService.save(actionId, draftId, input.content());
  }

  @Operation(summary = "Discard a draft: no version changes")
  @DeleteMapping({THREAT_ARSENAL_URL + DRAFT, TENANT_THREAT_ARSENAL_URL + DRAFT})
  @Transactional
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.THREAT_ARSENAL)
  public void discardDraft(
      @RequireTenantSelector TxCtx ctx,
      @NotBlank @PathVariable final String actionId,
      @NotBlank @PathVariable final String draftId) {
    writeScopeResolver.tenantForWrite(ctx, null);
    threatArsenalDraftService.discard(actionId, draftId);
  }

  @Operation(
      summary = "Publish a draft",
      description =
          "Saves a new version from the version the draft started from, numbered as any update:"
              + " approved at once for a holder of Approve content, pending approval otherwise. A"
              + " draft fixing a rejected version sends that version, same number, for approval"
              + " again. The draft is gone after.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "The published version's action"),
    @ApiResponse(
        responseCode = "400",
        description = "Missing fields, or the version the draft started from was deleted")
  })
  @PostMapping({
    THREAT_ARSENAL_URL + DRAFT + "/publish",
    TENANT_THREAT_ARSENAL_URL + DRAFT + "/publish"
  })
  @Transactional
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.THREAT_ARSENAL)
  public ThreatArsenalAction publishDraft(
      @RequireTenantSelector TxCtx ctx,
      @NotBlank @PathVariable final String actionId,
      @NotBlank @PathVariable final String draftId,
      @Valid @RequestBody ThreatArsenalActionUpdateInput input) {
    writeScopeResolver.tenantForWrite(ctx, null);
    return threatArsenalDraftService.publish(actionId, draftId, input);
  }

  // -- DRAFTS OF NEW ACTIONS: no action and no version until published --

  @Operation(
      summary = "Drafts of new actions, last changed first",
      description =
          "Drafts are shared: everyone who can read the Threat Arsenal sees them; saving,"
              + " publishing and discarding one needs the right to create actions.")
  @GetMapping({
    THREAT_ARSENAL_URL + NEW_ACTION_DRAFTS,
    TENANT_THREAT_ARSENAL_URL + NEW_ACTION_DRAFTS
  })
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public List<PayloadDraftOutput> newActionDrafts(TxCtx ctx) {
    return threatArsenalDraftService.newActionDrafts();
  }

  @Operation(
      summary = "Save the Create form as the draft of a new action",
      description = "The action does not exist and has no version until the draft is published.")
  @PostMapping({
    THREAT_ARSENAL_URL + NEW_ACTION_DRAFTS,
    TENANT_THREAT_ARSENAL_URL + NEW_ACTION_DRAFTS
  })
  @Transactional
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.THREAT_ARSENAL)
  public PayloadDraftOutput createNewActionDraft(
      @RequireTenantSelector TxCtx ctx, @Valid @RequestBody PayloadDraftInput input) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    return threatArsenalDraftService.createNewActionDraft(tenantId, input.content());
  }

  @Operation(summary = "Save the draft of a new action: still no version")
  @PutMapping({THREAT_ARSENAL_URL + NEW_ACTION_DRAFT, TENANT_THREAT_ARSENAL_URL + NEW_ACTION_DRAFT})
  @Transactional
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.THREAT_ARSENAL)
  public PayloadDraftOutput saveNewActionDraft(
      @RequireTenantSelector TxCtx ctx,
      @NotBlank @PathVariable final String draftId,
      @Valid @RequestBody PayloadDraftInput input) {
    writeScopeResolver.tenantForWrite(ctx, null);
    return threatArsenalDraftService.saveNewActionDraft(draftId, input.content());
  }

  @Operation(summary = "Discard the draft of a new action")
  @DeleteMapping({
    THREAT_ARSENAL_URL + NEW_ACTION_DRAFT,
    TENANT_THREAT_ARSENAL_URL + NEW_ACTION_DRAFT
  })
  @Transactional
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.THREAT_ARSENAL)
  public void discardNewActionDraft(
      @RequireTenantSelector TxCtx ctx, @NotBlank @PathVariable final String draftId) {
    writeScopeResolver.tenantForWrite(ctx, null);
    threatArsenalDraftService.discardNewActionDraft(draftId);
  }

  @Operation(
      summary = "Publish the draft of a new action",
      description =
          "Creates the action and its first version: approved at once for a holder of Approve"
              + " content, pending approval otherwise. The draft is gone after.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "The created action"),
    @ApiResponse(responseCode = "400", description = "Missing fields or name already used")
  })
  @PostMapping({
    THREAT_ARSENAL_URL + NEW_ACTION_DRAFT + "/publish",
    TENANT_THREAT_ARSENAL_URL + NEW_ACTION_DRAFT + "/publish"
  })
  @Transactional
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.THREAT_ARSENAL)
  public ThreatArsenalAction publishNewActionDraft(
      @RequireTenantSelector TxCtx ctx,
      @NotBlank @PathVariable final String draftId,
      @Valid @RequestBody ThreatArsenalActionCreateInput input) {
    return threatArsenalDraftService.publishNewActionDraft(ctx, draftId, input);
  }
}
