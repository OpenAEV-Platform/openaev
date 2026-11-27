package io.openaev.processor.core;

import static io.openaev.service.account.AbstractPrivilegeService.getUUIDFromName;
import static io.openaev.service.catalog_connectors.CatalogConnectorIngestionService.OPENAEV_KEY_TOKEN;
import static io.openaev.utils.fixtures.CatalogConnectorFixture.createDefaultCatalogConnectorManagedByXtmComposer;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createConnectorInstanceConfiguration;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createDefaultConnectorInstance;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.service.TenantRoleService;
import io.openaev.service.account.CatalogConnectorPrivilegeService;
import io.openaev.service.account.ReservedServiceAccount;
import io.openaev.service.account.WellKnownAccess;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceConfigurationComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Sync catalog connectors role migration tests")
class V20261127_Sync_catalog_connectors_roleTest extends IntegrationTest {

  private static final WellKnownAccess ACCESS = ReservedServiceAccount.CATALOG_CONNECTOR.access();

  @Autowired private V20261127_Sync_catalog_connectors_role migration;
  @Autowired private CatalogConnectorPrivilegeService catalogConnectorPrivilegeService;
  @Autowired private TenantRoleService tenantRoleService;
  @Autowired private CatalogConnectorComposer catalogConnectorComposer;
  @Autowired private ConnectorInstanceComposer connectorInstanceComposer;
  @Autowired private ConnectorInstanceConfigurationComposer configurationComposer;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    catalogConnectorComposer.reset();
    connectorInstanceComposer.reset();
    configurationComposer.reset();
  }

  private void givenDeployedConnectorWithRoleMissingSyncCatalog(String tenantId) throws Exception {
    String token =
        catalogConnectorPrivilegeService.getOrCreateToken(TxCtx.forTenant(tenantId), tenantId);
    connectorInstanceComposer
        .forConnectorInstance(createDefaultConnectorInstance(tenantId))
        .withCatalogConnector(
            catalogConnectorComposer.forCatalogConnector(
                createDefaultCatalogConnectorManagedByXtmComposer(
                    "Deployed collector " + UUID.randomUUID())))
        .withConnectorInstanceConfiguration(
            configurationComposer.forConnectorInstanceConfiguration(
                createConnectorInstanceConfiguration(OPENAEV_KEY_TOKEN, token)))
        .persist();
    Set<Capability> withoutSyncCatalog = EnumSet.copyOf(ACCESS.capabilities());
    withoutSyncCatalog.remove(Capability.SYNC_CATALOG);
    tenantRoleService.updateRoleInternal(
        roleId(tenantId),
        ACCESS.roleName(),
        ACCESS.roleDescription(),
        withoutSyncCatalog,
        tenantId);
    entityManager.flush();
    entityManager.clear();
  }

  private String roleId(String tenantId) {
    return getUUIDFromName(ACCESS.roleId(), tenantId);
  }

  private Optional<Role> role(String tenantId) {
    return tenantRoleService.findByIdAndTenant(roleId(tenantId), tenantId);
  }

  @Test
  @DisplayName("Gives the catalog connectors role the right to send releases")
  void given_deployedConnectorAndRoleWithoutSyncCatalog_should_addSyncCatalog() throws Exception {
    // Arrange
    givenDeployedConnectorWithRoleMissingSyncCatalog(Tenant.DEFAULT_TENANT_UUID);

    // Act
    migration.doMigrate(new Tenant(Tenant.DEFAULT_TENANT_UUID));

    // Assert
    entityManager.flush();
    entityManager.clear();
    assertThat(role(Tenant.DEFAULT_TENANT_UUID).orElseThrow().getCapabilities())
        .contains(Capability.SYNC_CATALOG);
  }

  @Test
  @DisplayName("Creates no role in a tenant without connector deployed from the catalog")
  void given_tenantWithoutConnector_should_notCreateRole() throws Exception {
    // Arrange
    Tenant tenantWithoutConnector = tenantIsolationHelper.createTenant("Without connectors");

    // Act
    boolean migrated = migration.doMigrate(tenantWithoutConnector);

    // Assert
    assertThat(migrated).isTrue();
    assertThat(role(tenantWithoutConnector.getId())).isEmpty();
  }
}
