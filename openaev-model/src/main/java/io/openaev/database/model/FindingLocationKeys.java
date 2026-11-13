package io.openaev.database.model;

/**
 * Builds {@link FindingLocation#getLocationKey()} from the asset or user id.
 */
public final class FindingLocationKeys {

  private FindingLocationKeys() {}

  public static String keyOf(String assetId, String userId) {
    if (assetId != null) {
      return "asset:" + assetId;
    }
    if (userId != null) {
      return "user:" + userId;
    }
    return "unknown";
  }
}
