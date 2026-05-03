package io.openaev.api.platform.roles;

import io.openaev.database.model.Role;

public class PlatformRoleMapper {

  private PlatformRoleMapper() {}

  public static PlatformRoleOutput toOutput(Role role) {
    return new PlatformRoleOutput(role.getId(), role.getName(), role.getDescription());
  }
}
