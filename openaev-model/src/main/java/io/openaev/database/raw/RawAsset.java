package io.openaev.database.raw;

import java.time.Instant;
import java.util.Set;

/**
 * Spring Data projection interface for asset data.
 *
 * <p>This interface defines a projection for retrieving asset information including metadata,
 * timestamps, and associated relationships. It serves as a base projection that can be extended for
 * specific asset types like endpoints.
 *
 * @see io.openaev.database.model.Asset
 * @see RawEndpoint
 */
public interface RawAsset {

  /**
   * Returns the unique identifier of the asset.
   *
   * @return the asset ID
   */
  String getAsset_id();

  /**
   * Returns the type discriminator for the asset.
   *
   * @return the asset type (e.g., "Endpoint", "SecurityPlatform")
   */
  String getAsset_type();

  /**
   * Returns the display name of the asset.
   *
   * @return the asset name
   */
  String getAsset_name();

  /**
   * Returns the description of the asset.
   *
   * @return the asset description, or {@code null} if not set
   */
  String getAsset_description();

  /**
   * Returns the external reference identifier for integration with other systems.
   *
   * @return the external reference, or {@code null} if not set
   */
  String getAsset_external_reference();

  /**
   * Returns the platform type for endpoint assets.
   *
   * @return the platform type (e.g., "Linux", "Windows", "macOS")
   */
  String getEndpoint_platform();

  /**
   * Returns the creation timestamp of the asset.
   *
   * @return the creation timestamp
   */
  Instant getAsset_created_at();

  /**
   * Returns the last update timestamp of the asset.
   *
   * @return the update timestamp
   */
  Instant getAsset_updated_at();

  /**
   * Returns the set of finding IDs associated with this asset.
   *
   * @return set of associated finding IDs
   */
  Set<String> getAsset_findings();

  /**
   * Returns the set of tag IDs associated with this asset.
   *
   * @return set of associated tag IDs
   */
  Set<String> getAsset_tags();
}
