package io.openaev.service.account;

import io.openaev.database.model.Capability;
import io.openaev.database.model.CapabilityScope;
import io.openaev.database.model.Grant.GRANT_TYPE;
import io.openaev.database.model.User;
import io.openaev.rest.exception.PrivilegeGrantException;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Guards against privilege escalation by capability holders: whoever manages users, groups and
 * roles may only hand out capabilities they hold themselves, in the scope they hold them.
 */
public final class PrivilegeEscalationValidator {

  private PrivilegeEscalationValidator() {}

  // -- CAPABILITIES --

  public static void assertCanAssignCapabilities(
      @NotNull final User currentUser,
      @NotNull final Set<Capability> capabilities,
      @NotNull final CapabilityScope scope) {
    // The BYPASS check is what keeps the seeded Administrators role able to hand out BYPASS itself.
    if (currentUser.isAdmin() || currentUser.hasBypassIn(scope)) {
      return;
    }
    Set<Capability> missing = new TreeSet<>(capabilities);
    missing.removeAll(currentUser.getCapabilities(scope));
    if (!missing.isEmpty()) {
      throw new PrivilegeGrantException(
          PrivilegeGrantException.UNHELD_CAPABILITIES,
          missing.stream().map(Enum::name).toList(),
          "Cannot grant capabilities you do not hold at the "
              + scope.name().toLowerCase()
              + " level: "
              + missing.stream().map(Enum::name).collect(Collectors.joining(", ")));
    }
  }

  // -- GRANT --

  private static PrivilegeGrantException unheldGrant(
      final GRANT_TYPE requestedGrant, final String message) {
    return new PrivilegeGrantException(
        PrivilegeGrantException.UNHELD_RESOURCE_GRANT, List.of(requestedGrant.name()), message);
  }

  public static void assertCanAssignGrant(
      @NotNull final User currentUser,
      @NotNull final GRANT_TYPE requestedGrant,
      @NotNull final String resourceId) {
    if (currentUser.isAdminOrBypass()) {
      return;
    }

    String currentGrantName = currentUser.getGrants().get(resourceId);
    if (currentGrantName == null) {
      throw unheldGrant(
          requestedGrant,
          "Cannot grant "
              + requestedGrant.name()
              + " on resource "
              + resourceId
              + ": you hold no grant on it");
    }

    GRANT_TYPE currentGrant = GRANT_TYPE.valueOf(currentGrantName);
    if (currentGrant.getPriority() < requestedGrant.getPriority()) {
      throw unheldGrant(
          requestedGrant,
          "Cannot grant "
              + requestedGrant.name()
              + " on resource "
              + resourceId
              + ": you only hold "
              + currentGrant.name());
    }
  }
}
