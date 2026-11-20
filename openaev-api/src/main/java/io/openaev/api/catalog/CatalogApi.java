package io.openaev.api.catalog;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.catalog.dto.action.AvailableActionOutput;
import io.openaev.api.catalog.dto.payload.CatalogPayloadOutput;
import io.openaev.api.catalog.dto.scenario.CatalogScenarioOutput;
import io.openaev.api.catalog.dto.scenario.CatalogScenarioVersionOutput;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.rest.payload.form.PayloadUpsertInput;
import io.openaev.service.catalog.AvailableActionService;
import io.openaev.service.catalog.CatalogListService;
import io.openaev.service.catalog.payload.CatalogPayloadInstaller;
import io.openaev.service.catalog.scenario.CatalogScenarioOfferService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The content catalogue of the tenant, as the collector released it. */
@RestController
@RequiredArgsConstructor
public class CatalogApi {

  public static final String CATALOG_URI = "/api/catalog";
  public static final String TENANT_CATALOG_URI = TENANT_PREFIX + "/catalog";

  private final CatalogListService catalogListService;
  private final CatalogPayloadInstaller catalogPayloadInstaller;
  private final AvailableActionService availableActionService;
  private final CatalogScenarioOfferService catalogScenarioOfferService;
  private final TenantWriteScopeResolver writeScopeResolver;
  private final CatalogFeature catalogFeature;
  private final CatalogItemMapper catalogItemMapper;

  // -- READ --

  @Operation(summary = "Search catalogue scenarios not installed")
  @PostMapping(TENANT_CATALOG_URI + "/scenarios/search")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public Page<CatalogScenarioOutput> searchScenarios(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput input) {
    catalogFeature.require();
    return catalogScenarioOfferService
        .searchNotInstalled(writeScopeResolver.tenantForWrite(ctx, null), input)
        .map(catalogItemMapper::toOutput);
  }

  @LogExecutionTime
  @Operation(summary = "Facet counts of catalogue scenarios not installed")
  @PostMapping(TENANT_CATALOG_URI + "/scenarios/facet-counts")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public Map<String, Map<String, Long>> scenarioFacetCounts(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput input) {
    catalogFeature.require();
    return catalogScenarioOfferService.notInstalledFacetCounts(
        writeScopeResolver.tenantForWrite(ctx, null), input);
  }

  @Operation(summary = "Offered versions of a catalogue scenario, newest first")
  @GetMapping(TENANT_CATALOG_URI + "/scenarios/{externalId}/versions")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public List<CatalogScenarioVersionOutput> scenarioVersions(
      TxCtx ctx, @PathVariable String externalId) {
    catalogFeature.require();
    return catalogListService.offeredVersions(CatalogScenario.class, externalId).stream()
        .map(catalogItemMapper::toVersionOutput)
        .toList();
  }

  @Operation(summary = "Search catalogue actions the tenant does not have")
  @PostMapping(TENANT_CATALOG_URI + "/actions/search")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public Page<AvailableActionOutput> searchActions(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput input) {
    catalogFeature.require();
    return availableActionService
        .search(writeScopeResolver.tenantForWrite(ctx, null), input)
        .<AvailableActionOutput>map(
            AvailableActionMapper::toOutput, AvailableActionMapper::toOutput);
  }

  @LogExecutionTime
  @Operation(summary = "Facet counts of catalogue actions the tenant does not have")
  @PostMapping(TENANT_CATALOG_URI + "/actions/facet-counts")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public Map<String, Map<String, Long>> actionFacetCounts(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput input) {
    catalogFeature.require();
    return availableActionService.facetCounts(writeScopeResolver.tenantForWrite(ctx, null), input);
  }

  @Operation(summary = "Offered versions of a catalogue payload, newest first")
  @GetMapping(TENANT_CATALOG_URI + "/payloads/{externalId}/versions")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public List<CatalogPayloadOutput> payloadVersions(TxCtx ctx, @PathVariable String externalId) {
    catalogFeature.require();
    return catalogListService.offeredVersions(CatalogPayload.class, externalId).stream()
        .map(catalogItemMapper::toOutput)
        .toList();
  }

  @LogExecutionTime
  @Operation(summary = "Released content of a catalogue payload version")
  @GetMapping(TENANT_CATALOG_URI + "/payloads/{externalId}/versions/{version}/content")
  @Transactional(propagation = Propagation.SUPPORTS)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public PayloadUpsertInput payloadVersionContent(
      TxCtx ctx, @PathVariable String externalId, @PathVariable String version) {
    catalogFeature.require();
    return catalogPayloadInstaller.releasedContent(ctx, externalId, version);
  }
}
