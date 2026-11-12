package io.openaev.api.catalog.payload;

import static io.openaev.api.catalog.CatalogApi.TENANT_CATALOG_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.catalog.CatalogFeature;
import io.openaev.api.catalog.CatalogItemMapper;
import io.openaev.api.catalog.dto.CatalogInstallAllOutput;
import io.openaev.api.catalog.dto.CatalogInstallOutput;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Payload;
import io.openaev.database.model.ResourceType;
import io.openaev.service.catalog.payload.CatalogPayloadInstaller;
import io.openaev.service.catalog.payload.CatalogPayloadService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Adds catalogue payloads to the tenant's Threat Arsenal, and takes them out. */
@RestController
@RequiredArgsConstructor
public class CatalogPayloadInstallApi {

  private final CatalogPayloadInstaller catalogPayloadInstaller;
  private final CatalogPayloadService catalogPayloadService;
  private final TenantWriteScopeResolver writeScopeResolver;
  private final CatalogFeature catalogFeature;
  private final CatalogItemMapper catalogItemMapper;

  // -- CREATE --

  @Operation(summary = "Install a catalogue payload version")
  @PostMapping(TENANT_CATALOG_URI + "/payloads/{externalId}/versions/{version}/install")
  @ResponseStatus(HttpStatus.CREATED)
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.THREAT_ARSENAL)
  @Transactional(propagation = Propagation.SUPPORTS)
  public CatalogInstallOutput installPayload(
      TxCtx ctx, @PathVariable String externalId, @PathVariable String version) {
    catalogFeature.require();
    Payload payload =
        catalogPayloadInstaller.install(
            ctx, writeScopeResolver.tenantForWrite(ctx, null), externalId, version);
    return new CatalogInstallOutput(payload.getId());
  }

  @LogExecutionTime
  @Operation(summary = "Install every catalogue payload never installed")
  @PostMapping(TENANT_CATALOG_URI + "/payloads/install-all")
  @ResponseStatus(HttpStatus.CREATED)
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.THREAT_ARSENAL)
  @Transactional(propagation = Propagation.SUPPORTS)
  public CatalogInstallAllOutput installAllPayloads(TxCtx ctx) {
    catalogFeature.require();
    return catalogItemMapper.toOutput(
        catalogPayloadInstaller.installAll(ctx, writeScopeResolver.tenantForWrite(ctx, null)));
  }

  // -- DELETE --

  @Operation(
      summary = "Uninstall a catalogue payload",
      description =
          "Refused while injects run one of its versions; its versions are hidden, not deleted.")
  @DeleteMapping(TENANT_CATALOG_URI + "/installed-payloads/{payloadId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.THREAT_ARSENAL)
  @Transactional(propagation = Propagation.SUPPORTS)
  public void uninstallPayload(TxCtx ctx, @PathVariable String payloadId) {
    catalogFeature.require();
    catalogPayloadService.uninstall(ctx, writeScopeResolver.tenantForWrite(ctx, null), payloadId);
  }
}
