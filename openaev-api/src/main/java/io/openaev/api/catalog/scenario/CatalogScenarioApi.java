package io.openaev.api.catalog.scenario;

import static io.openaev.api.catalog.CatalogRoutes.TENANT_CATALOG_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.RequirePreviewFeature;
import io.openaev.api.catalog.CatalogItemMapper;
import io.openaev.api.catalog.dto.scenario.CatalogScenarioOutput;
import io.openaev.api.catalog.dto.scenario.CatalogScenarioVersionOutput;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.catalog.CatalogListService;
import io.openaev.service.catalog.scenario.CatalogScenarioOfferService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** The catalogue scenarios a tenant has not installed, and their versions. */
@RestController
@RequirePreviewFeature(PreviewFeature.CATALOG)
@RequiredArgsConstructor
public class CatalogScenarioApi {

  private final CatalogListService catalogListService;
  private final CatalogScenarioOfferService catalogScenarioOfferService;
  private final TenantWriteScopeResolver writeScopeResolver;
  private final CatalogItemMapper catalogItemMapper;

  // -- READ --

  @Operation(summary = "Catalogue scenarios not installed, latest version")
  @GetMapping(TENANT_CATALOG_URI + "/scenarios")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public List<CatalogScenarioOutput> scenarios(TxCtx ctx) {
    return catalogScenarioOfferService
        .notInstalled(writeScopeResolver.tenantForWrite(ctx, null))
        .stream()
        .map(catalogItemMapper::toOutput)
        .toList();
  }

  @Operation(summary = "Offered versions of a catalogue scenario, newest first")
  @GetMapping(TENANT_CATALOG_URI + "/scenarios/{externalId}/versions")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public List<CatalogScenarioVersionOutput> scenarioVersions(
      TxCtx ctx, @PathVariable String externalId) {
    return catalogListService.offeredVersions(CatalogScenario.class, externalId).stream()
        .map(catalogItemMapper::toVersionOutput)
        .toList();
  }
}
