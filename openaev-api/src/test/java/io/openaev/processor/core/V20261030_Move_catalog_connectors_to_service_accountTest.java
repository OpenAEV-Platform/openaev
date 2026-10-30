package io.openaev.processor.core;

import static io.openaev.service.account.ReservedServiceAccount.CATALOG_CONNECTOR;
import static io.openaev.service.catalog_connectors.CatalogConnectorIngestionService.OPENAEV_KEY_TOKEN;
import static io.openaev.utils.fixtures.CatalogConnectorFixture.createDefaultCatalogConnectorManagedByXtmComposer;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createConnectorInstanceConfiguration;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createDefaultConnectorInstance;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.IntegrationTest;
import io.openaev.database.model.ConnectorInstanceConfiguration;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.service.UserService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceConfigurationComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Move catalog connectors to the service account migration tests")
class V20261030_Move_catalog_connectors_to_service_accountTest extends IntegrationTest {

  private static final String DEPLOYER_TOKEN = "deployer-token";

  @Autowired private V20261030_Move_catalog_connectors_to_service_account migration;
  @Autowired private CatalogConnectorComposer catalogConnectorComposer;
  @Autowired private ConnectorInstanceComposer connectorInstanceComposer;
  @Autowired private ConnectorInstanceConfigurationComposer configurationComposer;
  @Autowired private ConnectorInstanceConfigurationRepository configurationRepository;
  @Autowired private UserService userService;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    catalogConnectorComposer.reset();
    connectorInstanceComposer.reset();
    configurationComposer.reset();
  }

  /** Persists a connector instance in the current tenant, holding {@code token}. */
  private ConnectorInstanceConfiguration givenInstanceHoldingToken(String token) throws JsonProcessingException {
    ConnectorInstanceConfiguration tokenConfiguration =
        createConnectorInstanceConfiguration(OPENAEV_KEY_TOKEN, token);
    connectorInstanceComposer
        .forConnectorInstance(createDefaultConnectorInstance())
        .withCatalogConnector(
            catalogConnectorComposer.forCatalogConnector(
                createDefaultCatalogConnectorManagedByXtmComposer("Deployed collector")))
        .withConnectorInstanceConfiguration(
            configurationComposer.forConnectorInstanceConfiguration(tokenConfiguration))
        .persist();
    return tokenConfiguration;
  }

  private String storedToken(ConnectorInstanceConfiguration configuration) {
    return configurationRepository
        .findById(configuration.getId())
        .orElseThrow()
        .getValue()
        .asText();
  }

  private Optional<User> serviceAccount(String tenantId) {
    return userService.findByEmailIgnoreCase(CATALOG_CONNECTOR.emailPattern().formatted(tenantId));
  }

  @Test
  @DisplayName("Moves the tenant's connector instances to the service account token")
  void given_instanceHoldingDeployerToken_should_moveItToServiceAccountToken() throws JsonProcessingException {
    // Arrange
    ConnectorInstanceConfiguration tokenConfiguration = givenInstanceHoldingToken(DEPLOYER_TOKEN);
    entityManager.flush();
    entityManager.clear();

    // Act
    migration.doMigrate(new Tenant(Tenant.DEFAULT_TENANT_UUID));

    // Assert
    entityManager.flush();
    entityManager.clear();
    String accountToken =
        serviceAccount(Tenant.DEFAULT_TENANT_UUID).orElseThrow().getTokens().get(0).getValue();
    assertThat(storedToken(tokenConfiguration))
        .isEqualTo(accountToken)
        .isNotEqualTo(DEPLOYER_TOKEN);
  }

  @Test
  @DisplayName("Creates no service account in a tenant without connector instance")
  void given_tenantWithoutInstance_should_notCreateServiceAccount() throws Exception {
    // Arrange
    Tenant tenantWithoutInstance = tenantIsolationHelper.createTenant("Without connectors");

    // Act
    boolean migrated = migration.doMigrate(tenantWithoutInstance);

    // Assert
    assertThat(migrated).isTrue();
    assertThat(serviceAccount(tenantWithoutInstance.getId())).isEmpty();
  }

  @Test
  @DisplayName("Leaves the connector instances of another tenant untouched")
  void given_instanceInAnotherTenant_should_keepItsToken() throws Exception {
    // Arrange
    givenInstanceHoldingToken(DEPLOYER_TOKEN);
    Tenant otherTenant = tenantIsolationHelper.createTenant("Other tenant");
    tenantIsolationHelper.switchToTenant(otherTenant.getId(), entityManager);
    ConnectorInstanceConfiguration otherTenantConfiguration =
        givenInstanceHoldingToken("other-tenant-token");
    tenantIsolationHelper.switchToTenant(Tenant.DEFAULT_TENANT_UUID, entityManager);

    // Act
    migration.doMigrate(new Tenant(Tenant.DEFAULT_TENANT_UUID));

    // Assert
    entityManager.flush();
    entityManager.clear();
    assertThat(storedToken(otherTenantConfiguration)).isEqualTo("other-tenant-token");
    assertThat(serviceAccount(otherTenant.getId())).isEmpty();
  }
}
