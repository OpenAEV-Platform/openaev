package io.openaev.rest.exception;

import java.util.List;

/**
 * Raised when a caller tries to hand out a privilege they do not hold themselves — capabilities on
 * a role or group, or a grant on a resource.
 *
 * <p>Carries a stable {@code code} plus the offending values as keys rather than prose, so the
 * client renders them in its own language: it already owns translations for capability names. The
 * message stays as the server-side trace.
 *
 * <p>Extends {@link BadRequestException} so that, should the dedicated handler ever be removed, the
 * request still degrades to a 400 with a readable message instead of a 500.
 */
public class PrivilegeGrantException extends BadRequestException {

  public static final String UNHELD_CAPABILITIES = "CANNOT_GRANT_UNHELD_CAPABILITIES";
  public static final String UNHELD_RESOURCE_GRANT = "CANNOT_GRANT_UNHELD_RESOURCE_GRANT";

  private final String code;
  private final List<String> details;

  public PrivilegeGrantException(
      final String code, final List<String> details, final String message) {
    super(message);
    this.code = code;
    this.details = List.copyOf(details);
  }

  public String getCode() {
    return code;
  }

  public List<String> getDetails() {
    return details;
  }
}
