package io.openaev.api.catalog;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.AccessControl;
import io.openaev.api.catalog.dto.CatalogManifest;
import io.openaev.api.catalog.dto.CatalogPayloadOutput;
import io.openaev.api.catalog.dto.CatalogScenarioOutput;
import io.openaev.api.catalog.dto.CatalogSyncOutput;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.catalog.CatalogFileService;
import io.openaev.service.catalog.CatalogListService;
import io.openaev.service.catalog.CatalogSyncService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** The content catalogue: releases pushed by the collector, listed for the tenant. */
@RestController
@RequiredArgsConstructor
public class CatalogApi {

  public static final String CATALOG_URI = "/api/catalog";
  public static final String TENANT_CATALOG_URI = TENANT_PREFIX + "/catalog";

  private final CatalogListService catalogListService;
  private final CatalogFileService catalogFileService;
  private final CatalogSyncService catalogSyncService;
  private final TenantWriteScopeResolver writeScopeResolver;
  private final ObjectMapper mapper;
  private final PreviewFeatureService previewFeatureService;

  // -- READ --

  @Operation(summary = "Catalogue scenarios, latest version first")
  @GetMapping(TENANT_CATALOG_URI + "/scenarios")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public List<CatalogScenarioOutput> scenarios(TxCtx ctx) {
    requireCatalog();
    return catalogListService.scenarios();
  }

  @Operation(summary = "Catalogue payloads, latest version first")
  @GetMapping(TENANT_CATALOG_URI + "/payloads")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public List<CatalogPayloadOutput> payloads(TxCtx ctx) {
    requireCatalog();
    return catalogListService.payloads();
  }

  // -- SYNC --

  @Operation(
      summary = "Receive a catalogue release from the collector",
      description = "Refused items or releases send a Sync blocked notification event.")
  @PostMapping(
      value = {CATALOG_URI + "/sync", TENANT_CATALOG_URI + "/sync"},
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.CATALOG_SYNC)
  @Transactional(propagation = Propagation.SUPPORTS)
  public CatalogSyncOutput sync(
      TxCtx ctx,
      @RequestPart("manifest") MultipartFile manifestFile,
      @RequestPart(value = "archives", required = false) List<MultipartFile> archiveFiles)
      throws Exception {
    requireCatalog();
    String writeTenantId = writeScopeResolver.tenantForWrite(ctx, null);
    CatalogManifest manifest = readManifest(manifestFile);
    if (manifest == null) {
      catalogSyncService.refuse(writeTenantId, null, "Unreadable manifest");
      throw new BadRequestException("Unreadable manifest");
    }
    return catalogSyncService.sync(
        ctx,
        writeTenantId,
        manifest,
        catalogFileService.read(manifest, archiveFiles == null ? List.of() : archiveFiles));
  }

  private CatalogManifest readManifest(MultipartFile manifestFile) {
    try {
      return mapper.readValue(manifestFile.getBytes(), CatalogManifest.class);
    } catch (Exception e) {
      return null;
    }
  }

  private void requireCatalog() {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.CATALOG)) {
      throw new ElementNotFoundException("The catalogue feature is disabled");
    }
  }
}
