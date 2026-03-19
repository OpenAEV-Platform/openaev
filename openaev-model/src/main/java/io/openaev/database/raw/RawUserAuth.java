package io.openaev.database.raw;

import java.util.Set;

/**
 * Spring Data projection interface for user authentication and authorization data.
 *
 * <p>This interface defines a projection for retrieving user identity and permission information
 * needed for authentication and authorization decisions. It includes admin status and associated
 * grants.
 *
 * @see io.openaev.database.model.User
 * @see RawGrant
 */
public interface RawUserAuth {

  /**
   * Returns the unique identifier of the user.
   *
   * @return the user ID
   */
  String getUser_id();

  /**
   * Returns whether the user has administrator privileges.
   *
   * @return {@code true} if the user is an admin, {@code false} otherwise
   */
  boolean getUser_admin();

  /**
   * Returns the set of grants (permissions) assigned to this user.
   *
   * @return set of user grants
   */
  Set<RawGrant> getUser_grants();
}
