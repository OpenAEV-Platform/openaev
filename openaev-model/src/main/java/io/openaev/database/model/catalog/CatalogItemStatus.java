package io.openaev.database.model.catalog;

public enum CatalogItemStatus {
  VERIFIED,
  DEPRECATED,
  REVOKED,
  // No longer in a release, kept while a scenario of the tenant still runs it.
  UNLISTED;

  public static CatalogItemStatus fromManifest(String status) {
    return status == null ? VERIFIED : valueOf(status.strip().toUpperCase());
  }

  public boolean isOffered() {
    return this == VERIFIED;
  }
}
