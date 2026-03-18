package io.openaev.utils.fixtures.platform;

import io.openaev.database.model.Capability;
import io.openaev.database.model.PlatformRole;
import java.util.Set;

public class PlatformRoleFixture {

  public static final String PLATFORM_ROLE_NAME = "Platform Admin";
  public static final String PLATFORM_ROLE_DESCRIPTION = "Default platform admin role";
  public static final Set<Capability> PLATFORM_ROLE_CAPABILITIES =
      Set.of(Capability.ACCESS_PLATFORM_SETTINGS);

  public static PlatformRole getPlatformRole() {
    return getPlatformRole(PLATFORM_ROLE_NAME);
  }

  public static PlatformRole getPlatformRole(String name) {
    return getPlatformRole(name, PLATFORM_ROLE_CAPABILITIES);
  }

  public static PlatformRole getPlatformRole(String name, Set<Capability> capabilities) {
    PlatformRole role = new PlatformRole();
    role.setName(name);
    role.setDescription(PLATFORM_ROLE_DESCRIPTION);
    role.setCapabilities(capabilities);
    return role;
  }
}
