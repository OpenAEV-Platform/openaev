package io.openaev.api.asset.software;

import static io.openaev.api.asset.AssetOptionsApi.ASSET_URI;
import static io.openaev.api.asset.AssetOptionsApi.TENANT_ASSET_URI;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.asset.software.dto.AssetSoftwareInput;
import io.openaev.api.asset.software.dto.AssetSoftwareOutput;
import io.openaev.api.asset.software.dto.SbomImportOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.SoftwareSource;
import io.openaev.rest.helper.RestBehavior;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Software inventory of assets: what an asset runs (vendor, product, version, CPE, PURL), written
 * by the API or a CycloneDX / SPDX SBOM import, and the assets that run a given software. Available
 * under {@code /api/assets} and the tenant prefix.
 */
@RestController
@RequiredArgsConstructor
@Tag(
    name = "Asset software",
    description = "Software inventory of assets, with CycloneDX and SPDX SBOM import.")
public class AssetSoftwareApi extends RestBehavior {

  private final AssetSoftwareService assetSoftwareService;

  @GetMapping({ASSET_URI + "/{assetId}/software", TENANT_ASSET_URI + "/{assetId}/software"})
  @Transactional(readOnly = true)
  @LogExecutionTime
  @AccessControl(
      resourceId = "#assetId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ASSET)
  @Operation(summary = "List the software an asset runs")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Software of the asset"),
        @ApiResponse(responseCode = "404", description = "Asset not found")
      })
  public List<AssetSoftwareOutput> assetSoftware(
      TxCtx ctx, @PathVariable @NotBlank final String assetId) {
    return assetSoftwareService.software(assetId);
  }

  @PostMapping({ASSET_URI + "/{assetId}/software", TENANT_ASSET_URI + "/{assetId}/software"})
  @Transactional(rollbackFor = Exception.class)
  @LogExecutionTime
  @AccessControl(
      resourceId = "#assetId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ASSET)
  @Operation(summary = "Record a software component on an asset (or update the same component)")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Component stored"),
        @ApiResponse(responseCode = "400", description = "No product name"),
        @ApiResponse(responseCode = "404", description = "Asset or parent component not found")
      })
  public AssetSoftwareOutput addAssetSoftware(
      TxCtx ctx,
      @PathVariable @NotBlank final String assetId,
      @Valid @RequestBody final AssetSoftwareInput input) {
    return assetSoftwareService.upsert(assetId, input, SoftwareSource.API);
  }

  @PostMapping({
    ASSET_URI + "/{assetId}/software/sbom",
    TENANT_ASSET_URI + "/{assetId}/software/sbom"
  })
  @Transactional(rollbackFor = Exception.class)
  @LogExecutionTime
  @AccessControl(
      resourceId = "#assetId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ASSET)
  @Operation(summary = "Import a CycloneDX or SPDX JSON SBOM on an asset")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Components stored"),
        @ApiResponse(responseCode = "400", description = "Not a CycloneDX or SPDX JSON document"),
        @ApiResponse(responseCode = "404", description = "Asset not found")
      })
  public SbomImportOutput importAssetSbom(
      TxCtx ctx, @PathVariable @NotBlank final String assetId, @RequestBody final JsonNode sbom) {
    return assetSoftwareService.importSbom(assetId, sbom);
  }

  @DeleteMapping({
    ASSET_URI + "/{assetId}/software/{softwareId}",
    TENANT_ASSET_URI + "/{assetId}/software/{softwareId}"
  })
  @Transactional(rollbackFor = Exception.class)
  @LogExecutionTime
  @AccessControl(
      resourceId = "#assetId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ASSET)
  @Operation(summary = "Remove a software component from an asset")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Component removed"),
        @ApiResponse(responseCode = "404", description = "Asset or component not found")
      })
  public void deleteAssetSoftware(
      TxCtx ctx,
      @PathVariable @NotBlank final String assetId,
      @PathVariable @NotNull final String softwareId) {
    assetSoftwareService.delete(assetId, softwareId);
  }

  @GetMapping({ASSET_URI + "/software", TENANT_ASSET_URI + "/software"})
  @Transactional(readOnly = true)
  @LogExecutionTime
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  @Operation(summary = "List the assets that run a software, by PURL, CPE, product or vendor")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Matching components with their asset"),
        @ApiResponse(responseCode = "400", description = "No criterion given")
      })
  public List<AssetSoftwareOutput> searchAssetSoftware(
      TxCtx ctx,
      @RequestParam(required = false) final String purl,
      @RequestParam(required = false) final String cpe,
      @RequestParam(required = false) final String product,
      @RequestParam(required = false) final String vendor) {
    return assetSoftwareService.search(purl, cpe, product, vendor);
  }
}
