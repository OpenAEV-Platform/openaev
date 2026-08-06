package io.openaev.utils;

import io.openaev.database.model.Capability;
import io.openaev.database.model.User;
import io.openaev.rest.exception.ForbiddenException;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public final class CapabilityAssignmentUtils {

  private CapabilityAssignmentUtils() {}

  public static void assertCanAssignCapabilities(
      @NotNull final User currentUser, @NotNull final Set<Capability> capabilities) {
    if (!currentUser.isAdminOrBypass()
        && !currentUser.getCapabilities().containsAll(capabilities)) {
      throw new ForbiddenException("Cannot assign capabilities that current user does not own");
    }
  }
}
