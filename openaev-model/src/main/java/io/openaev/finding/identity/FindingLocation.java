package io.openaev.finding.identity;

import java.util.Objects;

/**
 * Where a finding was detected: an asset, a user (phishing), or nowhere known. Part of the finding
 * identity, along with its type and normalized value.
 */
public sealed interface FindingLocation {

  String ASSET_KEY_PREFIX = "asset:";
  String USER_KEY_PREFIX = "user:";
  String UNKNOWN_KEY = "unknown";

  /**
   * The location as a single non-null string, for the unique key of {@code findings}. Must stay
   * equal to the generated column {@code finding_location_key}: {@code COALESCE('asset:' ||
   * asset_id, 'user:' || user_id, 'unknown')}.
   */
  String key();

  /**
   * Builds the location from the two nullable location columns.
   *
   * @throws IllegalArgumentException when both are set: a finding has at most one location
   */
  static FindingLocation of(String assetId, String userId) {
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

  record AssetLocation(String assetId) implements FindingLocation {
    public AssetLocation {
      requireId(assetId, "assetId");
    }

    @Override
    public String key() {
      return ASSET_KEY_PREFIX + assetId;
    }
  }

  record UserLocation(String userId) implements FindingLocation {
    public UserLocation {
      requireId(userId, "userId");
    }

    @Override
    public String key() {
      return USER_KEY_PREFIX + userId;
    }
  }

  enum UnknownLocation implements FindingLocation {
    INSTANCE;

    @Override
    public String key() {
      return UNKNOWN_KEY;
    }
  }

  private static void requireId(String id, String name) {
    Objects.requireNonNull(id, name);
    if (id.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
