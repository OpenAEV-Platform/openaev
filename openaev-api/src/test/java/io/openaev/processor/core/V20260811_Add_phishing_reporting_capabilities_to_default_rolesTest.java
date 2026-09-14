package io.openaev.processor.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.RoleRepository;
import io.openaev.service.TenantRoleService;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests for the runtime migration that adds the Phishing and Reporting capabilities to the
 * auto-generated Manager/Observer tenant roles (issue #7320).
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Add phishing/reporting capabilities to default roles migration tests")
@Transactional
// Roles are created through TenantRoleService#createRole, which now enforces that the current
// user holds the capabilities it hands out: run as admin to short-circuit that check.
@WithMockUser(isAdmin = true)
public class V20260811_Add_phishing_reporting_capabilities_to_default_rolesTest
    extends IntegrationTest {

  @Autowired private V20260811_Add_phishing_reporting_capabilities_to_default_roles migration;
  @Autowired private TenantRoleService roleService;
  @Autowired private RoleRepository roleRepository;

  /** The Observer capability set the buggy preset generated (roots only — no parents to add). */
  private static final Set<Capability> PRE_FIX_OBSERVER =
      Set.of(
          Capability.ACCESS_ASSESSMENT,
          Capability.ACCESS_ASSETS,
          Capability.ACCESS_THREAT_ARSENALS,
          Capability.ACCESS_DASHBOARDS,
          Capability.ACCESS_FINDINGS,
          Capability.ACCESS_DOCUMENTS,
          Capability.ACCESS_CHANNELS,
          Capability.ACCESS_CHALLENGES,
          Capability.ACCESS_LESSONS_LEARNED,
          Capability.ACCESS_SECURITY_PLATFORMS);

  /** The Manager capability set the buggy preset generated. */
  private static final Set<Capability> PRE_FIX_MANAGER =
      Set.of(
          Capability.ACCESS_ASSESSMENT,
          Capability.MANAGE_ASSESSMENT,
          Capability.DELETE_ASSESSMENT,
          Capability.LAUNCH_ASSESSMENT,
          Capability.MANAGE_TEAMS_AND_PLAYERS,
          Capability.DELETE_TEAMS_AND_PLAYERS,
          Capability.ACCESS_ASSETS,
          Capability.MANAGE_ASSETS,
          Capability.DELETE_ASSETS,
          Capability.ACCESS_THREAT_ARSENALS,
          Capability.MANAGE_THREAT_ARSENALS,
          Capability.DELETE_THREAT_ARSENALS,
          Capability.ACCESS_DASHBOARDS,
          Capability.MANAGE_DASHBOARDS,
          Capability.DELETE_DASHBOARDS,
          Capability.ACCESS_FINDINGS,
          Capability.MANAGE_FINDINGS,
          Capability.DELETE_FINDINGS,
          Capability.ACCESS_DOCUMENTS,
          Capability.MANAGE_DOCUMENTS,
          Capability.DELETE_DOCUMENTS,
          Capability.ACCESS_CHANNELS,
          Capability.MANAGE_CHANNELS,
          Capability.DELETE_CHANNELS,
          Capability.ACCESS_CHALLENGES,
          Capability.MANAGE_CHALLENGES,
          Capability.DELETE_CHALLENGES,
          Capability.ACCESS_LESSONS_LEARNED,
          Capability.MANAGE_LESSONS_LEARNED,
          Capability.DELETE_LESSONS_LEARNED,
          Capability.ACCESS_SECURITY_PLATFORMS,
          Capability.DELETE_SECURITY_PLATFORMS,
          Capability.MANAGE_SECURITY_PLATFORMS);

  private Role reload(Role role) {
    return roleRepository.findById(role.getId()).orElseThrow();
  }

  @Test
  @DisplayName("Should add phishing and reporting capabilities to a pristine Manager role")
  void given_pristineManagerRole_should_addPhishingAndReportingCapabilities() {
    Role manager = roleService.createRole("Manager", "Manager", PRE_FIX_MANAGER);

    migration.doMigrate(new Tenant(TenantContext.getCurrentTenant()));

    assertThat(reload(manager).getCapabilities())
        .contains(
            Capability.ACCESS_PHISHING,
            Capability.MANAGE_PHISHING,
            Capability.DELETE_PHISHING,
            Capability.ACCESS_REPORTINGS,
            Capability.MANAGE_REPORTINGS,
            Capability.DELETE_REPORTINGS)
        .containsAll(Capability.resolveWithParents(PRE_FIX_MANAGER));
  }

  @Test
  @DisplayName("Should add access phishing and reporting capabilities to a pristine Observer role")
  void given_pristineObserverRole_should_addAccessCapabilities() {
    Role observer = roleService.createRole("Observer", "Observer", PRE_FIX_OBSERVER);

    migration.doMigrate(new Tenant(TenantContext.getCurrentTenant()));

    Set<Capability> healed = reload(observer).getCapabilities();
    assertThat(healed)
        .contains(Capability.ACCESS_PHISHING, Capability.ACCESS_REPORTINGS)
        .containsAll(PRE_FIX_OBSERVER)
        .doesNotContain(Capability.MANAGE_PHISHING, Capability.DELETE_PHISHING);
  }

  @Test
  @DisplayName("Should leave a customized Manager role untouched")
  void given_customizedManagerRole_should_leaveItUntouched() {
    // An admin restricted the role: it no longer matches the auto-generated defaults.
    Set<Capability> customized = new HashSet<>(PRE_FIX_MANAGER);
    customized.remove(Capability.DELETE_ASSESSMENT);
    Role manager = roleService.createRole("Manager", "Manager", customized);
    Set<Capability> before = Set.copyOf(reload(manager).getCapabilities());

    migration.doMigrate(new Tenant(TenantContext.getCurrentTenant()));

    assertThat(reload(manager).getCapabilities())
        .containsExactlyInAnyOrderElementsOf(before)
        .doesNotContain(Capability.ACCESS_PHISHING, Capability.ACCESS_REPORTINGS);
  }

  @Test
  @DisplayName("Should leave a role created with the fixed preset untouched")
  void given_alreadyHealedRole_should_beIdempotent() {
    Set<Capability> fixed = new HashSet<>(PRE_FIX_MANAGER);
    fixed.addAll(
        Set.of(
            Capability.ACCESS_REPORTINGS,
            Capability.MANAGE_REPORTINGS,
            Capability.DELETE_REPORTINGS,
            Capability.ACCESS_PHISHING,
            Capability.MANAGE_PHISHING,
            Capability.DELETE_PHISHING));
    Role manager = roleService.createRole("Manager", "Manager", fixed);
    Set<Capability> before = Set.copyOf(reload(manager).getCapabilities());

    migration.doMigrate(new Tenant(TenantContext.getCurrentTenant()));

    assertThat(reload(manager).getCapabilities()).containsExactlyInAnyOrderElementsOf(before);
  }
}
