package io.openaev.service.account;

import io.openaev.database.model.Capability;
import io.openaev.database.model.CapabilityScope;
import io.openaev.database.model.Grant.GRANT_TYPE;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.User;
import io.openaev.rest.exception.ForbiddenException;
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

  // -- ADMIN FLAG --

  /**
   * The {@code user_admin} flag is the global RBAC bypass and is not grantable through the users
   * API: managing users would otherwise be enough to promote oneself. It is set from the
   * configuration only - the bootstrap admin and the SSO role mapping.
   */
  public static void assertAdminFlagUnchanged(final boolean requested, final boolean current) {
    if (requested != current) {
      throw new PrivilegeGrantException(
          PrivilegeGrantException.UNHELD_CAPABILITIES,
          List.of(Capability.BYPASS.name()),
          "The administrator flag cannot be set through the users API: grant a platform role"
              + " holding BYPASS instead");
    }
  }

  // -- CAPABILITIES --

  /**
   * The capabilities a group already hands out, for a check that compares them with the caller's.
   * Refuses when the roles read through the group are not the roles {@code groups_roles} links it
   * to: a role the request scope hides would be missing from the answer, and a partial answer lets
   * the caller through for a capability the check never saw. Applies to every caller, admin and
   * bypass holders included.
   *
   * @param storedRoleIds the role ids of the links of the group, read from {@code groups_roles}
   */
  public static Set<Capability> capabilitiesOfAllRoles(
      @NotNull final Group group, @NotNull final Set<String> storedRoleIds) {
    Set<String> readRoleIds =
        group.getRoles().stream().map(Role::getId).collect(Collectors.toSet());
    if (!readRoleIds.equals(storedRoleIds)) {
      throw new ForbiddenException("The roles of the group cannot all be read: " + group.getId());
    }
    return Role.capabilitiesOf(group.getRoles());
  }

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
