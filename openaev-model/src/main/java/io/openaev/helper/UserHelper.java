package io.openaev.helper;

import static java.util.Arrays.asList;

import io.openaev.database.model.Grant;
import io.openaev.database.model.User;
import java.util.List;

/**
 * Helper class for user-related operations.
 *
 * <p>This utility provides methods for querying users based on their grants/permissions and
 * generating user avatar URLs.
 *
 * @see User
 * @see Grant
 */
public class UserHelper {

  private UserHelper() {
    // Utility class - prevent instantiation
  }

  /**
   * Retrieves all distinct users who have any of the specified grant types.
   *
   * <p>This method traverses the grants, filters by the specified types, collects users from the
   * associated groups, and returns a deduplicated list.
   *
   * @param grants the list of grants to search through
   * @param types the grant types to filter by (vararg)
   * @return a list of distinct users matching any of the specified grant types
   */
  public static List<User> getUsersByType(List<Grant> grants, Grant.GRANT_TYPE... types) {
    return grants.stream()
        .filter(grant -> asList(types).contains(grant.getName()))
        .map(Grant::getGroup)
        .flatMap(group -> group.getUsers().stream())
        .distinct()
        .toList();
  }

  /**
   * Generates a Gravatar URL for the given email address.
   *
   * <p>Gravatar is a service that provides globally recognized avatars based on email addresses.
   * The URL includes a fallback to a generic "mystery man" avatar if no Gravatar is registered.
   *
   * @param email the email address to generate the Gravatar URL for
   * @return the Gravatar URL for the email's avatar
   * @see <a href="https://gravatar.com">Gravatar</a>
   */
  public static String getGravatar(String email) {
    String emailMd5 = CryptoHelper.md5Hex(email.trim().toLowerCase());
    return "https://www.gravatar.com/avatar/" + emailMd5 + "?d=mm";
  }
}
