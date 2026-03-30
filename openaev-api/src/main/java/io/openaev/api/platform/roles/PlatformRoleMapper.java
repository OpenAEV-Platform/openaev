package io.openaev.api.platform.roles;

import io.openaev.database.model.PlatformRole;

public class PlatformRoleMapper {

  private PlatformRoleMapper() {}

  public static PlatformRoleOutput toOutput(PlatformRole role) {
    return new PlatformRoleOutput(role.getId(), role.getName(), role.getDescription());
  }
}
