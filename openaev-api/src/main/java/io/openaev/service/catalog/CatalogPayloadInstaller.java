package io.openaev.service.catalog;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Payload;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Installs catalogue payloads, reading each released file from storage outside any transaction,
 * then installing it in its own.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogPayloadInstaller {

  private final CatalogPayloadService catalogPayloadService;
  private final CatalogFileService catalogFileService;

  /** Installs one released version; returns the payload installed. */
  public Payload install(TxCtx ctx, String tenantId, String externalId, String version) {
    return install(ctx, tenantId, catalogPayloadService.installable(ctx, externalId, version));
  }

  /**
   * Installs the latest version of every catalogue payload the tenant never installed; a payload
   * that fails is logged and skipped.
   */
  public InstalledPayloads installAll(TxCtx ctx, String tenantId) {
    List<Payload> installed = new ArrayList<>();
    List<InstallablePayloadVersion> failed = new ArrayList<>();
    for (InstallablePayloadVersion released : catalogPayloadService.neverInstalled(ctx, tenantId)) {
      try {
        installed.add(install(ctx, tenantId, released));
      } catch (RuntimeException e) {
        log.warn(
            "Catalogue payload {} {} not installed", released.externalId(), released.version(), e);
        failed.add(released);
      }
    }
    return new InstalledPayloads(installed, failed);
  }

  private Payload install(TxCtx ctx, String tenantId, InstallablePayloadVersion released) {
    byte[] file = catalogFileService.storedFile(released.fileHash());
    return catalogPayloadService.install(ctx, tenantId, released, file);
  }
}
