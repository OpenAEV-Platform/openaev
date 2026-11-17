package io.openaev.database.model.catalog;

public enum CatalogItemStatus {
  VERIFIED,
  DEPRECATED,
  REVOKED;

  public static CatalogItemStatus fromManifest(String status) {
    return status == null ? VERIFIED : valueOf(status.strip().toUpperCase());
  }

  public boolean isOffered() {
    return this == VERIFIED;
  }
}
