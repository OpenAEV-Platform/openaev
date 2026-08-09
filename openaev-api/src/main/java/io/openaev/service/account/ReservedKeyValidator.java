package io.openaev.service.account;

import static io.openaev.opencti.connectors.Constants.*;
import static io.openaev.opencti.connectors.service.PrivilegeService.CONNECTOR_EMAIL_PATTERN;
import static io.openaev.service.account.Constants.*;
import static io.openaev.service.account.ServiceAccountPrivilegeService.SERVICE_EMAIL_PATTERN;

import io.openaev.context.TenantContext;
import io.openaev.database.model.User;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.AbstractPrivilegeService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Validates that user-provided names do not conflict with system-reserved names used by service
 * accounts.
 */
public final class ReservedKeyValidator {

  private ReservedKeyValidator() {}

  private static final Set<String> RESERVED_GROUP_ID =
      Set.of(SERVICE_GROUP_ID, PROCESS_STIX_GROUP_ID);

  private static final Set<String> RESERVED_ROLE_ID = Set.of(SERVICE_ROLE_ID, PROCESS_STIX_ROLE_ID);

  /**
   * Compiled regexes derived from the service email patterns. The patterns contain a {@code %s}
   * placeholder (typically the tenant or connector id) which is converted to a wildcard match.
   */
  private static final List<Pattern> RESERVED_EMAIL_PATTERNS =
      List.of(toPattern(SERVICE_EMAIL_PATTERN), toPattern(CONNECTOR_EMAIL_PATTERN));

  private static Pattern toPattern(String emailPattern) {
    // Escape every regex metacharacter, then turn the (already-escaped) %s placeholder into .+
    String quoted = Pattern.quote(emailPattern).replace("%s", "\\E.+\\Q");
    return Pattern.compile("^" + quoted + "$", Pattern.CASE_INSENSITIVE);
  }

  /** Throws BadRequestException if the group id is reserved for system use. */
  public static void validateGroupId(String uuid) {
    List<String> idsReserved =
        RESERVED_GROUP_ID.stream()
            .map(
                id ->
                    AbstractPrivilegeService.getUUIDFromName(id, TenantContext.getCurrentTenant()))
            .toList();

    if (uuid != null && idsReserved.contains(uuid)) {
      throw new BadRequestException("The group is reserved for system use and cannot be used.");
    }
  }

  /** Throws BadRequestException if the group id is reserved for system use. */
  public static void validateRoleId(String uuid) {
    List<String> idsReserved =
        RESERVED_ROLE_ID.stream()
            .map(
                id ->
                    AbstractPrivilegeService.getUUIDFromName(id, TenantContext.getCurrentTenant()))
            .toList();

    if (uuid != null && idsReserved.contains(uuid)) {
      throw new BadRequestException("The role is reserved for system use and cannot be used.");
    }
  }

  /** Returns true if the email matches a reserved service-account email pattern. */
  public static boolean isReservedUserEmail(String email) {
    if (email == null) {
      return false;
    }
    return RESERVED_EMAIL_PATTERNS.stream().anyMatch(p -> p.matcher(email).matches());
  }

  /** Throws BadRequestException if the email matches a reserved service-account email pattern. */
  public static void validateUserEmailPattern(String email) {
    if (isReservedUserEmail(email)) {
      throw new BadRequestException("The user is reserved for system use and cannot be used.");
    }
  }

  /**
   * Strips reserved service/connector accounts from a collection of users about to become team
   * members. Reserved accounts are system users, not players: they are hidden from every player
   * list (see PlayerService#playerPagination), so team membership must enforce the same contract -
   * otherwise team_users_number reports members that no player list can ever display.
   */
  public static List<User> excludeReservedUsers(Iterable<User> users) {
    // Mutable list on purpose: callers hand the result to JPA entity collections.
    return StreamSupport.stream(users.spliterator(), false)
        .filter(user -> !isReservedUserEmail(user.getEmail()))
        .collect(Collectors.toCollection(ArrayList::new));
  }
}
