package io.openaev.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.GroupRepository;
import io.openaev.service.account.AdminPrivilegeService;
import io.openaev.utilstest.PinsUnscopedPlatformRead;
import io.openaev.utilstest.WithoutTenantScope;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * With {@code groups} active (dual-scope: the platform admin group has no tenant), the admin
 * bootstrap writes that platform row on every boot, an INSERT on the first one and an UPDATE on
 * every restart, the same way it does for the platform admin role (see {@link
 * DualScopeRolesBootTest}). The policy is the one decided for {@code roles}: the platform group is
 * readable with no scope and written only under the platform-write marker.
 *
 * <p>NOT {@code @Transactional}: the runner opens and owns its transaction.
 */
@DisplayName("Admin bootstrap with groups active")
class DualScopeGroupsBootTest extends IntegrationTest {

  private static final String PLATFORM_ADMIN_GROUP_ID =
      AdminPrivilegeService.PLATFORM_ADMIN_ACCESS.groupId();

  @Autowired private InitAdminCommandLineRunner initAdminCommandLineRunner;
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
    @DisplayName("given_theGroupAlreadyExists_should_bootAgainThroughTheUpdatePath")
    void given_theGroupAlreadyExists_should_bootAgainThroughTheUpdatePath() {
      // Arrange: the context boot already ran the INSERT path; the description is changed so the
      // restart has a real UPDATE to write back. A run that fails before the assertion leaves the
      // changed description in the shared database
      assertThat(platformAdminGroup().getTenant()).isNull();
      tenantScopedTransaction.executeAsPlatformWrite(
          TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
          () -> {
            Group group = groupRepository.findById(PLATFORM_ADMIN_GROUP_ID).orElseThrow();
            group.setDescription("changed before the restart");
            groupRepository.saveAndFlush(group);
          });
      assertThat(platformAdminGroup().getDescription()).isEqualTo("changed before the restart");

      // Act: the same run a restart makes, against the existing row
      initAdminCommandLineRunner.run();

      // Assert
      Group group = platformAdminGroup();
      assertThat(group.getTenant()).isNull();
      assertThat(group.getName())
          .isEqualTo(AdminPrivilegeService.PLATFORM_ADMIN_ACCESS.groupName());
      assertThat(group.getDescription())
          .isEqualTo(AdminPrivilegeService.PLATFORM_ADMIN_ACCESS.groupDescription());
    }

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
