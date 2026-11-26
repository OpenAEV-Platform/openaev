package io.openaev.opencti.connectors.service;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.opencti.connectors.service.LegacyOpenCTIConnectorMigration.LEGACY_CONNECTOR_BASE_ID;
import static io.openaev.opencti.connectors.service.LegacyOpenCTIConnectorMigration.LEGACY_EMAIL_PATTERN;
import static io.openaev.service.account.ReservedServiceAccount.OPENCTI_CONNECTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.opencti.client.OpenCTIClient;
import io.openaev.opencti.client.mutations.Ping;
import io.openaev.opencti.client.mutations.RegisterConnector;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.service.account.AbstractPrivilegeService;
import io.openaev.utils.fixtures.opencti.ResponseFixture;
import java.io.IOException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Pins the legacy-connector cleanup to the default tenant, with the production {@code
 * active-tables} list (so {@code roles} is armed) and a connector of a NON-default tenant.
 *
 * <p>{@link OpenCTIConnectorPrivilegeService#ensurePrivilegedUserExistsForConnector} scopes its
 * transaction to the connector's own tenant before calling {@link
 * LegacyOpenCTIConnectorMigration#deleteLegacyConnectorIfExists}, which deletes the
 * pre-multi-tenant role through {@link io.openaev.service.TenantRoleService#delete} in the default
 * tenant. With {@code roles} armed, a read scoped to the connector's tenant cannot see a role of
 * the default tenant, so an unscoped cleanup call comes back empty and throws, rolling back the
 * whole provisioning.
 */
@TestPropertySource(
    properties = {
      "openaev.xtm.opencti."
          + LegacyOpenCTIConnectorMigrationTenantScopeTest.CONNECTOR_TENANT_ID
          + ".enable=true",
      "openaev.xtm.opencti."
          + LegacyOpenCTIConnectorMigrationTenantScopeTest.CONNECTOR_TENANT_ID
          + ".url=http://opencti.invalid",
      "openaev.xtm.opencti."
          + LegacyOpenCTIConnectorMigrationTenantScopeTest.CONNECTOR_TENANT_ID
          + ".token=2f1a6d84-0003-4b5e-9a01-6d9c1b7e0003"
    })
@DisplayName("legacy OpenCTI connector cleanup under a non-default connector tenant (roles armed)")
class LegacyOpenCTIConnectorMigrationTenantScopeTest extends IntegrationTest {

  /** The tenant the connector under test belongs to; never the default one. */
  static final String CONNECTOR_TENANT_ID = "2f1a6d84-3001-4b5e-9a01-6d9c1b7e3001";

  private static final String LEGACY_EMAIL =
      LEGACY_EMAIL_PATTERN.formatted(LEGACY_CONNECTOR_BASE_ID);

  private static final String CONNECTOR_GROUP_ID =
      AbstractPrivilegeService.getUUIDFromName(
          OPENCTI_CONNECTOR.access().groupId(), CONNECTOR_TENANT_ID);

  private static final String CONNECTOR_ROLE_ID =
      AbstractPrivilegeService.getUUIDFromName(
          OPENCTI_CONNECTOR.access().roleId(), CONNECTOR_TENANT_ID);

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    // The production list, roles included, as the activation left it.
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private OpenCTIConnectorService openCTIConnectorService;
  @Autowired private DataSource dataSource;
  @MockitoBean private OpenCTIClient openCTIClient;

  private JdbcTemplate jdbc;
  private String legacyUserId;

  @BeforeEach
  void seedLegacyAccountAndConnectorTenant() throws IOException {
    jdbc = new JdbcTemplate(dataSource);

    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now()) ON CONFLICT DO NOTHING",
        CONNECTOR_TENANT_ID,
        "legacy-cleanup-" + CONNECTOR_TENANT_ID);

    // The pre-multi-tenant shape: one role and one group, both in the default tenant, held by the
    // single legacy connector user.
    jdbc.update(
        "INSERT INTO roles (role_id, role_name, role_description, tenant_id) VALUES (?, ?, ?, ?)",
        OPENCTI_CONNECTOR.access().roleId(),
        OPENCTI_CONNECTOR.access().roleName(),
        OPENCTI_CONNECTOR.access().roleDescription(),
        DEFAULT_TENANT_UUID);
    jdbc.update(
        "INSERT INTO groups (group_id, group_name, group_description, tenant_id) VALUES (?, ?, ?,"
            + " ?)",
        OPENCTI_CONNECTOR.access().groupId(),
        OPENCTI_CONNECTOR.access().groupName(),
        OPENCTI_CONNECTOR.access().groupDescription(),
        DEFAULT_TENANT_UUID);
    jdbc.update(
        "INSERT INTO groups_roles (role_id, group_id) VALUES (?, ?)",
        OPENCTI_CONNECTOR.access().roleId(),
        OPENCTI_CONNECTOR.access().groupId());

    legacyUserId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO users (user_id, user_email, user_admin, user_status) VALUES (?, ?, false, 0)",
        legacyUserId,
        LEGACY_EMAIL);
    jdbc.update(
        "INSERT INTO users_groups (group_id, user_id) VALUES (?, ?)",
        OPENCTI_CONNECTOR.access().groupId(),
        legacyUserId);

    when(openCTIClient.execute(any(), any(), any(RegisterConnector.class)))
        .thenReturn(ResponseFixture.getOkResponse());
    when(openCTIClient.execute(any(), any(), any(Ping.class)))
        .thenReturn(ResponseFixture.getOkResponse());

    openCTIConnectorService.clearRegisterBackoff();
    openCTIConnectorService.getConnectors().forEach(c -> c.setRegistered(false));
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM groups_roles WHERE role_id = ?", OPENCTI_CONNECTOR.access().roleId());
    jdbc.update("DELETE FROM users_groups WHERE user_id = ?", legacyUserId);
    jdbc.update(
        "DELETE FROM tokens WHERE token_user = ? OR token_user IN (SELECT user_id FROM users"
            + " WHERE user_email = ?)",
        legacyUserId,
        connectorEmail());
    jdbc.update(
        "DELETE FROM users WHERE user_id = ? OR user_email = ?", legacyUserId, LEGACY_EMAIL);
    jdbc.update(
        "DELETE FROM users WHERE user_email = ?",
        OPENCTI_CONNECTOR.emailFor(connectorUnderTest().getId()));
    jdbc.update("DELETE FROM groups WHERE group_id = ?", OPENCTI_CONNECTOR.access().groupId());
    jdbc.update("DELETE FROM roles WHERE role_id = ?", OPENCTI_CONNECTOR.access().roleId());
    // Cascades the connector tenant's own well-known group/role and any remaining users_tenants
    // row.
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", CONNECTOR_TENANT_ID);
  }

  private String connectorEmail() {
    return OPENCTI_CONNECTOR.emailFor(connectorUnderTest().getId());
  }

  private ConnectorBase connectorUnderTest() {
    return openCTIConnectorService.getConnectors().stream()
        .filter(c -> CONNECTOR_TENANT_ID.equals(c.getTenantId()))
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("the connector under test is not configured any more"));
  }

  @Test
  @DisplayName(
      "given a legacy connector account in the default tenant, should delete it and provision the"
          + " connector of another tenant")
  void given_legacyAccountInDefaultTenant_should_deleteItAndProvisionTheOtherTenantConnector() {
    // Act
    assertThatCode(() -> openCTIConnectorService.registerOrPingAllConnectors())
        .doesNotThrowAnyException();

    // Assert - the legacy, default-tenant account is gone
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE user_email = ?", Integer.class, LEGACY_EMAIL))
        .as("the legacy connector user must be deleted")
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM roles WHERE role_id = ? AND tenant_id = ?",
                Integer.class,
                OPENCTI_CONNECTOR.access().roleId(),
                DEFAULT_TENANT_UUID))
        .as("the legacy default-tenant role must be deleted")
        .isZero();

    // Assert - the connector's own tenant-scoped account is provisioned
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE user_email = ?", Integer.class, connectorEmail()))
        .as("the connector of the non-default tenant must be provisioned")
        .isEqualTo(1);
    assertThat(
            jdbc.queryForList(
                "SELECT tenant_id FROM roles WHERE role_id = ?", String.class, CONNECTOR_ROLE_ID))
        .containsExactly(CONNECTOR_TENANT_ID);
    assertThat(
            jdbc.queryForList(
                "SELECT tenant_id FROM groups WHERE group_id = ?",
                String.class,
                CONNECTOR_GROUP_ID))
        .containsExactly(CONNECTOR_TENANT_ID);
  }
}
