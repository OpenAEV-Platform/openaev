package io.openaev.api.asset;

import static io.openaev.api.asset.AssetOptionsApi.ASSET_URI;
import static io.openaev.api.asset.AssetOptionsApi.TENANT_ASSET_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.asset.dto.AssetBulkReplaceMarkingsInput;
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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(
    name = "Asset markings",
    description = "Assign sensitivity markings to a selection of assets, whatever their category.")
public class AssetMarkingsApi extends RestBehavior {

  private final AssetMarkingsService assetMarkingsService;
  private final TenantWriteScopeResolver writeScopeResolver;

  @PutMapping({ASSET_URI + "/markings/bulk", TENANT_ASSET_URI + "/markings/bulk"})
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
