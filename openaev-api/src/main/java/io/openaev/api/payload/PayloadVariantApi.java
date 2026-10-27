package io.openaev.api.payload;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.payload.PayloadApi.PAYLOAD_URI;
import static io.openaev.rest.payload.PayloadApi.TENANT_PAYLOAD_URI;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalAction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Payload;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.payload.service.PayloadVariantService;
import io.openaev.rest.payload.service.PayloadVariantService.Block;
import io.openaev.rest.payload.service.PayloadVariantService.BlockChoice;
import io.openaev.rest.payload.service.PayloadVariantService.BlockPreview;
import io.openaev.rest.payload.service.PayloadVariantService.RebasePreview;
import io.openaev.rest.payload.service.PayloadVariantService.VariantInfo;
import io.openaev.rest.payload.service.PayloadVariantService.VariantResult;
import io.openaev.service.payload.PayloadRevocationService;
import io.openaev.service.threat_arsenal.ThreatArsenalService;
import io.openaev.utils.mapper.ThreatArsenalMapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Customized catalogue payloads (ADR-011, point 3): create, change, rebase with a preview. */
@RestController
@RequiredArgsConstructor
public class PayloadVariantApi {

  public static final String IDENTITY_URI = "/api/payload-identities";
  public static final String TENANT_IDENTITY_URI = TENANT_PREFIX + "/payload-identities";

  private final PayloadVariantService payloadVariantService;
  private final PayloadRevocationService payloadRevocationService;
  private final ThreatArsenalMapper threatArsenalMapper;
  private final ThreatArsenalService threatArsenalService;

  public record VariantOutput(
      @JsonProperty("identity_id") String identityId,
      @JsonProperty("variant_action_id") String actionId,
      @JsonProperty("variant_payload_id") String payloadId,
      @JsonProperty("variant_version") String version,
      @JsonProperty("variant_overrides") Map<String, Object> overrides,
      @JsonProperty("variant_customizable_fields") List<String> customizableFields,
      @JsonProperty("base_payload_id") String basePayloadId,
      @JsonProperty("base_external_id") String baseExternalId,
      @JsonProperty("base_version") String baseVersion,
      @JsonProperty("newer_base_payload_id") String newerBasePayloadId,
      @JsonProperty("newer_base_version") String newerBaseVersion,
      @JsonProperty("conflict_target_version") String conflictTargetVersion,
      @JsonProperty("base_values") Map<String, JsonNode> baseValues,
      @JsonProperty("current_values") Map<String, JsonNode> currentValues) {}

  public record VariantSaveOutput(
      @JsonProperty("action") ThreatArsenalAction action,
      @JsonProperty("new_version") boolean newVersion,
      @JsonProperty("previous_action_id") String previousActionId,
      @JsonProperty("previous_version_usages") int previousUsages) {}

  public record OverridesInput(@JsonProperty("variant_overrides") Map<String, Object> overrides) {}

  public record BlockPreviewOutput(
      @JsonProperty("block") Block block,
      @JsonProperty("catalog_changed") boolean catalogChanged,
      @JsonProperty("variant_changed") boolean variantChanged,
      @JsonProperty("conflict") boolean conflict,
      @JsonProperty("base") Map<String, JsonNode> base,
      @JsonProperty("catalog") Map<String, JsonNode> catalog,
      @JsonProperty("mine") Map<String, JsonNode> mine) {}

  public record RebasePreviewOutput(
      @JsonProperty("current_version") String currentVersion,
      @JsonProperty("base_version") String baseVersion,
      @JsonProperty("target_payload_id") String targetPayloadId,
      @JsonProperty("target_version") String targetVersion,
      @JsonProperty("blocks") List<BlockPreviewOutput> blocks,
      @JsonProperty("missing_variables") List<String> missingVariables,
      @JsonProperty("has_conflict") boolean hasConflict) {}

  public record RebaseInput(
      @JsonProperty("target_payload_id") String targetPayloadId,
      @JsonProperty("choices") Map<Block, BlockChoice> choices) {}

  public record InheritedRevocationLiftInput(
      @JsonProperty("content_revocation_reason") @NotBlank String reason) {}

  @LogExecutionTime
  @Operation(summary = "Customize a catalogue payload version: a variant that keeps its updates")
  @PostMapping({
    PAYLOAD_URI + "/{payloadId}/customize",
    TENANT_PAYLOAD_URI + "/{payloadId}/customize"
  })
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.THREAT_ARSENAL)
  public VariantSaveOutput customize(TxCtx ctx, @PathVariable String payloadId) {
    return toOutput(payloadVariantService.customize(payloadId));
  }

  @LogExecutionTime
  @Operation(summary = "A customized payload: its changes, its base and any newer base")
  @GetMapping({IDENTITY_URI + "/{identityId}", TENANT_IDENTITY_URI + "/{identityId}"})
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.THREAT_ARSENAL)
  public VariantOutput variant(TxCtx ctx, @PathVariable String identityId) {
    VariantInfo info = payloadVariantService.info(identityId);
    return new VariantOutput(
        info.identity().getId(),
        actionIdOf(info.latest()),
        info.latest().getId(),
        info.latest().getVersion(),
        info.identity().getOverrides(),
        info.customizableFields(),
        info.base().getId(),
        info.base().getExternalId(),
        info.base().getVersion(),
        info.newestBase() == null ? null : info.newestBase().getId(),
        info.newestBase() == null ? null : info.newestBase().getVersion(),
        info.conflictTarget() == null ? null : info.conflictTarget().getVersion(),
        info.baseValues(),
        info.currentValues());
  }

  @LogExecutionTime
  @Operation(summary = "Change the fields a customized payload overrides; a change adds a version")
  @PutMapping({
    IDENTITY_URI + "/{identityId}/overrides",
    TENANT_IDENTITY_URI + "/{identityId}/overrides"
  })
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.THREAT_ARSENAL)
  public VariantSaveOutput overrides(
      TxCtx ctx, @PathVariable String identityId, @RequestBody OverridesInput input) {
    return toOutput(payloadVariantService.updateOverrides(identityId, input.overrides()));
  }

  @LogExecutionTime
  @Operation(summary = "Preview a rebase on a newer catalogue version, block by block")
  @GetMapping({
    IDENTITY_URI + "/{identityId}/rebase-preview",
    TENANT_IDENTITY_URI + "/{identityId}/rebase-preview"
  })
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.THREAT_ARSENAL)
  public RebasePreviewOutput rebasePreview(
      TxCtx ctx,
      @PathVariable String identityId,
      @RequestParam(name = "target_payload_id", required = false) String targetPayloadId) {
    RebasePreview preview = payloadVariantService.previewRebase(identityId, targetPayloadId);
    return new RebasePreviewOutput(
        preview.current().getVersion(),
        preview.base().getVersion(),
        preview.target().getId(),
        preview.target().getVersion(),
        preview.blocks().stream().map(PayloadVariantApi::toOutput).toList(),
        preview.missingVariables(),
        preview.hasConflict());
  }

  @LogExecutionTime
  @Operation(summary = "Rebase on a catalogue version, with a choice for each conflicting block")
  @PostMapping({
    IDENTITY_URI + "/{identityId}/resolve-conflict",
    TENANT_IDENTITY_URI + "/{identityId}/resolve-conflict"
  })
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.THREAT_ARSENAL)
  public VariantSaveOutput rebase(
      TxCtx ctx, @PathVariable String identityId, @RequestBody RebaseInput input) {
    return toOutput(
        payloadVariantService.rebase(identityId, input.targetPayloadId(), input.choices()));
  }

  @LogExecutionTime
  @Operation(summary = "Free a customized version from the revocation it inherits from its base")
  @PostMapping({
    PAYLOAD_URI + "/{payloadId}/inherited-revocation/lift",
    TENANT_PAYLOAD_URI + "/{payloadId}/inherited-revocation/lift"
  })
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(
      resourceId = "#payloadId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.PAYLOAD)
  public void liftInherited(
      TxCtx ctx,
      @PathVariable String payloadId,
      @Valid @RequestBody InheritedRevocationLiftInput input) {
    payloadRevocationService.liftInherited(payloadId, input.reason());
  }

  private VariantSaveOutput toOutput(VariantResult result) {
    Payload previous = result.previous();
    return new VariantSaveOutput(
        result.injectorContract() == null
            ? null
            : threatArsenalService.describe(
                threatArsenalMapper.toThreatArsenalAction(result.injectorContract())),
        previous != null,
        previous == null ? null : actionIdOf(previous),
        result.previousUsages());
  }

  private String actionIdOf(Payload payload) {
    return payloadVariantService.actionIdOf(payload);
  }

  private static BlockPreviewOutput toOutput(BlockPreview block) {
    return new BlockPreviewOutput(
        block.block(),
        block.catalogChanged(),
        block.variantChanged(),
        block.conflict(),
        block.base(),
        block.catalog(),
        block.mine());
  }
}
