package io.openaev.database.raw;

import io.openaev.database.model.Grant;

/**
 * Spring Data projection interface for grant (permission) data.
 *
 * <p>This interface defines a projection for retrieving grant information that links users to
 * resources with specific permission levels. Grants are part of the role-based access control
 * (RBAC) system.
 *
 * @see io.openaev.database.model.Grant
 */
public interface RawGrant {

  /**
   * Returns the unique identifier of the grant.
   *
   * @return the grant ID
   */
  String getGrant_id();

  /**
   * Returns the name/type of the grant (permission level).
   *
   * @return the grant name (e.g., "planner", "observer")
   */
  String getGrant_name();

  /**
   * Returns the ID of the user this grant is assigned to.
   *
   * @return the user ID
   */
  String getUser_id();

  /**
   * Returns the ID of the resource this grant provides access to.
   *
   * @return the resource ID (e.g., exercise ID, scenario ID)
   */
  String getGrant_resource();

  /**
   * Returns the type of resource this grant applies to.
   *
   * @return the resource type enum value
   */
  Grant.GRANT_RESOURCE_TYPE getGrant_resource_type();
}
