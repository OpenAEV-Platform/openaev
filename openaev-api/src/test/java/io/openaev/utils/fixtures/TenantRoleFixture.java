package io.openaev.utils.fixtures;

import io.openaev.database.model.Capability;
import io.openaev.database.model.Role;
import java.util.Set;

public class TenantRoleFixture {
  public static final String ROLE_NAME = "rolename";
  public static final Set<Capability> CAPABILITIES =
      Set.of(Capability.ACCESS_ASSETS, Capability.ACCESS_CHALLENGES);

  public static Role getRole() {
    Role role = new Role();
    role.setName(ROLE_NAME);
    role.setCapabilities(CAPABILITIES);
    return role;
  }

  public static Role getRole(String name) {
    return getRole(name, CAPABILITIES);
  }

  public static Role getRole(Set<Capability> capabilities) {
    return getRole(ROLE_NAME, capabilities);
  }

  public static Role getRole(String name, Set<Capability> capabilities) {
    Role role = new Role();
    role.setName(name);
    role.setCapabilities(capabilities);
    return role;
  }
}
