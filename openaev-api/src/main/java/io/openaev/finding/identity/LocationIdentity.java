package io.openaev.finding.identity;

import java.util.Objects;

/**
 * Where a finding was detected: an asset, a user (phishing), or nowhere known. Part of the finding
 * identity, along with its type and normalized value.
 */
public sealed interface LocationIdentity {

  /**
   * Builds the location from the two nullable location columns.
   *
   * @throws IllegalArgumentException when both are set: a finding has at most one location
   */
  static LocationIdentity of(String assetId, String userId) {
    if (assetId != null && userId != null) {
      throw new IllegalArgumentException("A finding has at most one location");
    }
    if (assetId != null) {
      return new AssetLocation(assetId);
    }
    if (userId != null) {
      return new UserLocation(userId);
    }
    return UnknownLocation.INSTANCE;
  }

  record AssetLocation(String assetId) implements LocationIdentity {
    public AssetLocation {
      requireId(assetId, "assetId");
    }
  }

  record UserLocation(String userId) implements LocationIdentity {
    public UserLocation {
      requireId(userId, "userId");
    }
  }

  enum UnknownLocation implements LocationIdentity {
    INSTANCE
  }

  private static void requireId(String id, String name) {
    Objects.requireNonNull(id, name);
    if (id.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
