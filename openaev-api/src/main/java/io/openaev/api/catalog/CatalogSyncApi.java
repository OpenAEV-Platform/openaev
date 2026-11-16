package io.openaev.api.catalog;

import static io.openaev.api.catalog.CatalogApi.CATALOG_URI;
import static io.openaev.api.catalog.CatalogApi.TENANT_CATALOG_URI;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.AccessControl;
import io.openaev.api.catalog.dto.CatalogManifest;
import io.openaev.api.catalog.dto.CatalogSyncOutput;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.catalog.CatalogFileService;
import io.openaev.service.catalog.CatalogSyncService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Receives the catalogue releases the collector pushes. */
@RestController
@RequiredArgsConstructor
public class CatalogSyncApi {

  private final CatalogFileService catalogFileService;
  private final CatalogSyncService catalogSyncService;
  private final TenantWriteScopeResolver writeScopeResolver;
  private final ObjectMapper mapper;
  private final CatalogFeature catalogFeature;

  // -- CREATE --

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
    catalogFeature.require();
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
}
