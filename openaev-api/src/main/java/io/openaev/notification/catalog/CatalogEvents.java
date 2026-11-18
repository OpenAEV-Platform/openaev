package io.openaev.notification.catalog;

import java.util.List;

/** Catalogue events published in their transaction and notified once it commits. */
public final class CatalogEvents {

  private CatalogEvents() {}

  public record RefusedVersion(String name, String version, String reason) {}

  public record CatalogReleaseApplied(
      String tenantId, String release, List<RefusedVersion> refused) {}

  /** A release refused as a whole: nothing of it was applied. */
  public record CatalogReleaseRefused(String tenantId, String release, String reason) {}
}
