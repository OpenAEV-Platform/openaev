package io.openaev.notification.content;

import java.util.List;

/** Content changes published in their transaction and notified once it commits. */
public final class ContentNotificationEvents {

  private ContentNotificationEvents() {}

  public record PayloadVersionAdded(String tenantId, String payloadId) {}

  public record PayloadInstalledAutomatically(String tenantId, String payloadId, String release) {}

  public record PayloadRevocationChanged(
      String tenantId, String payloadId, boolean revoked, String reason) {}

  public record ListedVersion(String externalId, String version, String reason) {}

  /** A revocation list applies to every tenant. */
  public record ListRevocationsChanged(
      String repository, List<ListedVersion> revoked, List<ListedVersion> lifted) {}

  public record Refused(String name, String version, String reason) {}

  public record CatalogReleaseApplied(
      String tenantId,
      String release,
      boolean firstSync,
      List<String> addedItemIds,
      List<Refused> refused) {}
}
