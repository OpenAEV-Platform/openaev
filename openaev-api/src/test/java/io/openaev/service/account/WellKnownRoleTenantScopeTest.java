package io.openaev.service.account;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.service.account.AbstractPrivilegeService.getUUIDFromName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.context.TxCtx;
import io.openaev.rest.exception.TenantWriteScopeException;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Pins the tenant of the well-known role written by the privilege services that take a {@link
 * TxCtx}: the agent service account and the catalog connector account. The OpenCTI connector family
 * has its own pin in {@code OpenCTIConnectorPrivilegeScopeTest}; the admin bootstrap reuses the
 * tenant's Admin role, so its scope is pinned in {@code InitAdminCommandLineRunnerTest}.
 *
 * <p>Not transactional: each call opens and commits its own transaction, as the startup runner, the
 * onboarding and the request do. The role is read back through JDBC, which the statement inspector
 * never rewrites, so the assertion measures what was written and not what a scope lets the test
 * see.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Well-known role of a privilege service lands in the tenant of its scope")
class WellKnownRoleTenantScopeTest extends IntegrationTest {

  /** One entry per service whose role write takes the scope of its caller. */
  enum Family {
    AGENT(ReservedServiceAccount.AGENT.access()),
    CATALOG_CONNECTOR(ReservedServiceAccount.CATALOG_CONNECTOR.access());

    final WellKnownAccess access;

    Family(WellKnownAccess access) {
      this.access = access;
    }

    String roleIdIn(String tenantId) {
      return getUUIDFromName(access.roleId(), tenantId);
    }
  }

  @Autowired private AgentServiceAccountPrivilegeService agentServiceAccountPrivilegeService;
  @Autowired private CatalogConnectorPrivilegeService catalogConnectorPrivilegeService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;

  @BeforeEach
  void seedTwoTenants() throws Exception {
    tenantA = tenantHelper.createTenant("well-known-role-a").getId();
    tenantB = tenantHelper.createTenant("well-known-role-b").getId();
  }

  @AfterEach
  void cleanup() {
    for (String tenantId : new String[] {tenantA, tenantB}) {
      if (tenantId == null) {
        continue;
      }
      jdbc.update(
          "DELETE FROM users_groups WHERE group_id IN"
              + " (SELECT group_id FROM groups WHERE tenant_id = ?)",
          tenantId);
      jdbc.update("DELETE FROM users_tenants WHERE tenant_id = ?", tenantId);
      List<String> accountEmails =
          List.of(
              ReservedServiceAccount.AGENT.emailPattern().formatted(tenantId),
              CatalogConnectorPrivilegeService.accountEmail(tenantId));
      for (String email : accountEmails) {
        jdbc.update(
            "DELETE FROM tokens WHERE token_user IN (SELECT user_id FROM users WHERE user_email = ?)",
            email);
        jdbc.update("DELETE FROM users WHERE user_email = ?", email);
      }
    }
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
  }

  private void ensureRole(Family family, TxCtx ctx, String tenantId) {
    switch (family) {
      case AGENT -> agentServiceAccountPrivilegeService.ensurePrivilegedUserExists(ctx, tenantId);
      case CATALOG_CONNECTOR -> catalogConnectorPrivilegeService.getOrCreateToken(ctx, tenantId);
    }
  }

  private List<String> tenantsOfRole(String roleId) {
    return jdbc.queryForList("SELECT tenant_id FROM roles WHERE role_id = ?", String.class, roleId);
  }

  @Nested
  @DisplayName("a scope that pins the tenant")
  class PinnedScope {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Family.class)
    @DisplayName("given the scope of tenant B, should write the role in tenant B")
    void given_scopeOfTenantB_should_writeTheRoleInTenantB(Family family) {
      // Arrange
      TxCtx scopeOfB = TxCtx.forTenant(tenantB);

      // Act
      ensureRole(family, scopeOfB, tenantB);

      // Assert
      assertThat(tenantsOfRole(family.roleIdIn(tenantB))).containsExactly(tenantB);
      assertThat(tenantsOfRole(family.roleIdIn(tenantA)))
          .as("no role is written in the other tenant")
          .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Family.class)
    @DisplayName(
        "given the scope of the default tenant, should write the role in the default tenant")
    void given_scopeOfDefaultTenant_should_writeTheRoleInTheDefaultTenant(Family family) {
      // Arrange
      TxCtx scopeOfDefault = TxCtx.forTenant(DEFAULT_TENANT_UUID);

      // Act
      ensureRole(family, scopeOfDefault, DEFAULT_TENANT_UUID);

      // Assert
      assertThat(tenantsOfRole(family.roleIdIn(DEFAULT_TENANT_UUID)))
          .containsExactly(DEFAULT_TENANT_UUID);
      assertThat(tenantsOfRole(family.roleIdIn(tenantB))).isEmpty();
    }
  }

  @Nested
  @DisplayName("a scope that does not contain the tenant")
  class ForeignScope {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Family.class)
    @DisplayName("given no scope, should refuse the write and leave the roles untouched")
    void given_noScope_should_refuseTheWrite(Family family) {
      // Arrange
      int rolesBefore = tenantsOfRole(family.roleIdIn(tenantB)).size();

      // Act / Assert
      assertThatThrownBy(() -> ensureRole(family, TxCtx.missing(), tenantB))
          .isInstanceOf(TenantWriteScopeException.class);
      assertThat(tenantsOfRole(family.roleIdIn(tenantB))).hasSize(rolesBefore);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Family.class)
    @DisplayName(
        "given the scope of tenant A for tenant B, should refuse the write and leave the roles"
            + " untouched")
    void given_scopeOfAnotherTenant_should_refuseTheWrite(Family family) {
      // Arrange
      int rolesBefore = tenantsOfRole(family.roleIdIn(tenantB)).size();

      // Act / Assert
      assertThatThrownBy(() -> ensureRole(family, TxCtx.forTenant(tenantA), tenantB))
          .isInstanceOf(TenantWriteScopeException.class);
      assertThat(tenantsOfRole(family.roleIdIn(tenantB))).hasSize(rolesBefore);
      assertThat(tenantsOfRole(family.roleIdIn(tenantA))).isEmpty();
    }
  }
}
