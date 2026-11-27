package io.openaev.api.catalog.scenario;

import static io.openaev.api.catalog.CatalogRoutes.TENANT_CATALOG_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
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
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The catalogue scenarios of a tenant, installed or not, and their versions. */
@RestController
@RequirePreviewFeature(PreviewFeature.CATALOG)
@RequiredArgsConstructor
public class CatalogScenarioApi {

  private final CatalogListService catalogListService;
  private final CatalogScenarioOfferService catalogScenarioOfferService;
  private final TenantWriteScopeResolver writeScopeResolver;
  private final CatalogItemMapper catalogItemMapper;

  // -- READ --

  @Operation(summary = "Search catalogue scenarios, installed or not")
  @PostMapping(TENANT_CATALOG_URI + "/scenarios/search")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public Page<CatalogScenarioOutput> searchScenarios(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput input) {
    return catalogScenarioOfferService
        .search(writeScopeResolver.tenantForWrite(ctx, null), input)
        .map(catalogItemMapper::toOutput);
  }

  @LogExecutionTime
  @Operation(summary = "Facet counts of catalogue scenarios")
  @PostMapping(TENANT_CATALOG_URI + "/scenarios/facet-counts")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public Map<String, Map<String, Long>> scenarioFacetCounts(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput input) {
    return catalogScenarioOfferService.facetCounts(
        writeScopeResolver.tenantForWrite(ctx, null), input);
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
