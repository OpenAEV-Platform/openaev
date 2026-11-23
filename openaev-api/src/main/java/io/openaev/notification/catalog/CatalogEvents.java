package io.openaev.notification.catalog;

import io.openaev.database.model.catalog.ContentType;
import java.util.List;

/** Catalogue events published in their transaction and notified once it commits. */
public final class CatalogEvents {

  private CatalogEvents() {}

  public record RefusedVersion(String name, String version, String reason) {}

  /** A version the release added; new when the catalogue knew no version of its content. */
  public record AddedVersion(
      ContentType type,
      String repository,
      String externalId,
      String name,
      String version,
      boolean isNew) {}

  public record CatalogReleaseApplied(
      String tenantId,
      String release,
      boolean firstSync,
      List<AddedVersion> added,
      List<RefusedVersion> refused) {}

  /** What a release installed by itself: payload versions by id, scenarios and failures by name. */
  public record InstalledAutomatically(
      String tenantId,
      String release,
      List<String> payloadIds,
      List<String> scenarios,
      List<String> failed) {

    public boolean isEmpty() {
      return payloadIds.isEmpty() && scenarios.isEmpty() && failed.isEmpty();
    }
  }

  /** A release refused as a whole: nothing of it was applied. */
  public record CatalogReleaseRefused(String tenantId, String release, String reason) {}
}
