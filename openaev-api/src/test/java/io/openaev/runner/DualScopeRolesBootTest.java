package io.openaev.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.RoleRepository;
import io.openaev.service.account.AdminPrivilegeService;
import io.openaev.utilstest.PinsUnscopedPlatformRead;
import io.openaev.utilstest.WithoutTenantScope;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * With {@code roles} active (dual-scope: the platform {@code Admin} role has no tenant), the admin
 * bootstrap writes that platform row on every boot, an INSERT on the first one and an UPDATE on
 * every restart. The context loading at all is the first half of the proof: the bootstrap runs as a
 * {@code CommandLineRunner} during it.
 *
 * <p>NOT {@code @Transactional}: the runner opens and owns its transaction.
 */
@DisplayName("Admin bootstrap with roles active")
class DualScopeRolesBootTest extends IntegrationTest {

  private static final String PLATFORM_ADMIN_ROLE_ID =
      AdminPrivilegeService.PLATFORM_ADMIN_ACCESS.roleId();

  @Autowired private InitAdminCommandLineRunner initAdminCommandLineRunner;
  @Autowired private RoleRepository roleRepository;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;

  private Role platformAdminRole() {
    return tenantScopedTransaction.execute(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () -> roleRepository.findById(PLATFORM_ADMIN_ROLE_ID).orElseThrow());
  }

  /**
   * The decision for {@code roles}: a platform role is readable with no scope, as {@code
   * can_access_tenant} lets every platform row through, and a tenant role is not. The loads that
   * read roles with no scope (a token or a login, a user with no membership) evaluate the platform
   * roles only.
   */
  @Nested
  @WithoutTenantScope
  @DisplayName("read with no scope")
  class ReadWithNoScope {

    @Test
    @PinsUnscopedPlatformRead(table = "roles")
    @DisplayName("given_noScope_should_readThePlatformAdminRoleWithItsCapabilities")
    void given_noScope_should_readThePlatformAdminRoleWithItsCapabilities() {
      // Act: no transaction is open, the repository opens one with no scope
      Optional<Role> role = roleRepository.findById(PLATFORM_ADMIN_ROLE_ID);

      // Assert
      assertThat(role).isPresent();
      assertThat(role.get().getTenant()).isNull();
      assertThat(role.get().getCapabilities()).containsExactly(Capability.BYPASS);
    }

    @Test
    @DisplayName("given_noScope_should_notReadATenantRole")
    void given_noScope_should_notReadATenantRole() {
      // Arrange: a role of the default tenant, committed
      Role tenantRole =
          tenantScopedTransaction.execute(
              TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
              () -> {
                Role role = new Role();
                role.setName("role read with no scope");
                role.setCapabilities(Set.of(Capability.ACCESS_ASSESSMENT));
                role.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
                return roleRepository.save(role);
              });
      try {
        // Act
        Optional<Role> unscoped = roleRepository.findById(tenantRole.getId());
        Optional<Role> scoped =
            tenantScopedTransaction.execute(
                TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
                () -> roleRepository.findById(tenantRole.getId()));

        // Assert
        assertThat(unscoped).isEmpty();
        assertThat(scoped).isPresent();
      } finally {
        tenantScopedTransaction.execute(
            TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
            () -> roleRepository.deleteById(tenantRole.getId()));
      }
    }
  }

  @Nested
  @DisplayName("platform admin role")
  class PlatformAdminRole {

    @Test
    @DisplayName("given_theRoleAlreadyExists_should_bootAgainThroughTheUpdatePath")
    void given_theRoleAlreadyExists_should_bootAgainThroughTheUpdatePath() {
      // Arrange: the context boot already ran the INSERT path; the description is changed so the
      // restart has a real UPDATE to write back. A run that fails before the assertion leaves the
      // changed description in the shared database
      assertThat(platformAdminRole().getTenant()).isNull();
      tenantScopedTransaction.executeAsPlatformWrite(
          TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
          () -> {
            Role role = roleRepository.findById(PLATFORM_ADMIN_ROLE_ID).orElseThrow();
            role.setDescription("changed before the restart");
            roleRepository.saveAndFlush(role);
          });
      assertThat(platformAdminRole().getDescription()).isEqualTo("changed before the restart");

      // Act: the same run a restart makes, against the existing row
      initAdminCommandLineRunner.run();

      // Assert
      Role role = platformAdminRole();
      assertThat(role.getTenant()).isNull();
      assertThat(role.getName()).isEqualTo(AdminPrivilegeService.PLATFORM_ADMIN_ACCESS.roleName());
      assertThat(role.getDescription())
          .isEqualTo(AdminPrivilegeService.PLATFORM_ADMIN_ACCESS.roleDescription());
    }

    @Test
    @DisplayName("given_anUnmarkedTransaction_should_refuseToRewriteThePlatformRole")
    void given_anUnmarkedTransaction_should_refuseToRewriteThePlatformRole() {
      // Arrange
      Role role = platformAdminRole();
      role.setDescription("rewritten by an unmarked transaction");

      // Act + Assert: the same UPDATE, outside a platform-write transaction
      assertThatThrownBy(
              () ->
                  tenantScopedTransaction.execute(
                      TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
                      () -> {
                        roleRepository.saveAndFlush(role);
                      }))
          .isInstanceOf(RuntimeException.class)
          .rootCause()
          .hasMessageContaining(
              "write of a platform row refused: the transaction is not platform-write");
    }
  }
}
