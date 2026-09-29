package io.openaev.rest.role.form;

import io.openaev.database.model.Role;
import jakarta.validation.constraints.NotNull;

public class RoleMapper {

  private RoleMapper() {}

  public static RoleOutput toOutput(@NotNull final Role role) {
    return new RoleOutput(
        role.getId(), role.getName(), role.getDescription(), role.getCapabilities());
  }
}
