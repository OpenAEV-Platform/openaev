package io.openaev.service.account;

import static io.openaev.opencti.connectors.Constants.PROCESS_STIX_GROUP_NAME;
import static io.openaev.opencti.connectors.Constants.PROCESS_STIX_ROLE_NAME;
import static io.openaev.opencti.connectors.service.PrivilegeService.CONNECTOR_EMAIL_PATTERN;
import static io.openaev.service.account.Constants.SERVICE_GROUP_NAME;
import static io.openaev.service.account.Constants.SERVICE_ROLE_NAME;
import static io.openaev.service.account.ServiceAccountPrivilegeService.SERVICE_EMAIL_PATTERN;

import io.openaev.rest.exception.BadRequestException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates that user-provided names do not conflict with system-reserved names used by service
 * accounts.
 */
public final class ReservedNameValidator {

  private ReservedNameValidator() {}

  private static final Set<String> RESERVED_ROLE_NAMES =
      Set.of(SERVICE_ROLE_NAME, PROCESS_STIX_ROLE_NAME);
  private static final Set<String> RESERVED_GROUP_NAMES =
      Set.of(SERVICE_GROUP_NAME, PROCESS_STIX_GROUP_NAME);

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

  /** Throws BadRequestException if the role name is reserved for system use. */
  public static void validateRoleName(String name) {
    if (name != null && RESERVED_ROLE_NAMES.contains(name)) {
      throw new BadRequestException(
          "The role '%s' is reserved for system use and cannot be used.".formatted(name));
    }
  }

  /** Throws BadRequestException if the group name is reserved for system use. */
  public static void validateGroupName(String name) {
    if (name != null && RESERVED_GROUP_NAMES.contains(name)) {
      throw new BadRequestException(
          "The group '%s' is reserved for system use and cannot be used.".formatted(name));
    }
  }

  /** Throws BadRequestException if the email matches a reserved service-account email pattern. */
  public static void validateUserEmailPattern(String email) {
    if (email == null) {
      return;
    }
    boolean matchesReserved =
        RESERVED_EMAIL_PATTERNS.stream().anyMatch(p -> p.matcher(email).matches());
    if (matchesReserved) {
      throw new BadRequestException("The user is reserved for system use and cannot be used.");
    }
  }
}
