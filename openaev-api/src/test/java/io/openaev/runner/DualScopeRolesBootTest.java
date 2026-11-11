package io.openaev.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.RoleRepository;
import io.openaev.service.account.AdminPrivilegeService;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

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

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    // The production list with roles on it: the test holds before and after roles joins that list
    registry.add(
        "openaev.tenant.active-tables",
        () ->
            String.join(
                ",",
                Stream.concat(ProductionActiveTables.names().stream(), Stream.of("roles"))
                    .distinct()
                    .toList()));
  }

  @Autowired private InitAdminCommandLineRunner initAdminCommandLineRunner;
  @Autowired private RoleRepository roleRepository;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;

  private Role platformAdminRole() {
    return tenantScopedTransaction.execute(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () -> roleRepository.findById(PLATFORM_ADMIN_ROLE_ID).orElseThrow());
  }

  @Nested
  @DisplayName("platform admin role")
  class PlatformAdminRole {

    @Test
    @DisplayName("given_theRoleAlreadyExists_should_bootAgainThroughTheUpdatePath")
    void given_theRoleAlreadyExists_should_bootAgainThroughTheUpdatePath() {
      // Arrange: the context boot already ran the INSERT path
      assertThat(platformAdminRole().getTenant()).isNull();

      // Act: the same run a restart makes, against the existing row
      initAdminCommandLineRunner.run();

      // Assert
      Role role = platformAdminRole();
      assertThat(role.getTenant()).isNull();
      assertThat(role.getName()).isEqualTo(AdminPrivilegeService.PLATFORM_ADMIN_ACCESS.roleName());
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
