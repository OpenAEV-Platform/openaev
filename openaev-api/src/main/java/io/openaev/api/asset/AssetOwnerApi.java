package io.openaev.api.asset;

import static io.openaev.api.asset.AssetOptionsApi.ASSET_URI;
import static io.openaev.api.asset.AssetOptionsApi.TENANT_ASSET_URI;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RequirePreviewFeature;
import io.openaev.api.asset.dto.AssetOwnerInput;
import io.openaev.api.asset.dto.AssetOwnerOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.settings.PreviewFeature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
 * Owners of assets (#9725): a person or a team, as business owner ({@code OWNED_BY}) or technical
 * owner ({@code MANAGED_BY}), each with its source. Part of the exploration graph preview feature,
 * like the asset links: a 404 while {@code EXPLORATION_GRAPH} is off (the default).
 */
@RestController
@RequirePreviewFeature(PreviewFeature.EXPLORATION_GRAPH)
@RequiredArgsConstructor
@Tag(name = "Asset owners", description = "The people and teams that own or manage an asset.")
public class AssetOwnerApi extends RestBehavior {

  static final String USER_OWNED_URI = "/api/users/{userId}/owned-assets";
  static final String TEAM_OWNED_URI = "/api/teams/{teamId}/owned-assets";

  private final AssetOwnerService assetOwnerService;

  @GetMapping({ASSET_URI + "/{assetId}/owners", TENANT_ASSET_URI + "/{assetId}/owners"})
  @Transactional(readOnly = true)
  @LogExecutionTime
  @AccessControl(
      resourceId = "#assetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ASSET)
  @Operation(summary = "List the owners of an asset")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Owners of the asset"),
        @ApiResponse(responseCode = "404", description = "Asset not found")
      })
  public List<AssetOwnerOutput> assetOwners(
      TxCtx ctx, @PathVariable @NotBlank final String assetId) {
    return assetOwnerService.owners(assetId);
  }

  @PostMapping({ASSET_URI + "/{assetId}/owners", TENANT_ASSET_URI + "/{assetId}/owners"})
  @Transactional(rollbackFor = Exception.class)
  @LogExecutionTime
  @AccessControl(
      resourceId = "#assetId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ASSET)
  @Operation(summary = "Add a person or a team as owner of an asset")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Owner stored (or already there)"),
        @ApiResponse(
            responseCode = "400",
            description = "Not exactly one of a person and a team, or outside the asset's tenant"),
        @ApiResponse(responseCode = "404", description = "Asset or team not found")
      })
  public AssetOwnerOutput addAssetOwner(
      TxCtx ctx,
      @PathVariable @NotBlank final String assetId,
      @Valid @RequestBody final AssetOwnerInput input) {
    return assetOwnerService.addOwner(assetId, input);
  }

  @DeleteMapping({
    ASSET_URI + "/{assetId}/owners/{ownerId}",
    TENANT_ASSET_URI + "/{assetId}/owners/{ownerId}"
  })
  @Transactional(rollbackFor = Exception.class)
  @LogExecutionTime
  @AccessControl(
      resourceId = "#assetId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ASSET)
  @Operation(summary = "Remove an owner of an asset")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Owner removed"),
        @ApiResponse(responseCode = "404", description = "Asset or owner not found")
      })
  public void deleteAssetOwner(
      TxCtx ctx,
      @PathVariable @NotBlank final String assetId,
      @PathVariable @NotBlank final String ownerId) {
    assetOwnerService.deleteOwner(assetId, ownerId);
  }

  @GetMapping({USER_OWNED_URI, TENANT_PREFIX + "/users/{userId}/owned-assets"})
  @Transactional(readOnly = true)
  @LogExecutionTime
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  @Operation(summary = "List the assets a person owns or manages")
  public List<AssetOwnerOutput> userOwnedAssets(
      TxCtx ctx, @PathVariable @NotBlank final String userId) {
    return assetOwnerService.ownedByUser(userId);
  }

  @GetMapping({TEAM_OWNED_URI, TENANT_PREFIX + "/teams/{teamId}/owned-assets"})
  @Transactional(readOnly = true)
  @LogExecutionTime
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  @Operation(summary = "List the assets a team owns or manages")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Assets of the team"),
        @ApiResponse(responseCode = "404", description = "Team not found")
      })
  public List<AssetOwnerOutput> teamOwnedAssets(
      TxCtx ctx, @PathVariable @NotBlank final String teamId) {
    return assetOwnerService.ownedByTeam(teamId);
  }
}
