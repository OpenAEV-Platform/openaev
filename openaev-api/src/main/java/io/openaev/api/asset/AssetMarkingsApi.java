package io.openaev.api.asset;

import static io.openaev.api.asset.AssetOptionsApi.ASSET_URI;
import static io.openaev.api.asset.AssetOptionsApi.TENANT_ASSET_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.asset.dto.AssetBulkReplaceMarkingsInput;
import io.openaev.api.asset.dto.AssetMarkingsOutput;
import io.openaev.api.asset.dto.AssetUpdateMarkingsInput;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Assigns markings to an asset (design step 3.3).
 *
 * <p>Deliberately on {@code /api/assets} rather than {@code /api/endpoints}: {@code marking_ids}
 * lives on the {@code assets} table, so one endpoint marks every asset category — endpoint,
 * security platform, AI target — instead of one endpoint per subtype that would each have to repeat
 * the same guard.
 */
@RestController
@RequiredArgsConstructor
@Tag(
    name = "Asset markings",
    description = "Assign sensitivity markings to an asset, whatever its category.")
public class AssetMarkingsApi extends RestBehavior {

  private final AssetMarkingsService assetMarkingsService;
  private final TenantWriteScopeResolver writeScopeResolver;

  @PutMapping({ASSET_URI + "/{assetId}/markings", TENANT_ASSET_URI + "/{assetId}/markings"})
  @Transactional(rollbackFor = Exception.class)
  @LogExecutionTime
  @AccessControl(
      resourceId = "#assetId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ASSET)
  @Operation(
      summary = "Replace the markings carried by an asset",
      description =
          "Replaces the whole set: an empty list clears every marking and makes the asset visible"
              + " to everyone again. Requires ASSIGN_MARKING to add any marking the asset does not"
              + " already carry, and/or DELETE_MARKING_ASSIGNMENT to remove any it currently does -"
              + " whichever of the two this request's payload actually does, checked independently"
              + " and in addition to the asset's own WRITE control above. A caller may only assign"
              + " markings they hold themselves, and only markings defined in their own tenant."
              + " Removing a marking is recorded as a declassification.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Asset updated"),
        @ApiResponse(
            responseCode = "403",
            description =
                "Missing ASSIGN_MARKING/DELETE_MARKING_ASSIGNMENT for what this payload changes,"
                    + " or assigning a marking the caller lacks"),
        @ApiResponse(
            responseCode = "404",
            description =
                "Asset or marking not found - including an asset marked above the caller's"
                    + " clearance, which is indistinguishable from one that does not exist")
      })
  // The @AccessControl WRITE check above answers "may you change what this asset carries" (same
  // gate as other asset-write endpoints); it says nothing about markings specifically. Which of
  // ASSIGN_MARKING / DELETE_MARKING_ASSIGNMENT this request additionally needs depends on the diff
  // between the payload and the asset's current markings, so that check is done in
  // AssetMarkingsService.updateAssetMarkings, which already loads the asset's current state - see
  // its doc. Per-definition escalation (you may not assign a marking you do not hold yourself) is a
  // separate, narrower check also enforced there, via MarkingEscalationValidator.
  public AssetMarkingsOutput updateAssetMarkings(
      TxCtx ctx,
      @PathVariable @NotBlank final String assetId,
      @Valid @RequestBody final AssetUpdateMarkingsInput input) {
    // Tenant resolved here and passed down, per the multi-tenancy convention: the service never
    // touches TenantContext. It is the tenant whose clearance the caller is checked against.
    return AssetMarkingsOutput.from(
        assetMarkingsService.updateAssetMarkings(
            writeScopeResolver.tenantForWrite(ctx, null), assetId, input));
  }

  @PutMapping({ASSET_URI + "/markings/bulk", TENANT_ASSET_URI + "/markings/bulk"})
  // SUPPORTS, not REQUIRED: the selected assets are updated chunk by chunk, each chunk in its own
  // short transaction (see AssetMarkingsService.bulkReplaceAssetMarkings), so this entrypoint must
  // not itself open one long transaction spanning the whole bulk scope.
  @Transactional(propagation = Propagation.SUPPORTS)
  @LogExecutionTime
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.ASSET)
  @Operation(
      summary = "Replace the markings carried by every asset in a bulk selection",
      description =
          "Replaces the whole set on every selected asset, the bulk mirror of the single-asset"
              + " PUT above: an empty list clears every marking. Selection is either an explicit id"
              + " list or a search input with optional exclusions (select-all), same shape as the"
              + " bulk delete endpoint. Requires DELETE_MARKING_ASSIGNMENT if any asset in scope"
              + " would lose a marking and/or ASSIGN_MARKING if any would gain one - the same"
              + " per-direction rule as the single-asset PUT, aggregated over the whole scope"
              + " upfront so the gate still fails closed before any chunk writes. A caller may only"
              + " assign markings they hold themselves, and only markings defined in their own"
              + " tenant. Removing a marking from an asset is recorded as a declassification.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Assets updated"),
        @ApiResponse(
            responseCode = "403",
            description =
                "Missing ASSIGN_MARKING or DELETE_MARKING_ASSIGNMENT, or assigning a marking the"
                    + " caller lacks"),
        @ApiResponse(
            responseCode = "404",
            description = "One or more marking definitions not found in the current tenant")
      })
  public List<String> bulkReplaceAssetMarkings(
      TxCtx ctx, @Valid @RequestBody final AssetBulkReplaceMarkingsInput input) {
    return assetMarkingsService.bulkReplaceAssetMarkings(
        ctx, writeScopeResolver.tenantForWrite(ctx, null), input);
  }
}
