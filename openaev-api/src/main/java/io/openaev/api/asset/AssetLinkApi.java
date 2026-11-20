package io.openaev.api.asset;

import static io.openaev.api.asset.AssetOptionsApi.ASSET_URI;
import static io.openaev.api.asset.AssetOptionsApi.TENANT_ASSET_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RequirePreviewFeature;
import io.openaev.api.asset.dto.AssetLinkInput;
import io.openaev.api.asset.dto.AssetLinkOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.AssetLinkType;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.settings.PreviewFeature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Links between assets of any category (#9447), on {@code /api/assets} like the markings: an
 * identity asset logged on to an endpoint, a service running on a host. The graph draws them as
 * {@code LINKED_TO} edges. Part of the exploration graph preview feature: a 404 while {@code
 * EXPLORATION_GRAPH} is off (the default).
 */
@RestController
@RequirePreviewFeature(PreviewFeature.EXPLORATION_GRAPH)
@RequiredArgsConstructor
@Tag(name = "Asset links", description = "Link an asset to other assets of the same tenant.")
public class AssetLinkApi extends RestBehavior {

  private final AssetLinkService assetLinkService;

  @GetMapping({ASSET_URI + "/{assetId}/links", TENANT_ASSET_URI + "/{assetId}/links"})
  @Transactional(readOnly = true)
  @LogExecutionTime
  @AccessControl(
      resourceId = "#assetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ASSET)
  @Operation(summary = "List the links that start or end on an asset")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Links of the asset"),
        @ApiResponse(responseCode = "404", description = "Asset not found")
      })
  public List<AssetLinkOutput> assetLinks(TxCtx ctx, @PathVariable @NotBlank final String assetId) {
    return assetLinkService.links(assetId);
  }

  @PostMapping({ASSET_URI + "/{assetId}/links", TENANT_ASSET_URI + "/{assetId}/links"})
  @Transactional(rollbackFor = Exception.class)
  @LogExecutionTime
  @AccessControl(
      resourceId = "#assetId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ASSET)
  @Operation(summary = "Link an asset to another asset of the same tenant")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Link stored (or already there)"),
        @ApiResponse(
            responseCode = "400",
            description = "Self link, or the assets belong to different tenants"),
        @ApiResponse(responseCode = "404", description = "Asset or linked asset not found")
      })
  public AssetLinkOutput addAssetLink(
      TxCtx ctx,
      @PathVariable @NotBlank final String assetId,
      @Valid @RequestBody final AssetLinkInput input) {
    return assetLinkService.addLink(assetId, input);
  }

  @DeleteMapping({
    ASSET_URI + "/{assetId}/links/{linkedAssetId}/{linkType}",
    TENANT_ASSET_URI + "/{assetId}/links/{linkedAssetId}/{linkType}"
  })
  @Transactional(rollbackFor = Exception.class)
  @LogExecutionTime
  @AccessControl(
      resourceId = "#assetId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ASSET)
  @Operation(summary = "Remove a link that starts on an asset")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Link removed"),
        @ApiResponse(responseCode = "404", description = "Asset or link not found")
      })
  public void deleteAssetLink(
      TxCtx ctx,
      @PathVariable @NotBlank final String assetId,
      @PathVariable @NotBlank final String linkedAssetId,
      @PathVariable @NotNull final AssetLinkType linkType) {
    assetLinkService.deleteLink(assetId, linkedAssetId, linkType);
  }
}
