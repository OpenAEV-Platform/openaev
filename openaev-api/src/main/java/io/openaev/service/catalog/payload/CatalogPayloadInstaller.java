package io.openaev.service.catalog.payload;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Payload;
import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.service.catalog.CatalogFileService;
import io.openaev.service.catalog.CatalogInstaller;
import io.openaev.service.catalog.CatalogOffer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Installs catalogue payloads, each version in a transaction of its own. */
@Service
@RequiredArgsConstructor
public class CatalogPayloadInstaller extends CatalogInstaller<InstallablePayloadVersion, Payload> {

  private final CatalogPayloadOfferService catalogPayloadOfferService;
  private final CatalogPayloadService catalogPayloadService;
  private final CatalogFileService catalogFileService;

  /** Installs one released version; returns the payload installed. */
  public Payload install(TxCtx ctx, String tenantId, String externalId, String version) {
    return install(
        ctx,
        tenantId,
        catalogPayloadOfferService.installable(ctx, externalId, version),
        CatalogHistory.Mode.MANUAL);
  }

  @Override
  protected CatalogOffer<InstallablePayloadVersion> offer() {
    return catalogPayloadOfferService;
  }

  @Override
  protected Payload install(
      TxCtx ctx, String tenantId, InstallablePayloadVersion released, CatalogHistory.Mode mode) {
    byte[] file = catalogFileService.storedFile(released.fileHash());
    return catalogPayloadService.install(ctx, tenantId, released, file, mode);
  }
}
