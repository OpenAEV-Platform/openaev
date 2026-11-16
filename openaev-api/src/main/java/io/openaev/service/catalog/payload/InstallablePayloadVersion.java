package io.openaev.service.catalog.payload;

import io.openaev.database.model.catalog.CatalogPayload;

/** A released payload version to install, read before its file is. */
public record InstallablePayloadVersion(
    String repository, String externalId, String version, String contentHash, String fileHash) {

  static InstallablePayloadVersion of(CatalogPayload item) {
    return new InstallablePayloadVersion(
        item.getRelease().getRepository(),
        item.getExternalId(),
        item.getVersion(),
        item.getContentHash(),
        item.getArchiveHash());
  }
}
