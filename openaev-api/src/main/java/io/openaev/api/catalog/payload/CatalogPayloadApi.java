package io.openaev.api.catalog.payload;

import static io.openaev.api.catalog.CatalogRoutes.TENANT_CATALOG_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RequirePreviewFeature;
import io.openaev.api.catalog.CatalogItemMapper;
import io.openaev.api.catalog.dto.payload.CatalogPayloadOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.rest.payload.form.PayloadUpsertInput;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.catalog.CatalogListService;
import io.openaev.service.catalog.payload.CatalogPayloadInstaller;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** The released versions of a catalogue payload, and their content. */
@RestController
@RequirePreviewFeature(PreviewFeature.CATALOG)
@RequiredArgsConstructor
public class CatalogPayloadApi {

  private final CatalogListService catalogListService;
  private final CatalogPayloadInstaller catalogPayloadInstaller;
  private final CatalogItemMapper catalogItemMapper;

  // -- READ --

  @Operation(summary = "Offered versions of a catalogue payload, newest first")
  @GetMapping(TENANT_CATALOG_URI + "/payloads/{externalId}/versions")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public List<CatalogPayloadOutput> payloadVersions(TxCtx ctx, @PathVariable String externalId) {
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
    return catalogPayloadInstaller.releasedContent(ctx, externalId, version);
  }
}
