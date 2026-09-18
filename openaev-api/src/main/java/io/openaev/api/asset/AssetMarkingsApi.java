package io.openaev.api.asset;

import static io.openaev.api.asset.AssetOptionsApi.ASSET_URI;
import static io.openaev.api.asset.AssetOptionsApi.TENANT_ASSET_URI;

import io.openaev.aop.AccessControl;
import io.openaev.api.asset.dto.AssetUpdateMarkingsInput;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Asset;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
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
  @AccessControl(
      resourceId = "#assetId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ASSET)
  @Operation(
      summary = "Replace the markings carried by an asset",
      description =
          "Replaces the whole set: an empty list clears every marking and makes the asset visible"
              + " to everyone again. A caller may only assign markings they hold themselves, and"
              + " only markings defined in their own tenant. Removing a marking is recorded as a"
              + " declassification.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Asset updated"),
        @ApiResponse(responseCode = "403", description = "Assigning a marking the caller lacks"),
        @ApiResponse(
            responseCode = "404",
            description =
                "Asset or marking not found - including an asset marked above the caller's"
                    + " clearance, which is indistinguishable from one that does not exist")
      })
  // TODO: replace with the "Assign marking" capability chain (design Q8) once Task 1 lands. The
  // asset's own WRITE control is the honest interim, matching the group markings endpoint.
  public Asset updateAssetMarkings(
      TxCtx ctx,
      @PathVariable @NotBlank final String assetId,
      @Valid @RequestBody final AssetUpdateMarkingsInput input) {
    // Tenant resolved here and passed down, per the multi-tenancy convention: the service never
    // touches TenantContext. It is the tenant whose clearance the caller is checked against.
    return assetMarkingsService.updateAssetMarkings(
        writeScopeResolver.tenantForWrite(ctx, null), assetId, input);
  }
}
