package io.openaev.service.catalog.payload;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Payload;
import io.openaev.database.model.catalog.ContentChange;
import io.openaev.rest.payload.form.PayloadUpsertInput;
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
        ContentChange.Mode.MANUAL);
  }

  /** What installing a released version would add, read from its file. */
  public PayloadUpsertInput releasedContent(TxCtx ctx, String externalId, String version) {
    InstallablePayloadVersion released =
        catalogPayloadOfferService.installable(ctx, externalId, version);
    return catalogPayloadService.read(catalogFileService.storedFile(released.fileHash()));
  }

  @Override
  protected CatalogOffer<InstallablePayloadVersion> offer() {
    return catalogPayloadOfferService;
  }

  @Override
  protected Payload install(
      TxCtx ctx, String tenantId, InstallablePayloadVersion released, ContentChange.Mode mode) {
    byte[] file = catalogFileService.storedFile(released.fileHash());
    return catalogPayloadService.install(ctx, tenantId, released, file, mode);
  }
}
