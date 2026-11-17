package io.openaev.api.catalog;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.api.catalog.dto.payload.CatalogPayloadOutput;
import io.openaev.api.catalog.dto.scenario.CatalogScenarioOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.catalog.CatalogListService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The content catalogue of the tenant, as the collector released it. */
@RestController
@RequiredArgsConstructor
public class CatalogApi {

  public static final String CATALOG_URI = "/api/catalog";
  public static final String TENANT_CATALOG_URI = TENANT_PREFIX + "/catalog";

  private final CatalogListService catalogListService;
  private final CatalogFeature catalogFeature;

  // -- READ --

  @Operation(summary = "Catalogue scenarios, latest version first")
  @GetMapping(TENANT_CATALOG_URI + "/scenarios")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public List<CatalogScenarioOutput> scenarios(TxCtx ctx) {
    catalogFeature.require();
    return catalogListService.scenarios();
  }

  @Operation(summary = "Catalogue payloads, latest version first")
  @GetMapping(TENANT_CATALOG_URI + "/payloads")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public List<CatalogPayloadOutput> payloads(TxCtx ctx) {
    catalogFeature.require();
    return catalogListService.payloads();
  }
}
