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
import io.openaev.service.catalog.AvailableActions;
import io.openaev.service.catalog.CatalogListService;
import io.openaev.service.catalog.payload.CatalogPayloadInstaller;
import io.openaev.service.catalog.scenario.CatalogScenarioOfferService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

  @Operation(summary = "Catalogue scenarios not installed, latest version")
  @GetMapping(TENANT_CATALOG_URI + "/scenarios")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public List<CatalogScenarioOutput> scenarios(TxCtx ctx) {
    catalogFeature.require();
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
    catalogFeature.require();
    return catalogListService.offeredVersions(CatalogScenario.class, externalId).stream()
        .map(catalogItemMapper::toVersionOutput)
        .toList();
  }

  @Operation(summary = "Catalogue actions the tenant does not have yet")
  @GetMapping(TENANT_CATALOG_URI + "/actions")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public List<AvailableActionOutput> actions(TxCtx ctx) {
    catalogFeature.require();
    AvailableActions available =
        availableActionService.available(writeScopeResolver.tenantForWrite(ctx, null));
    return Stream.concat(
            available.payloads().stream().map(AvailableActionMapper::toOutput),
            available.contracts().stream().map(AvailableActionMapper::toOutput))
        .toList();
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
