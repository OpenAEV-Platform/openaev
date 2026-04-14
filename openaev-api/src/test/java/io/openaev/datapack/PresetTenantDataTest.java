package io.openaev.datapack;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.Capability;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PresetTenantDataTest {

  @Nested
  @DisplayName("Observer role")
  class ObserverRole {

    private final Set<Capability> capabilities = PresetTenantData.DEFAULT_ROLES.get("Observer");

    @Test
    @DisplayName("given_observerRole_should_containAccessTenantSettings")
    void given_observerRole_should_containAccessTenantSettings() {
      // -- ASSERT --
      assertThat(capabilities).contains(Capability.ACCESS_TENANT_SETTINGS);
    }

    @Test
    @DisplayName("given_observerRole_should_notContainManageOrDeleteTenantSettings")
    void given_observerRole_should_notContainManageOrDeleteTenantSettings() {
      // -- ASSERT --
      assertThat(capabilities)
          .doesNotContain(Capability.MANAGE_TENANT_SETTINGS, Capability.DELETE_TENANT_SETTINGS);
    }

    @Test
    @DisplayName("given_observerRole_should_onlyHaveAccessCapabilities")
    void given_observerRole_should_onlyHaveAccessCapabilities() {
      // -- ASSERT --
      assertThat(capabilities)
          .allMatch(c -> c.name().startsWith("ACCESS_"), "Observer should only have ACCESS_* capabilities");
    }
  }

  @Nested
  @DisplayName("Manager role")
  class ManagerRole {

    private final Set<Capability> capabilities = PresetTenantData.DEFAULT_ROLES.get("Manager");

    @Test
    @DisplayName("given_managerRole_should_containAllTenantSettingsCapabilities")
    void given_managerRole_should_containAllTenantSettingsCapabilities() {
      // -- ASSERT --
      assertThat(capabilities)
          .contains(
              Capability.ACCESS_TENANT_SETTINGS,
              Capability.MANAGE_TENANT_SETTINGS,
              Capability.DELETE_TENANT_SETTINGS);
    }
  }

  @Nested
  @DisplayName("Admin role")
  class AdminRole {

    @Test
    @DisplayName("given_adminRole_should_containOnlyBypass")
    void given_adminRole_should_containOnlyBypass() {
      // -- ASSERT --
      assertThat(PresetTenantData.DEFAULT_ROLES.get("Admin")).containsExactly(Capability.BYPASS);
    }
  }

  @Test
  @DisplayName("given_defaultRoles_should_containExactlyThreeRoles")
  void given_defaultRoles_should_containExactlyThreeRoles() {
    // -- ASSERT --
    assertThat(PresetTenantData.DEFAULT_ROLES).containsOnlyKeys("Observer", "Manager", "Admin");
  }
}

