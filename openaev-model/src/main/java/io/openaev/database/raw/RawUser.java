package io.openaev.database.raw;

import io.openaev.helper.UserHelper;
import java.util.List;

/**
 * Spring Data projection interface for user data.
 *
 * <p>This interface defines a projection for retrieving user profile information including contact
 * details, organizational associations, and group memberships. It also provides convenience methods
 * for computing display names and Gravatar URLs.
 *
 * @see io.openaev.database.model.User
 */
public interface RawUser {

  /**
   * Computes and returns the Gravatar URL for this user based on their email address.
   *
   * @return the Gravatar URL
   */
  default String getUser_gravatar() {
    return UserHelper.getGravatar(getUser_email());
  }

  /**
   * Returns the unique identifier of the user.
   *
   * @return the user ID
   */
  String getUser_id();

  /**
   * Returns the user's first name.
   *
   * @return the first name, or {@code null} if not set
   */
  String getUser_firstname();

  /**
   * Returns the user's last name.
   *
   * @return the last name, or {@code null} if not set
   */
  String getUser_lastname();

  /**
   * Returns the user's email address.
   *
   * @return the email address
   */
  String getUser_email();

  /**
   * Returns the user's phone number.
   *
   * @return the phone number, or {@code null} if not set
   */
  String getUser_phone();

  /**
   * Returns the ID of the user's organization.
   *
   * @return the organization ID, or {@code null} if not associated with an organization
   */
  String getUser_organization();

  /**
   * Returns the list of tag IDs associated with this user.
   *
   * @return list of tag IDs
   */
  List<String> getUser_tags();

  /**
   * Returns the list of group IDs the user belongs to.
   *
   * @return list of group IDs
   */
  List<String> getUser_groups();

  /**
   * Returns the list of team IDs the user is a member of.
   *
   * @return list of team IDs
   */
  List<String> getUser_teams();

  /**
   * Computes and returns the display name for this user.
   *
   * <p>Returns the full name (first name + last name) if both are available, otherwise falls back
   * to the email address.
   *
   * @return the computed display name
   */
  default String computeName() {
    if (this.getUser_firstname() != null && this.getUser_lastname() != null) {
      return this.getUser_firstname() + " " + this.getUser_lastname();
    } else {
      return this.getUser_email();
    }
  }
}
