package io.openaev.utils;

import io.openaev.database.model.Capability;
import io.openaev.database.model.Grant;
import io.openaev.database.model.User;
import io.openaev.rest.exception.ForbiddenException;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public final class AssignmentUtils {

  private AssignmentUtils() {}

  public static void assertCanAssignCapabilities(
      @NotNull final User currentUser, @NotNull final Set<Capability> capabilities) {
    if (!currentUser.isAdminOrBypass()
        && !currentUser.getCapabilities().containsAll(capabilities)) {
      throw new ForbiddenException("Cannot assign capabilities that current user does not own");
    }
  }

  public static void assertCanAssignGrant(
      @NotNull final User currentUser,
      @NotNull final Grant.GRANT_TYPE requestedGrant,
      @NotNull final String resourceId) {
    if (currentUser.isAdminOrBypass()) {
      return;
    }

    String currentGrantName = currentUser.getGrants().get(resourceId);
    if (currentGrantName == null) {
      throw new ForbiddenException("Cannot assign grants that current user does not own");
    }

    Grant.GRANT_TYPE currentGrant = Grant.GRANT_TYPE.valueOf(currentGrantName);
    if (currentGrant.getPriority() < requestedGrant.getPriority()) {
      throw new ForbiddenException("Cannot assign grants that current user does not own");
    }
  }
}
