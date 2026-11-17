package io.openaev.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.GroupRepository;
import io.openaev.service.account.AdminPrivilegeService;
import io.openaev.utilstest.PinsUnscopedPlatformRead;
import io.openaev.utilstest.WithoutTenantScope;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * With {@code groups} active (dual-scope: the platform admin group has no tenant), the admin
 * bootstrap writes that platform row on every boot, an INSERT on the first one and an UPDATE on
 * every restart, the same way it does for the platform admin role (see {@link
 * DualScopeRolesBootTest}). {@code groups} is not in {@code openaev.tenant.active-tables} in
 * production yet; this class decides and pins the policy ahead of that activation, the way {@link
 * DualScopeRolesBootTest} did for roles.
 *
 * <p>NOT {@code @Transactional}: the runner opens and owns its transaction.
 */
@DisplayName("Admin bootstrap with groups active")
class DualScopeGroupsBootTest extends IntegrationTest {

  private static final String PLATFORM_ADMIN_GROUP_ID =
      AdminPrivilegeService.PLATFORM_ADMIN_ACCESS.groupId();

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    // The production list with groups on it: the test holds before and after groups joins it
    registry.add(
        "openaev.tenant.active-tables",
        () ->
            String.join(
                ",",
                Stream.concat(ProductionActiveTables.names().stream(), Stream.of("groups"))
                    .distinct()
                    .toList()));
  }

  @Autowired private GroupRepository groupRepository;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;

  private Group platformAdminGroup() {
    return tenantScopedTransaction.execute(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () -> groupRepository.findById(PLATFORM_ADMIN_GROUP_ID).orElseThrow());
  }

  /**
   * The decision for {@code groups}, the same one taken for {@code roles}: a platform group is
   * readable with no scope, as {@code can_access_tenant} lets every platform row through, and a
   * tenant group is not.
   */
  @Nested
  @WithoutTenantScope
  @DisplayName("read with no scope")
  class ReadWithNoScope {

    @Test
    @PinsUnscopedPlatformRead(table = "groups")
    @DisplayName("given_noScope_should_readThePlatformAdminGroup")
    void given_noScope_should_readThePlatformAdminGroup() {
      // Act: no transaction is open, the repository opens one with no scope
      Optional<Group> group = groupRepository.findById(PLATFORM_ADMIN_GROUP_ID);

      // Assert
      assertThat(group).isPresent();
      assertThat(group.get().getTenant()).isNull();
    }

    @Test
    @DisplayName("given_noScope_should_notReadATenantGroup")
    void given_noScope_should_notReadATenantGroup() {
      // Arrange: a group of the default tenant, committed
      Group tenantGroup =
          tenantScopedTransaction.execute(
              TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
              () -> {
                Group group = new Group();
                group.setName("group read with no scope");
                group.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
                return groupRepository.save(group);
              });
      try {
        // Act
        Optional<Group> unscoped = groupRepository.findById(tenantGroup.getId());
        Optional<Group> scoped =
            tenantScopedTransaction.execute(
                TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
                () -> groupRepository.findById(tenantGroup.getId()));

        // Assert
        assertThat(unscoped).isEmpty();
        assertThat(scoped).isPresent();
      } finally {
        tenantScopedTransaction.execute(
            TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
            () -> groupRepository.deleteById(tenantGroup.getId()));
      }
    }
  }

  @Nested
  @DisplayName("platform admin group")
  class PlatformAdminGroup {

    @Test
    @DisplayName("given_anUnmarkedTransaction_should_refuseToRewriteThePlatformGroup")
    void given_anUnmarkedTransaction_should_refuseToRewriteThePlatformGroup() {
      // Arrange
      Group group = platformAdminGroup();
      group.setDescription("rewritten by an unmarked transaction");

      // Act + Assert: the same UPDATE, outside a platform-write transaction
      assertThatThrownBy(
              () ->
                  tenantScopedTransaction.execute(
                      TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
                      () -> {
                        groupRepository.saveAndFlush(group);
                      }))
          .isInstanceOf(RuntimeException.class)
          .rootCause()
          .hasMessageContaining(
              "write of a platform row refused: the transaction is not platform-write");
    }
  }
}
