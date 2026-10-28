package io.openaev.api.catalog;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ContentType;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.catalog.CatalogInstallService;
import io.openaev.service.catalog.CatalogPayloadService;
import io.openaev.service.catalog.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** The content catalogue (F16): releases pushed by the collector, browsed in the Available tabs. */
@RestController
@RequiredArgsConstructor
public class CatalogApi {

  public static final String CATALOG_URI = "/api/catalog";
  public static final String TENANT_CATALOG_URI = TENANT_PREFIX + "/catalog";

  private final CatalogService catalogService;
  private final CatalogInstallService catalogInstallService;
  private final CatalogPayloadService catalogPayloadService;
  private final TenantWriteScopeResolver writeScopeResolver;
  private final ObjectMapper mapper;

  // -- READ --

  @LogExecutionTime
  @Operation(summary = "Catalogue scenarios, latest version first, with what the tenant installed")
  @GetMapping({CATALOG_URI + "/scenarios", TENANT_CATALOG_URI + "/scenarios"})
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public List<CatalogItemOutput> scenarios(TxCtx ctx) {
    return catalogService.list(ContentType.SCENARIO);
  }

  @LogExecutionTime
  @Operation(summary = "Catalogue payloads, latest version first, with what the tenant installed")
  @GetMapping({CATALOG_URI + "/payloads", TENANT_CATALOG_URI + "/payloads"})
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public List<CatalogItemOutput> payloads(TxCtx ctx) {
    return catalogService.list(ContentType.PAYLOAD);
  }

  @LogExecutionTime
  @Operation(summary = "Every catalogue version of a payload, newest first")
  @GetMapping({
    CATALOG_URI + "/payloads/{externalId}/versions",
    TENANT_CATALOG_URI + "/payloads/{externalId}/versions"
  })
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public List<CatalogItemOutput> payloadVersions(TxCtx ctx, @PathVariable String externalId) {
    return catalogService.listPayloadVersions(externalId);
  }

  @LogExecutionTime
  @Operation(summary = "What installing this scenario version needs: injects and their targets")
  @GetMapping({
    CATALOG_URI + "/scenarios/{externalId}/versions/{version}/install-preview",
    TENANT_CATALOG_URI + "/scenarios/{externalId}/versions/{version}/install-preview"
  })
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.SCENARIO)
  public CatalogInstallPreviewOutput installPreview(
      TxCtx ctx, @PathVariable String externalId, @PathVariable String version) {
    return catalogInstallService.preview(externalId, version);
  }

  @LogExecutionTime
  @Operation(summary = "Releases of an installed catalogue scenario and its history")
  @GetMapping({
    CATALOG_URI + "/installed-scenarios/{scenarioId}/versions",
    TENANT_CATALOG_URI + "/installed-scenarios/{scenarioId}/versions"
  })
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public CatalogScenarioVersionsOutput versions(TxCtx ctx, @PathVariable String scenarioId) {
    return catalogInstallService.versions(scenarioId);
  }

  @LogExecutionTime
  @Operation(summary = "Target state of each inject of a scenario")
  @GetMapping({
    CATALOG_URI + "/installed-scenarios/{scenarioId}/targets",
    TENANT_CATALOG_URI + "/installed-scenarios/{scenarioId}/targets"
  })
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public ScenarioTargetsOutput targets(TxCtx ctx, @PathVariable String scenarioId) {
    return catalogInstallService.targets(scenarioId);
  }

  @LogExecutionTime
  @Operation(summary = "Catalogue settings of the tenant")
  @GetMapping({CATALOG_URI + "/settings", TENANT_CATALOG_URI + "/settings"})
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.THREAT_ARSENAL)
  public CatalogSettingsOutput settings(TxCtx ctx) {
    return new CatalogSettingsOutput(
        catalogPayloadService.isAutoInstall(writeScopeResolver.tenantForWrite(ctx, null)));
  }

  // -- CREATE --

  @LogExecutionTime
  @Operation(
      summary = "Install a catalogue payload version",
      description =
          "A single identical local copy claiming the external ID becomes the version; otherwise"
              + " the package is imported, refused unless its content has the release hash. The"
              + " repository is needed only when several repositories release the external ID.")
  @PostMapping({
    CATALOG_URI + "/payloads/{externalId}/versions/{version}/install",
    TENANT_CATALOG_URI + "/payloads/{externalId}/versions/{version}/install"
  })
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.THREAT_ARSENAL)
  @Transactional(rollbackFor = Exception.class)
  public CatalogPayloadInstallOutput installPayload(
      TxCtx ctx,
      @PathVariable String externalId,
      @PathVariable String version,
      @RequestParam(required = false) String repository)
      throws Exception {
    catalogPayloadService.install(
        ctx, writeScopeResolver.tenantForWrite(ctx, null), repository, externalId, version);
    return new CatalogPayloadInstallOutput(List.of(externalId));
  }

  @LogExecutionTime
  @Operation(summary = "Install the latest version of every catalogue payload not installed yet")
  @PostMapping({
    CATALOG_URI + "/payloads/install-all",
    TENANT_CATALOG_URI + "/payloads/install-all"
  })
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.THREAT_ARSENAL)
  @Transactional(rollbackFor = Exception.class)
  public CatalogPayloadInstallOutput installAllPayloads(TxCtx ctx) throws Exception {
    return new CatalogPayloadInstallOutput(
        catalogPayloadService.installAll(ctx, writeScopeResolver.tenantForWrite(ctx, null)));
  }

  @LogExecutionTime
  @Operation(
      summary = "Install a catalogue scenario version",
      description =
          "Installed next to the versions already there. Injects keep the targets of the last"
              + " installed version; the others get the chosen ones, and none may be left without.")
  @PostMapping({
    CATALOG_URI + "/scenarios/{externalId}/versions/{version}/install",
    TENANT_CATALOG_URI + "/scenarios/{externalId}/versions/{version}/install"
  })
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackFor = Exception.class)
  public CatalogInstallOutput install(
      TxCtx ctx,
      @PathVariable String externalId,
      @PathVariable String version,
      @RequestBody(required = false) CatalogInstallInput input)
      throws Exception {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    return catalogInstallService.install(ctx, tenantId, externalId, version, input);
  }

  // -- UPDATE --

  @LogExecutionTime
  @Operation(summary = "Turn the automatic install of new catalogue payloads on or off")
  @PutMapping({CATALOG_URI + "/settings", TENANT_CATALOG_URI + "/settings"})
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.TENANT_SETTING)
  @Transactional(rollbackFor = Exception.class)
  public CatalogSettingsOutput updateSettings(
      TxCtx ctx, @Valid @RequestBody CatalogSettingsInput input) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    catalogPayloadService.setAutoInstall(tenantId, input.payloadAutoInstall());
    return new CatalogSettingsOutput(catalogPayloadService.isAutoInstall(tenantId));
  }

  @LogExecutionTime
  @Operation(
      summary = "Give targets to the injects that have none",
      description = "Refused while an inject would stay without a target.")
  @PutMapping({
    CATALOG_URI + "/installed-scenarios/{scenarioId}/targets",
    TENANT_CATALOG_URI + "/installed-scenarios/{scenarioId}/targets"
  })
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public ScenarioTargetsOutput completeTargets(
      TxCtx ctx, @PathVariable String scenarioId, @RequestBody CatalogInstallInput input) {
    return catalogInstallService.completeTargets(scenarioId, input);
  }

  // -- DELETE --

  @LogExecutionTime
  @Operation(
      summary = "Uninstall a catalogue payload",
      description =
          "Refused while a scenario, atomic test or chained step uses one of its versions. Its"
              + " versions are hidden, not deleted, and new ones no longer install themselves.")
  @DeleteMapping({
    CATALOG_URI + "/installed-payloads/{payloadId}",
    TENANT_CATALOG_URI + "/installed-payloads/{payloadId}"
  })
  @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.THREAT_ARSENAL)
  @Transactional(rollbackFor = Exception.class)
  public void uninstallPayload(TxCtx ctx, @PathVariable String payloadId) {
    catalogPayloadService.uninstallByPayload(payloadId);
  }

  @LogExecutionTime
  @Operation(summary = "Uninstall a catalogue scenario version; its payload versions stay")
  @DeleteMapping({
    CATALOG_URI + "/installed-scenarios/{scenarioId}",
    TENANT_CATALOG_URI + "/installed-scenarios/{scenarioId}"
  })
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.SCENARIO)
  @Transactional(rollbackFor = Exception.class)
  public void uninstall(TxCtx ctx, @PathVariable String scenarioId) {
    catalogInstallService.uninstall(scenarioId);
  }

  // -- SYNC --

  @LogExecutionTime
  @Operation(
      summary = "Receive a content repository release",
      description =
          "Sent by the collector: the release manifest and its archives. Each archive is checked"
              + " against the manifest hash and for safe extraction; a failing item is refused"
              + " alone. The same release twice changes nothing.")
  @PostMapping(
      value = {CATALOG_URI + "/sync", TENANT_CATALOG_URI + "/sync"},
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.CATALOG_SYNC)
  @Transactional(rollbackFor = Exception.class)
  public CatalogSyncOutput sync(
      TxCtx ctx,
      @RequestPart("manifest") MultipartFile manifestFile,
      @RequestPart(value = "archives", required = false) List<MultipartFile> archiveFiles)
      throws Exception {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    CatalogManifest manifest;
    try {
      manifest = mapper.readValue(manifestFile.getBytes(), CatalogManifest.class);
    } catch (Exception e) {
      throw new BadRequestException("Unreadable manifest");
    }
    if (manifest.repository() == null || manifest.release() == null) {
      throw new BadRequestException("The manifest needs a repository and a release");
    }
    Map<String, byte[]> archives = new HashMap<>();
    for (MultipartFile file : archiveFiles == null ? List.<MultipartFile>of() : archiveFiles) {
      archives.put(file.getOriginalFilename(), file.getBytes());
    }
    Map<String, String> refusals = catalogService.verifyAndStore(manifest, archives);
    CatalogSyncOutput output = catalogService.apply(ctx, tenantId, manifest, archives, refusals);
    if (output.applied()) {
      catalogPayloadService.afterSync(ctx, tenantId, manifest);
    }
    return output;
  }
}
