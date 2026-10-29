package io.openaev.processor.datapack;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.RoleRepository;
import io.openaev.service.AbstractPrivilegeService;
import io.openaev.service.TenantRoleService;
import io.openaev.service.account.AdminPrivilegeService;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests for the datapack aligning the default tenant's Admin/Manager/Observer roles and groups with
 * runtime-provisioned tenants, which the default tenant never got from {@link
 * V20260330_Default_tenant_data}.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("DataPack V20261001 default tenant roles and groups tests")
@Transactional
@WithMockUser(isAdmin = true)
class V20261001_Default_tenant_roles_and_groupsTest extends IntegrationTest {

  private static final String TENANT_ID = Tenant.DEFAULT_TENANT_UUID;

  @Autowired private V20261001_Default_tenant_roles_and_groups dataPack;
  @Autowired private GroupRepository groupRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private TenantRoleService tenantRoleService;

  /** Mirrors a fresh platform: Flyway seeded the default roles, but no default group exists. */
  @BeforeEach
  void removeDefaultGroups() {
    entityManager
        .createNativeQuery(
            "DELETE FROM groups WHERE tenant_id = ?1 AND group_name IN ('Admin', 'Manager',"
                + " 'Observer')")
        .setParameter(1, TENANT_ID)
        .executeUpdate();
    entityManager.clear();
  }

  private Map<String, Group> defaultGroupsByName() {
    return groupRepository.findAllByTenantId(TENANT_ID).stream()
        .filter(group -> PresetTenantData.DEFAULT_ROLES.containsKey(group.getName()))
        .collect(Collectors.toMap(Group::getName, Function.identity()));
  }

  private List<Role> rolesNamed(String roleName) {
    return roleRepository.findAllByTenantId(TENANT_ID).stream()
        .filter(role -> roleName.equals(role.getName()))
        .toList();
  }

  /** The single role Flyway seeded under this name ({@code V4_29__Add_default_roles}). */
  private Role flywayRole(String roleName) {
    List<Role> roles = rolesNamed(roleName);
    assertThat(roles).hasSize(1);
    return roles.getFirst();
  }

  private Set<Capability> capabilitiesAfterProcessing(Role role) {
    assertThat(dataPack.doProcess(new Tenant(TENANT_ID))).isTrue();
    entityManager.flush();
    entityManager.clear();
    return roleRepository.findById(role.getId()).orElseThrow().getCapabilities();
  }

  private static Set<Capability> preset(String roleName) {
    return Capability.resolveWithParents(PresetTenantData.DEFAULT_ROLES.get(roleName));
  }

  private void saveCapabilities(Role role, Set<Capability> capabilities) {
    role.setCapabilities(capabilities);
    roleRepository.save(role);
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  @DisplayName("Should bind each default group to the Flyway-seeded role without duplicating it")
  void given_freshDefaultTenant_should_bindEachDefaultGroupToTheExistingRole() {
    Map<String, Integer> roleCountsBefore =
        PresetTenantData.DEFAULT_ROLES.keySet().stream()
            .collect(Collectors.toMap(Function.identity(), name -> rolesNamed(name).size()));
    assertThat(roleCountsBefore).allSatisfy((name, count) -> assertThat(count).isPositive());

    assertThat(dataPack.doProcess(new Tenant(TENANT_ID))).isTrue();

    Map<String, Group> groups = defaultGroupsByName();
    assertThat(groups).containsOnlyKeys(PresetTenantData.DEFAULT_ROLES.keySet());
    groups.forEach(
        (name, group) -> {
          assertThat(group.isDefaultUserAssignation()).isFalse();
          assertThat(group.getRoles()).extracting(Role::getName).containsExactly(name);
          assertThat(rolesNamed(name)).hasSize(roleCountsBefore.get(name));
        });
    String bootstrapAdminRoleId =
        AbstractPrivilegeService.getUUIDFromName(AdminPrivilegeService.ADMIN_ROLE_ID, TENANT_ID);
    assertThat(groups.get(PresetTenantData.ADMIN).getRoles())
        .extracting(Role::getId)
        .doesNotContain(bootstrapAdminRoleId);
  }

  @Test
  @DisplayName("Should create a missing default role from the preset")
  void given_missingObserverRole_should_createItFromPreset() {
    entityManager
        .createNativeQuery("DELETE FROM roles WHERE tenant_id = ?1 AND role_name = 'Observer'")
        .setParameter(1, TENANT_ID)
        .executeUpdate();
    entityManager.clear();

    assertThat(dataPack.doProcess(new Tenant(TENANT_ID))).isTrue();

    List<Role> observers = rolesNamed("Observer");
    assertThat(observers).hasSize(1);
    assertThat(observers.getFirst().getCapabilities())
        .containsAll(Capability.resolveWithParents(PresetTenantData.DEFAULT_ROLES.get("Observer")));
    assertThat(defaultGroupsByName().get("Observer").getRoles())
        .extracting(Role::getId)
        .containsExactly(observers.getFirst().getId());
  }

  @Test
  @DisplayName("Should leave an existing default group untouched")
  void given_existingManagerGroup_should_leaveItUntouched() {
    Group existing = new Group();
    existing.setName("Manager");
    existing.setDescription("Customized by an admin");
    existing.setTenant(entityManager.getReference(Tenant.class, TENANT_ID));
    existing = groupRepository.save(existing);

    assertThat(dataPack.doProcess(new Tenant(TENANT_ID))).isTrue();

    Group manager = defaultGroupsByName().get("Manager");
    assertThat(manager.getId()).isEqualTo(existing.getId());
    assertThat(manager.getDescription()).isEqualTo("Customized by an admin");
    assertThat(manager.getRoles()).isEmpty();
  }

  @Test
  @DisplayName("Should skip tenants other than the default one")
  void given_nonDefaultTenant_should_skip() {
    assertThat(dataPack.doProcess(new Tenant(UUID.randomUUID().toString()))).isTrue();

    assertThat(defaultGroupsByName()).isEmpty();
  }

  @Test
  @DisplayName("Should align the Flyway-seeded Observer and Manager roles with the preset")
  void given_flywaySeededRoles_should_alignCapabilitiesWithPreset() {
    Role observer = flywayRole("Observer");
    Role manager = flywayRole("Manager");
    Set<Capability> observerBefore = Set.copyOf(observer.getCapabilities());
    Set<Capability> managerBefore = Set.copyOf(manager.getCapabilities());

    assertThat(capabilitiesAfterProcessing(observer))
        .containsExactlyInAnyOrderElementsOf(preset("Observer"))
        .containsAll(observerBefore);
    assertThat(roleRepository.findById(manager.getId()).orElseThrow().getCapabilities())
        .containsExactlyInAnyOrderElementsOf(preset("Manager"))
        .containsAll(managerBefore);
  }

  @Test
  @DisplayName("Should align a Manager role already healed by the phishing/reporting migration")
  void given_managerHealedByPhishingReportingMigration_should_alignCapabilitiesWithPreset() {
    Role manager = flywayRole("Manager");
    Set<Capability> healed = new HashSet<>(manager.getCapabilities());
    healed.addAll(
        Set.of(
            Capability.ACCESS_REPORTINGS,
            Capability.MANAGE_REPORTINGS,
            Capability.DELETE_REPORTINGS,
            Capability.ACCESS_PHISHING,
            Capability.MANAGE_PHISHING,
            Capability.DELETE_PHISHING));
    saveCapabilities(manager, healed);

    assertThat(capabilitiesAfterProcessing(manager))
        .containsExactlyInAnyOrderElementsOf(preset("Manager"));
  }

  @Test
  @DisplayName("Should align a Manager role an admin restricted")
  void given_restrictedManagerRole_should_alignCapabilitiesWithPreset() {
    Role manager = flywayRole("Manager");
    Set<Capability> restricted = new HashSet<>(manager.getCapabilities());
    restricted.remove(Capability.DELETE_ASSESSMENT);
    saveCapabilities(manager, restricted);

    assertThat(capabilitiesAfterProcessing(manager))
        .containsExactlyInAnyOrderElementsOf(preset("Manager"));
  }

  @Test
  @DisplayName("Should leave a Manager role extended beyond the preset untouched")
  void given_managerRoleExtendedBeyondPreset_should_leaveCapabilitiesUntouched() {
    Role manager = flywayRole("Manager");
    Set<Capability> extended = new HashSet<>(manager.getCapabilities());
    extended.add(Capability.ACCESS_TENANT_SETTINGS);
    saveCapabilities(manager, extended);

    assertThat(capabilitiesAfterProcessing(manager))
        .containsExactlyInAnyOrderElementsOf(Capability.resolveWithParents(extended))
        .doesNotContain(Capability.MANAGE_TAGS, Capability.ACCESS_PHISHING);
  }

  @Test
  @DisplayName("Should never grant BYPASS to an Admin role holding nothing")
  void given_adminRoleWithoutCapabilities_should_notGrantBypass() {
    Role admin =
        tenantRoleService.createRoleInternal(
            UUID.randomUUID().toString(), "Admin", "Admin", Set.of(), TENANT_ID);

    assertThat(capabilitiesAfterProcessing(admin)).isEmpty();
  }
}
