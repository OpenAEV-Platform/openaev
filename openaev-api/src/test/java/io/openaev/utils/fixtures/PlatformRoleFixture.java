package io.openaev.utils.fixtures;

import io.openaev.database.model.Capability;
import io.openaev.database.model.Role;
import java.util.Set;

public class PlatformRoleFixture {

  public static final String PLATFORM_ROLE_DESCRIPTION = "Default platform admin role";
  public static final Set<Capability> PLATFORM_ROLE_CAPABILITIES =
      Set.of(Capability.ACCESS_PLATFORM_SETTINGS);

  public static Role getPlatformRole(String name) {
    return getPlatformRole(name, PLATFORM_ROLE_CAPABILITIES);
  }

  public static Role getPlatformRole(String name, Set<Capability> capabilities) {
    Role role = new Role();
    role.setName(name);
    role.setDescription(PLATFORM_ROLE_DESCRIPTION);
    role.setCapabilities(capabilities);
    role.setTenant(null);
    return role;
  }
}
