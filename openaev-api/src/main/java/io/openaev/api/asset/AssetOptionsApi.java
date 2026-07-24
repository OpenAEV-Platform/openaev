package io.openaev.api.asset;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.AssetService;
import io.openaev.utils.FilterUtilsJpa;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(
    name = "Asset options",
    description =
        "Filter options over the unified asset inventory - every asset category except security"
            + " platforms.")
public class AssetOptionsApi {

  public static final String ASSET_URI = "/api/assets";
  public static final String TENANT_ASSET_URI = TENANT_PREFIX + "/assets";

  private final AssetService assetService;

  // -- OPTIONS --

  /**
   * Filter options over the WHOLE asset inventory (every category except security platforms).
   * Findings can attach to any asset - agentless web applications included - so filter builders
   * such as notification trigger criteria must propose every asset, not only endpoints.
   */
  @LogExecutionTime
  @GetMapping({ASSET_URI + "/options", TENANT_ASSET_URI + "/options"})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  @Operation(
      summary = "Search asset options",
      description = "Name-based filter options over every asset category except security platforms")
  @Transactional(readOnly = true)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The matching options")})
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText) {
    return assetService.getOptionsByName(searchText, PageRequest.of(0, 50));
  }

  /** Resolve option labels for a set of asset ids, whatever the asset category. */
  @LogExecutionTime
  @PostMapping({ASSET_URI + "/options", TENANT_ASSET_URI + "/options"})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  @Operation(
      summary = "Resolve asset options",
      description = "Resolve filter option labels for a set of asset ids")
  @Transactional(readOnly = true)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The resolved options")})
  public List<FilterUtilsJpa.Option> optionsByIds(
      @RequestBody(required = false) final List<String> ids) {
    // A missing or empty body simply resolves to no options rather than a client error.
    return assetService.getOptionsByIds(ids);
  }
}
