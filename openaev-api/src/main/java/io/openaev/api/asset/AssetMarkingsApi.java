package io.openaev.api.asset;

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

  @PutMapping(TENANT_ASSET_URI + "/{assetId}/markings")
  @Transactional(rollbackFor = Exception.class)
  @LogExecutionTime
  @AccessControl(
      resourceId = "#assetId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ASSET)
  @Operation(summary = "Replace the whole marking set carried by an asset")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Asset updated"),
        @ApiResponse(
            responseCode = "403",
            description = "Missing marking capability for what this payload changes"),
        @ApiResponse(responseCode = "404", description = "Asset or marking not found")
      })
  // MANAGE_MARKING_ASSIGNMENT and escalation are checked in
  // AssetMarkingsService.updateAssetMarkings instead - see its doc.
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

  @PutMapping(TENANT_ASSET_URI + "/markings/bulk")
  @Transactional(propagation = Propagation.SUPPORTS)
  @LogExecutionTime
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.ASSET)
  @Operation(summary = "Replace the whole marking set carried by every asset in a bulk selection")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Assets updated"),
        @ApiResponse(
            responseCode = "403",
            description = "Missing marking capability for what this payload changes"),
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
