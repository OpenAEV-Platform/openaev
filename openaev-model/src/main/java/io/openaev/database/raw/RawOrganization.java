package io.openaev.database.raw;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data projection interface for organization data.
 *
 * <p>This interface defines a projection for retrieving organization information including
 * metadata, associated tags, and inject statistics. Organizations represent logical groupings of
 * users and teams within the platform.
 *
 * @see io.openaev.database.model.Organization
 */
@SuppressWarnings("unused")
public interface RawOrganization {

  /**
   * Returns the unique identifier of the organization.
   *
   * @return the organization ID
   */
  String getOrganization_id();

  /**
   * Returns the display name of the organization.
   *
   * @return the organization name
   */
  String getOrganization_name();

  /**
   * Returns the description of the organization.
   *
   * @return the organization description, or {@code null} if not set
   */
  String getOrganization_description();

  /**
   * Returns the creation timestamp of the organization.
   *
   * @return the creation timestamp
   */
  Instant getOrganization_created_at();

  /**
   * Returns the last update timestamp of the organization.
   *
   * @return the update timestamp
   */
  Instant getOrganization_updated_at();

  /**
   * Returns the list of tag IDs associated with this organization.
   *
   * @return list of tag IDs
   */
  List<String> getOrganization_tags();

  /**
   * Returns the list of inject IDs associated with this organization.
   *
   * @return list of inject IDs
   */
  List<String> getOrganization_injects();

  /**
   * Returns the count of injects associated with this organization.
   *
   * @return the inject count
   */
  long getOrganization_injects_number();
}
